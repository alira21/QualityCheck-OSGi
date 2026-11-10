package ec.udla.qualitycheck.quality.internal;

/** Resultado de inspeccionar una pieza. */
final class InspectionResult {

    private final boolean approved;
    private final String reason;

    private InspectionResult(boolean approved, String reason) {
        this.approved = approved;
        this.reason = reason;
    }

    static InspectionResult approved() {
        return new InspectionResult(true, "dentro de tolerancia");
    }

    static InspectionResult rejected(String reason) {
        return new InspectionResult(false, reason);
    }

    boolean isApproved() { return approved; }
    String getReason() { return reason; }
}
