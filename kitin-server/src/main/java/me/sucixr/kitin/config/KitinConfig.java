package me.sucixr.kitin.config;

import com.google.common.base.Throwables;
import me.sucixr.kitin.network.qos.GlobalChunkLimiter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.*;
import java.util.logging.Level;
import static net.minecraft.core.RegistryAccess.LOGGER;

public class KitinConfig {
    public static void load() {
        try {
            me.sucixr.kitin.config.KitinConfig.init(new java.io.File("config/kitin.yml"));
        } catch (Exception e) {
            LOGGER.error("Failed to load Kitin configuration", e);
        }
    }

    private static final String HEADER = "Kitin Server Configuration\n" +
            "Folia fork maintained by SucIXR\n" +
            "Github: https://github.com/SucIXR/Kitin\n" +
            "具体配置文件含义详见项目文件/readme/kitin-yml.md\n" +
            "Configuration file meaning please refer to the project file/readme/kitin-yml.md";

    private static File CONFIG_FILE;
    public static YamlConfiguration config;

    public static int version;
    public static boolean verbose;
    private static boolean isReloading = false;

    // 独立的文件加载逻辑,原来在init内
    private static void loadYaml() {
        if (CONFIG_FILE == null) CONFIG_FILE = new File("config/kitin.yml");
        config = new YamlConfiguration();
        try {
            config.load(CONFIG_FILE);
        } catch (IOException ignore) {
        } catch (InvalidConfigurationException ex) {
            Bukkit.getLogger().log(Level.SEVERE, "Could not load kitin.yml", ex);
            throw Throwables.propagate(ex);
        }
        config.options().header(HEADER);
        config.options().copyDefaults(true);
    }

    public static void init(File configFile) {
        CONFIG_FILE = configFile;
        loadYaml();

        version = getInt("config-version", 1);
        set("config-version", 1);

        readConfig(KitinConfig.class, null);
    }

    // 反射重载
    public static String reload(String module) {
        isReloading = true;
        loadYaml();

        String target = module.toLowerCase(Locale.ROOT);

        if (target.equals("all")) {
            readConfig(KitinConfig.class, null);
            isReloading = false;
            return "All configurations";
        }

        boolean found = false;
        try {
            for (Method method : KitinConfig.class.getDeclaredMethods()) {
                if (Modifier.isPrivate(method.getModifiers()) && method.getParameterCount() == 0) {
                    String methodName = method.getName().toLowerCase(Locale.ROOT);
                    // 匹配逻辑：方法名包含输入的模块名，且以 settings 结尾
                    if (methodName.contains(target) && methodName.endsWith("settings")) {
                        method.setAccessible(true);
                        method.invoke(null);
                        found = true;
                        break;
                    }
                }
            }
        } catch (Exception ex) {
            isReloading = false;
            throw new RuntimeException(ex);
        }
        isReloading = false;

        if (found) return "Module '" + module + "'";
        throw new IllegalArgumentException("Unknown module: " + module);
    }

    // 给命令补全用的
    public static Set<String> getReloadableModules() {
        Set<String> modules = new HashSet<>();
        modules.add("all");
        for (Method method : KitinConfig.class.getDeclaredMethods()) {
            if (method.getName().endsWith("Settings")) {
                modules.add(method.getName().replace("Settings", "").toLowerCase(Locale.ROOT));
            }
        }
        return modules;
    }

