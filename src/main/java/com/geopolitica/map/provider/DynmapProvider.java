package com.geopolitica.map.provider;

import com.geopolitica.map.GeopoliticaMapPlugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;

import java.util.logging.Level;

/**
 * Draws one {@link AreaMarker} per claimed chunk in a dedicated "Geopolitica"
 * marker set, using Dynmap's {@code dynmap-api} - the lightweight artifact
 * Dynmap itself publishes specifically for addons like this one.
 */
public class DynmapProvider implements MapProvider {

    private static final String SET_ID = "geopolitica.towns";

    private GeopoliticaMapPlugin plugin;
    private MarkerSet markerSet;

    @Override
    public String getName() {
        return "Dynmap";
    }

    @Override
    public boolean tryEnable(GeopoliticaMapPlugin plugin) {
        Plugin dynmap = Bukkit.getPluginManager().getPlugin("dynmap");
        if (dynmap == null || !dynmap.isEnabled()) {
            return false;
        }
        this.plugin = plugin;
        try {
            DynmapAPI api = (DynmapAPI) dynmap;
            if (!api.markerAPIInitialized()) {
                return false;
            }
            MarkerAPI markerAPI = api.getMarkerAPI();
            if (markerAPI == null) {
                return false;
            }
            MarkerSet set = markerAPI.getMarkerSet(SET_ID);
            if (set == null) {
                set = markerAPI.createMarkerSet(SET_ID, "Geopolitica", null, false);
            }
            this.markerSet = set;
            return this.markerSet != null;
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Failed to hook into Dynmap", t);
            return false;
        }
    }

    @Override
    public void disable() {
        if (markerSet != null) {
            try {
                markerSet.deleteMarkerSet();
            } catch (Throwable ignored) {
                // Dynmap may already be disabling.
            }
            markerSet = null;
        }
    }

    @Override
    public void upsertClaim(ClaimMarker marker) {
        if (markerSet == null) {
            return;
        }
        String id = marker.markerId();
        double[] xs = {marker.minX(), marker.minX(), marker.maxX(), marker.maxX()};
        double[] zs = {marker.minZ(), marker.maxZ(), marker.maxZ(), marker.minZ()};

        AreaMarker area = markerSet.findAreaMarker(id);
        if (area == null) {
            area = markerSet.createAreaMarker(id, marker.label(), true, marker.world(), xs, zs, false);
            if (area == null) {
                return;
            }
        } else {
            area.setCornerLocations(xs, zs);
            area.setLabel(marker.label(), true);
        }

        int rgb = marker.color().getRGB() & 0xFFFFFF;
        area.setFillStyle(plugin.getMapConfig().getFillOpacity(), rgb);
        area.setLineStyle(plugin.getMapConfig().getStrokeWeight(), plugin.getMapConfig().getStrokeOpacity(), rgb);
        area.setDescription(marker.popupHtml());
    }

    @Override
    public void removeClaim(String world, int chunkX, int chunkZ) {
        if (markerSet == null) {
            return;
        }
        AreaMarker area = markerSet.findAreaMarker(ClaimMarker.markerId(world, chunkX, chunkZ));
        if (area != null) {
            area.deleteMarker();
        }
    }
}
