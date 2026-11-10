# QualityCheck — Proyecto OSGi con Apache Karaf

Sistema de control de calidad industrial formado por dos bundles OSGi con bajo acoplamiento:

| Bundle | Responsabilidad | Exporta | Publica / consume |
|---|---|---|---|
| `qualitycheck-production` | Fabrica piezas con medidas simuladas (largo y diametro) | Paquete `...production.api` (interfaz + modelo) | **Publica** `ProductionService` |
| `qualitycheck-quality-control` | Inspecciona cada pieza y la aprueba o rechaza segun tolerancias | Nada | **Consume** `ProductionService` via `ServiceTracker` |

## 1. Fundamento teorico

**OSGi** es una especificacion para construir aplicaciones Java modulares y dinamicas. La unidad de despliegue
es el *bundle* (un JAR con metadatos en `MANIFEST.MF`). El framework se organiza en capas:

- **Modulo**: cada bundle tiene su propio classloader y solo ve los paquetes que importa (`Import-Package`).
- **Ciclo de vida**: `INSTALLED -> RESOLVED -> STARTING -> ACTIVE -> STOPPING -> UNINSTALLED`, gestionable en caliente.
- **Servicios**: registro central donde los bundles publican, buscan y usan objetos por su interfaz.

**Patrones de arquitectura**:

- **Microkernel / Plug-in** (principal): el framework (Felix dentro de Karaf) es el nucleo; los bundles son plug-ins.
- **Orientado a servicios (SOA en la JVM)**: modelo *publish - find - bind* sobre el service registry.
- **Capas**: la especificacion separa modulo, ciclo de vida, servicio y seguridad.
- Patrones de diseno asociados: Service Registry/Locator, inversion de dependencias y Whiteboard.

**Que podemos hacer**: desplegar, actualizar y detener modulos sin reiniciar el sistema, convivir varias versiones de una
libreria y construir plataformas extensibles (Eclipse IDE, openHAB, Liferay, gateways IoT).

## 2. Diseno del bajo acoplamiento

```
 qualitycheck-production                      qualitycheck-quality-control
 +------------------------------+             +--------------------------------+
 | api (EXPORTADO)              |  Import-    | internal (privado)             |
 |   ProductionService  <-------|--Package----|   QualityControlActivator      |
 |   Piece                      |             |   ServiceTracker  -----+       |
 | internal (PRIVADO)           |             |   QualityInspector     |       |
 |   ProductionServiceImpl      |             +------------------------|-------+
 |   ProductionActivator --+    |                                      |
 +-------------------------|----+                                      |
                           | registerService()            getService() |
                           v                                           |
                 +----------------------------------------------+      |
                 |        OSGi Service Registry (Karaf)         |<-----+
                 +----------------------------------------------+
```

- QualityControl solo conoce la **interfaz**, nunca la clase `ProductionServiceImpl` (que no se exporta).
- La conexion se hace en tiempo de ejecucion por el registro de servicios, no con `new`.
- Al detener Production, su servicio se retira del registro; el `ServiceTracker` de QC lo detecta y QC sigue `Active`
  en espera. Al reiniciar Production, QC lo detecta otra vez y retoma la inspeccion sin intervencion.
- El paquete `api` sigue disponible aunque Production este detenido (detener no es desinstalar), por eso QC no falla.
- Se usa `BundleActivator` directamente, sin Declarative Services, para no requerir features adicionales.

## 3. Requisitos

- JDK 17 (`java -version` y `mvn -version` deben mostrar Java 17 o superior)
- Maven 3.8+
- Apache Karaf 4.4.x (https://karaf.apache.org/download.html)

## 4. Compilacion

```bash
cd QualityCheck-OSGi
mvn clean install
```

Genera e instala en `~/.m2/repository` los JAR:
- `qualitycheck-production/target/qualitycheck-production-1.0.0.jar`
- `qualitycheck-quality-control/target/qualitycheck-quality-control-1.0.0.jar`

## 5. Ejecucion en Karaf

```bash
cd apache-karaf-4.4.x
bin/karaf          # Windows: bin\karaf.bat
```

En la consola `karaf@root()>` instalar **primero Production** (QC importa su paquete `api`):

```
bundle:install -s mvn:ec.udla/qualitycheck-production/1.0.0
bundle:install -s mvn:ec.udla/qualitycheck-quality-control/1.0.0
bundle:list
```

Si `mvn:` no encuentra los artefactos, instalar por ruta de archivo:

```
bundle:install -s file:/ruta/QualityCheck-OSGi/qualitycheck-production/target/qualitycheck-production-1.0.0.jar
bundle:install -s file:/ruta/QualityCheck-OSGi/qualitycheck-quality-control/target/qualitycheck-quality-control-1.0.0.jar
```

## 6. Pruebas de inicio y parada independiente

Anotar los IDs que muestra `bundle:list` (en los ejemplos: Production = 53, QualityControl = 54).

### Prueba 1 — Ambos activos
```
bundle:list
service:list ec.udla.qualitycheck.production.api.ProductionService
```
Esperado: ambos `Active`; QC imprime `APROBADA` / `RECHAZADA` cada 4 s.

### Prueba 2 — Detener Production (QC debe seguir vivo)
```
bundle:stop 53
bundle:list
```
Esperado:
- `[PRODUCTION] Bundle detenido. Servicio retirado...`
- `[QUALITY] Servicio de produccion RETIRADO. QualityControl continua activo.`
- Production en `Resolved`, QC sigue `Active` e imprime `Sin servicio de produccion. QC sigue activo en espera`.

### Prueba 3 — Reiniciar Production (reconexion automatica)
```
bundle:start 53
```
Esperado: `[QUALITY] Servicio de produccion DETECTADO...` y la inspeccion se reanuda sola. Los contadores de QC
se conservan; el contador de piezas de Production empieza de nuevo (nueva instancia del servicio).

### Prueba 4 — Detener QualityControl (Production no se ve afectado)
```
bundle:stop 54
bundle:list
service:list ec.udla.qualitycheck.production.api.ProductionService
```
Esperado: QC muestra su resumen y queda `Resolved`; Production sigue `Active` y su servicio sigue registrado.

### Prueba 5 — Reiniciar QualityControl
```
bundle:start 54
```

### Prueba 6 — Arrancar QC sin Production activo
```
bundle:stop 53
bundle:restart 54
bundle:start 53
```
Esperado: QC arranca en espera y comienza a inspeccionar en cuanto aparece el servicio.

### Comandos utiles de diagnostico
```
bundle:headers 53      # ver Export-Package / Import-Package generados
bundle:diag 54         # explica por que un bundle no resuelve
log:tail               # ver el log de Karaf (Ctrl+C para salir)
bundle:uninstall 54 53 # limpiar
```

> Nota: si se **desinstala** Production (no solo detener) y se ejecuta `bundle:refresh`, QC dejara de resolver porque
> pierde el paquete `api`. Eso es esperado: la consigna pide arrancar/parar, que es lo que demuestran las pruebas.

## 7. Solucion de problemas

| Sintoma | Causa probable | Solucion |
|---|---|---|
| `Unable to resolve ... osgi.ee=JavaSE 17` | Karaf corre con Java 11 | Ejecutar Karaf con JDK 17 (`JAVA_HOME`) |
| `missing requirement ... production.api` | Se instalo QC antes que Production | Instalar Production y luego `bundle:refresh` |
| `Error resolving artifact mvn:...` | No se ejecuto `mvn install` o otro repo local | Usar la instalacion por `file:` |
| No se ve la salida en consola | Karaf iniciado en segundo plano (`bin/start`) | Usar `bin/karaf` o `log:tail` |
