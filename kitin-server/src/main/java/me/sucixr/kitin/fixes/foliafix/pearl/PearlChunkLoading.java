package me.sucixr.kitin.fixes.foliafix.pearl;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueOutput;

import static me.sucixr.kitin.fixes.foliafix.pearl.PearlSnapshot.PEARL_DATA_MAP;

/*
环节	    原版逻辑	             Folia的限制手段	                后果
运行时	珍珠跨区读取 Player对象  ServerLevel禁止跨区查找实体	    珍珠以为玩家离线，被Folia严格的owner != null检测抹杀
存盘时	玩家 NBT 记录珍珠坐标	ServerPlayer 移除珍珠注册列表	下线不保存珍珠数据，珍珠丢失
读盘时	玩家上线加载珍珠区块	   玩家 NBT 为空，不发 Ticket	    珍珠的生成就是靠NBT，没有就不生成
传送时	珍珠直接修改玩家坐标	   通过异步传送	                    暂未发现

总结:Folia几乎在源码的各个地方都大刀阔斧地阻止了实体跨线程访问!owner != null检测遍地都是,如果直接修复原有珍珠方法几乎不可能

实现原理:
特性        Folia               Kitin                        效果
玩家查找    查当前 Region(局部)   查 PlayerList (全局)        无视跨区，永不丢失目标
传送执行    异步                  同Folia                     Folia本身珍珠传送就是异步的，意义何在
区块加载    依赖玩家对象存在       无条件自我维持               [下线不消失，区块常驻]

以下内容是为了解决"永不丢失目标导致的玩家退出游戏后珍珠依旧存在"而进行的修复
修复方法:
影子持久化 运行时维护一个轻量级静态快照SimplePearlData。玩家下线时将快照写入 NBT，上线时由 Folia 自动复活珍珠，实现"下线消失，上线复活"。
线程桥接  利用taskScheduler将传送逻辑调度至玩家所在的线程执行。
这种方案巧妙避开了Folia的重重线程安全检测
 */

public class PearlChunkLoading {
    public static void saveEnderPearls(ServerPlayer player, ValueOutput output) {
        if (me.sucixr.kitin.config.KitinConfig.pearlFixNotSave) {
            return;
        }
        PearlSnapshot.SimplePearlData data = PEARL_DATA_MAP.remove(player.getUUID());

        if (data != null) {
            ValueOutput.ValueOutputList valueOutputList = output.childrenList("ender_pearls");
            ValueOutput child = valueOutputList.addChild();
            net.minecraft.resources.Identifier dimId = net.minecraft.resources.Identifier.parse(data.dimensionId());
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimKey =
                    net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimId);
            child.store("ender_pearl_dimension", net.minecraft.world.level.Level.RESOURCE_KEY_CODEC, dimKey);
            child.putString("id", "minecraft:ender_pearl");
            child.store("Owner", net.minecraft.core.UUIDUtil.CODEC, data.ownerId());
            child.store("Pos", net.minecraft.world.phys.Vec3.CODEC, new net.minecraft.world.phys.Vec3(data.x(), data.y(), data.z()));
            child.store("Motion", net.minecraft.world.phys.Vec3.CODEC, new net.minecraft.world.phys.Vec3(data.motX(), data.motY(), data.motZ()));
            child.store("Rotation", net.minecraft.world.phys.Vec2.CODEC, new net.minecraft.world.phys.Vec2(data.yRot(), data.xRot()));
        }
    }
}
