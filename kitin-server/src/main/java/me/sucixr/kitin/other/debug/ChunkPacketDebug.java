//package me.sucixr.kitin.other.debug;
//
//import com.mojang.logging.LogUtils;
//import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
//import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
//import net.minecraft.nbt.CompoundTag;
//import net.minecraft.nbt.NbtIo;
//import org.slf4j.Logger;
//
//import java.io.DataOutputStream;
//import java.io.OutputStream;
//import java.util.List;
//import java.util.concurrent.atomic.LongAdder;
//
//public class ChunkPacketDebug {
//    private static final Logger LOGGER = LogUtils.getLogger();
//    private static final long PRINT_INTERVAL_MS = 5000;
//
//    // 启动时间，用于计算平均流速
//    private static final long START_TIME = System.currentTimeMillis();
//    private static long lastPrintTime = System.currentTimeMillis();
//
//    // === 累积总量计数器 (Total Cumulative) ===
//    // 1. 内部制造的数据量 (CPU生成量)
//    private static final LongAdder totalInternalBytes = new LongAdder();
//    private static final LongAdder totalInternalTerrain = new LongAdder();
//    private static final LongAdder totalInternalLight = new LongAdder();
//    private static final LongAdder totalInternalNBT = new LongAdder();
//
//    // 2. 真实网络发送量 (网卡发送量)
//    private static final LongAdder totalNetworkRaw = new LongAdder(); // 压缩前交给Netty的大小
//    private static final LongAdder totalNetworkReal = new LongAdder(); // 压缩后实际发出的大小
//
//    // 辅助流
//    private static final ByteCounterOutputStream COUNT_STREAM = new ByteCounterOutputStream();
//    private static final DataOutputStream DATA_OUT = new DataOutputStream(COUNT_STREAM);
//
//    /**
//     * [注入点1] 记录区块内部结构 (由 PlayerChunkSender 调用)
//     */
//    public static void record(ClientboundLevelChunkWithLightPacket packet) {
//        if (packet == null) return;
//        try {
//            long beSize = 0;
//            // 1. 计算 NBT
//            List<ClientboundLevelChunkPacketData.BlockEntityInfo> blockEntities =
//                    packet.getChunkData().getBlockEntitiesData();
//
//            synchronized (DATA_OUT) {
//                if (blockEntities != null) {
//                    for (ClientboundLevelChunkPacketData.BlockEntityInfo info : blockEntities) {
//                        CompoundTag tag = info.getTag();
//                        if (tag != null) {
//                            COUNT_STREAM.reset();
//                            NbtIo.write(tag, DATA_OUT);
//                            beSize += COUNT_STREAM.getCount();
//                        }
//                    }
//                }
//            }
//            totalInternalNBT.add(beSize);
//
//            // 2. 计算地形
//            long terrainSize = packet.getChunkData().getReadBuffer().readableBytes();
//            totalInternalTerrain.add(terrainSize);
//
//            // 3. 计算光照
//            long lightSize = 0;
//            if (packet.getLightData() != null) {
//                if (packet.getLightData().getSkyUpdates() != null) {
//                    for (byte[] data : packet.getLightData().getSkyUpdates()) if (data != null) lightSize += data.length;
//                }
//                if (packet.getLightData().getBlockUpdates() != null) {
//                    for (byte[] data : packet.getLightData().getBlockUpdates()) if (data != null) lightSize += data.length;
//                }
//            }
//            totalInternalLight.add(lightSize);
//
//            // 汇总内部数据
//            totalInternalBytes.add(beSize + terrainSize + lightSize);
//
//        } catch (Exception e) {
//            // ignore
//        }
//    }
//
//    /**
//     * [注入点2] 记录真实网络流量 (由 CompressionEncoder 调用)
//     */
//    public static void recordTraffic(long rawSize, long compressedSize) {
//        totalNetworkRaw.add(rawSize);
//        totalNetworkReal.add(compressedSize);
//        printReport();
//    }
//
//    private static void printReport() {
//        long now = System.currentTimeMillis();
//        if (now - lastPrintTime < PRINT_INTERVAL_MS) return;
//
//        synchronized (ChunkPacketDebug.class) {
//            if (now - lastPrintTime < PRINT_INTERVAL_MS) return;
//            lastPrintTime = now;
//
//            // 获取快照数据
//            long durationSec = (now - START_TIME) / 1000;
//            if (durationSec == 0) durationSec = 1;
//
//            // 网络层数据
//            long netReal = totalNetworkReal.sum();
//            long netRaw = totalNetworkRaw.sum();
//
//            // 内部层数据
//            long intTotal = totalInternalBytes.sum();
//            long intLight = totalInternalLight.sum();
//            long intTerr = totalInternalTerrain.sum();
//            long intNBT = totalInternalNBT.sum();
//
//            if (netReal == 0 && intTotal == 0) return;
//
//            // 计算压缩率 (Network Efficiency)
//            double compressionRatio = netRaw > 0 ? (double) netReal / netRaw * 100.0 : 0.0;
//
//            // 计算平均流速
//            String speedReal = formatSize(netReal / durationSec) + "/s";
//            String speedInternal = formatSize(intTotal / durationSec) + "/s";
//
//            LOGGER.info(" ");
//            LOGGER.info("================== [Kitin Traffic Audit] ==================");
//            LOGGER.info(String.format(" Duration: %ds | Global Compress Ratio: %.1f%% (Lower is Better)", durationSec, compressionRatio));
//            LOGGER.info("-----------------------------------------------------------");
//            LOGGER.info(String.format(" %-15s | %-18s | %-18s", "METRIC", "INTERNAL (Gen)", "NETWORK (Sent)"));
//            LOGGER.info("-----------------|--------------------|--------------------");
//            LOGGER.info(String.format(" %-15s | %-18s | %-18s", "TOTAL DATA", formatSize(intTotal), formatSize(netReal)));
//            LOGGER.info(String.format(" %-15s | %-18s | %-18s", "AVG SPEED", speedInternal, speedReal));
//            LOGGER.info("-----------------------------------------------------------");
//            LOGGER.info(" [Internal Composition] (Who generated this data?)");
//            LOGGER.info(String.format("   💡 Light:    %6.1f%%  [%s]", calcRatio(intLight, intTotal), formatSize(intLight)));
//            LOGGER.info(String.format("   🌍 Terrain:  %6.1f%%  [%s]", calcRatio(intTerr, intTotal), formatSize(intTerr)));
//            LOGGER.info(String.format("   📦 NBT:      %6.1f%%  [%s]", calcRatio(intNBT, intTotal), formatSize(intNBT)));
//            LOGGER.info("===========================================================");
//            LOGGER.info(" ");
//        }
//    }
//
//    private static double calcRatio(long part, long total) {
//        if (total == 0) return 0.0;
//        return (double) part / total * 100.0;
//    }
//
//    private static String formatSize(long v) {
//        if (v < 1024) return v + " B";
//        int z = (63 - Long.numberOfLeadingZeros(v)) / 10;
//        return String.format("%.2f %sB", (double)v / (1L << (z*10)), " KMGTPE".charAt(z));
//    }
//
//    private static class ByteCounterOutputStream extends OutputStream {
//        private long count = 0;
//        public void write(int b) { count++; }
//        public void write(byte[] b, int off, int len) { count += len; }
//        public long getCount() { return count; }
//        public void reset() { count = 0; }
//    }
//}