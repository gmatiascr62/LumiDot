# LumiDot

LED de notificaciones virtual para teléfonos Android sin LED físico (estilo NotifyBuddy / aodNotify).
Sin publicidad, sin analíticas, **sin permiso de Internet**.

## Qué hace

- Detecta notificaciones con `NotificationListenerService` (API oficial).
- Cuando llega una notificación con la pantalla apagada, muestra una pantalla **negra absoluta**
  sobre la pantalla de bloqueo, con brillo mínimo y un punto que parpadea. En AMOLED los píxeles
  negros están apagados: sólo se ilumina el punto.
- El LED se apaga solo cuando se leen o descartan las notificaciones, al tocar la pantalla,
  al presionar el botón de encendido o al cumplirse el tiempo máximo configurado.
- Personalización: color, tamaño, halo, posición (arrastrando en la vista previa o con presets),
  patrón (pulso, respiración, parpadeo, doble destello, latido, fijo) y velocidad.
- Por aplicación: elegir qué apps encienden el LED (todas excepto excluidas / sólo elegidas)
  y asignar un color propio a cada una; con varias apps pendientes el LED alterna colores.
- Comportamiento: respetar No molestar, ignorar notificaciones silenciosas, no mostrar durante
  llamadas, no mostrar con batería baja, horario silencioso, tiempo máximo, brillo de pantalla,
  desplazamiento anti-quemado.
- Vista previa interactiva y prueba a pantalla completa **sin conceder ningún permiso**.

## Accesos que usa (todos opcionales para abrir la app)

| Acceso | Para qué | ¿Obligatorio? |
|---|---|---|
| Acceso a notificaciones | Saber cuándo llega/se elimina una notificación. No se lee el contenido. | Para la detección |
| Mostrar sobre otras apps (`SYSTEM_ALERT_WINDOW`) | Es la excepción oficial de Android para abrir una pantalla desde segundo plano. Sin esto el LED no puede aparecer solo. | Para el LED automático |
| `TURN_SCREEN_ON` | Encender la pantalla al mostrar el LED (permiso normal). | Automático |
| Sin restricción de batería | Evitar que algunos fabricantes cierren la app. Se abre la lista del sistema, sin pedir `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. | Opcional |

No se usa `QUERY_ALL_PACKAGES` (la lista de apps se limita a las que tienen ícono en el launcher),
ni servicio de accesibilidad, ni `USE_FULL_SCREEN_INTENT`, ni servicios en primer plano.

## Limitaciones honestas de Android

- No existe API para encender un LED sin usar la pantalla: LumiDot la enciende en negro al mínimo.
- Android 10+ bloquea abrir actividades desde segundo plano; la única vía oficial aplicable a esta app
  es el acceso "Mostrar sobre otras apps".
- Con la pantalla encendida y en uso no se muestra el LED (ya ves la notificación).
- Algunas capas de fabricante (MIUI/HyperOS, One UI, EMUI) pueden cerrar el servicio; conviene quitar
  la restricción de batería.

## Play Protect: "Aplicación bloqueada para proteger tu dispositivo"

**Causa:** es la *protección mejorada contra fraude* de Google Play Protect (activa en cada vez más
países). Bloquea la instalación de APKs descargados desde **navegadores, apps de mensajería o gestores
de archivos** cuando declaran alguno de estos permisos: `RECEIVE_SMS`, `READ_SMS`,
`BIND_NOTIFICATION_LISTENER_SERVICE` o `BIND_ACCESSIBILITY_SERVICE`. LumiDot necesita
`NotificationListenerService` para funcionar, así que **cualquier** APK de LumiDot que se instale
de esa forma puede ser bloqueado, sin importar la firma o el resto del código. No es un indicio de
malware y no debe "evadirse".

**Lo que se corrigió en el proyecto para no sumar señales de riesgo:**

- Sin permiso de Internet, sin `QUERY_ALL_PACKAGES`, sin accesibilidad, sin SMS.
- Servicio de notificaciones no exportado y protegido por `BIND_NOTIFICATION_LISTENER_SERVICE`,
  con filtro `conversations|alerting` (no recibe notificaciones silenciosas/persistentes).
- `targetSdk 36`, `allowBackup=false`, reglas de extracción de datos que excluyen todo.
- Divulgación clara antes de pedir el acceso a notificaciones; la app funciona en modo prueba sin él.
- Firma estable: keystore de depuración fijo para builds de prueba y firma de release por secrets.
- Release con R8 (minificado) y sin bloque de dependencias incrustado.

**Formas legítimas de instalarlo:**

1. **Google Play (recomendado):** publicar en *Prueba interna* de Play Console. Las apps instaladas
   desde Play no están sujetas a este bloqueo. Play exige justificar el uso del acceso a
   notificaciones (función principal: LED de notificaciones) y una política de privacidad.
2. **ADB** desde una computadora: `adb install LumiDot-debug.apk` (no es una fuente de
   "sideloading desde Internet").
3. En Android 13+, tras instalar fuera de Play, el acceso a notificaciones aparece como
   **"Ajuste restringido"**: Información de la app → menú ⋮ → *Permitir ajustes restringidos*.

## Compilar

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/
./gradlew assembleRelease      # firmado si hay keystore configurado
./gradlew testDebugUnitTest
```

Requisitos: JDK 17+, Android SDK con `platforms;android-36`.

### Firma de release

Localmente, creá `keystore.properties` en la raíz (está en `.gitignore`):

```properties
storeFile=/ruta/a/lumidot-release.jks
storePassword=...
keyAlias=lumidot
keyPassword=...
```

En GitHub Actions, configurá los secrets `LUMIDOT_KEYSTORE_BASE64` (`base64 -w0 lumidot-release.jks`),
`LUMIDOT_KEYSTORE_PASSWORD`, `LUMIDOT_KEY_ALIAS` y `LUMIDOT_KEY_PASSWORD`.
Crear el keystore: `keytool -genkeypair -v -keystore lumidot-release.jks -alias lumidot -keyalg RSA -keysize 4096 -validity 10000`.
**Guardalo con copia de seguridad: sin él no se pueden publicar actualizaciones.**

## Descargar el APK

- Cada push compila en **Actions → Build LumiDot APK → artefacto `LumiDot-apk`**.
- Cada push a `main` publica/actualiza la pre-release **`latest-build`** en la sección *Releases*
  (descargable desde el teléfono sin iniciar sesión). Los tags `v*` crean una release versionada.

## Arquitectura

```
app/src/main/java/com/lumidot/app/
├── LumiDotApp.kt                 Application (repositorio de ajustes)
├── data/                         LedSettings, BlinkPattern, SettingsRepository (DataStore)
├── service/                      LumiNotificationListener, AlertTracker (estado en memoria)
├── led/                          LedController (reglas para mostrar), LedActivity, LedDotView
├── ui/                           MainActivity + pantallas Compose (Inicio, LED, Apps, Ajustes)
└── util/Permissions.kt           Estado y atajos a los ajustes del sistema
```

## Privacidad

LumiDot no tiene permiso de Internet, no lee el texto de las notificaciones y sólo guarda localmente
la configuración y los nombres de paquete de las apps que enviaron notificaciones (para listarlas).
