package me.sucixr.kitin.fixes.foliafix.waypoint;

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

public final class KitinServerWaypointManager extends ServerWaypointManager {

    private final ServerLevel world;
    private final Set<ServerPlayer> receivers = ConcurrentHashMap.newKeySet();
    private final Set<WaypointTransmitter> transmitters = ConcurrentHashMap.newKeySet();

    private static final class PlayerEntry {
        final Object2ObjectOpenHashMap<WaypointTransmitter, WaypointTransmitter.Connection> connections =
                new Object2ObjectOpenHashMap<>();
        long lastProcessedGameTick = Long.MIN_VALUE;
    }

    private final Map<UUID, PlayerEntry> entries = new ConcurrentHashMap<>();

    public KitinServerWaypointManager(ServerLevel world) {
        this.world = world;
        GlobalPositionProbe.getInstance();
    }

    private boolean locatorBarEnabled() {
        return this.world.getGameRules().rules.get(GameRules.LOCATOR_BAR);
    }

    private static void scheduleToOrRun(final ServerPlayer player, final Runnable task) {
        if (TickThread.isTickThreadFor(player)) {
            task.run();
            return;
        }
        player.getBukkitEntity().taskScheduler.schedule((ServerPlayer p) -> task.run(), null, 1L);
    }

    private double getReceiveRange(final ServerPlayer player) {
        return player.getAttributeValue(Attributes.WAYPOINT_RECEIVE_RANGE);
    }

    private void updateConnectionOnReceiverThread(
            final long scheduledGameTick,
            final ServerPlayer receiver,
            final WaypointTransmitter tx
    ) {
        final PlayerEntry entry = this.entries.computeIfAbsent(receiver.getUUID(), k -> new PlayerEntry());
        final WaypointTransmitter.Connection existing = entry.connections.get(tx);
        int offset = (tx instanceof Entity e) ? e.getId() : tx.hashCode();
        int interval = WaypointSyncPolicy.getInstance().getUpdateIntervalOrDisconnect(
                receiver, tx, this.locatorBarEnabled(), this.getReceiveRange(receiver));
        if (existing != null){
            if ((scheduledGameTick + Math.abs(offset)) % interval != 0) {
                return;//错峰
            }
        }

        entry.lastProcessedGameTick = scheduledGameTick;
        //Fix start 修复珍珠通过传送门导致协议错误问题，如果实体在服务端已经死了，绝对别发包
        if (tx instanceof Entity e) {
            if (e.isRemoved()) {
                this.disconnectOnReceiverThread(receiver, tx);//把连接断开,这个可选,不确定加上好还是不加好
                return;
            }
        }
        //Fix end
        if(interval == -1){
            if (existing != null) {
                existing.disconnect();
                entry.connections.remove(tx);
            }
        }//连接断开，移除

        if (existing != null && !existing.isBroken()) {
            existing.update();
            return;
        }//连接正常，更新


        tx.makeWaypointConnectionWith(receiver).ifPresent(conn -> {
            entry.connections.put(tx, conn);
            conn.connect();
            conn.update();
        });
    }

    @Override
    public void updatePlayer(final ServerPlayer player) {
        if (!this.receivers.contains(player) || player.level() != this.world) return;

        GlobalPositionProbe.getInstance().pushSnapshot(player);

        final PlayerEntry entry = this.entries.computeIfAbsent(player.getUUID(), k -> new PlayerEntry());
        final long tick = this.world.getGameTime();
        if ((tick - entry.lastProcessedGameTick) < 911L) {//静止不动
            return;
        }
        scheduleToOrRun(player, () -> {
            for (WaypointTransmitter tx : this.transmitters) {
                if (tx == player) continue;
                this.updateConnectionOnReceiverThread(tick, player, tx);
            }
        });
    }

    private void disconnectOnReceiverThread(final ServerPlayer receiver, final WaypointTransmitter tx) {
        final PlayerEntry entry = this.entries.get(receiver.getUUID());
        if (entry == null) return;
        final WaypointTransmitter.Connection existing = entry.connections.remove(tx);
        if (existing != null) existing.disconnect();
    }

    private void disconnectAllOnReceiverThread(final ServerPlayer receiver) {
        final PlayerEntry entry = this.entries.remove(receiver.getUUID());
        if (entry == null) return;
        for (final WaypointTransmitter.Connection conn : entry.connections.values()) { conn.disconnect(); }
        entry.connections.clear();
    }

    @Override public void addPlayer(ServerPlayer player) {
        if (player.level() != this.world) return;
        this.receivers.add(player);
        final long tick = this.world.getGameTime();
        scheduleToOrRun(player, () -> {
            for (WaypointTransmitter tx : this.transmitters) {
                if (tx == player) continue;
                this.updateConnectionOnReceiverThread(tick, player, tx);
            }
        });
    }
    @Override public void removePlayer(ServerPlayer player) {
        if (player.level() != this.world) return;
        this.receivers.remove(player);
        scheduleToOrRun(player, () -> this.disconnectAllOnReceiverThread(player));
    }
    @Override public void trackWaypoint(WaypointTransmitter waypoint) {
        if (waypoint instanceof Entity e && e.level() != this.world) return;
        this.transmitters.add(waypoint);
        final long tick = this.world.getGameTime();
        for (ServerPlayer receiver : this.receivers.toArray(new ServerPlayer[0])) {
            scheduleToOrRun(receiver, () -> this.updateConnectionOnReceiverThread(tick, receiver, waypoint));
        }
    }
    @Override public void untrackWaypoint(WaypointTransmitter waypoint) {
        this.transmitters.remove(waypoint);
        for (ServerPlayer receiver : this.receivers.toArray(new ServerPlayer[0])) {
            scheduleToOrRun(receiver, () -> this.disconnectOnReceiverThread(receiver, waypoint));
        }
    }
    @Override public void updateWaypoint(WaypointTransmitter waypoint) {
        if (!this.transmitters.contains(waypoint)) return;
        final long tick = this.world.getGameTime();
        for (ServerPlayer receiver : this.receivers.toArray(new ServerPlayer[0])) {
            scheduleToOrRun(receiver, () -> this.updateConnectionOnReceiverThread(tick, receiver, waypoint));
        }
    }
    @Override public void breakAllConnections() {
        for (ServerPlayer receiver : this.receivers.toArray(new ServerPlayer[0])) {
            scheduleToOrRun(receiver, () -> this.disconnectAllOnReceiverThread(receiver));
        }
    }
}