package me.sucixr.kitin.other.old;

import ca.spottedleaf.moonrise.common.util.TickThread;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.waypoints.ServerWaypointManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.waypoints.WaypointTransmitter;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class KitinServerWaypointManager_OLD2 extends ServerWaypointManager {

    private final ServerLevel world;

    // --- 新增：LOD 配置 ---
    // 这个值越大，远处更新越频繁；值越小，远处更新越慢。
    // Canvas 默认大概是 4000.0，你可以根据服务器性能调整。
    private static final double LOD_SCALE = 4000.0;
    // 近距离阈值（平方）：在这个距离内强制每 tick 更新，不降频。
    // 332^2 ≈ 110224. 约 20 个区块。
    private static final double LOD_SAFE_DISTANCE_SQR = 332.0 * 332.0;

    // 只存本维度的接收者/发射源（onTrackingStart/onTrackingEnd 会在对应世界调用）
    private final Set<ServerPlayer> receivers = ConcurrentHashMap.newKeySet();
    private final Set<WaypointTransmitter> transmitters = ConcurrentHashMap.newKeySet();

    private static final class PlayerEntry {
        final Object2ObjectOpenHashMap<WaypointTransmitter, WaypointTransmitter.Connection> connections =
                new Object2ObjectOpenHashMap<>();

        // ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓ 修改部分 ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓

        // 记录 EMA 平滑后的坐标（虚拟坐标）
        final Object2ObjectOpenHashMap<WaypointTransmitter, net.minecraft.world.phys.Vec3> emaPositions =
                new Object2ObjectOpenHashMap<>();

        // 记录上一次实际发送更新时的 EMA 坐标（用来对比变化量）
        final Object2ObjectOpenHashMap<WaypointTransmitter, net.minecraft.world.phys.Vec3> lastSentEmaPositions =
                new Object2ObjectOpenHashMap<>();

        // ↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑

        long lastProcessedGameTick = Long.MIN_VALUE;
    }

    private final Map<UUID, PlayerEntry> entries = new ConcurrentHashMap<>();

    public KitinServerWaypointManager_OLD2(ServerLevel world) {
        this.world = world;
    }

    // 1.21.11 你说的写法：rules.get(GameRules.LOCATOR_BAR) 不带 .get()
    private boolean locatorBarEnabled() {
        return this.world.getGameRules().rules.get(GameRules.LOCATOR_BAR);
    }

    private static void scheduleToOrRun(final ServerPlayer player, final Runnable task) {
        if (TickThread.isTickThreadFor(player)) {
            task.run();
            return;
        }
        // 这里用 taskScheduler 模拟 Canvas 的 scheduleToOrRun
        player.getBukkitEntity().taskScheduler.schedule((ServerPlayer p) -> task.run(), null, 1L);
    }

    private boolean isInThisWorld(final WaypointTransmitter tx) {
        if (tx instanceof Entity e) {
            return e.level() == this.world;
        }
        // 理论上 waypoint transmitter 都是实体；不是的话当作允许
        return true;
    }

    private double getReceiveRange(final ServerPlayer player) {
        // 原版就是靠这个属性控制是否接收 + 接收距离
        return player.getAttributeValue(Attributes.WAYPOINT_RECEIVE_RANGE);
    }

    // --- 新增：LOD 判断逻辑 ---
    private boolean shouldSkipUpdate(ServerPlayer receiver, Entity target) {
        // 注意：这里读取 target 坐标在 Folia 下可能有线程风险，但用于 LOD 概率计算是可以接受的
        double distSqr = receiver.distanceToSqr(target);

        // 1. 如果距离很近（例如 20 区块内），绝不跳过，保证丝滑
        if (distSqr < LOD_SAFE_DISTANCE_SQR) {
            return false;
        }

        // 2. 距离越远，跳过概率越大
        // 公式：P(更新) = 1 / (1 + (distance / SCALE)^2)
        double dist = Math.sqrt(distSqr);
        double scaled = dist / LOD_SCALE;
        double updateChance = 1.0 / (1.0 + (scaled * scaled));

        // 生成随机数，如果随机数大于更新概率，则跳过
        return ThreadLocalRandom.current().nextDouble() > updateChance;
    }
    // -----------------------

    private void updateConnectionOnReceiverThread(
            final long scheduledGameTick,
            final ServerPlayer receiver,
            final WaypointTransmitter tx
    ) {
        // 重要：维度过滤（修复“进地狱还显示主世界”的问题）
        if (!this.isInThisWorld(tx) || receiver.level() != this.world) {
            return;
        }

        final PlayerEntry entry = this.entries.computeIfAbsent(receiver.getUUID(), k -> new PlayerEntry());

        // 重要：防止“旧调度结果覆盖新结果”
        if (scheduledGameTick < entry.lastProcessedGameTick) {
            return;
        }
        entry.lastProcessedGameTick = scheduledGameTick;

        // locator bar 关了就直接断开
        if (!this.locatorBarEnabled() || this.getReceiveRange(receiver) <= 0.0) {
            final WaypointTransmitter.Connection existing = entry.connections.remove(tx);
            //清理EMA
            entry.emaPositions.remove(tx);
            entry.lastSentEmaPositions.remove(tx);
            if (existing != null) {
                existing.disconnect();
            }
            return;
        }

        // 超出接收距离也断开（尽量还原原版行为）
//        if (tx instanceof Entity e) {
//            final double range = this.getReceiveRange(receiver);
//            final double dx = receiver.getX() - e.getX();
//            final double dy = receiver.getY() - e.getY();
//            final double dz = receiver.getZ() - e.getZ();
//            if ((dx * dx + dy * dy + dz * dz) > (range * range)) {
//                final WaypointTransmitter.Connection existing = entry.connections.remove(tx);
//                if (existing != null) existing.disconnect();
//                return;
//            }
//        }

        final WaypointTransmitter.Connection existing = entry.connections.get(tx);

        // 连接存在且没坏
        if (existing != null && !existing.isBroken()) {

            // --- 插入 LOD 逻辑 ---
            // 只有在【更新现有连接】时才尝试跳过，新建连接必须立即执行
            if (tx instanceof Entity e) {
                if (shouldSkipUpdate(receiver, e)) {
                    // 命中 LOD 策略：本次不发送更新包，直接返回
                    // 这就是 Canvas 节省带宽和性能的核心
                    return;
                }
// ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓ EMA 平滑算法 (含异常剔除) ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓

                // [A] 获取当前原始坐标
                net.minecraft.world.phys.Vec3 rawPos = new net.minecraft.world.phys.Vec3(e.getX(), e.getY(), e.getZ());

                // [B] 获取上一次的 EMA 坐标
                net.minecraft.world.phys.Vec3 emaPos = entry.emaPositions.get(tx);

                if (emaPos == null) {
                    emaPos = rawPos; // 第一次初始化
                } else {
                    // --- 【关键修复】输入端异常剔除 (Sanity Check) ---
                    // 计算原始坐标(Raw)和当前平滑坐标(EMA)的距离
                    double sanityDistSqr = rawPos.distanceToSqr(emaPos);

                    // 阈值设定：100.0 (即 10 格)。
                    // 逻辑：如果一个实体在 1 tick (0.05秒) 内瞬移了超过 10 格（速度相当于 200格/秒），
                    // 这绝对是 Folia 线程读取到了脏数据 (比如 0,0,0)。
                    if (sanityDistSqr > 100.0) {
                        // 认定为脏数据，直接丢弃本次更新！
                        // 不更新 EMA，不发包，不断开连接，假装无事发生。
                        return;
                    }
                    // ------------------------------------------------

                    // [C] 数据正常，进行 EMA 计算
                    // alpha 建议 0.15 ~ 0.25，太小会有拖影
                    final double alpha = 0.2;
                    emaPos = emaPos.add(rawPos.subtract(emaPos).scale(alpha));
                }

                // 更新 EMA 存储
                entry.emaPositions.put(tx, emaPos);

                // [D] 距离检查 (使用安全的 EMA 保证不断开)
                double range = this.getReceiveRange(receiver);
                if (emaPos.distanceToSqr(receiver.position()) > range * range) {
                    final WaypointTransmitter.Connection toRemove = entry.connections.remove(tx);
                    // 清理数据
                    entry.emaPositions.remove(tx);
                    entry.lastSentEmaPositions.remove(tx);
                    if (toRemove != null) toRemove.disconnect();
                    return;
                }

                // [E] 输出端死区 (可选，用于过滤微小抖动)
                // 既然你之前觉得大抖动才是问题，这个阈值可以设小一点，或者干脆不要
                net.minecraft.world.phys.Vec3 lastSentEma = entry.lastSentEmaPositions.get(tx);
                if (lastSentEma != null) {
                    double dSqr = emaPos.distanceToSqr(lastSentEma);
                    // 0.01 = 0.1 格。过滤掉微小的浮动。
                    if (dSqr < 0.01) {
                        return;
                    }
                }

                // 确认发送更新
                entry.lastSentEmaPositions.put(tx, emaPos);

                // ↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑
            }
            // -------------------

            existing.update();
            return;
        }

        // 连接存在且没坏 → 更新即可
        if (existing != null && !existing.isBroken()) {
            existing.update();
            return;
        }

        // 需要新建/重建连接
        if (existing != null) {
            existing.disconnect();
            entry.emaPositions.remove(tx); // 清理  新建连接时，记得也要初始化 EMA，防止下次 update 报错或者跳变
            entry.connections.remove(tx);
        }

        tx.makeWaypointConnectionWith(receiver).ifPresent(conn -> {
            entry.connections.put(tx, conn);
            // 初始化 EMA
            if (tx instanceof Entity e) {
                net.minecraft.world.phys.Vec3 startPos = new net.minecraft.world.phys.Vec3(e.getX(), e.getY(), e.getZ());
                entry.emaPositions.put(tx, startPos);
                entry.lastSentEmaPositions.put(tx, startPos);
            }
            conn.connect();
            conn.update();
        });
    }

    private void disconnectOnReceiverThread(final ServerPlayer receiver, final WaypointTransmitter tx) {
        final PlayerEntry entry = this.entries.get(receiver.getUUID());
        if (entry == null) return;

        // --- 清理 EMA 数据 ---
        entry.emaPositions.remove(tx);
        entry.lastSentEmaPositions.remove(tx);
        // -------------------

        final WaypointTransmitter.Connection existing = entry.connections.remove(tx);
        if (existing != null) {
            existing.disconnect();
        }
    }

    private void disconnectAllOnReceiverThread(final ServerPlayer receiver) {
        final PlayerEntry entry = this.entries.remove(receiver.getUUID());
        if (entry == null) return;

        for (final WaypointTransmitter.Connection conn : entry.connections.values()) {
            conn.disconnect();
        }
        entry.connections.clear();
    }

    @Override
    public void addPlayer(final ServerPlayer player) {
        // 只接收本世界
        if (player.level() != this.world) return;

        this.receivers.add(player);

        // 初次加入：把当前 transmitters 全部建连接
        final long tick = this.world.getGameTime();
        scheduleToOrRun(player, () -> {
            for (final WaypointTransmitter tx : this.transmitters) {
                if (tx == player) continue;
                this.updateConnectionOnReceiverThread(tick, player, tx);
            }
        });
    }

    @Override
    public void removePlayer(final ServerPlayer player) {
        if (player.level() != this.world) return;

        this.receivers.remove(player);

        scheduleToOrRun(player, () -> this.disconnectAllOnReceiverThread(player));
    }

    @Override
    public void trackWaypoint(final WaypointTransmitter waypoint) {
        if (!this.isInThisWorld(waypoint)) return;

        this.transmitters.add(waypoint);

        // 新 waypoint：给所有 receiver 建连接（在 receiver 线程执行）
        final long tick = this.world.getGameTime();
        for (final ServerPlayer receiver : this.receivers.toArray(new ServerPlayer[0])) {
            scheduleToOrRun(receiver, () -> this.updateConnectionOnReceiverThread(tick, receiver, waypoint));
        }
    }

    @Override
    public void untrackWaypoint(final WaypointTransmitter waypoint) {
        this.transmitters.remove(waypoint);

        // 断开所有 receiver 上对应连接（在 receiver 线程执行）
        for (final ServerPlayer receiver : this.receivers.toArray(new ServerPlayer[0])) {
            scheduleToOrRun(receiver, () -> this.disconnectOnReceiverThread(receiver, waypoint));
        }
    }

    @Override
    public void updateWaypoint(final WaypointTransmitter waypoint) {
        if (!this.transmitters.contains(waypoint)) return;

        // waypoint 位置变了：更新所有 receiver 的连接（仍然在 receiver 线程执行）
        final long tick = this.world.getGameTime();
        for (final ServerPlayer receiver : this.receivers.toArray(new ServerPlayer[0])) {
            scheduleToOrRun(receiver, () -> this.updateConnectionOnReceiverThread(tick, receiver, waypoint));
        }
    }

    @Override
    public void updatePlayer(final ServerPlayer player) {
        // 玩家移动：需要更新“该玩家作为接收者”的所有连接
        if (!this.receivers.contains(player) || player.level() != this.world) return;

        final long tick = this.world.getGameTime();
        scheduleToOrRun(player, () -> {
            for (final WaypointTransmitter tx : this.transmitters) {
                if (tx == player) continue;
                this.updateConnectionOnReceiverThread(tick, player, tx);
            }
        });
    }

    @Override
    public void breakAllConnections() {
        // 断开所有连接（逐个 receiver 在其线程上做）
        for (final ServerPlayer receiver : this.receivers.toArray(new ServerPlayer[0])) {
            scheduleToOrRun(receiver, () -> this.disconnectAllOnReceiverThread(receiver));
        }
    }

    //@Override  父类没有这玩意
    public void remakeConnections() {
        // 重新建立连接（逐个 receiver 在其线程上做）
        for (final ServerPlayer receiver : this.receivers.toArray(new ServerPlayer[0])) {
            final long tick = this.world.getGameTime();
            scheduleToOrRun(receiver, () -> {
                this.disconnectAllOnReceiverThread(receiver);
                for (final WaypointTransmitter tx : this.transmitters) {
                    if (tx == receiver) continue;
                    this.updateConnectionOnReceiverThread(tick, receiver, tx);
                }
            });
        }
    }
}
