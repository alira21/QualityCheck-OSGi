package ec.udla.qualitycheck.production.api;

import java.time.Instant;

/**
 * Pieza fabricada por la linea de produccion (objeto inmutable).
 * Forma parte del contrato publico que comparten ambos bundles.
 */
public final class Piece {

    private final long id;
    private final double lengthMm;
    private final double diameterMm;
    private final Instant producedAt;

    public Piece(long id, double lengthMm, double diameterMm, Instant producedAt) {
        this.id = id;
        this.lengthMm = lengthMm;
        this.diameterMm = diameterMm;
        this.producedAt = producedAt;
    }

    public long getId() { return id; }
    public double getLengthMm() { return lengthMm; }
    public double getDiameterMm() { return diameterMm; }
    public Instant getProducedAt() { return producedAt; }

    @Override
    public String toString() {
        return String.format("Pieza #%d [largo=%.3f mm, diametro=%.3f mm]", id, lengthMm, diameterMm);
    }
}
