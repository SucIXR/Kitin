package me.sucixr.kitin.scheduler.old;

import ca.spottedleaf.moonrise.common.util.TickThread;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.waypoints.ServerWaypointManager;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.waypoints.WaypointTransmitter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kitin region-threading safe waypoint manager for Folia.
 *
 * 核心原则：
 * - 任何 Connection 的 connect/update/disconnect 只允许在“viewer 玩家所属 tick 线程”执行
 * - 其他线程只打标记（pending），由 viewer 自己在 updatePlayer() 里统一处理
 */
public final class KitinServerWaypointManager_1 extends ServerWaypointManager {

    private final ServerLevel world;

    // 当前世界内的 transmitter / receiver 列表（并发安全）
    private final Set<WaypointTransmitter> waypoints = ConcurrentHashMap.newKeySet();
    private final Set<ServerPlayer> players = ConcurrentHashMap.newKeySet();

    // 每个 viewer 的状态（只在 viewer 线程里动 connections；pending 可跨线程打标）
    private final Map<UUID, PlayerState> playerStates = new ConcurrentHashMap<>();

    private static final class PlayerState {
        // 只允许在 viewer 线程访问/修改
        final Map<WaypointTransmitter, WaypointTransmitter.Connection> connections = new HashMap<>();

        // 允许跨线程打标记（弱一致迭代即可）
        final Set<WaypointTransmitter> pendingCreate = ConcurrentHashMap.newKeySet();
        final Set<WaypointTransmitter> pendingUpdate = ConcurrentHashMap.newKeySet();
        final Set<WaypointTransmitter> pendingRemove = ConcurrentHashMap.newKeySet();
    }

    public KitinServerWaypointManager_1(ServerLevel world) {
        this.world = world;
    }

    private boolean isLocatorBarEnabledFor(final ServerPlayer player) {
        // 你 1.21.11 分支的正确写法：不需要 .get()
        return player.level().getGameRules().rules.get(GameRules.LOCATOR_BAR);
    }

    // ------------------------------------------------------------------------
    // WaypointManager impl
    // ------------------------------------------------------------------------

    @Override
    public void trackWaypoint(final WaypointTransmitter waypoint) {
        // track 只应该发生在这个世界的 manager 中
        this.waypoints.add(waypoint);

        // 给所有 receiver 打“需要建立连接”的标
        for (final ServerPlayer player : this.players) {
            if (player == waypoint) continue;
            final PlayerState st = this.playerStates.get(player.getUUID());
            if (st == null) continue;

            st.pendingRemove.remove(waypoint);
            st.pendingCreate.add(waypoint);
        }
    }

    @Override
    public void updateWaypoint(final WaypointTransmitter waypoint) {
        if (!this.waypoints.contains(waypoint)) {
            return;
        }

        // 给所有 receiver 打“需要更新”的标
        for (final ServerPlayer player : this.players) {
            if (player == waypoint) continue;
            final PlayerState st = this.playerStates.get(player.getUUID());
            if (st == null) continue;

            // 如果尚未建连，让 viewer 自己先 create
            st.pendingCreate.add(waypoint);
            st.pendingUpdate.add(waypoint);
        }
    }

    @Override
    public void untrackWaypoint(final WaypointTransmitter waypoint) {
        this.waypoints.remove(waypoint);

        // 给所有 receiver 打“需要移除”的标
        for (final ServerPlayer player : this.players) {
            final PlayerState st = this.playerStates.get(player.getUUID());
            if (st == null) continue;

            st.pendingCreate.remove(waypoint);
            st.pendingUpdate.remove(waypoint);
            st.pendingRemove.add(waypoint);
        }
    }

    @Override
    public void addPlayer(final ServerPlayer player) {
        this.players.add(player);
        this.playerStates.computeIfAbsent(player.getUUID(), k -> new PlayerState());

        // 如果该玩家接收定位条：为现存 waypoints 建连
        if (this.isLocatorBarEnabledFor(player) && player.isReceivingWaypoints()) {
            final PlayerState st = this.playerStates.get(player.getUUID());
            for (final WaypointTransmitter w : this.waypoints) {
                if (w == player) continue;
                st.pendingCreate.add(w);
            }
        }

        // 如果该玩家本身正在 transmit：track 自己（让别人看到）
        if (player.isTransmittingWaypoint()) {
            this.trackWaypoint(player);
        }
    }