    static void readConfig(Class<?> clazz, Object instance) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (Modifier.isPrivate(method.getModifiers())) {
                if (method.getParameterTypes().length == 0 && method.getReturnType() == Void.TYPE) {
                    // 解决配置文件加载异常，增加名称过滤，防止错误执行 loadYaml 方法
                    // 只有名字以 "Settings" 结尾的方法才会被视为配置加载方法
                    if (!method.getName().endsWith("Settings")) {
                        continue;
                    }
                    // end
                    try {
                        method.setAccessible(true);
                        method.invoke(instance);
                    } catch (InvocationTargetException ex) {
                        throw Throwables.propagate(ex.getCause());
                    } catch (Exception ex) {
                        Bukkit.getLogger().log(Level.SEVERE, "Error invoking " + method, ex);
                    }
                }
            }
        }

        try {
            config.save(CONFIG_FILE);
        } catch (IOException ex) {
            Bukkit.getLogger().log(Level.SEVERE, "Could not save " + CONFIG_FILE, ex);
        }
    }

    private static void set(String path, Object val) {
        config.addDefault(path, val);
        config.set(path, val);
    }

    private static boolean getBoolean(String path, boolean def) {
        config.addDefault(path, def);
        return config.getBoolean(path, config.getBoolean(path));
    }

    private static int getInt(String path, int def) {
        config.addDefault(path, def);
        return config.getInt(path, config.getInt(path));
    }

    private static double getDouble(String path, double def) {
        config.addDefault(path, def);
        return config.getDouble(path, config.getDouble(path));
    }

    private static String getString(String path, String def) {
        config.addDefault(path, def);
        return config.getString(path, config.getString(path));
    }



    private static List<String> getList(String path, List<String> def) {
        config.addDefault(path, def);
        return config.getStringList(path);
    }

    // Kitin start - Config helper
    private static <T> List<T> getListFromSection(String path, java.util.function.BiFunction<String, ConfigurationSection, T> mapper) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            // 如果配置不存在，注册一个空的默认值，使其在保存时显示为 {}
            config.addDefault(path, new HashMap<>());
            return new ArrayList<>();
        }

        List<T> result = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection subSection = section.getConfigurationSection(key);
            if (subSection != null) {
                T obj = mapper.apply(key, subSection);
                if (obj != null) result.add(obj);
            }
        }
        return result;
    }
    // Kitin end

    private static <T> Set<T> resolveRegistry(String key, net.minecraft.core.Registry<T> registry, net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<T>> registryKey) {
        Set<T> result = new HashSet<>();
        try {
            if (key.contains("*")) {
                String match = key.replace("*", "");
                for (T obj : registry) {
                    Identifier id = registry.getKey(obj);
                    if (id.getPath().contains(match)) {
                        result.add(obj);
                    }
                }
                return result;
            }
            if (key.startsWith("#")) {
                String tagString = key.substring(1);
                NamespacedKey nsKey = key.contains(":") ? NamespacedKey.fromString(tagString) : NamespacedKey.minecraft(tagString);
                if (nsKey != null) {
                    Identifier tagId = Identifier.tryParse(nsKey.toString());
                    TagKey<T> tagKey = TagKey.create(registryKey, tagId);
                    registry.getTagOrEmpty(tagKey).forEach(holder -> result.add(holder.value()));
                }
                return result;
            }
            NamespacedKey nsKey = key.contains(":") ? NamespacedKey.fromString(key) : NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT));
            if (nsKey != null) {
                Identifier nmsId = Identifier.tryParse(nsKey.toString());
                if (nmsId != null) {
                    registry.getOptional(nmsId).ifPresent(result::add);
                }
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Kitin] Parsing error (" + key + ")");
        }
        return result;
    }
    private static Set<EntityType<?>> getEntityType(String key) {
        return resolveRegistry(key, BuiltInRegistries.ENTITY_TYPE, Registries.ENTITY_TYPE);
    }
    private static Set<Item> getItemType(String key) {
        return resolveRegistry(key, BuiltInRegistries.ITEM, Registries.ITEM);
    }
    private static Set<Block> getBlockType(String key) {
        return resolveRegistry(key, BuiltInRegistries.BLOCK, Registries.BLOCK);
    }


