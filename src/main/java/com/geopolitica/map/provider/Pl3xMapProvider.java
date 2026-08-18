package com.geopolitica.map.provider;

import com.geopolitica.map.GeopoliticaMapPlugin;
import net.pl3x.map.core.Pl3xMap;
import net.pl3x.map.core.markers.layer.Layer;
import net.pl3x.map.core.markers.layer.SimpleLayer;
import net.pl3x.map.core.markers.marker.Marker;
import net.pl3x.map.core.markers.marker.Rectangle;
import net.pl3x.map.core.markers.option.Options;
import net.pl3x.map.core.registry.Registry;
import net.pl3x.map.core.world.World;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Draws one rectangle marker per claimed chunk on a shared "Geopolitica"
 * {@link SimpleLayer}, per world, registered with Pl3xMap's per-world layer
 * registry. Pl3xMap does not publish a separate lightweight API artifact, so
 * this compiles against its full plugin jar as a provided-scope dependency
 * (never bundled - see geopolitica-map/pom.xml).
 */
public class Pl3xMapProvider implements MapProvider {

    private static final String LAYER_KEY = "geopolitica";

    private GeopoliticaMapPlugin plugin;

    @Override
    public String getName() {
        return "Pl3xMap";
    }

    @Override
    public boolean tryEnable(GeopoliticaMapPlugin plugin) {
        Plugin pl3xmap = Bukkit.getPluginManager().getPlugin("Pl3xMap");
        if (pl3xmap == null || !pl3xmap.isEnabled()) {
            return false;
        }
        try {
            if (!Pl3xMap.api().isEnabled()) {
                return false;
            }
        } catch (Throwable t) {
            return false;
        }
        this.plugin = plugin;
        return true;
    }

    @Override
    public void ensureLayers(GeopoliticaMapPlugin plugin) {
        try {
            // Registers (or leaves alone, if already present) the "Geopolitica" layer on
            // every world Pl3xMap currently knows about - even ones with no claims yet -
            // so the layer toggle is visible in the UI regardless of how many markers
            // are actually drawn on it right now.
            for (World world : Pl3xMap.api().getWorldRegistry()) {
                ensureLayer(world);
            }
        } catch (Throwable ignored) {
            // Pl3xMap not fully ready yet - the next redundant call will pick this back up.
        }
    }

    @Override
    public void disable() {
        try {
            for (World world : Pl3xMap.api().getWorldRegistry()) {
                Registry<Layer> registry = world.getLayerRegistry();
                if (registry.has(LAYER_KEY)) {
                    registry.unregister(LAYER_KEY);
                }
            }
        } catch (Throwable ignored) {
            // Pl3xMap may already be disabling.
        }
    }

    @Override
    public void upsertClaim(ClaimMarker marker) {
        SimpleLayer layer = layerFor(marker.world());
        if (layer == null) {
            return;
        }
        Rectangle rect = Marker.rectangle(marker.markerId(), marker.minX(), marker.minZ(), marker.maxX(), marker.maxZ());
        int fillRgba = withAlpha(marker.color(), plugin.getMapConfig().getFillOpacity());
        int strokeRgba = withAlpha(marker.color(), plugin.getMapConfig().getStrokeOpacity());
        rect.setOptions(Options.builder()
                .stroke(true)
                .strokeWeight(plugin.getMapConfig().getStrokeWeight())
                .strokeColor(strokeRgba)
                .fill(true)
                .fillColor(fillRgba)
                .tooltipContent(marker.label())
                .popupContent(marker.popupHtml())
                .build());
        layer.addMarker(rect);
    }

    @Override
    public void removeClaim(String world, int chunkX, int chunkZ) {
        SimpleLayer layer = layerFor(world);
        if (layer != null) {
            layer.removeMarker(ClaimMarker.markerId(world, chunkX, chunkZ));
        }
    }

    private SimpleLayer layerFor(String worldName) {
        try {
            World world = Pl3xMap.api().getWorldRegistry().get(worldName);
            if (world == null) {
                return null;
            }
            return ensureLayer(world);
        } catch (Throwable t) {
            return null;
        }
    }

    private SimpleLayer ensureLayer(World world) {
        Registry<Layer> registry = world.getLayerRegistry();
        if (registry.has(LAYER_KEY)) {
            return (SimpleLayer) registry.get(LAYER_KEY);
        }
        SimpleLayer layer = new SimpleLayer(LAYER_KEY, () -> "Geopolitica");
        layer.setShowControls(true);
        layer.setDefaultHidden(false);
        layer.setPriority(2);
        registry.register(LAYER_KEY, layer);
        return layer;
    }

    private static int withAlpha(java.awt.Color color, double opacity) {
        int alpha = (int) Math.round(Math.max(0, Math.min(1, opacity)) * 255);
        return new java.awt.Color(color.getRed(), color.getGreen(), color.getBlue(), alpha).getRGB();
    }
}
