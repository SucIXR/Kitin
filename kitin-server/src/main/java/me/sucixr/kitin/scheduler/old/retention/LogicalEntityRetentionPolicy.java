package me.sucixr.kitin.scheduler.old.retention;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

public interface LogicalEntityRetentionPolicy {
    RetentionDecision decide(ServerLevel level, Entity entity);
}
