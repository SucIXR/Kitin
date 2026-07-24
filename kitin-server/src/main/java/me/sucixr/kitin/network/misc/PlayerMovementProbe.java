package me.sucixr.kitin.network.misc;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerMovementProbe {

    private static final PlayerMovementProbe INSTANCE = new PlayerMovementProbe();
    public static PlayerMovementProbe getInstance() {
        return INSTANCE;
    }

    private final Map<UUID, InternalState> states = new ConcurrentHashMap<>();

    public static final class State {
        public int resistance;     // 0 ~ 4
        public boolean stable;      // 是否处于“稳定移动 / 停留”
    }

    private static final class InternalState {
        int lastX, lastZ;
        long lastCheckTime;
        int resistance;
        boolean initialized;
    }

    /**
     * 采样玩家移动状态（无副作用）
     */
    public State sample(ServerPlayer player) {
        InternalState s = states.computeIfAbsent(player.getUUID(), k -> new InternalState());
        ChunkPos pos = player.chunkPosition();
        long now = System.currentTimeMillis();

        if (!s.initialized) {
            s.lastX = pos.x();
            s.lastZ = pos.z();
            s.lastCheckTime = now;
            s.initialized = true;

            State out = new State();
            out.resistance = 0;
            out.stable = true;
            return out;
        }

        int dx = Math.abs(pos.x() - s.lastX);
        int dz = Math.abs(pos.z() - s.lastZ);
        int maxDist = Math.max(dx, dz);

        // 状态 A：原地 / 小范围晃动
        if (maxDist <= 1) {
            if (now - s.lastCheckTime >= 5000) {
                s.resistance = Math.min(4, s.resistance + 1);
                s.lastCheckTime = now;
            }
        }
        // 状态 B：明显移动
        else {
            s.resistance -= (maxDist >= 3 ? 4 : 2);
            if (s.resistance < 0) s.resistance = 0;

            if (s.resistance == 0) {
                s.lastX = pos.x(); // Changed from s.lastX = pos.x;
                s.lastZ = pos.z();
                s.lastCheckTime = now;
            }
        }

        State out = new State();
        out.resistance = s.resistance;
        out.stable = s.resistance > 0;
        return out;
    }
}
