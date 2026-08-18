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

    /**
     * Proactively creates/registers this provider's layer (marker set) for
     * every world the backing map plugin currently knows about, independent
     * of whether any claim marker has been drawn yet - a layer with zero
     * markers must still show up as a toggle in the map's UI.
     *
     * <p>Called redundantly at several points (right after {@link #tryEnable},
     * on a short delayed retry, before every periodic resync, and whenever a
     * world loads) specifically to survive the backing map plugin not having
     * finished initializing its own per-world state yet when we first hook
     * in. Idempotent and cheap to call repeatedly. Must never throw.</p>
     */
    void ensureLayers(GeopoliticaMapPlugin plugin);

    /** Removes every marker this provider has drawn and releases its hook. Must never throw. */
    void disable();

    /** Draws or updates the marker for one claimed chunk. Must never throw. */
    void upsertClaim(ClaimMarker marker);

    /** Removes the marker for one claimed chunk, if present. Must never throw. */
    void removeClaim(String world, int chunkX, int chunkZ);
}
