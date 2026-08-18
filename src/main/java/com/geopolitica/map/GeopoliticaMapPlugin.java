package com.geopolitica.map;

import com.geopolitica.api.GeopoliticaAPI;
import com.geopolitica.api.service.ClaimService;
import com.geopolitica.api.service.TownService;
import com.geopolitica.map.command.MapCommand;
import com.geopolitica.map.config.MapConfig;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;

public class GeopoliticaMapPlugin extends JavaPlugin {

    private MapConfig mapConfig;
    private MapManager mapManager;
    private BukkitTask resyncTask;

    @Override
    public void onEnable() {
        mapConfig = new MapConfig(this);
        mapConfig.load();

        TownService townService;
        ClaimService claimService;
        try {
            townService = GeopoliticaAPI.getTownService();
            claimService = GeopoliticaAPI.getClaimService();
        } catch (IllegalStateException e) {
            getLogger().severe("Geopolitica is not loaded; disabling. " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        mapManager = new MapManager(this, mapConfig, townService, claimService);
        getServer().getPluginManager().registerEvents(mapManager, this);
        mapManager.enable();

        getCommand("gmap").setExecutor(new MapCommand(this));

        scheduleResync();

        getLogger().info("Geopolitica Map enabled.");
    }

    @Override
    public void onDisable() {
        if (resyncTask != null) {
            resyncTask.cancel();
            resyncTask = null;
        }
        if (mapManager != null) {
            mapManager.disable();
        }
        getLogger().info("Geopolitica Map disabled.");
    }

    /** Reloads config, re-runs map plugin detection and does a full marker resync. */
    public void reload() {
        if (resyncTask != null) {
            resyncTask.cancel();
            resyncTask = null;
        }
        mapConfig.load();
        mapManager.disable();
        mapManager.enable();
        scheduleResync();
    }

    private void scheduleResync() {
        long intervalTicks = mapConfig.getResyncIntervalSeconds() * 20L;
        resyncTask = getServer().getScheduler().runTaskTimer(this, mapManager::resyncAll, intervalTicks, intervalTicks);
    }

    public MapConfig getMapConfig() {
        return mapConfig;
    }

    public MapManager getMapManager() {
        return mapManager;
    }
}
