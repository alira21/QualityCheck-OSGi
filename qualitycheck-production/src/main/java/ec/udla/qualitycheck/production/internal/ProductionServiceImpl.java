package ec.udla.qualitycheck.production.internal;

import ec.udla.qualitycheck.production.api.Piece;
import ec.udla.qualitycheck.production.api.ProductionService;

import java.time.Instant;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementacion privada: no se exporta, otros bundles no pueden verla.
 * Simula medidas alrededor de valores nominales con ruido gaussiano
 * y, ocasionalmente, un defecto de fabricacion.
 */
class ProductionServiceImpl implements ProductionService {

    static final double NOMINAL_LENGTH_MM = 100.0;
    static final double NOMINAL_DIAMETER_MM = 25.0;
    private static final double DEFECT_PROBABILITY = 0.15;

    private final String lineId;
    private final AtomicLong counter = new AtomicLong();
    private final Random random = new Random();

    ProductionServiceImpl(String lineId) {
        this.lineId = lineId;
    }

    @Override
    public synchronized Piece producePiece() {
        double length = NOMINAL_LENGTH_MM + random.nextGaussian() * 0.20;
        double diameter = NOMINAL_DIAMETER_MM + random.nextGaussian() * 0.05;

        if (random.nextDouble() < DEFECT_PROBABILITY) {
            // Defecto simulado: desviacion grande en una de las medidas
            if (random.nextBoolean()) {
                length += (random.nextBoolean() ? 1 : -1) * (0.6 + random.nextDouble());
            } else {
                diameter += (random.nextBoolean() ? 1 : -1) * (0.2 + random.nextDouble() * 0.3);
            }
        }

        long id = counter.incrementAndGet();
        return new Piece(id, length, diameter, Instant.now());
    }

    @Override
    public long getProducedCount() {
        return counter.get();
    }

    @Override
    public String getLineId() {
        return lineId;
    }
}
