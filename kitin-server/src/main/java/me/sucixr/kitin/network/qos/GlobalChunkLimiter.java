package me.sucixr.kitin.network.qos;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class GlobalChunkLimiter {
    private static volatile int globalMaxChunkSendRate = -1;
    //private static final int GLOBAL_MAX_CHUNKS_PER_TICK = 3;//每Tick 允许全局发多少个包
//一个区块有多大？
//虚空/超平坦: 约 1 KB - 3 KB
//普通生存地形: 约 4 KB - 10 KB
//复杂主城/模组服: 可能会飙升到 20 KB - 50 KB
//GLOBAL_MAX_CHUNKS_PER_TICK=服务器带宽(以KB算)*0.98(一般生存服的地形带宽占比)/(一个区块的平均大小*20)
//一般来说，服务器Mbps*0.9(普通生存服)(取整)
//你可以认为，你服务器有多少Mbps。就填多少
    private static final AtomicInteger currentTickUsage = new AtomicInteger(0);
    private static final AtomicLong lastResetTime = new AtomicLong(System.currentTimeMillis());

    public static void setLimit(int limit) {
        globalMaxChunkSendRate = limit;
    }

    public static boolean tryAcquire() {
        int limit = globalMaxChunkSendRate;
        if (limit == -1) {
            return true;
        }
        tickReset();
        int current = currentTickUsage.get();
        if (current >= limit) {
            return false;
        }
        return currentTickUsage.incrementAndGet() <= limit;
    }

    private static void tickReset() {
        long now = System.currentTimeMillis();
        long last = lastResetTime.get();

        if (now - last >= 50) {
            if (lastResetTime.compareAndSet(last, now)) {
                currentTickUsage.set(0);
            }
        }
    }
}