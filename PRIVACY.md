# Política de privacidad de LumiDot

**Última actualización:** 9 de octubre de 2026

LumiDot es una aplicación para Android que simula un LED de notificaciones en teléfonos que no lo tienen. Esta política explica qué información usa la app y cómo la trata.

## Resumen

- LumiDot **no recopila, no envía y no comparte** datos personales.
- La app **no tiene permiso de acceso a Internet**: técnicamente no puede transmitir información fuera del dispositivo.
- No contiene publicidad, analíticas, rastreadores ni SDK de terceros que recopilen datos.
- No requiere cuentas ni registro.

## Acceso a notificaciones

Si lo autorizás, LumiDot usa el servicio de Android `NotificationListenerService` para saber **cuándo** llega o se elimina una notificación y así encender o apagar el LED. Para eso usa únicamente:

- el nombre del paquete de la app que publicó la notificación (por ejemplo, `com.whatsapp`), para aplicar tus reglas por app y colores;
- metadatos técnicos de la notificación (identificador interno, importancia, si es persistente o silenciosa, hora de publicación).

LumiDot **no lee, no muestra y no guarda** el título, el texto, las imágenes ni ningún otro contenido de tus notificaciones. Los metadatos se mantienen sólo en memoria mientras la notificación está pendiente y se descartan al eliminarse.

## Datos guardados en el dispositivo

La app guarda localmente, en almacenamiento privado de la aplicación:

- tu configuración (color, tamaño, posición, patrón de parpadeo, horarios, etc.);
- tus reglas por app (qué apps encienden el LED y con qué color);
- la lista de nombres de paquete de las apps que enviaron notificaciones, para mostrarlas primero en la sección "Apps".

Estos datos no salen del teléfono, están excluidos de las copias de seguridad en la nube y se borran al desinstalar la app o al borrar sus datos desde los ajustes de Android.

## Otros accesos

- **Mostrar sobre otras apps:** se usa sólo para que Android permita abrir la pantalla del LED cuando el teléfono está bloqueado. No se usa para superponer contenido sobre otras apps ni para leer la pantalla.
- **Encender la pantalla:** se usa para mostrar el LED cuando llega una notificación.
- **Lista de apps instaladas:** la app consulta sólo las apps con ícono en el lanzador para que puedas elegir cuáles encienden el LED. Esta información no sale del dispositivo.

Podés revocar cualquiera de estos accesos en cualquier momento desde los ajustes de Android.

## Menores de edad

LumiDot no está dirigida a menores de 13 años y no recopila datos de nadie, incluidos menores.

## Cambios

Si esta política cambia, la nueva versión se publicará en esta misma dirección con la fecha de actualización.

## Contacto

Para consultas sobre privacidad, abrí un reporte en https://github.com/gmatiascr62/LumiDot/issues

---

# LumiDot Privacy Policy (English)

**Last updated:** October 9, 2026

- LumiDot **does not collect, transmit or share** any personal data. The app **does not have the Internet permission**.
- With your consent, it uses Android's `NotificationListenerService` only to know **when** notifications are posted or removed, using the posting app's package name and technical metadata. It **never reads, displays or stores** notification titles, text or other content.
- Settings and per-app rules are stored locally, excluded from cloud backups, and deleted when the app is uninstalled.
- "Display over other apps" is used only so Android allows the LED screen to open while the phone is locked.
- No ads, analytics, trackers or third-party data-collecting SDKs. No accounts.
- Contact: https://github.com/gmatiascr62/LumiDot/issues
