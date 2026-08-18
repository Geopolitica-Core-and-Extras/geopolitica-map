package com.geopolitica.map.provider;

import com.geopolitica.map.GeopoliticaMapPlugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import xyz.jpenilla.squaremap.api.BukkitAdapter;
import xyz.jpenilla.squaremap.api.Key;
import xyz.jpenilla.squaremap.api.LayerProvider;
import xyz.jpenilla.squaremap.api.MapWorld;
import xyz.jpenilla.squaremap.api.Point;
import xyz.jpenilla.squaremap.api.Registry;
import xyz.jpenilla.squaremap.api.SimpleLayerProvider;
import xyz.jpenilla.squaremap.api.Squaremap;
import xyz.jpenilla.squaremap.api.marker.Marker;
import xyz.jpenilla.squaremap.api.marker.MarkerOptions;

import java.util.Optional;

/**
 * Draws one rectangle marker per claimed chunk on a shared "Geopolitica"
 * layer, per world, registered with squaremap's per-world layer registry.
 */
public class SquaremapProvider implements MapProvider {

    private static final Key LAYER_KEY = Key.of("geopolitica");

    private GeopoliticaMapPlugin plugin;

    @Override
    public String getName() {
        return "squaremap";
    }

    @Override
    public boolean tryEnable(GeopoliticaMapPlugin plugin) {
        Plugin squaremap = Bukkit.getPluginManager().getPlugin("squaremap");
        if (squaremap == null || !squaremap.isEnabled()) {
            return false;
        }
        if (Bukkit.getServicesManager().load(Squaremap.class) == null) {
            return false;
        }
        this.plugin = plugin;
        return true;
    }

    @Override
    public void disable() {
        Squaremap api = Bukkit.getServicesManager().load(Squaremap.class);
        if (api == null) {
            return;
        }
        for (MapWorld world : api.mapWorlds()) {
            Registry<LayerProvider> registry = world.layerRegistry();
            if (registry.hasEntry(LAYER_KEY)) {
                registry.unregister(LAYER_KEY);
            }
        }
    }

    @Override
    public void upsertClaim(ClaimMarker marker) {
        SimpleLayerProvider layer = layerFor(marker.world());
        if (layer == null) {
            return;
        }
        Marker rect = Marker.rectangle(
                Point.of(marker.minX(), marker.minZ()),
                Point.of(marker.maxX(), marker.maxZ())
        ).markerOptions(MarkerOptions.builder()
                .stroke(true)
                .strokeColor(marker.color())
                .strokeWeight(plugin.getMapConfig().getStrokeWeight())
                .strokeOpacity(plugin.getMapConfig().getStrokeOpacity())
                .fill(true)
                .fillColor(marker.color())
                .fillOpacity(plugin.getMapConfig().getFillOpacity())
                .hoverTooltip(marker.label())
                .clickTooltip(marker.popupHtml())
                .build());
        layer.addMarker(Key.of(marker.markerId()), rect);
    }

    @Override
    public void removeClaim(String world, int chunkX, int chunkZ) {
        SimpleLayerProvider layer = layerFor(world);
        if (layer != null) {
            layer.removeMarker(Key.of(ClaimMarker.markerId(world, chunkX, chunkZ)));
        }
    }

    @SuppressWarnings("unchecked")
    private SimpleLayerProvider layerFor(String worldName) {
        org.bukkit.World bukkitWorld = Bukkit.getWorld(worldName);
        if (bukkitWorld == null) {
            return null;
        }
        Squaremap api = Bukkit.getServicesManager().load(Squaremap.class);
        if (api == null) {
            return null;
        }
        Optional<MapWorld> mapWorld = api.getWorldIfEnabled(BukkitAdapter.worldIdentifier(bukkitWorld));
        if (mapWorld.isEmpty()) {
            return null;
        }
        Registry<LayerProvider> registry = mapWorld.get().layerRegistry();
        if (registry.hasEntry(LAYER_KEY)) {
            return (SimpleLayerProvider) registry.get(LAYER_KEY);
        }
        SimpleLayerProvider layer = SimpleLayerProvider.builder("Geopolitica")
                .showControls(true)
                .defaultHidden(false)
                .layerPriority(2)
                .zIndex(200)
                .build();
        registry.register(LAYER_KEY, layer);
        return layer;
    }
}
