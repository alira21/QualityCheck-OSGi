package ec.udla.qualitycheck.quality.internal;

import ec.udla.qualitycheck.production.api.Piece;
import ec.udla.qualitycheck.production.api.ProductionService;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Ciclo de vida del bundle QualityControl.
 *
 * Bajo acoplamiento: solo depende de la interfaz ProductionService y la obtiene
 * dinamicamente del registro OSGi mediante un ServiceTracker. Si el servicio
 * desaparece (Production detenido), este bundle sigue ACTIVE y queda en espera;
 * cuando el servicio vuelve, retoma la inspeccion automaticamente.
 */
public class QualityControlActivator implements BundleActivator {

    private static final String TAG = "[QUALITY]    ";
    private static final long INSPECTION_PERIOD_SECONDS = 4;
    private static final int WAIT_LOG_EVERY_N_CYCLES = 3;

    private final QualityInspector inspector = new QualityInspector();

    private ServiceTracker<ProductionService, ProductionService> tracker;
    private ScheduledExecutorService scheduler;
    private int waitingCycles;
    private volatile boolean stopping;

    @Override
    public void start(BundleContext context) {
        stopping = false;
        tracker = new ServiceTracker<>(context, ProductionService.class, new ProductionTracker(context));
        tracker.open();

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "qualitycheck-inspector");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::inspectionCycle, 1, INSPECTION_PERIOD_SECONDS, TimeUnit.SECONDS);

        System.out.println(TAG + "Bundle iniciado. Inspeccion cada " + INSPECTION_PERIOD_SECONDS + " s.");
        if (tracker.getService() == null) {
            System.out.println(TAG + "Aun no hay servicio de produccion. Esperando...");
        }
    }

    @Override
    public void stop(BundleContext context) throws InterruptedException {
        stopping = true;
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler.awaitTermination(2, TimeUnit.SECONDS);
            scheduler = null;
        }
        if (tracker != null) {
            tracker.close();
            tracker = null;
        }
        System.out.printf(TAG + "Bundle detenido. Resumen: aprobadas=%d, rechazadas=%d%n",
                inspector.getApprovedCount(), inspector.getRejectedCount());
    }

    /** Un ciclo de inspeccion. Nunca deja escapar excepciones (eso cancelaria la tarea). */
    private void inspectionCycle() {
        try {
            ServiceTracker<ProductionService, ProductionService> currentTracker = tracker;
            ProductionService production = currentTracker != null ? currentTracker.getService() : null;

            if (production == null) {
                if (waitingCycles++ % WAIT_LOG_EVERY_N_CYCLES == 0) {
                    System.out.printf(TAG + "Sin servicio de produccion. QC sigue activo en espera "
                            + "(aprobadas=%d, rechazadas=%d)%n",
                            inspector.getApprovedCount(), inspector.getRejectedCount());
                }
                return;
            }
            waitingCycles = 0;

            Piece piece = production.producePiece();
            InspectionResult result = inspector.inspect(piece);

            if (result.isApproved()) {
                System.out.println(TAG + "APROBADA  " + piece);
            } else {
                System.out.println(TAG + "RECHAZADA " + piece + " -> " + result.getReason());
            }
        } catch (Exception e) {
            // Por ejemplo, si Production se detiene justo durante la llamada
            System.out.println(TAG + "Ciclo omitido: " + e.getClass().getSimpleName()
                    + (e.getMessage() != null ? " - " + e.getMessage() : ""));
        }
    }

    /** Recibe las notificaciones del framework cuando el servicio aparece o desaparece. */
    private final class ProductionTracker
            implements ServiceTrackerCustomizer<ProductionService, ProductionService> {

        private final BundleContext context;

        ProductionTracker(BundleContext context) {
            this.context = context;
        }

        @Override
        public ProductionService addingService(ServiceReference<ProductionService> reference) {
            ProductionService service = context.getService(reference);
            System.out.println(TAG + "Servicio de produccion DETECTADO (linea "
                    + reference.getProperty("qualitycheck.line") + "). Inspeccion en curso.");
            return service;
        }

        @Override
        public void modifiedService(ServiceReference<ProductionService> reference, ProductionService service) {
            // Sin cambios de propiedades relevantes
        }

        @Override
        public void removedService(ServiceReference<ProductionService> reference, ProductionService service) {
            if (!stopping) System.out.println(TAG + "Servicio de produccion RETIRADO. QualityControl continua activo.");
            context.ungetService(reference);
        }
    }
}
