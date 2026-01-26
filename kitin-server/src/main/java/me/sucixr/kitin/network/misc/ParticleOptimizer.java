package me.sucixr.kitin.network.misc;

import net.minecraft.core.BlockPos;
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

/**
 * [Kitin 核心优化] 智能粒子管线优化器
 * 负责粒子的缓冲、剔除、排序和打包，解决高频粒子导致的网络拥塞和客户端卡顿。
 */
public class ParticleOptimizer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ParticleOptimizer.class);

    // 粒子缓冲区
    private final List<ClientboundLevelParticlesPacket> buffer = new ArrayList<>();
    // 递归锁：防止在 flush 发包时再次触发拦截
    private boolean isProcessing = false;

    private final ServerPlayer player;

    public ParticleOptimizer(ServerPlayer player) {
        this.player = player;
    }

    /**
     * 拦截并处理发包请求
     * @param packet 待发送的包
     * @param listener 发包的监听器 (用于回调发送)
     * @return true 表示包已被拦截/处理，调用者不应继续发送；false 表示包未被拦截，调用者应原样发送
     */
    public boolean handleSend(Packet<?> packet, ServerGamePacketListenerImpl listener) {
        // 1. 拦截粒子包
        if (packet instanceof ClientboundLevelParticlesPacket p) {
            // 如果正在处理中(isProcessing=true)，说明这是处理后的包，直接放行
            if (!isProcessing) {
                synchronized (buffer) {
                    buffer.add(p);
                    // 内存熔断保护：防止瞬间积压过多导致 OOM
                    if (buffer.size() >= 5000) {
                        flush(listener);
                    }
                }
                return true; // 已拦截，不要直接发
            }
        }

        // 2. 顺序保护
        // 如果遇到了非粒子包（如音效、方块更新），为了保证时序，必须先把积压的粒子发出去
        if (!isProcessing) {
            boolean shouldFlush = false;
            synchronized (buffer) {
                if (!buffer.isEmpty()) shouldFlush = true;
            }
            if (shouldFlush) {
                flush(listener);
            }
        }

        return false; // 放行其他包
    }

    /**
     * 每 Tick 调用一次，清空剩余粒子
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
        if (buffer.isEmpty()) return;

        isProcessing = true;
        try {
            List<Packet<? super ClientGamePacketListener>> finalToSend;

            // 拷贝一份数据进行处理，释放锁
            List<ClientboundLevelParticlesPacket> workingCopy;
            synchronized (buffer) {
                workingCopy = new ArrayList<>(buffer);
                buffer.clear();
            }

            // === 阶段 1: 数量检测 ===
            if (workingCopy.size() <= 100) {
                // 数量很少，直接放行
                finalToSend = new ArrayList<>(workingCopy);
            } else {
                // === 阶段 2: 视线剔除 (Frustum Culling) ===
                Vec3 playerPos = player.getEyePosition();
                Vec3 playerLook = player.getLookAngle();

                Iterator<ClientboundLevelParticlesPacket> it = workingCopy.iterator();
                while (it.hasNext()) {
                    ClientboundLevelParticlesPacket p = it.next();
                    // 只对火焰粒子(墙体)做剔除，防止误删全向特效 -- 针对Dominion
                    if (p.getParticle().getType() == ParticleTypes.FLAME) {
                        // =========== [在此处插入代码] ===========
                        // 目标：如果是实心方块里的粒子，直接剔除

                        BlockPos pos = BlockPos.containing(p.getX(), p.getY(), p.getZ());
                        // 1. 安全检查：必须确认区块已加载，绝对不要触发区块加载！
                        if (player.level().isLoaded(pos)) {
                            BlockState state = player.level().getBlockState(pos);
                            // 2. 遮挡检查：如果是完全不透明的方块(如石头)，剔除
                            if (state.canOcclude()) {
                                it.remove();
                                continue; // 跳过后续计算，直接处理下一个
                            }
                        }
                        // =========== [插入结束] ===========
                        Vec3 particlePos = new Vec3(p.getX(), p.getY(), p.getZ());
                        Vec3 toParticle = particlePos.subtract(playerPos).normalize();

                        // 点积 < -0.2 表示在脑后，剔除
                        if (playerLook.dot(toParticle) < -0.2) {
                            it.remove();
                        }
                    }
                }

                // === 阶段 3: 距离优先截断 ===
                if (workingCopy.size() > 500) {
                    double pX = player.getX();
                    double pY = player.getY();
                    double pZ = player.getZ();

                    // 按距离排序 (由近到远)
                    workingCopy.sort((p1, p2) -> {
                        double d1 = (p1.getX()-pX)*(p1.getX()-pX) + (p1.getY()-pY)*(p1.getY()-pY) + (p1.getZ()-pZ)*(p1.getZ()-pZ);
                        double d2 = (p2.getX()-pX)*(p2.getX()-pX) + (p2.getY()-pY)*(p2.getY()-pY) + (p2.getZ()-pZ)*(p2.getZ()-pZ);
                        return Double.compare(d1, d2);
                    });

                    // 只保留最近的 500 个
                    finalToSend = new ArrayList<>(workingCopy.subList(0, 500));
                } else {
                    finalToSend = new ArrayList<>(workingCopy);
                }
            }

            // === 阶段 4: 打包发送 ===
            if (!finalToSend.isEmpty()) {
                if (finalToSend.size() == 1) {
                    listener.send(finalToSend.get(0));
                } else {
                    // 打包成 Bundle，触发 Zlib 压缩
                    listener.send(new ClientboundBundlePacket(finalToSend));
                }
            }

        } catch (Exception e) {
            LOGGER.error("Error in particle pipeline", e);
        } finally {
            isProcessing = false;
        }
    }
}