    @Override
    public void updatePlayer(final ServerPlayer player) {
        // 重要：updatePlayer 必须在“player 所属 tick 线程”运行
        TickThread.ensureTickThread(player, "Cannot update waypoints off owning thread of player");

        final PlayerState st = this.playerStates.get(player.getUUID());
        if (st == null) return;

        // 如果定位条关闭 / 或玩家不接收：断开所有连接并清空
        if (!this.isLocatorBarEnabledFor(player) || !player.isReceivingWaypoints()) {
            breakConnectionsOnViewerThread(st);
            st.pendingCreate.clear();
            st.pendingUpdate.clear();
            st.pendingRemove.clear();
            return;
        }

        // 1) 先处理 remove（避免“先 update 后 remove”的抖动）
        if (!st.pendingRemove.isEmpty()) {
            drainSet(st.pendingRemove, waypoint -> {
                final WaypointTransmitter.Connection conn = st.connections.remove(waypoint);
                if (conn != null) conn.disconnect();
            });
        }

        // 2) 再处理 create
        if (!st.pendingCreate.isEmpty()) {
            drainSet(st.pendingCreate, waypoint -> {
                if (waypoint == player) return;

                // 若已存在连接，跳过
                if (st.connections.containsKey(waypoint)) return;

                // 让 transmitter 决定是否要给 viewer 建连接（原版逻辑）
                waypoint.makeWaypointConnectionWith(player).ifPresentOrElse(conn -> {
                    st.connections.put(waypoint, conn);
                    conn.connect();
                }, () -> {
                    // make 返回 empty：确保断开并移除
                    final WaypointTransmitter.Connection old = st.connections.remove(waypoint);
                    if (old != null) old.disconnect();
                });
            });
        }

        // 3) 最后处理 update（统一在 viewer 线程更新，避免跨区乱序）
        if (!st.pendingUpdate.isEmpty()) {
            drainSet(st.pendingUpdate, waypoint -> {
                if (waypoint == player) return;

                final WaypointTransmitter.Connection conn = st.connections.get(waypoint);
                if (conn == null) {
                    // 如果还没连接但被打了 update 标，留给下 tick（create 已经打过标）
                    return;
                }

                if (!conn.isBroken()) {
                    conn.update();
                } else {
                    // broken：按原版语义重建/移除
                    waypoint.makeWaypointConnectionWith(player).ifPresentOrElse(newConn -> {
                        newConn.connect();
                        st.connections.put(waypoint, newConn);
                    }, () -> {
                        conn.disconnect();
                        st.connections.remove(waypoint);
                    });
                }
            });
        }
    }

    @Override
    public void removePlayer(final ServerPlayer player) {
        // removePlayer 一样建议在 player 所属线程调用，但在 Folia 回调里通常是安全的
        final PlayerState st = this.playerStates.remove(player.getUUID());
        this.players.remove(player);

        // 断开 player 作为 viewer 的所有连接
        if (st != null) {
            // 这里可能不在 viewer 线程：保险做法是只 disconnect 不 update
            for (final WaypointTransmitter.Connection conn : st.connections.values()) {
                conn.disconnect();
            }
            st.connections.clear();
            st.pendingCreate.clear();
            st.pendingUpdate.clear();
            st.pendingRemove.clear();
        }

        // **关键修复：如果 player 自己是 transmitter，离开世界时必须 untrack**
        // 否则旧世界仍会让其他玩家保持连接 → 你说的“进地狱还显示主世界玩家”
        if (player.isTransmittingWaypoint()) {
            this.untrackWaypoint(player);
        }
    }

    @Override
    public void breakAllConnections() {
        for (final ServerPlayer p : new ArrayList<>(this.players)) {
            this.removePlayer(p);
        }
    }

    @Override
    public void remakeConnections(final WaypointTransmitter waypoint) {
        // 等价于：先移除再创建（打标给所有玩家）
        this.untrackWaypoint(waypoint);
        this.trackWaypoint(waypoint);
    }

    @Override
    public Set<WaypointTransmitter> transmitters() {
        return new HashSet<>(this.waypoints);
    }

    public ServerLevel getWorld() {
        return this.world;
    }

    // ------------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------------

    private static void breakConnectionsOnViewerThread(final PlayerState st) {
        for (final WaypointTransmitter.Connection conn : st.connections.values()) {
            conn.disconnect();
        }
        st.connections.clear();
    }

    private static <T> void drainSet(final Set<T> set, final java.util.function.Consumer<T> consumer) {
        // 弱一致迭代 + remove：足够用了（这套 pending 本来就不要求强一致）
        final Iterator<T> it = set.iterator();
        while (it.hasNext()) {
            final T v = it.next();
            it.remove();
            consumer.accept(v);
        }
    }
}
