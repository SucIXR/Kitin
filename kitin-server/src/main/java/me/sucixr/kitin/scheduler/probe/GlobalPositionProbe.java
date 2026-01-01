package me.sucixr.kitin.scheduler.probe;

import me.sucixr.kitin.scheduler.data.PlayerSnapshot;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

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
        //频率，ms计
        publisher.scheduleAtFixedRate(this::publish, 50, 50, TimeUnit.MILLISECONDS);//这里还真得用50，不然反而更卡
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

    private static final class PublishedIndex {
        static final PublishedIndex EMPTY = new PublishedIndex(new PlayerSnapshot[0]);
        final PlayerSnapshot[] snapshots;
        PublishedIndex(final PlayerSnapshot[] snapshots) { this.snapshots = snapshots; }
    }
}
