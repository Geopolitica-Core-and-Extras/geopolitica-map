package com.geopolitica.map.provider;

import com.geopolitica.map.GeopoliticaMapPlugin;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Shape;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Draws one {@link ShapeMarker} per claimed chunk on every BlueMap map for
 * the claim's world, in a shared "Geopolitica" {@link MarkerSet}.
 *
 * <p>BlueMap's API is asynchronous by nature - it may finish loading (or be
 * reloaded) well after this plugin enables - so the live {@link BlueMapAPI}
 * instance is tracked via {@link BlueMapAPI#onEnable}/{@link BlueMapAPI#onDisable}
 * rather than fetched once.</p>
 */
public class BlueMapProvider implements MapProvider {

    private static final String SET_ID = "geopolitica";

    private GeopoliticaMapPlugin plugin;
    private volatile BlueMapAPI api;
    private Consumer<BlueMapAPI> onEnable;
    private Consumer<BlueMapAPI> onDisable;

    @Override
    public String getName() {
        return "BlueMap";
    }

    @Override
    public boolean tryEnable(GeopoliticaMapPlugin plugin) {
        Plugin bluemap = Bukkit.getPluginManager().getPlugin("BlueMap");
        if (bluemap == null || !bluemap.isEnabled()) {
            return false;
        }
        this.plugin = plugin;
        this.onEnable = a -> {
            this.api = a;
            plugin.getMapManager().resyncAll();
        };
        this.onDisable = a -> this.api = null;

        BlueMapAPI.onEnable(onEnable);
        BlueMapAPI.onDisable(onDisable);
        BlueMapAPI.getInstance().ifPresent(a -> this.api = a);
        return true;
    }

    @Override
    public void ensureLayers(GeopoliticaMapPlugin plugin) {
        BlueMapAPI current = this.api;
        if (current == null) {
            // Not ready yet (BlueMap's own world/map setup is asynchronous) - re-check
            // in case onEnable() already fired before we registered the listener above.
            BlueMapAPI.getInstance().ifPresent(a -> this.api = a);
            current = this.api;
        }
        if (current == null) {
            return;
        }
        for (BlueMapMap map : current.getMaps()) {
            map.getMarkerSets().computeIfAbsent(SET_ID, id -> new MarkerSet("Geopolitica"));
        }
    }

    @Override
    public void disable() {
        if (onEnable != null) {
            BlueMapAPI.unregisterListener(onEnable);
        }
        if (onDisable != null) {
            BlueMapAPI.unregisterListener(onDisable);
        }
        BlueMapAPI current = this.api;
        if (current != null) {
            for (BlueMapMap map : current.getMaps()) {
                map.getMarkerSets().remove(SET_ID);
            }
        }
        this.api = null;
    }

    @Override
    public void upsertClaim(ClaimMarker marker) {
        withMapsOf(marker.world(), map -> {
            MarkerSet set = map.getMarkerSets().computeIfAbsent(SET_ID, id -> new MarkerSet("Geopolitica"));
            Shape shape = Shape.createRect(marker.minX(), marker.minZ(), marker.maxX(), marker.maxZ());
            ShapeMarker shapeMarker = new ShapeMarker(marker.label(), shape, plugin.getMapConfig().getShapeHeight());

            java.awt.Color color = marker.color();
            shapeMarker.setFillColor(new de.bluecolored.bluemap.api.math.Color(
                    color.getRed(), color.getGreen(), color.getBlue(), (float) plugin.getMapConfig().getFillOpacity()));
            shapeMarker.setLineColor(new de.bluecolored.bluemap.api.math.Color(
                    color.getRed(), color.getGreen(), color.getBlue(), (float) plugin.getMapConfig().getStrokeOpacity()));
            shapeMarker.setLineWidth(plugin.getMapConfig().getStrokeWeight());
            shapeMarker.setDetail(marker.popupHtml());

            set.getMarkers().put(marker.markerId(), shapeMarker);
        });
    }

    @Override
    public void removeClaim(String world, int chunkX, int chunkZ) {
        withMapsOf(world, map -> {
            MarkerSet set = map.getMarkerSets().get(SET_ID);
            if (set != null) {
                set.getMarkers().remove(ClaimMarker.markerId(world, chunkX, chunkZ));
            }
        });
    }

    private void withMapsOf(String worldName, Consumer<BlueMapMap> action) {
        BlueMapAPI current = this.api;
        if (current == null) {
            return;
        }
        World bukkitWorld = Bukkit.getWorld(worldName);
        if (bukkitWorld == null) {
            return;
        }
        Optional<BlueMapWorld> bmWorld = current.getWorld(bukkitWorld);
        bmWorld.ifPresent(w -> {
            for (BlueMapMap map : w.getMaps()) {
                action.accept(map);
            }
        });
    }
}
