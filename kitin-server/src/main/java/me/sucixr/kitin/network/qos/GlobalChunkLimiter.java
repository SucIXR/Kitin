package me.sucixr.kitin.network.qos;

import me.sucixr.kitin.config.KitinConfig;

import java.util.concurrent.TimeUnit;

public class GlobalChunkLimiter {

    // 配置：每秒限制多少个区块
    private static volatile double globalRate = -1.0;

    // 核心限流器实例 (直接使用 Paper 的算法)
    private static final AllocatingRateLimiter limiter = new AllocatingRateLimiter(TimeUnit.SECONDS.toNanos(1L));

    // 锁对象，确保多线程并发安全 (Paper 的原类不是线程安全的)
    private static final Object mutex = new Object();

    /**
     * 设置全局限流
     * @param chunksPerSecond 每秒最大区块数
     */
    public static void setLimit(double chunksPerSecond) {
        synchronized (mutex) {
            if (chunksPerSecond <= 0) {
                globalRate = -1.0;
            } else {
                globalRate = chunksPerSecond;
                // 重置限流器状态
                limiter.reset(System.nanoTime());
            }
        }
    }

    /**
     * 尝试获取发送 1 个区块的权限
     * @return true=允许发送, false=拦截
     */
    public static boolean tryAcquire() {
        if (globalRate <= 0) {
            return true;
        }

        synchronized (mutex) {
            long now = System.nanoTime();

            // 1. 先"发工资" (Tick Allocation)
            // 允许的最大突发量(MaxAllocation)建议设为 1秒 的量，或者更平滑点 0.1秒
            //double maxBurst = globalRate * 0.05;
            // [Kitin Fix] 允许 0.2秒 (200ms) 的突发量，应对高频循环
            double maxBurst = Math.max(1.0, globalRate * KitinConfig.globalChunkSendBurstFactor);
            limiter.tickAllocation(now, globalRate, maxBurst);

            // 2. 尝试"消费" (Take Allocation)
            // 尝试取走 1 个令牌
            long taken = limiter.takeAllocation(now, globalRate, 1);

            return taken == 1;
        }
    }

    // ==================================================================================
    // 下面直接复制 Paper (Moonrise) 的 AllocatingRateLimiter 源码
    // ==================================================================================

    private static final class AllocatingRateLimiter {
        // max difference granularity in ns
        private final long maxGranularity;

        private double allocation = 0.0;
        private long lastAllocationUpdate;

        // 浮点数补偿：存储上次取整后剩下的小数
        private double takeCarry = 0.0;
        private long lastTakeUpdate;

        public AllocatingRateLimiter(final long maxGranularity) {
            this.maxGranularity = maxGranularity;
        }

        public void reset(final long time) {
            this.allocation = 0.0; // 可能需要给个初始额度?
            this.lastAllocationUpdate = time;
            this.takeCarry = 0.0;
            this.lastTakeUpdate = time;
        }

        // rate in units/s, and time in ns
        public void tickAllocation(final long time, final double rate, final double maxAllocation) {
            // 防止时间倒流或突发过大
            long timeDiff = time - this.lastAllocationUpdate;
            if (timeDiff < 0) timeDiff = 0; // Kitin fix: 防御性编程

            final long diff = Math.min(this.maxGranularity, timeDiff);
            this.lastAllocationUpdate = time;

            // 核心公式：现有令牌 + (时间差 * 速率)
            this.allocation = Math.min(maxAllocation - this.takeCarry, this.allocation + rate * (diff * 1.0E-9D));
        }

        // rate in units/s, and time in ns
        public long takeAllocation(final long time, final double rate, final long maxTake) {
            if (maxTake < 1L) {
                return 0L;
            }

            double ret = this.takeCarry;

            long timeDiff = time - this.lastTakeUpdate;
            if (timeDiff < 0) timeDiff = 0; // Kitin fix

            final long diff = Math.min(this.maxGranularity, timeDiff);
            this.lastTakeUpdate = time;

            // 这里 Paper 做了一个很聪明的处理：
            // 它在 take 的时候，也会根据当前时间再次计算一下 allocation（虽然 tickAllocation 算过了）
            // 这里的逻辑是计算 "本次 take 能拿到的最大额度"
            // Math.min(maxTake - takeCarry, allocation) 是桶里有的
            // rate * (diff * 1.0E-9) 是这微小瞬间产生的
//            final double take = Math.min(
//                    Math.min((double)maxTake - this.takeCarry, this.allocation),
//                    rate * (diff * 1.0E-9)
//            ); // 不行，会发现地形加载非常缓慢，强制1tick一次

            final double take = Math.min((double)maxTake - this.takeCarry, this.allocation);

            // 累加到 ret (包含上次的 carry)
            ret += take;
            // 扣除桶里的令牌
            this.allocation -= take;

            // 向下取整，算出实际能拿多少个整数令牌
            final long retInteger = (long)Math.floor(ret);

            // 把剩下的小数存回 takeCarry，留给下次用
            this.takeCarry = ret - (double)retInteger;

            return retInteger;
        }
    }
}