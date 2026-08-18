package com.geopolitica.map.provider;

import com.geopolitica.map.GeopoliticaMapPlugin;

/**
 * A soft integration with one live web-map plugin. Implementations must be
 * self-contained: nothing outside this package may reference a specific map
 * plugin's classes, so that a server without e.g. BlueMap installed never
 * even attempts to load {@code BlueMapProvider}'s bytecode.
 */
public interface MapProvider {

    /** Display name used in logs and {@code /gmap status}. */
    String getName();

    /**
     * Checks whether the backing plugin is installed/enabled and hooks into
     * it if so. Must never throw - any failure (missing plugin, incompatible
     * version, API not ready yet) should simply return {@code false}.
     *
     * @return true if this provider is now active and ready to draw markers
     */
    boolean tryEnable(GeopoliticaMapPlugin plugin);

    /** Removes every marker this provider has drawn and releases its hook. Must never throw. */
    void disable();

    /** Draws or updates the marker for one claimed chunk. Must never throw. */
    void upsertClaim(ClaimMarker marker);

    /** Removes the marker for one claimed chunk, if present. Must never throw. */
    void removeClaim(String world, int chunkX, int chunkZ);
}
