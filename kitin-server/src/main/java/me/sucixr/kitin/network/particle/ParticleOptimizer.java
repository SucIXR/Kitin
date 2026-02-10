package me.sucixr.kitin.network.particle;

import me.sucixr.kitin.config.KitinConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;


/**
 * [Kitin 核心优化] 智能粒子管线优化器
 * 负责粒子的缓冲、剔除、排序和打包，解决高频粒子导致的网络拥塞和客户端卡顿。
 */
public class ParticleOptimizer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ParticleOptimizer.class);

    // === 调优参数 ===
    private static final int PLAYER_MAX_PARTICLES_PER_PACKET = KitinConfig.playerMaxParticlesPerPacket;
    private static final int MAX_BUFFER_SIZE = KitinConfig.playerMaxBufferSize;
    private static final int PLAYER_MIN_OPTIMIZE_THRESHOLD = KitinConfig.playerMinOptimizeThreshold;
    
    // === 全局流控参数 ===
    // 全服每 Tick (50ms) 允许发送的最大粒子总数
    private static final int GLOBAL_MAX_PACKET_PARTICLES_PER_TICK = KitinConfig.globalMaxPacketParticlesPerTick;
    // 粒子包最大允许延迟的 Tick 数，超过则丢弃
    private static final int GLOBAL_MAX_DELAY_TICKS = KitinConfig.globalMaxDelayTicks;

    // 粒子缓冲区
    private final List<ClientboundLevelParticlesPacket> buffer = new ArrayList<>();
    // 递归锁：防止在 flush 发包时再次触发拦截
    private boolean isProcessing = false;
    // 当前缓冲区内的粒子已经滞留了多少 Tick
    private int delayedTicks = 0;

    private final ServerPlayer player;

    public ParticleOptimizer(ServerPlayer player) {
        this.player = player;
    }

    /**
     * 全局流量控制器 (线程安全)
     */
    private static class GlobalLimiter {
        private static final AtomicInteger sentThisTick = new AtomicInteger(0);
        private static final AtomicLong lastTickTime = new AtomicLong(0);

        /**
         * 尝试申请发送配额
         * @param amount 请求发送的粒子数量
         * @return true 表示允许发送，false 表示配额已满
         */
        public static boolean tryAcquire(int amount) {
            long currentTick = System.currentTimeMillis() / 50; // 简单的 Tick 估算
            long last = lastTickTime.get();
            
            if (currentTick != last) {
                // 尝试更新 Tick 时间，只有一个线程会成功重置计数器
                if (lastTickTime.compareAndSet(last, currentTick)) {
                    sentThisTick.set(0);
                }
            }
            
            // 预先检查，避免无谓的 addAndGet
            if (sentThisTick.get() + amount > GLOBAL_MAX_PACKET_PARTICLES_PER_TICK) {
                return false;
            }
            
            return sentThisTick.addAndGet(amount) <= GLOBAL_MAX_PACKET_PARTICLES_PER_TICK;
        }
    }

    /**
     * 拦截并处理发包请求
     */
    public boolean handleSend(Packet<?> packet, ServerGamePacketListenerImpl listener) {
        if (packet instanceof ClientboundLevelParticlesPacket p) {
            if (!isProcessing) {
                boolean shouldFlush = false;
                synchronized (buffer) {
                    buffer.add(p);
                    if (buffer.size() >= MAX_BUFFER_SIZE) {
                        shouldFlush = true;
                    }
                }
                if (shouldFlush) {
                    flush(listener);
                }
                return true;
            }
        }

        // 顺序保护：遇到非粒子包，先清空积压的粒子
        if (!isProcessing) {
            boolean shouldFlush = false;
            synchronized (buffer) {
                if (!buffer.isEmpty()) shouldFlush = true;
            }
            if (shouldFlush) {
                flush(listener);
            }
        }

        return false;
    }

    /**
     * 每 Tick 调用一次
     */
    public void tick(ServerGamePacketListenerImpl listener) {
        boolean shouldFlush = false;
        synchronized (buffer) {
            if (!buffer.isEmpty()) shouldFlush = true;
        }
        if (shouldFlush) {
            flush(listener);
        }
    }

    /**
     * 核心管线：处理并发送缓冲区内的粒子
     */
    private void flush(ServerGamePacketListenerImpl listener) {
        synchronized (buffer) {
            if (buffer.isEmpty()) return;
        }

        isProcessing = true;
        try {
            List<ClientboundLevelParticlesPacket> finalToSend;
            List<ClientboundLevelParticlesPacket> workingCopy;

            // 1. 取出数据
            synchronized (buffer) {
                if (buffer.isEmpty()) return;
                workingCopy = new ArrayList<>(buffer);
                buffer.clear();
            }

            // 2. 优化处理 (剔除、排序)
            if (workingCopy.size() <= PLAYER_MIN_OPTIMIZE_THRESHOLD) {
                finalToSend = new ArrayList<>(workingCopy);
            } else {
                // --- 视线剔除 ---
                Vec3 playerPos = player.getEyePosition();
                Vec3 playerLook = player.getLookAngle();
                Iterator<ClientboundLevelParticlesPacket> it = workingCopy.iterator();
                while (it.hasNext()) {
                    ClientboundLevelParticlesPacket p = it.next();
                    ParticleOptions particleType = p.getParticle();
                    
                    // 针对 Dominion (FLAME) 和 Residence (DUST) 的墙体特效进行剔除
                    if (particleType.getType() == ParticleTypes.FLAME || particleType.getType() == ParticleTypes.DUST) {
                        BlockPos pos = BlockPos.containing(p.getX(), p.getY(), p.getZ());
                        
                        // Folia 兼容性保护：防止异步访问世界数据导致崩溃
                        boolean isOccluded = false;
                        try {
                            if (player.level().isLoaded(pos)) {
                                BlockState state = player.level().getBlockState(pos);
                                if (state.canOcclude()) {
                                    isOccluded = true;
                                }
                            }
                        } catch (Exception ignored) {
                            // 如果发生异步访问错误，跳过方块遮挡检查，仅依赖视线剔除
                        }

                        if (isOccluded) {
                            it.remove();
                            continue;
                        }

                        Vec3 particlePos = new Vec3(p.getX(), p.getY(), p.getZ());
                        Vec3 toParticle = particlePos.subtract(playerPos).normalize();
                        if (playerLook.dot(toParticle) < -0.2) {
                            it.remove();
                        }
                    }
                }

                // --- 距离排序与截断 ---
                if (workingCopy.size() > PLAYER_MAX_PARTICLES_PER_PACKET) {
                    double pX = player.getX();
                    double pY = player.getY();
                    double pZ = player.getZ();
                    workingCopy.sort((p1, p2) -> {
                        double d1 = (p1.getX()-pX)*(p1.getX()-pX) + (p1.getY()-pY)*(p1.getY()-pY) + (p1.getZ()-pZ)*(p1.getZ()-pZ);
                        double d2 = (p2.getX()-pX)*(p2.getX()-pX) + (p2.getY()-pY)*(p2.getY()-pY) + (p2.getZ()-pZ)*(p2.getZ()-pZ);
                        return Double.compare(d1, d2);
                    });
                    finalToSend = new ArrayList<>(workingCopy.subList(0, PLAYER_MAX_PARTICLES_PER_PACKET));
                } else {
                    finalToSend = new ArrayList<>(workingCopy);
                }
            }

            if (finalToSend.isEmpty()) return;

            // 3. 发送逻辑
            // 如果只有一个粒子，直接发送，不走全局流控 (豁免小包)
            if (finalToSend.size() == 1) {
                listener.send(finalToSend.get(0));
                // 单发包不计入 delayedTicks，也不重置它，保持原状
            } else {
                // 只有打包的大包才走全局流控
                if (GlobalLimiter.tryAcquire(finalToSend.size())) {
                    // === 配额充足，发送 ===
                    List<Packet<? super ClientGamePacketListener>> bundlePackets = new ArrayList<>(finalToSend);
                    listener.send(new ClientboundBundlePacket(bundlePackets));
                    // 发送成功，重置延迟计数
                    delayedTicks = 0;
                } else {
                    // === 配额不足，延迟处理 ===
                    delayedTicks++;
                    if (delayedTicks <= GLOBAL_MAX_DELAY_TICKS) {
                        // 未超时：将处理好的粒子放回缓冲区头部 (插队)，等待下一 Tick
                        synchronized (buffer) {
                            buffer.addAll(0, finalToSend);
                        }
                    } else {
                        // 超时：直接丢弃
                        delayedTicks = 0;
                    }
                }
            }

        } catch (Exception e) {
            LOGGER.error("Error in particle pipeline", e);
        } finally {
            isProcessing = false;
        }
    }
}