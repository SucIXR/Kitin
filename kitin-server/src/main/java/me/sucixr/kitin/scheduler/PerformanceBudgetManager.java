package me.sucixr.kitin.scheduler;

import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import io.netty.channel.Channel;
import java.util.WeakHashMap;
import java.util.Map;

public class PerformanceBudgetManager {
    private static final PerformanceBudgetManager INSTANCE = new PerformanceBudgetManager();
    public static PerformanceBudgetManager getInstance() { return INSTANCE; }

    private static final double SCALE = 16.0;
    private double currentStressLevel = 1.0;
    // --- 新增：平滑控制参数 ---
    private double targetStressLevel = 1.0; // 目标值
    private static final double SMOOTH_FACTOR = 0.1; // 平滑系数 (0.1 代表每 tick 只改变 10% 的差距)
    // --- 新增：玩家网络平滑缓存 ---
    // 使用 WeakHashMap 自动管理玩家退出后的内存释放
    private final Map<ServerPlayer, Double> playerNetSmoothCache = new WeakHashMap<>();
    // 记录每个玩家上一次的 Ping 用于计算抖动
    private final Map<ServerPlayer, Integer> playerLastPingCache = new WeakHashMap<>();

    //private int lastPing = -1;

    // --- 新增：可配置的调度区间 ---
    private final double MIN_THRESHOLD = 10.0; // 开始调度的 MSPT (比如 30.0 或 5.0)
    private final double MAX_THRESHOLD = 50.0; // 彻底压制的 MSPT (固定 50.0 比较好)

    public double getCurrentStressLevel() { return this.currentStressLevel; }

    public void onTickStart(long currentMspt) {
        // 核心：归一化处理 (将 MSPT 映射到 0.0 ~ 1.0 之间)
        double t = (currentMspt - MIN_THRESHOLD) / (MAX_THRESHOLD - MIN_THRESHOLD);

        // 限制范围在 [0, 1]
        t = Math.max(0.0, Math.min(1.0, t));

        // 使用 Smoothstep 公式实现平滑过渡：3t^2 - 2t^3
        // 这个公式能保证在起点和终点处的变化率都是 0，不会产生突变
        double smoothPenalty = t * t * (3 - 2 * t);
        // 修正：先更新 target，再更新 current
        this.targetStressLevel = 1.0 - (smoothPenalty * 0.95);
        this.currentStressLevel += (this.targetStressLevel - this.currentStressLevel) * SMOOTH_FACTOR;
    }

        public boolean shouldProcessAI(Entity entity) {
        double stress = this.currentStressLevel;

        // 1. 猪灵激进降频
        if (stress < 0.95) {
            if (entity instanceof net.minecraft.world.entity.monster.zombie.ZombifiedPiglin) {
                if (entity.level().random.nextDouble() > (stress * stress)) return false;
            }
        }

        ServerPlayer player = (ServerPlayer) entity.level().getNearestPlayer(entity, 128);
        if (player == null) return false;

        // 2. 网络健康检测 (保持原样，它是对 stress 的二次修正)
        double instantNetHealth = 1.0;
        try {
            Channel channel = player.connection.connection.channel;
            if (channel != null) {
                if (!channel.isWritable()) instantNetHealth *= 0.3;
                long pending = channel.bytesBeforeUnwritable();
                if (pending < 524288) instantNetHealth *= 0.7;
            }
        } catch (Throwable t_err) {}
        //抖动检测
        int currentPing = player.connection.latency();
        Integer lastPingVal = playerLastPingCache.get(player); // 拿取局部变量
        if (lastPingVal != null && lastPingVal != -1) { // 检查局部变量
            if (Math.abs(currentPing - lastPingVal) > 120) {
                instantNetHealth *= 0.8;
            }
        }
        playerLastPingCache.put(player, currentPing);

        // 应用 EMA 平滑处理网络因子
        double smoothedNet = playerNetSmoothCache.getOrDefault(player, 1.0);
        // 网络恢复可以快一点 (0.2)，网络恶化可以慢一点，或者统一使用 0.1
        smoothedNet += (instantNetHealth - smoothedNet) * 0.1;
        playerNetSmoothCache.put(player, smoothedNet);

        // 3. 距离与视野
        double distance = entity.distanceTo(player);
        double distanceProb = 1.0 / (1.0 + Math.pow(distance / SCALE, 2));

        Vec3 lookVec = player.getLookAngle();
        Vec3 toEntityVec = entity.position().subtract(player.position()).normalize();
        double dot = lookVec.dot(toEntityVec);
        double viewMultiplier = dot > 0.5 ? 1.0 : (dot > 0 ? 0.5 : 0.1);

        // 综合判定
        // 确保使用 smoothedNet 才能让 AI 动作不因为一瞬间的丢包而抽搐
        double finalProbability = distanceProb * viewMultiplier * stress * smoothedNet;

        double minSafeDistance = (entity instanceof net.minecraft.world.entity.monster.zombie.ZombifiedPiglin) ? 2.0 : 4.0;
        if (distance < minSafeDistance) return true;

        return entity.getRandom().nextDouble() < finalProbability;
    }
}