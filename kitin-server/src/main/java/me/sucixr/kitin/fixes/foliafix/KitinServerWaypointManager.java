package me.sucixr.kitin.fixes.foliafix;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.waypoints.ServerWaypointManager;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.waypoints.WaypointTransmitter;

/**
 * 简化版KitinWaypointManager
 * 不使用Bukkit调度器，避免插件依赖问题
 */
public class KitinServerWaypointManager extends ServerWaypointManager {

    private final Set<WaypointTransmitter> waypoints = ConcurrentHashMap.newKeySet();
    private final Set<ServerPlayer> players = ConcurrentHashMap.newKeySet();
    private final ServerLevel world;

    // 存储每个玩家的连接
    private final java.util.Map<ServerPlayer, java.util.Map<WaypointTransmitter, WaypointTransmitter.Connection>> connections =
            new ConcurrentHashMap<>();

    public KitinServerWaypointManager(ServerLevel world) {
        this.world = world;
    }

    private boolean isLocatorBarDisabled() {
        // 在 1.21.11 中，使用 get() 方法而不是 getBoolean()
        // GameRules.LOCATOR_BAR 是一个 GameRule<Boolean> 对象
        return !world.getGameRules().get(GameRules.LOCATOR_BAR);
    }

    @Override
    public void trackWaypoint(WaypointTransmitter waypoint) {
        if (isLocatorBarDisabled()) return;
        waypoints.add(waypoint);

        for (ServerPlayer player : players) {
            if (player != waypoint) {
                // 直接执行，不调度
                try {
                    createConnectionInternal(player, waypoint);
                } catch (Exception e) {
                    // 忽略错误
                }
            }
        }
    }

    @Override
    public void updateWaypoint(WaypointTransmitter waypoint) {
        if (isLocatorBarDisabled()) return;

        for (ServerPlayer player : players) {
            if (player == waypoint) continue;

            // 简单的距离检查
            if (player.distanceTo((ServerPlayer)waypoint) > 332.0F) {
                // 太远，跳过
                continue;
            }

            try {
                updateWaypointInternal(waypoint, player);
            } catch (Exception e) {
                // 忽略错误
            }
        }
    }

    private void updateWaypointInternal(WaypointTransmitter waypoint, ServerPlayer player) {
        java.util.Map<WaypointTransmitter, WaypointTransmitter.Connection> map =
                connections.get(player);

        if (map == null) {
            return;
        }

        WaypointTransmitter.Connection conn = map.get(waypoint);
        if (conn != null) {
            if (!conn.isBroken()) {
                conn.update();
            }
        }
    }

    @Override
    public void untrackWaypoint(WaypointTransmitter waypoint) {
        for (ServerPlayer player : players) {
            try {
                disconnectWaypointInternal(waypoint, player);
            } catch (Exception e) {
                // 忽略错误
            }
        }
        waypoints.remove(waypoint);
    }

    private void disconnectWaypointInternal(WaypointTransmitter waypoint, ServerPlayer player) {
        java.util.Map<WaypointTransmitter, WaypointTransmitter.Connection> map =
                connections.get(player);

        if (map != null) {
            WaypointTransmitter.Connection conn = map.remove(waypoint);
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    @Override
    public void addPlayer(ServerPlayer player) {
        players.add(player);

        if (isLocatorBarDisabled()) return;

        for (WaypointTransmitter waypoint : waypoints) {
            if (player != waypoint) {
                try {
                    createConnectionInternal(player, waypoint);
                } catch (Exception e) {
                    // 忽略错误
                }
            }
        }
    }

    @Override
    public void updatePlayer(ServerPlayer player) {
        if (isLocatorBarDisabled()) return;

        java.util.Map<WaypointTransmitter, WaypointTransmitter.Connection> map =
                connections.get(player);

        if (map != null) {
            for (WaypointTransmitter.Connection conn : map.values()) {
                if (!conn.isBroken()) {
                    conn.update();
                }
            }
        }
    }

    @Override
    public void removePlayer(ServerPlayer player) {
        // 断开所有连接
        java.util.Map<WaypointTransmitter, WaypointTransmitter.Connection> playerConnections =
                connections.remove(player);

        if (playerConnections != null) {
            for (WaypointTransmitter.Connection conn : playerConnections.values()) {
                conn.disconnect();
            }
        }

        players.remove(player);
    }

    @Override
    public void breakAllConnections() {
        for (ServerPlayer player : players) {
            removePlayer(player);
        }
    }

    @Override
    public void remakeConnections(WaypointTransmitter waypoint) {
        for (ServerPlayer player : players) {
            if (player != waypoint) {
                try {
                    createConnectionInternal(player, waypoint);
                } catch (Exception e) {
                    // 忽略错误
                }
            }
        }
    }

    @Override
    public Set<WaypointTransmitter> transmitters() {
        return new HashSet<>(waypoints);
    }

    private void createConnectionInternal(ServerPlayer player, WaypointTransmitter waypoint) {
        if (player == waypoint) return;

        java.util.Map<WaypointTransmitter, WaypointTransmitter.Connection> map =
                connections.computeIfAbsent(player, k -> new ConcurrentHashMap<>());

        waypoint.makeWaypointConnectionWith(player).ifPresentOrElse(connection -> {
            map.put(waypoint, connection);
            connection.connect();
        }, () -> {
            WaypointTransmitter.Connection existing = map.remove(waypoint);
            if (existing != null) existing.disconnect();
        });
    }

    public ServerLevel getWorld() {
        return world;
    }
}