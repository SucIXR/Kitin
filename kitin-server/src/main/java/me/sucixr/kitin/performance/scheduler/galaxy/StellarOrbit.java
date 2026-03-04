package me.sucixr.kitin.performance.scheduler.galaxy;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.game.*;

public enum StellarOrbit {
    /** 光子轨道 (Level 3)：绝对最高路权，永远秒发。心跳、玩家强制位移 */
    PHOTON_ORBIT(3),

    /** 彗星轨道 (Level 2)：高路权。实体移动、攻击判定、血量更新 */
    COMET_ORBIT(2),

    /** 小行星轨道 (Level 1)：普通路权。方块更新、声音、普通聊天 */
    ASTEROID_ORBIT(1),

    /** 气态巨行星轨道 (Level 0)：最低路权（慢车道）。区块地形、大型光照更新 */
    GAS_GIANT_ORBIT(0);

    public final int level;

    StellarOrbit(int level) {
        this.level = level;
    }

    // 核心安检口：根据包的类型，分配物理轨道
    public static StellarOrbit classify(Packet<?> packet) {
        if (packet instanceof ClientboundKeepAlivePacket || packet instanceof ClientboundPlayerPositionPacket) {
            return PHOTON_ORBIT;
        }
        if (packet instanceof ClientboundSetEntityDataPacket || packet instanceof ClientboundMoveEntityPacket) {
            return COMET_ORBIT;
        }
        if (packet instanceof ClientboundLevelChunkWithLightPacket || packet instanceof ClientboundLightUpdatePacket) {
            return GAS_GIANT_ORBIT;
        }
        // 其他所有包默认走普通通道
        return ASTEROID_ORBIT;
    }
}