//========================================================================

    //----------------------------------------

    public static volatile boolean disableMaxTntPerTickAndOptimize = false;
    public static volatile boolean pearlFixEnabled = true;
    public static volatile int pearlFixMaxSave = -1;
    private static void fixesSettings() {
        pearlFixEnabled = getBoolean("fixes.ender-pearl-chunk-loading.enabled", true);
        pearlFixMaxSave = getInt("fixes.ender-pearl-chunk-loading.player-max-save-ender-pearl", -1);
        //
        disableMaxTntPerTickAndOptimize = getBoolean("fixes.disable-max-tnt-per-tick-and-optimize", disableMaxTntPerTickAndOptimize);
    }

    //----------------------------------------

    public static volatile boolean chunkLazyLoading = true; // 加上volatile代表允许reload
    public static volatile int globalMaxChunkSendRate = -1;
    public static volatile double globalChunkSendBurstFactor = 0.05;
    public static volatile boolean optimizeAllBoats = false;
    public static volatile boolean optimizeAllMinecarts = false;
    public static volatile Set<EntityType<?>> optimizedSyncEntities = Collections.emptySet(); //原为 public static Set<EntityType<?>> optimizedSyncEntities = new HashSet<>();
    public static volatile int playerMaxParticlesPerPacket = 250;
    public static volatile int playerMaxBufferSize = 5000;
    public static volatile int playerMinOptimizeThreshold = 50;
    public static volatile int globalMaxDelayTicks = 10;
    public static volatile int globalMaxPacketParticlesPerTick = 499;
    
    // Kitin start - Multi-port listening
    public static class ListenerConfig {
        public final String name;
        public final String bindAddress;
        public final int port;
        public final boolean proxyProtocol;

        public ListenerConfig(String name, String bindAddress, int port, boolean proxyProtocol) {
            this.name = name;
            this.bindAddress = bindAddress;
            this.port = port;
            this.proxyProtocol = proxyProtocol;
        }
    }
    public static volatile List<ListenerConfig> extraListeners = Collections.emptyList();
    // Kitin end
    
    private static void networkSettings() {
        chunkLazyLoading = getBoolean("network.chunk-lazy-loading",true);
        //
        globalMaxChunkSendRate = getInt("network.chunk-send.global-max-chunk-send-rate", globalMaxChunkSendRate);
        globalChunkSendBurstFactor = getDouble("network.chunk-send.global-chunk-send-burst-factor", globalChunkSendBurstFactor);

        // Kitin start - QoS multi-line support
        List<GlobalChunkLimiter.GroupRule> rules = getListFromSection("network.chunk-send.qos-groups", (key, section) -> {
            double rate = section.getDouble("rate", -1.0);
            String bindAddress = section.getString("bind-address", null);
            String upstream = section.getString("upstream", null);
            String virtualHost = section.getString("virtual-host", null);

            java.util.function.Predicate<ServerPlayer> matcher = p -> {
                if (p.connection == null || p.connection.connection == null) return false;

                if (virtualHost != null && !virtualHost.isEmpty()) {
                    if (p.connection.connection.virtualHost != null) {
                        String playerVirtualHost = p.connection.connection.virtualHost.getHostString();
                        if (!playerVirtualHost.equalsIgnoreCase(virtualHost)) return false;
                    } else if (p.connection.connection.hostname != null) {
                        String hostname = p.connection.connection.hostname;
                        if (hostname.contains(":")) hostname = hostname.split(":")[0];
                        if (!hostname.equalsIgnoreCase(virtualHost)) return false;
                    } else {
                        return false;
                    }
                }

                if (bindAddress != null && !bindAddress.isEmpty()) {
                    if (p.connection.connection.channel == null) return false;
                    SocketAddress socketAddress = p.connection.connection.channel.localAddress();
                    if (socketAddress instanceof InetSocketAddress inetAddr) {
                        String localIp = inetAddr.getAddress().getHostAddress();
                        if (!localIp.equals(bindAddress)) return false;
                    } else {
                        return false;
                    }
                }
                return true;
            };

            return new GlobalChunkLimiter.GroupRule(key, rate, upstream, virtualHost, matcher);
        });

        // 始终添加默认组作为兜底
        rules.add(new GlobalChunkLimiter.GroupRule(GlobalChunkLimiter.DEFAULT_GROUP, globalMaxChunkSendRate, null, null, p -> true));
        GlobalChunkLimiter.reload(rules);
        // Kitin end
        
        // Kitin start - Multi-port listening
        if (!isReloading) { // 防止重载时重复绑定
            extraListeners = getListFromSection("network.extra-listeners", (key, section) -> {
                int port = section.getInt("port", -1);
                if (port < 0 || port > 65535) return null;

                String bindAddress = section.getString("bind-address", "0.0.0.0");
                boolean proxyProtocol = section.getBoolean("proxy-protocol", false);

                return new ListenerConfig(key, bindAddress, port, proxyProtocol);
            });
        }
        // Kitin end

        //
        optimizeAllBoats = false;
        optimizeAllMinecarts = false;
        Set<EntityType<?>> tempEntities = new HashSet<>(); // reload Folia安全 原为optimizedSyncEntities.clear();
        List<String> defaultEntities = new ArrayList<>();
        defaultEntities.add("$AbstractMinecart");
        defaultEntities.add("$AbstractBoat");
        defaultEntities.add("shulker");
        List<String> optimizedSyncEntities_configList = getList("network.reduce-high-frequency-entity-sync-packets.entitys", defaultEntities);
        for (String key : optimizedSyncEntities_configList) {
            if (key.equalsIgnoreCase("$AbstractMinecart")) {
                optimizeAllMinecarts = true;
                continue;
            }
            if (key.equalsIgnoreCase("$AbstractBoat")) {
                optimizeAllBoats = true;
                continue;
            }
            tempEntities.addAll(getEntityType(key)); // reload Folia安全
        }
        optimizedSyncEntities = Set.copyOf(tempEntities); // reload Folia安全
        //
        playerMaxParticlesPerPacket = getInt("network.particle.player-max-particles-per-packet", playerMaxParticlesPerPacket);
        //playerMaxBufferSize = getInt("network.particle.player-max-buffer-size", playerMaxBufferSize);
        playerMinOptimizeThreshold = getInt("network.particle.player-min-optimize-threshold", playerMinOptimizeThreshold);
        globalMaxDelayTicks = getInt("network.particle.global-max-delay-ticks", globalMaxDelayTicks);
        globalMaxPacketParticlesPerTick = getInt("network.particle.global-max-packet-particles-per-tick", globalMaxPacketParticlesPerTick);

    }

    public static volatile boolean useSimplerEntityPush = true;
    public static volatile boolean optimizeDropper = false;
    private static void performanceSettings() {
        useSimplerEntityPush = getBoolean("performance.use-simpler-entity-push", useSimplerEntityPush);
        //
        optimizeDropper = getBoolean("performance.optimize-dropper", false); //可以加入if (!isReloading)判断来拒绝重载
    }

    //----------------------------------------

    public static volatile Set<net.minecraft.world.item.Item> lazyChunkBarrierItems = Collections.emptySet();
    public static volatile Set<Block> sandDuperBlacklist = Collections.emptySet();
    public static volatile double sandDuperMinTps = 5.0; // Kitin - TPS protection
    public static volatile boolean highAltitudeVoidEnabled = true;
    public static volatile int highAltitudeVoidHeight = 114514;
    public static volatile boolean highAltitudeVoidDamagePlayers = false;
    public static volatile boolean highAltitudeVoidLogWarnings = true;
    public static volatile boolean useSecureSeed = false;
    private static void safetySettings() {
        Set<Item> lazyChunkBarrierItems_tempItems = new HashSet<>(); // reload Folia安全
        List<String> lazyChunkBarrierItems_defaultItems = new ArrayList<>();
        lazyChunkBarrierItems_defaultItems.add("*concrete");
        lazyChunkBarrierItems_defaultItems.add("#wool_carpets");
        lazyChunkBarrierItems_defaultItems.add("minecraft:obsidian");
        lazyChunkBarrierItems_defaultItems.add("minecraft:poppy");
        lazyChunkBarrierItems_defaultItems.add("minecraft:prismarine_shard");
        lazyChunkBarrierItems_defaultItems.add("prismarine_crystals");
        List<String> lazyChunkBarrierItemsconfigList = getList("safety.lazy-chunk-barrier.items", lazyChunkBarrierItems_defaultItems);
        for (String key : lazyChunkBarrierItemsconfigList) {
            lazyChunkBarrierItems_tempItems.addAll(getItemType(key)); // reload Folia安全
        }
        lazyChunkBarrierItems = Set.copyOf(lazyChunkBarrierItems_tempItems); // reload Folia安全
        //
        Set<Block> blacklistblocks_tempBlocks = new HashSet<>(); // reload Folia安全
        List<String> blacklistblocks_defaultBlocks = new ArrayList<>();
        List<String> blacklistblocks_configList = getList("safety.sand-duper.blacklistblocks", blacklistblocks_defaultBlocks);
        for (String key : blacklistblocks_configList) {
            blacklistblocks_tempBlocks.addAll(getBlockType(key)); // reload Folia安全
        }
        sandDuperBlacklist = Set.copyOf(blacklistblocks_tempBlocks); // reload Folia安全
        sandDuperMinTps = getDouble("safety.sand-duper.min-tps-threshold", 5.0);
        //
        // Kitin start - High Altitude Void
        highAltitudeVoidEnabled = getBoolean("safety.high-altitude-void.enabled", true);
        highAltitudeVoidHeight = getInt("safety.high-altitude-void.max-height", 10000);
        highAltitudeVoidDamagePlayers = getBoolean("safety.high-altitude-void.damage-players", false);
        highAltitudeVoidLogWarnings = getBoolean("safety.high-altitude-void.log-warnings", true);
        // Kitin end
        //
        useSecureSeed = getBoolean("safety.use-secure-seed",false);
    }

}