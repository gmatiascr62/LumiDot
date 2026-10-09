# Publicar LumiDot en Google Play (prueba interna)

Todo lo que pide Play Console está en esta carpeta. Las apps instaladas desde Google Play
no pasan por el bloqueo de Play Protect para APKs descargados.

## Archivos

| Archivo | Uso en Play Console |
|---|---|
| `LumiDot-release.aab` (Releases → `latest-build`) | Paquete que se sube en cada versión |
| `graphics/icon-512.png` | Ficha → Ícono de la app (512×512) |
| `graphics/feature-graphic-1024x500.png` | Ficha → Gráfico de funciones |
| `listing/es-419/title.txt` | Nombre de la app (30 caracteres máx.) |
| `listing/es-419/short_description.txt` | Descripción breve (80 máx.) |
| `listing/es-419/full_description.txt` | Descripción completa |
| `release-notes-es-419.txt` | Notas de la versión |
| [`../PRIVACY.md`](../PRIVACY.md) | Política de privacidad |

URL de la política de privacidad: https://github.com/gmatiascr62/LumiDot/blob/main/PRIVACY.md

## 1. Crear la app

1. En https://play.google.com/console → **Crear app**.
2. Nombre: `LumiDot`. Idioma predeterminado: **Español (Latinoamérica) – es-419**.
3. Tipo: **App**. Gratuita o paga: **Gratuita**. Aceptá las declaraciones.

## 2. Primera versión de prueba interna

1. **Probar y publicar → Pruebas → Prueba interna → Crear versión**.
2. Firma de apps de Google Play: dejá la opción por defecto (**clave generada por Google**).
   Tu keystore pasa a ser la *clave de subida*: si la perdés, Google permite reemplazarla.
3. Subí `LumiDot-release.aab`, pegá las notas de la versión y guardá.
4. Pestaña **Verificadores**: creá una lista de correo con tu cuenta de Gmail (la del teléfono).
5. **Revisar versión → Iniciar lanzamiento**.
6. Copiá el **vínculo para unirse** desde la pestaña Verificadores, abrilo en el teléfono,
   aceptá la invitación e instalá desde Play.

Antes de instalar desde Play, **desinstalá cualquier LumiDot instalado a mano**: Play la firma con
otra clave y Android no deja actualizar entre firmas distintas.

Cada versión nueva necesita un `versionCode` mayor. El CI lo calcula solo (10 + número de ejecución),
así que basta con subir el AAB de la última compilación.

## 3. Contenido de la app (Política → Contenido de la app)

Respuestas que corresponden al código actual:

- **Política de privacidad:** la URL de arriba.
- **Acceso a la app:** todas las funciones están disponibles sin acceso especial (no hay inicio de sesión).
- **Anuncios:** la app **no** contiene anuncios.
- **Clasificación de contenido:** categoría *Utilidad, productividad, comunicación u otra*.
  Respondé **No** a violencia, sexualidad, lenguaje, sustancias, apuestas, interacción entre usuarios,
  compartir ubicación y compras digitales.
- **Público objetivo:** mayores de 18 (o 13+). La app no está diseñada para niños.
- **Seguridad de los datos:**
  - ¿Recopila o comparte alguno de los tipos de datos requeridos? → **No**.
  - Motivo: la app no tiene permiso de Internet; los metadatos de notificaciones se procesan sólo
    en el dispositivo y Play no considera "recopilación" el procesamiento que no sale del teléfono.
- **Apps gubernamentales, funciones financieras, salud:** No.

Si Play Console pide justificar el **acceso a notificaciones**, usá:

> LumiDot es un LED de notificaciones virtual. Usa NotificationListenerService exclusivamente para
> detectar cuándo se publica o elimina una notificación y encender o apagar un indicador luminoso en
> pantalla. No lee el contenido de las notificaciones, no las guarda y no tiene permiso de Internet.

## 4. Pasar a producción (más adelante)

- Cargá al menos 2 capturas de pantalla del teléfono en la ficha.
- Las cuentas de desarrollador personales nuevas deben hacer una **prueba cerrada con al menos
  12 verificadores durante 14 días** antes de poder publicar en producción.
