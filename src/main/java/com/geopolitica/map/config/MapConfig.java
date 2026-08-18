package com.geopolitica.map.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class MapConfig {

    private final JavaPlugin plugin;
    private FileConfiguration config;

    public MapConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public boolean isProviderEnabled(String key) {
        return config.getBoolean("providers." + key, true);
    }

    public int getResyncIntervalSeconds() {
        return Math.max(10, config.getInt("resync-interval-seconds", 300));
    }

    public double getFillOpacity() {
        return clamp01(config.getDouble("fill-opacity", 0.35));
    }

    public double getStrokeOpacity() {
        return clamp01(config.getDouble("stroke-opacity", 0.85));
    }

    public int getStrokeWeight() {
        return Math.max(1, config.getInt("stroke-weight", 2));
    }

    public float getShapeHeight() {
        return (float) config.getDouble("shape-height", 65.0);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
