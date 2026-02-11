package me.sucixr.kitin.network.qos;

import me.sucixr.kitin.config.KitinConfig;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class GlobalChunkLimiter {

    // 默认组名
    public static final String DEFAULT_GROUP = "default";

    // 存储所有组的限流器：GroupName -> Limiter
    private static final Map<String, AllocatingRateLimiter> limiters = new ConcurrentHashMap<>();

    // 存储规则列表（有序）
    private static final List<GroupRule> rules = new ArrayList<>();

    // 存储组的级联链：GroupName -> List<Limiter> (包含自身和所有上游)
    private static final Map<String, List<AllocatingRateLimiter>> groupChains = new ConcurrentHashMap<>();

    // 玩家组缓存：PlayerUUID -> GroupName
    private static final Map<java.util.UUID, String> playerGroupCache = new ConcurrentHashMap<>();

    // 锁对象
    private static final Object configMutex = new Object();
    private static final Object acquireMutex = new Object(); // 用于级联扣费的原子锁

    /**
     * 初始化或重载配置
     * @param configRules 从配置文件读取的规则列表
     */
    public static void reload(List<GroupRule> configRules) {
        synchronized (configMutex) {
            limiters.clear();
            rules.clear();
            groupChains.clear();
            playerGroupCache.clear();

            if (configRules != null) {
                rules.addAll(configRules);
            }

            // 1. 创建所有限流器
            for (GroupRule rule : rules) {
                createLimiter(rule.name, rule.rate);
            }
            // 确保默认组存在（如果未配置）
            if (!limiters.containsKey(DEFAULT_GROUP)) {
                createLimiter(DEFAULT_GROUP, -1); // -1 代表不限速，但在链中可能作为占位
            }

            // 2. 构建级联链 (预计算，避免运行时递归)
            for (GroupRule rule : rules) {
                List<AllocatingRateLimiter> chain = new ArrayList<>();
                String currentName = rule.name;
                
                // 防止循环引用的简单计数器
                int depth = 0;
                while (currentName != null && depth < 10) {
                    AllocatingRateLimiter limiter = limiters.get(currentName);
                    if (limiter != null) {
                        chain.add(limiter);
                    }
                    
                    // 查找上游
                    String upstream = getUpstreamName(currentName);
                    if (upstream == null || upstream.equals(currentName)) {
                        break;
                    }
                    currentName = upstream;
                    depth++;
                }
                groupChains.put(rule.name, chain);
            }
            // 默认组的链
            if (!groupChains.containsKey(DEFAULT_GROUP)) {
                AllocatingRateLimiter defLimiter = limiters.get(DEFAULT_GROUP);
                if (defLimiter != null) {
                    groupChains.put(DEFAULT_GROUP, Collections.singletonList(defLimiter));
                } else {
                    groupChains.put(DEFAULT_GROUP, Collections.emptyList());
                }
            }
        }
    }

    private static String getUpstreamName(String groupName) {
        for (GroupRule rule : rules) {
            if (rule.name.equals(groupName)) {
                return rule.upstream;
            }
        }
        return null;
    }

    private static void createLimiter(String name, double rate) {
        if (rate <= 0) return; // 不限速
        AllocatingRateLimiter limiter = new AllocatingRateLimiter(TimeUnit.SECONDS.toNanos(1L), rate);
        limiter.reset(System.nanoTime());
        limiters.put(name, limiter);
    }

    /**
     * 尝试获取发送 1 个区块的权限
     */
    public static boolean tryAcquire(ServerPlayer player) {
        if (limiters.isEmpty()) return true;

        String group = resolveGroup(player);
        List<AllocatingRateLimiter> chain = groupChains.get(group);

        if (chain == null || chain.isEmpty()) {
            return true;
        }

        // 使用全局锁确保级联扣费的原子性
        // 虽然是全局锁，但仅涉及简单的数学计算，性能影响极小
        synchronized (acquireMutex) {
            long now = System.nanoTime();

            // 1. 先更新所有涉及的限流器状态 (发工资)
            for (AllocatingRateLimiter limiter : chain) {
                double maxBurst = Math.max(1.0, limiter.rate * KitinConfig.globalChunkSendBurstFactor);
                limiter.tickAllocation(now, maxBurst);
            }

            // 2. 检查是否所有限流器都有足够的余额 (预检)
            for (AllocatingRateLimiter limiter : chain) {
                if (!limiter.canTake(1)) {
                    return false; // 只要有一个不允许，整体拒绝
                }
            }

            // 3. 实际扣费
            for (AllocatingRateLimiter limiter : chain) {
                limiter.take(1);
            }

            return true;
        }
    }

    // 兼容旧API
    public static boolean tryAcquire() {
        return tryAcquire(DEFAULT_GROUP);
    }
    
    private static boolean tryAcquire(String groupName) {
        List<AllocatingRateLimiter> chain = groupChains.get(groupName);
        if (chain == null || chain.isEmpty()) return true;

        synchronized (acquireMutex) {
            long now = System.nanoTime();
            for (AllocatingRateLimiter limiter : chain) {
                double maxBurst = Math.max(1.0, limiter.rate * KitinConfig.globalChunkSendBurstFactor);
                limiter.tickAllocation(now, maxBurst);
            }
            for (AllocatingRateLimiter limiter : chain) {
                if (!limiter.canTake(1)) return false;
            }
            for (AllocatingRateLimiter limiter : chain) {
                limiter.take(1);
            }
            return true;
        }
    }

    public static void setLimit(double chunksPerSecond) {
        synchronized (configMutex) {
            if (rules.isEmpty()) {
                rules.add(new GroupRule(DEFAULT_GROUP, chunksPerSecond, null, null, p -> true));
                createLimiter(DEFAULT_GROUP, chunksPerSecond);
                reload(new ArrayList<>(rules)); // 重新构建链
            }
        }
    }

    private static String resolveGroup(ServerPlayer player) {
        return playerGroupCache.computeIfAbsent(player.getUUID(), uuid -> {
            for (GroupRule rule : rules) {
                if (rule.matches(player)) {
                    return rule.name;
                }
            }
            return DEFAULT_GROUP;
        });
    }

    public static void onPlayerQuit(ServerPlayer player) {
        playerGroupCache.remove(player.getUUID());
    }

    // ==================================================================================
    // 规则定义类
    // ==================================================================================

    public static class GroupRule {
        public final String name;
        public final double rate;
        public final String upstream; // 上游组名
        public final String virtualHost; // 虚拟主机名匹配
        private final Predicate<ServerPlayer> matcher;

        public GroupRule(String name, double rate, String upstream, String virtualHost, Predicate<ServerPlayer> matcher) {
            this.name = name;
            this.rate = rate;
            this.upstream = upstream;
            this.virtualHost = virtualHost;
            this.matcher = matcher;
        }

        public boolean matches(ServerPlayer player) {
            return matcher.test(player);
        }
    }

    // ==================================================================================
    // 修改后的 AllocatingRateLimiter
    // ==================================================================================

    private static final class AllocatingRateLimiter {
        private final long maxGranularity;
        public final double rate; // 存储速率，方便访问

        private double allocation = 0.0;
        private long lastAllocationUpdate;

        private double takeCarry = 0.0;
        // 移除了 lastTakeUpdate，因为我们现在统一在 tickAllocation 更新时间

        public AllocatingRateLimiter(final long maxGranularity, double rate) {
            this.maxGranularity = maxGranularity;
            this.rate = rate;
        }

        public void reset(final long time) {
            this.allocation = 0.0;
            this.lastAllocationUpdate = time;
            this.takeCarry = 0.0;
        }

        public void tickAllocation(final long time, final double maxAllocation) {
            long timeDiff = time - this.lastAllocationUpdate;
            if (timeDiff < 0) timeDiff = 0;

            final long diff = Math.min(this.maxGranularity, timeDiff);
            this.lastAllocationUpdate = time;

            this.allocation = Math.min(maxAllocation - this.takeCarry, this.allocation + this.rate * (diff * 1.0E-9D));
        }

        // 检查是否足够扣除，不修改状态
        public boolean canTake(long amount) {
            // 逻辑：floor(takeCarry + allocation) >= amount
            // 因为 take = min(amount - takeCarry, allocation)
            // 实际获得 = takeCarry + take
            // 如果 allocation 足够大，take = amount - takeCarry，实际获得 = amount
            // 如果 allocation 不够，take = allocation，实际获得 = takeCarry + allocation
            return Math.floor(this.takeCarry + this.allocation) >= amount;
        }

        // 实际扣除，假设已经检查过 canTake
        public void take(long amount) {
            final double take = Math.min((double)amount - this.takeCarry, this.allocation);
            
            // 这里的逻辑是：我们想要凑齐 'amount' 个整数
            // 实际凑到的浮点数是 this.takeCarry + take
            // 理论上如果 canTake 返回 true，那么 floor(this.takeCarry + take) 应该 >= amount
            
            double total = this.takeCarry + take;
            long retInteger = (long)Math.floor(total);
            
            this.allocation -= take;
            this.takeCarry = total - retInteger;
        }
    }
}
