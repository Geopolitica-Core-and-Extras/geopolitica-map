package com.geopolitica.map.provider;

import java.awt.Color;

/**
 * Everything a {@link MapProvider} needs to draw one claimed chunk, already
 * resolved from Geopolitica's live state (town/state/nation names, color,
 * description) so providers never have to touch the Geopolitica API directly.
 */
public record ClaimMarker(
        String world,
        int chunkX,
        int chunkZ,
        /** Short title shown on hover/on the marker itself. */
        String label,
        /** Full HTML popup content shown when the marker is clicked. */
        String popupHtml,
        Color color
) {

    /** Stable, per-chunk identifier used as the marker's key on every provider. */
    public String markerId() {
        return "geo_" + world + "_" + chunkX + "_" + chunkZ;
    }

    public static String markerId(String world, int chunkX, int chunkZ) {
        return "geo_" + world + "_" + chunkX + "_" + chunkZ;
    }

    public double minX() {
        return chunkX * 16.0;
    }

    public double minZ() {
        return chunkZ * 16.0;
    }

    public double maxX() {
        return minX() + 16.0;
    }

    public double maxZ() {
        return minZ() + 16.0;
    }
}
