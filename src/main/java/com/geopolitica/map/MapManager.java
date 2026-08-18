package com.geopolitica.map;

import com.geopolitica.api.claim.Claim;
import com.geopolitica.api.events.ClaimCreateEvent;
import com.geopolitica.api.events.ClaimRemoveEvent;
import com.geopolitica.api.events.TownDisbandEvent;
import com.geopolitica.api.nation.Nation;
import com.geopolitica.api.service.ClaimService;
import com.geopolitica.api.service.TownService;
import com.geopolitica.api.state.State;
import com.geopolitica.api.town.Town;
import com.geopolitica.map.config.MapConfig;
import com.geopolitica.map.provider.ClaimMarker;
import com.geopolitica.map.provider.MapProvider;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Owns the set of active {@link MapProvider}s and keeps every one of them in
 * sync with Geopolitica's towns. Claiming, unclaiming and town disbandment
 * are reflected immediately via Geopolitica's events; everything else
 * (renames, color/description edits, state/nation reassignment) is picked up
 * by the periodic full {@link #resyncAll()}.
 */
public class MapManager implements Listener {

    private final GeopoliticaMapPlugin plugin;
    private final MapConfig config;
    private final TownService townService;
    private final ClaimService claimService;
    private final List<MapProvider> providers = new ArrayList<>();

    public MapManager(GeopoliticaMapPlugin plugin, MapConfig config, TownService townService, ClaimService claimService) {
        this.plugin = plugin;
        this.config = config;
        this.townService = townService;
        this.claimService = claimService;
    }

    /** (name, config key, factory) for every known integration, in a fixed, predictable order. */
    @SuppressWarnings("unchecked")
    private List<Supplier<MapProvider>> candidateFactories() {
        return List.of(
                supplierFor("dynmap", "com.geopolitica.map.provider.DynmapProvider"),
                supplierFor("bluemap", "com.geopolitica.map.provider.BlueMapProvider"),
                supplierFor("squaremap", "com.geopolitica.map.provider.SquaremapProvider"),
                supplierFor("pl3xmap", "com.geopolitica.map.provider.Pl3xMapProvider")
        );
    }

    private Supplier<MapProvider> supplierFor(String configKey, String className) {
        return () -> {
            if (!config.isProviderEnabled(configKey)) {
                return null;
            }
            try {
                Class<?> clazz = Class.forName(className);
                return (MapProvider) clazz.getDeclaredConstructor().newInstance();
            } catch (Throwable t) {
                // The backing map plugin (or a class it needs) simply isn't on the classpath - expected and fine.
                return null;
            }
        };
    }

    /** Detects and hooks every installed/enabled map plugin, then draws every current claim. */
    public void enable() {
        providers.clear();
        for (Supplier<MapProvider> factory : candidateFactories()) {
            MapProvider provider = safeGet(factory);
            if (provider == null) {
                continue;
            }
            boolean hooked = safeTryEnable(provider);
            if (hooked) {
                providers.add(provider);
                plugin.getLogger().info("Hooked into " + provider.getName() + ".");
            }
        }
        if (providers.isEmpty()) {
            plugin.getLogger().info("No supported map plugin (Dynmap, BlueMap, squaremap, Pl3xMap) found; "
                    + "markers will be drawn automatically once one is installed and the plugin is reloaded.");
            return;
        }

        // The layer/marker-set toggle must appear even with zero claims, so it is
        // created proactively here rather than only as a side effect of drawing a
        // marker. Some map plugins (squaremap, Pl3xMap, BlueMap) finish setting up
        // their own per-world state asynchronously/slightly after their onEnable()
        // returns, so a single attempt right now can race and silently do nothing -
        // hence the extra delayed retry below, on top of the one that already
        // happens at the start of every periodic resync.
        ensureLayers();
        Bukkit.getScheduler().runTaskLater(plugin, this::ensureLayers, 40L);
        Bukkit.getScheduler().runTaskLater(plugin, this::ensureLayers, 200L);

        resyncAll();
    }

    public void disable() {
        for (MapProvider provider : providers) {
            try {
                provider.disable();
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Error disabling " + provider.getName() + " integration", t);
            }
        }
        providers.clear();
    }

    public List<String> activeProviderNames() {
        return providers.stream().map(MapProvider::getName).toList();
    }

    /**
     * Re-runs every provider's proactive layer/marker-set registration. Cheap
     * and idempotent, so it is called from several places (see {@link #enable()})
     * purely for redundancy against the backing map plugins' own startup timing.
     */
    public void ensureLayers() {
        for (MapProvider provider : providers) {
            try {
                provider.ensureLayers(plugin);
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Error ensuring layers on " + provider.getName(), t);
            }
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        // A world that loads after startup (e.g. created by another plugin) has no
        // layer registered on any provider yet - catch up immediately rather than
        // waiting for the next periodic resync.
        ensureLayers();
    }

    /** Rebuilds every marker from Geopolitica's current state. Safe to call repeatedly. */
    public void resyncAll() {
        if (providers.isEmpty()) {
            return;
        }
        ensureLayers();
        int count = 0;
        for (Town town : townService.getTowns()) {
            for (Claim claim : claimService.getClaims(town)) {
                upsert(claim);
                count++;
            }
        }
        int resyncedCount = count;
        plugin.getLogger().fine(() -> "Resynced " + resyncedCount + " claim markers across " + providers.size() + " map provider(s).");
    }

    @EventHandler
    public void onClaimCreate(ClaimCreateEvent event) {
        upsert(event.getClaim());
    }

    @EventHandler
    public void onClaimRemove(ClaimRemoveEvent event) {
        removeClaim(event.getClaim());
    }

    @EventHandler
    public void onTownDisband(TownDisbandEvent event) {
        // purgeClaims() (which runs right after this event) doesn't fire ClaimRemoveEvent per
        // claim, so this is the only chance to clean up markers for a disbanding town's territory.
        for (Claim claim : event.getTown().getClaims()) {
            removeClaim(claim);
        }
    }

    private void upsert(Claim claim) {
        if (providers.isEmpty()) {
            return;
        }
        ClaimMarker marker = buildMarker(claim);
        for (MapProvider provider : providers) {
            try {
                provider.upsertClaim(marker);
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Error drawing a claim marker on " + provider.getName(), t);
            }
        }
    }

    private void removeClaim(Claim claim) {
        if (providers.isEmpty()) {
            return;
        }
        for (MapProvider provider : providers) {
            try {
                provider.removeClaim(claim.getWorldName(), claim.getChunkX(), claim.getChunkZ());
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Error removing a claim marker on " + provider.getName(), t);
            }
        }
    }

    private ClaimMarker buildMarker(Claim claim) {
        Town town = claim.getTown();
        Color color = town.getMapColor();
        String label = escapeHtml(town.getName());
        String popup = buildPopup(town);
        return new ClaimMarker(claim.getWorldName(), claim.getChunkX(), claim.getChunkZ(), label, popup, color);
    }

    private String buildPopup(Town town) {
        StringBuilder html = new StringBuilder();
        html.append("<div class=\"geopolitica-popup\"><b>").append(escapeHtml(town.getName())).append("</b><br/>");
        html.append("Mayor: ").append(escapeHtml(town.getOwner().getName())).append("<br/>");
        html.append("Residents: ").append(town.getResidents().size()).append("<br/>");

        java.util.Optional<State> state = town.getState();
        java.util.Optional<Nation> nation = town.getNation();
        if (state.isPresent()) {
            html.append("State: ").append(escapeHtml(state.get().getName())).append("<br/>");
        }
        if (nation.isPresent()) {
            html.append("Nation: ").append(escapeHtml(nation.get().getName())).append("<br/>");
        }
        if (!town.getDescription().isEmpty()) {
            html.append("<i>").append(escapeHtml(town.getDescription())).append("</i>");
        }
        html.append("</div>");
        return html.toString();
    }

    private static String escapeHtml(String text) {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private MapProvider safeGet(Supplier<MapProvider> factory) {
        try {
            return factory.get();
        } catch (Throwable t) {
            return null;
        }
    }

    private boolean safeTryEnable(MapProvider provider) {
        try {
            return provider.tryEnable(plugin);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Error hooking into " + provider.getName(), t);
            return false;
        }
    }
}
