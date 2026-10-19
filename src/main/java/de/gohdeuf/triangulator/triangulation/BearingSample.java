package de.gohdeuf.triangulator.triangulation;

/**
 * Eine einzelne Peilung: Standort des eigenen Spielers zum Messzeitpunkt
 * plus absoluter Peilwinkel (Azimuth) auf das Ziel, in Radiant.
 *
 * <p>Minecraft-Richtungskonvention (XZ-Ebene):
 * {@code richtung = (-sin(theta), cos(theta))} – theta=0 zeigt nach Sueden (+Z),
 * theta=pi/2 nach Westen (-X). Falls das Ziel in der Praxis gespiegelt wirkt,
 * nur die Richtungsdefinition in {@link LeastSquaresTriangulator} anpassen.
 */
public record BearingSample(double observerX, double observerZ,
                            double azimuthRad, long timestampMillis) {
}
