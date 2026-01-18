package me.sucixr.kitin.other.old.retention;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
//import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;

public final class EnderPearlRetentionPolicy implements LogicalEntityRetentionPolicy {

    private static final EnderPearlRetentionPolicy INSTANCE = new EnderPearlRetentionPolicy();
    public static EnderPearlRetentionPolicy getInstance() { return INSTANCE; }

    @Override
    public RetentionDecision decide(ServerLevel level, Entity entity) {
        // 只修 Ender Pearl loader：只保活珍珠
        if (entity instanceof ThrownEnderpearl) {
            // 半径 2：更稳（5x5 chunks），半径 1：更省
            return RetentionDecision.yesRadius(2);
        }
        return RetentionDecision.no();
    }
}
