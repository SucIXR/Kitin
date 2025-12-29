package me.sucixr.kitin.scheduler.old;

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

public final class KitinServerWaypointManager_OLD extends ServerWaypointManager {

    private final ServerLevel world;

    // 只存本维度的接收者/发射源（onTrackingStart/onTrackingEnd 会在对应世界调用）
    private final Set<ServerPlayer> receivers = ConcurrentHashMap.newKeySet();
    private final Set<WaypointTransmitter> transmitters = ConcurrentHashMap.newKeySet();

    private static final class PlayerEntry {
        final Object2ObjectOpenHashMap<WaypointTransmitter, WaypointTransmitter.Connection> connections =
                new Object2ObjectOpenHashMap<>();

        long lastProcessedGameTick = Long.MIN_VALUE;
    }

    private final Map<UUID, PlayerEntry> entries = new ConcurrentHashMap<>();

    public KitinServerWaypointManager_OLD(ServerLevel world) {
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
            if (existing != null) {
                existing.disconnect();
            }
            return;
        }

        // 超出接收距离也断开（尽量还原原版行为）
        if (tx instanceof Entity e) {
            final double range = this.getReceiveRange(receiver);
            final double dx = receiver.getX() - e.getX();
            final double dy = receiver.getY() - e.getY();
            final double dz = receiver.getZ() - e.getZ();
            if ((dx * dx + dy * dy + dz * dz) > (range * range)) {
                final WaypointTransmitter.Connection existing = entry.connections.remove(tx);
                if (existing != null) existing.disconnect();
                return;
            }
        }

        final WaypointTransmitter.Connection existing = entry.connections.get(tx);

        // 连接存在且没坏 → 更新即可
        if (existing != null && !existing.isBroken()) {
            existing.update();
            return;
        }

        // 需要新建/重建连接
        if (existing != null) {
            existing.disconnect();
            entry.connections.remove(tx);
        }

        tx.makeWaypointConnectionWith(receiver).ifPresent(conn -> {
            entry.connections.put(tx, conn);
            conn.connect();
            conn.update();
        });
    }

    private void disconnectOnReceiverThread(final ServerPlayer receiver, final WaypointTransmitter tx) {
        final PlayerEntry entry = this.entries.get(receiver.getUUID());
        if (entry == null) return;

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

    //@Override
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
