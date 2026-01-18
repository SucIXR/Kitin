package me.sucixr.kitin.other.old;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LazyChunkSyncController {
    private static final LazyChunkSyncController INSTANCE = new LazyChunkSyncController();
    public static LazyChunkSyncController getInstance() { return INSTANCE; }

    private final Map<UUID, SyncState> states = new ConcurrentHashMap<>();

    private static class SyncState {
        int lastX, lastZ;
        long lastCheckTime;
        int resistance = 0; // 范围 0-4
    }

    public boolean shouldSkipUpdate(ServerPlayer player) {
        SyncState state = states.computeIfAbsent(player.getUUID(), k -> new SyncState());
        ChunkPos currentPos = player.chunkPosition();
        long now = System.currentTimeMillis();

        int dx = Math.abs(currentPos.x - state.lastX);
        int dz = Math.abs(currentPos.z - state.lastZ);
        int maxDist = Math.max(dx, dz);

        // --- 极简逻辑门 ---

        if (maxDist <= 1) {
            // 状态 A：原地晃悠
            // 每秒涨 1 分阻力，上限 4 分
            if (now - state.lastCheckTime >= 5000) {
                state.resistance = Math.min(4, state.resistance + 1);
                state.lastCheckTime = now;
            }
            // 只要有阻力，在 3x3 内移动就拦截
            return state.resistance > 0;
        } else {
            // 状态 B：开始跨区
            // 根据跨区远近扣分
            state.resistance -= (maxDist >= 3 ? 4 : 2);
            if (state.resistance < 0) state.resistance = 0;

            // 状态 C：阻力判定
            if (state.resistance == 0) {
                // 阻力击穿，同步位置并放行
                updateState(state, currentPos, now);
                return false;
            }
            return true; // 阻力还在，继续拦截
        }
    }

    private void updateState(SyncState state, ChunkPos pos, long time) {
        state.lastX = pos.x;
        state.lastZ = pos.z;
        state.lastCheckTime = time;
    }
}