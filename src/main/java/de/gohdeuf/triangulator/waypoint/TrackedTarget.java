package de.gohdeuf.triangulator.waypoint;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import de.gohdeuf.triangulator.triangulation.BearingSample;
import de.gohdeuf.triangulator.triangulation.LeastSquaresTriangulator;

/**
 * Beobachtungszustand fuer einen einzelnen Mitspieler.
 * Identifiziert wird ueber die Waypoint-ID (UUID des Spielers, sofern
 * serverseitig so gesetzt – sonst der String-Schluessel).
 */
public final class TrackedTarget {

    public final String id;               // kanonischer Schluessel
    public final UUID playerUuid;         // null, wenn der Server einen String nutzt
    private volatile String name;         // Anzeigename (falls bekannt)
    public volatile int color = 0xFFFFFF;
    public volatile long lastSeenMillis;

    private final Deque<BearingSample> samples = new ArrayDeque<>();
    private Optional<LeastSquaresTriangulator.Result> estimate = Optional.empty();
    private DirectPosition directPosition;

    public record DirectPosition(double x, double z, boolean approximate) {
    }

    public TrackedTarget(String id, UUID playerUuid, String name) {
        this.id = id;
        this.playerUuid = playerUuid;
        this.name = name == null ? id : name;
    }

    public String name() {
        return name;
    }

    public void updateName(String name) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
    }

    public synchronized void setDirectPosition(double x, double z, boolean approximate) {
        directPosition = new DirectPosition(x, z, approximate);
        lastSeenMillis = System.currentTimeMillis();
    }

    public synchronized void clearDirectPosition() {
        directPosition = null;
    }

    public synchronized Optional<DirectPosition> directPosition() {
        return Optional.ofNullable(directPosition);
    }

    public synchronized void addSample(BearingSample sample, int maxSamples) {
        samples.addLast(sample);
        while (samples.size() > maxSamples) samples.removeFirst();
        lastSeenMillis = sample.timestampMillis();
    }

    public synchronized void removeSamplesOlderThan(long cutoffMillis) {
        samples.removeIf(s -> s.timestampMillis() < cutoffMillis);
    }

    public synchronized int sampleCount() {
        return samples.size();
    }

    public synchronized List<BearingSample> snapshot() {
        return new ArrayList<>(samples);
    }

    public synchronized void setEstimate(Optional<LeastSquaresTriangulator.Result> estimate) {
        this.estimate = estimate;
    }

    public synchronized Optional<LeastSquaresTriangulator.Result> estimate() {
        return estimate;
    }
}
