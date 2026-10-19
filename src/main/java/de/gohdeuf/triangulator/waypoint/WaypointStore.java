package de.gohdeuf.triangulator.waypoint;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-sichere Zentrale aller aktuell beobachteten Ziele.
 * Schreibzugriff erfolgt vom Netzwerk-Thread (Mixin), Lesezugriff vom Render-Thread (HUD).
 */
public final class WaypointStore {

    private static final WaypointStore INSTANCE = new WaypointStore();

    public static WaypointStore getInstance() {
        return INSTANCE;
    }

    private final Map<String, TrackedTarget> targets = new ConcurrentHashMap<>();

    private WaypointStore() {
    }

    public TrackedTarget getOrCreate(String id, java.util.UUID uuid, String name) {
        return targets.computeIfAbsent(id, k -> new TrackedTarget(k, uuid, name));
    }

    public Optional<TrackedTarget> get(String id) {
        return Optional.ofNullable(targets.get(id));
    }

    public void remove(String id) {
        targets.remove(id);
    }

    public void clear() {
        targets.clear();
    }

    public Collection<TrackedTarget> all() {
        return targets.values();
    }
}
