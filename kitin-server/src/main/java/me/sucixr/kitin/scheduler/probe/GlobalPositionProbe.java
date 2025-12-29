package me.sucixr.kitin.scheduler.probe;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.UUID;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class GlobalPositionProbe {

    private static final GlobalPositionProbe INSTANCE = new GlobalPositionProbe();
    public static GlobalPositionProbe getInstance() { return INSTANCE; }

    // 采样态：tick 线程写，异步线程读
    private final Map<UUID, PlayerSnapshot> live = new ConcurrentHashMap<>();

    // 发布态：异步线程写（原子替换），tick 线程只读
    private final AtomicReference<PublishedIndex> published =
            new AtomicReference<>(PublishedIndex.EMPTY);

    private final ScheduledExecutorService publisher =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "Kitin-GlobalPositionProbe-Publisher");
                t.setDaemon(true);
                return t;
            });

    private GlobalPositionProbe() {
        // 50ms 发布一次（可以改 100ms 更省）
        publisher.scheduleAtFixedRate(this::publish, 50, 50, TimeUnit.MILLISECONDS);
    }

    /** tick 线程调用：O(1) 更新该玩家快照 */
    public void pushSnapshot(final ServerPlayer player) {
        live.put(player.getUUID(), PlayerSnapshot.capture(player));
    }

    /** 玩家退出/卸载时调用 */
    public void remove(final UUID uuid) {
        live.remove(uuid);
    }

    /** 异步线程调用：把 live 发布为只读数组（无锁切换） */
    private void publish() {
        // 注意：这里不要加锁 live，也不要阻塞 tick 线程
        final PlayerSnapshot[] arr = live.values().toArray(PlayerSnapshot[]::new);
        published.set(new PublishedIndex(arr));
    }

    /**
     * tick 线程调用：只读 published，不拿锁，不做重活
     * rangeSqr 用 double，避免 sqrt。
     */
    public void forEachNearby(
            final ServerPlayer receiver,
            final double rangeSqr,
            final Consumer<PlayerSnapshot> consumer
    ) {
        final PublishedIndex idx = published.get();
        final PlayerSnapshot[] arr = idx.snapshots;
        if (arr.length == 0) return;

        final double rx = receiver.getX();
        final double ry = receiver.getY();
        final double rz = receiver.getZ();
        final ResourceKey<Level> dim = receiver.level().dimension();

        for (final PlayerSnapshot s : arr) {
            if (s == null) continue;
            if (!dim.equals(s.dimension())) continue;
            if (s.uuid().equals(receiver.getUUID())) continue;

            // 可选：只让“正在发射定位”的目标参与
            if (!s.transmittingWaypoints()) continue;

            final double dx = rx - s.x();
            final double dy = ry - s.y();
            final double dz = rz - s.z();
            final double d2 = dx*dx + dy*dy + dz*dz;

            if (d2 <= rangeSqr) {
                consumer.accept(s);
            }
        }
    }

    private static final class PublishedIndex {
        static final PublishedIndex EMPTY = new PublishedIndex(new PlayerSnapshot[0]);
        final PlayerSnapshot[] snapshots;
        PublishedIndex(final PlayerSnapshot[] snapshots) { this.snapshots = snapshots; }
    }
}
