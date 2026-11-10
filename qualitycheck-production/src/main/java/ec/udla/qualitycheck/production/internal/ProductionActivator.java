package ec.udla.qualitycheck.production.internal;

import ec.udla.qualitycheck.production.api.ProductionService;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import java.util.Dictionary;
import java.util.Hashtable;

/**
 * Ciclo de vida del bundle Production.
 * start(): publica ProductionService en el registro de servicios OSGi.
 * stop():  retira el servicio; los consumidores son notificados por el framework.
 */
public class ProductionActivator implements BundleActivator {

    private static final String TAG = "[PRODUCTION] ";

    private ServiceRegistration<ProductionService> registration;
    private ProductionServiceImpl service;

    @Override
    public void start(BundleContext context) {
        service = new ProductionServiceImpl("LINEA-A");

        Dictionary<String, Object> props = new Hashtable<>();
        props.put("qualitycheck.line", service.getLineId());
        props.put("service.description", "Servicio de produccion de piezas simuladas");

        registration = context.registerService(ProductionService.class, service, props);
        System.out.println(TAG + "Bundle iniciado. Servicio publicado para " + service.getLineId());
    }

    @Override
    public void stop(BundleContext context) {
        if (registration != null) {
            try {
                registration.unregister();
            } catch (IllegalStateException alreadyUnregistered) {
                // El framework ya lo retiro; no hay nada mas que hacer
            }
            registration = null;
        }
        long total = service != null ? service.getProducedCount() : 0;
        service = null;
        System.out.println(TAG + "Bundle detenido. Servicio retirado. Piezas fabricadas: " + total);
    }
}
