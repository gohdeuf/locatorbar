package de.gohdeuf.triangulator.triangulation;

import java.util.List;
import java.util.Optional;

/**
 * Berechnet aus mehreren Peilstrahlen die wahrscheinlichste Zielposition.
 *
 * <p>Mathematischer Kern: Jede Beobachtung i definiert eine Gerade
 * {@code x = p_i + t * d_i}. Gesucht ist der Punkt x, der die Summe der
 * quadrierten Abstaende zu allen Geraden minimiert. Mit der Projektionsmatrix
 * {@code A_i = I - d_i d_i^T} (symmetrisch und idempotent) ist das Residuum
 * {@code r_i = A_i (x - p_i)} – linear in x. Die Normalengleichung
 * {@code (sum A_i) x = sum A_i p_i} ist also ein 2x2-LGS, das geschlossen
 * geloest werden kann. Kein Gauss-Newton, keine Iteration, kein Startwert.
 */
public final class LeastSquaresTriangulator {

    private LeastSquaresTriangulator() {
    }

    public record Result(double x, double z, double rmsResidual, int sampleCount,
                         boolean illConditioned) {
    }

    /**
     * @param samples mindestens 2 Peilungen; Gewichtung ueber {@code weight}
     * @return leer, wenn zu wenige (oder nahezu parallele) Strahlen vorliegen
     */
    public static Optional<Result> solve(List<WeightedSample> samples) {
        if (samples.size() < 2) return Optional.empty();

        double axx = 0, axz = 0, azz = 0, bx = 0, bz = 0;
        for (WeightedSample ws : samples) {
            BearingSample s = ws.sample();
            double theta = s.azimuthRad();
            double dx = -Math.sin(theta);
            double dz = Math.cos(theta);

            // A = I - d d^T
            double axxI = 1.0 - dx * dx;
            double axzI = -dx * dz;
            double azzI = 1.0 - dz * dz;

            axx += ws.weight() * axxI;
            axz += ws.weight() * axzI;
            azz += ws.weight() * azzI;
            bx += ws.weight() * (axxI * s.observerX() + axzI * s.observerZ());
            bz += ws.weight() * (axzI * s.observerX() + azzI * s.observerZ());
        }

        double det = axx * azz - axz * axz;
        double trace = axx + azz;
        // Konditionsgroesse fuer symmetrische 2x2: det / (trace/2)^2 in (0, 1].
        // Werte nahe 0 => Strahlen fast parallel => Seitlich bewegen!
        boolean ill = trace < 1e-9 || det < 1e-6 * (trace * trace * 0.25 + 1e-12) * samples.size();
        if (Math.abs(det) < 1e-12) return Optional.empty();

        double x = (bx * azz - axz * bz) / det;
        double z = (axx * bz - axz * bx) / det;

        // Restunsicherheit: gewichteter RMS-Abstand der Strahlen zum Schaetzwert
        double sumW = 0, sumRes = 0;
        for (WeightedSample ws : samples) {
            BearingSample s = ws.sample();
            double theta = s.azimuthRad();
            double dx = -Math.sin(theta);
            double dz = Math.cos(theta);
            double rx = x - s.observerX();
            double rz = z - s.observerZ();
            double t = rx * dx + rz * dz;
            double px = rx - t * dx;
            double pz = rz - t * dz;
            sumW += ws.weight();
            sumRes += ws.weight() * (px * px + pz * pz);
        }
        double rms = sumW > 0 ? Math.sqrt(sumRes / sumW) : Double.MAX_VALUE;

        return Optional.of(new Result(x, z, rms, samples.size(), ill));
    }

    /** Sample + Gewicht (z. B. Frische der Messung). */
    public record WeightedSample(BearingSample sample, double weight) {
    }
}
