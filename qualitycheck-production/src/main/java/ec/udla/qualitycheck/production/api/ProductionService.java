package ec.udla.qualitycheck.production.api;

/**
 * Contrato del servicio de produccion. Es lo unico que conoce QualityControl:
 * no conoce la clase que lo implementa ni el bundle que lo publica.
 */
public interface ProductionService {

    /** Fabrica una nueva pieza con medidas simuladas. */
    Piece producePiece();

    /** Total de piezas fabricadas desde que el servicio se inicio. */
    long getProducedCount();

    /** Identificador de la linea de produccion. */
    String getLineId();
}
