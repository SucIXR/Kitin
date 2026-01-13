package me.sucixr.kitin.scheduler.old;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import org.slf4j.Logger;
public class KitinWatchdog {
    private static final Logger LOGGER = LogUtils.getLogger();

    // 阈值: 5秒 (5,000,000,000 纳秒)
    //private static final long TIME_BUDGET_NS = 5_000_000_000L;
    private static final long TIME_BUDGET_NS = 20_000_000L;

    // 日志冷却: 10秒
    private static final long LOG_COOLDOWN_MS = 10_000L;

    private long tickStartTime;
    private long lastLogTime;

    /**
     * 在每 tick 开始时调用，重置计时器
     */
    public void startTick() {
        this.tickStartTime = System.nanoTime();
    }

    /**
     * 检查实体是否超时。如果超时，执行清理并返回 true。
     * @param entity 当前正在处理的实体
     * @return 如果实体被熔断清理了，返回 true；否则返回 false。
     */
    public boolean checkAndDiscard(Entity entity) {
        // [修改点 2] 安全检查：如果 tickStartTime 是 0，说明 ServerLevel.tick() 还没跑或者没调用 startTick
        // 此时绝对不能拦截，直接放行，防止误删实体！
        if (this.tickStartTime == 0) {
            return false;
        }
        // 1. 快速筛选：只监控掉落物和经验球
        if (!(entity instanceof ItemEntity) && !(entity instanceof ExperienceOrb)) {
            return false;
        }

        // 2. 检查时间预算
        long duration = System.nanoTime() - this.tickStartTime;
        if (duration > TIME_BUDGET_NS) {

            // 3. 记录日志 (带冷却)
            long now = System.currentTimeMillis();
            if (now - this.lastLogTime > LOG_COOLDOWN_MS) {
                this.lastLogTime = now;

                // 使用 SLF4J 占位符风格
                LOGGER.warn("Tick overload detected! Discarding entity {} at {} (Tick duration: {}ms)",
                        entity.getType().getDescriptionId(),
                        entity.blockPosition(),
                        duration / 1_000_000L
                );
            }

            // 4. 执行熔断
            entity.discard();
            return true; // 告诉 ServerLevel 这个实体已经被处理掉了，跳过后续逻辑
        }

        return false;
    }
}
