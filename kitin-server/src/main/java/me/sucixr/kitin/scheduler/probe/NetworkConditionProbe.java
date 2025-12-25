package me.sucixr.kitin.scheduler.probe;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NetworkConditionProbe {

    private static final NetworkConditionProbe INSTANCE = new NetworkConditionProbe();

    public static NetworkConditionProbe getInstance() {
        return INSTANCE;
    }

    public enum NetworkTier {
        GOOD,       // 网络健康
        FAIR,       // 轻微退化
        POOR,       // 明显卡顿
        CRITICAL    // 需要强制限流
    }

    private static final class NetworkState {
        double pingEMA;        // 平均延迟
        double jitterEMA;      // 抖动
        double sendPressure;   // 发送阻力
        boolean initialized;
    }

    private final Map<UUID, NetworkState> states = new ConcurrentHashMap<>();

    private static final double EMA_ALPHA = 0.2;

    public NetworkTier sample(ServerPlayer player) {
        NetworkState state = states.computeIfAbsent(
                player.getUUID(), k -> new NetworkState()
        );

        int ping = player.connection.latency();

        if (!state.initialized) {
            state.pingEMA = ping;
            state.jitterEMA = 0;
            state.sendPressure = 0;
            state.initialized = true;
            return NetworkTier.GOOD;
        }

        // 计算 EMA ping
        double prevPing = state.pingEMA;
        state.pingEMA += (ping - state.pingEMA) * EMA_ALPHA;

        // jitter = ping 波动
        double delta = Math.abs(ping - prevPing);
        state.jitterEMA += (delta - state.jitterEMA) * EMA_ALPHA;
        // 近似发送压力模型
        // latency 很高 + jitter 很高 ≈ TCP 拥塞
        state.sendPressure =
                Math.min(1.0,
                        (state.jitterEMA / 100.0) +
                                (state.pingEMA / 400.0)
                );
        if (state.sendPressure > 1.2 || state.jitterEMA > 180) {
            return NetworkTier.CRITICAL;
        }
        if (state.sendPressure > 0.8 || state.jitterEMA > 100) {
            return NetworkTier.POOR;
        }
        if (state.sendPressure > 0.4 || state.jitterEMA > 50) {
            return NetworkTier.FAIR;
        }
        return NetworkTier.GOOD;
    }
}
