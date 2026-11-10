package ec.udla.qualitycheck.quality.internal;

import ec.udla.qualitycheck.production.api.Piece;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Reglas de calidad. Las tolerancias pertenecen a QualityControl,
 * no a Production: cada bundle es duenio de su propia responsabilidad.
 */
final class QualityInspector {

    static final double NOMINAL_LENGTH_MM = 100.0;
    static final double LENGTH_TOLERANCE_MM = 0.50;
    static final double NOMINAL_DIAMETER_MM = 25.0;
    static final double DIAMETER_TOLERANCE_MM = 0.15;

    private final AtomicLong approved = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();

    InspectionResult inspect(Piece piece) {
        List<String> problems = new ArrayList<>();

        double lengthDev = piece.getLengthMm() - NOMINAL_LENGTH_MM;
        if (Math.abs(lengthDev) > LENGTH_TOLERANCE_MM) {
            problems.add(String.format("largo fuera de tolerancia (%+.3f mm)", lengthDev));
        }

        double diameterDev = piece.getDiameterMm() - NOMINAL_DIAMETER_MM;
        if (Math.abs(diameterDev) > DIAMETER_TOLERANCE_MM) {
            problems.add(String.format("diametro fuera de tolerancia (%+.3f mm)", diameterDev));
        }

        if (problems.isEmpty()) {
            approved.incrementAndGet();
            return InspectionResult.approved();
        }
        rejected.incrementAndGet();
        return InspectionResult.rejected(String.join("; ", problems));
    }

    long getApprovedCount() { return approved.get(); }
    long getRejectedCount() { return rejected.get(); }
}
