package me.sucixr.kitin.config;

import com.google.common.base.Throwables;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
            "Github: https://github.com/SucIXR/Kitin\n";

    private static File CONFIG_FILE;
    public static YamlConfiguration config;

    public static int version;
    public static boolean verbose;

    public static void init(File configFile) {
        CONFIG_FILE = configFile;
        config = new YamlConfiguration();
        try {
            config.load(CONFIG_FILE);
        } catch (IOException ignore) {
        } catch (InvalidConfigurationException ex) {
            Bukkit.getLogger().log(Level.SEVERE, "Could not load kitin.yml, please correct your syntax errors", ex);
            throw Throwables.propagate(ex);
        }
        config.options().header(HEADER);
        config.options().copyDefaults(true);

        version = getInt("config-version", 1);
        set("config-version", 1);

        readConfig(KitinConfig.class, null);
    }

    static void readConfig(Class<?> clazz, Object instance) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (Modifier.isPrivate(method.getModifiers())) {
                if (method.getParameterTypes().length == 0 && method.getReturnType() == Void.TYPE) {
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

    public static int globalMaxChunkSendRate = -1;
    private static void networkSettings() {
        globalMaxChunkSendRate = getInt("network.qos.global-max-chunk-send-rate", globalMaxChunkSendRate);
        me.sucixr.kitin.network.qos.GlobalChunkLimiter.setLimit(globalMaxChunkSendRate);
    }

    public static boolean useSimplerEntityPush = true;
    private static void physicsSettings() {
        useSimplerEntityPush = getBoolean("performance.physics.use-simpler-entity-push", useSimplerEntityPush);
    }

}