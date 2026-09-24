# Calendario Lebeche — App Android nativa de calendario con CalDAV

App Android **nativa** (no TWA) de calendario para la asociación Lebeche. Se conecta a
servidores **CalDAV** (como **Synology Calendar**) para mostrar, crear, editar y borrar
eventos, con **notificaciones** de recordatorio y **sincronización bidireccional**.
Además, **exporta los eventos al calendario del sistema Android** para que se vean en la
app "Calendario" del móvil.

---

## Características

- Conexión a **CalDAV** con descubrimiento automático (`/.well-known/caldav`, PROPFIND de
  principal y `calendar-home-set`), compatible con **Synology Calendar**.
- Sincronización **bidireccional**: pull incremental con `sync-token` (`sync-collection`) y
  push de cambios locales (crear/editar/borrar con `PUT`/`DELETE`).
- Vista de **calendario mensual** + agenda del día seleccionado.
- Crear/editar/ver **detalle** de eventos: título, fecha/hora, todo el día, lugar,
  descripción, calendario de destino y recordatorio.
- **Eventos recurrentes** (RRULE): se expanden en la vista y en los recordatorios.
- **Notificaciones** de recordatorio ("X minutos antes") con `AlarmManager`; se reprograman
  tras reiniciar el dispositivo y para la siguiente ocurrencia de eventos recurrentes.
- **Exportación al calendario del sistema** (`CalendarContract`): los eventos aparecen en
  la app Calendario / Google Calendar del móvil.
- Sincronización **periódica** con `WorkManager` (cada 30 min) + botón manual + al abrir.

---

## Tecnología

| Componente | Tecnología |
|---|---|
| Lenguaje | Kotlin |
| UI | Jetpack Compose + Material 3 |
| CalDAV / HTTP | OkHttp 4 |
| iCalendar | biweekly 0.6.8 |
| Persistencia | SQLite (propia) |
| Sincronización | WorkManager |
| Recordatorios | AlarmManager + BroadcastReceiver |

- Package ID: `com.lebeche.calendario`
- Nombre: **Calendario Lebeche**
- minSdk **28** (Android 9+) · target/compileSdk **36**
- Icono: `LOGOS/icono lebeche negro.jpg` (símbolo de Lebeche en negro)

---

## Estructura

```
calendario-lebeche/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/lebeche/calendario/
│   │   ├── data/        # Modelos, SQLite, cifrado de contraseñas (Keystore)
│   │   ├── caldav/      # Cliente CalDAV (OkHttp + XML + biweekly)
│   │   ├── cal/         # Espejo en CalendarContract (calendario del sistema)
│   │   ├── notif/       # Recordatorios y notificaciones
│   │   ├── sync/        # SyncWorker (WorkManager)
│   │   ├── ui/          # Pantallas Compose
│   │   ├── Repository.kt
│   │   ├── App.kt
│   │   └── MainActivity.kt
│   └── res/             # Iconos, temas, strings
└── scripts/generar-iconos.ps1   # Regenera los mipmaps desde el JPG
```

---

## Compilar

```powershell
cd g:\GITHUB\calendario-lebeche
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME='C:\Users\jesus\AppData\Local\Android\Sdk'
.\gradlew.bat :app:assembleDebug      # APK de desarrollo
.\gradlew.bat :app:assembleRelease    # APK firmado (instalación directa)
.\gradlew.bat :app:bundleRelease      # AAB firmado (subir a Google Play)
```

Salidas:
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release.apk`
- Release AAB: `app/build/outputs/bundle/release/app-release.aab`

### Regenerar iconos

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File G:\GITHUB\calendario-lebeche\scripts\generar-iconos.ps1
```

---

## Conectar con Synology Calendar

1. Instala y abre la app. En la **primera apertura** se muestra una pantalla de bienvenida.
2. Introduce el **usuario** y la **contraseña** de la cuenta CalDAV (contacta con **Pelotxo** si no conoces tus credenciales de acceso). No hace falta escribir la URL del NAS.
3. Pulsa **Conectar**. La app se conecta a la URL por defecto `https://pelotxo.synology.me:5001/caldav/` y descubre automaticamente los calendarios.
4. Activa/desactiva cada calendario y usa **Sincronizar** cuando quieras.

> **Nota Synology**: el servicio CalDAV debe estar habilitado en el NAS (Synology Calendar →
> Configuracion). La ruta real es `https://pelotxo.synology.me:5001/caldav/` (la barra final es obligatoria).
> Si quieres usar otro servidor, ve a **Ajustes** → **Añadir cuenta CalDAV**.---

## Permisos

- `INTERNET` — conexión CalDAV.
- `POST_NOTIFICATIONS` — recordatorios (Android 13+).
- `READ_CALENDAR` / `WRITE_CALENDAR` — exportación al calendario del sistema.
- `RECEIVE_BOOT_COMPLETED` — reprogramar recordatorios al reiniciar.
- `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` — recordatorios puntuales.

---

## Firma (keystore)

La app se firma con `signing.keystore` (alias `calendario-lebeche`); las credenciales están
en `keystore.properties`. **Ambos están en `.gitignore`: guarda una copia de seguridad**, es
la única forma de actualizar la app en Google Play.

---

## Limitaciones conocidas (v1)

- Los eventos se gestionan en **UTC** (sin conversión de zona horaria para recurrentes a
  través de cambios de horario DST).
- La detección de errores de autenticación CalDAV se muestra de forma genérica (se guarda la
  cuenta aunque el descubrimiento falle; se puede reintentar con "Sincronizar").
- Las notificaciones de eventos recurrentes cubren los próximos 90 días.

## Mejoras recientes
- **Instalación release en emuladores**: se desactiva la generación del *baseline profile* (`androidx.profileinstaller`) para evitar el error `INSTALL_BASELINE_PROFILE_FAILED` al instalar el APK release en emuladores x86/x86_64.
- **Colores de calendario sincronizados en cada sincronización**: el color y el nombre de cada calendario de Synology se actualizan automáticamente en cada sincronización (no solo al conectar la cuenta). El parser de color ahora acepta tanto el formato RGBA como ARGB del canal alfa.
- **Tipografía Inter**: la app usa la misma tipografía que la PWA (Inter), con títulos en cursiva + negrita como los encabezados de la PWA.
- **Crear evento en el día seleccionado**: al tocar un día del calendario y pulsar el botón de añadir evento (+), la fecha de inicio/fin queda pre-rellenada con ese día.
- **Corrección de la navegación**: El botón físico de retroceso del dispositivo ("Atrás") ya no cierra la aplicación repentinamente. Se utiliza `BackHandler` de Jetpack Compose para volver suavemente a la pantalla principal sin interrumpir la experiencia.
- **Configuración por defecto del NAS**: Se actualizó el nombre de cuenta por defecto a "lebeche", evitando conectar a cuentas antiguas de prueba. Y se adaptó el mensaje a "contacta con la organización". La ruta por defecto se mantiene en `https://pelotxo.synology.me:5001/caldav/`.
- **Soporte corregido para los Colores del Calendario**: El color de los calendarios ya se muestra correctamente en Android. El parser ahora tiene en cuenta el canal alfa (ARGB/RGBA) enviado por el estándar de Apple/Synology que antes ocasionaba que los colores se renderizaran como invisibles o transparentes.
- **Etiquetas de Eventos (Categorías)**: Las etiquetas añadidas a cada evento ahora son completamente visibles en la vista rápida (`EventRow`).
- **Mejora Visual Profesional**: Se aplicaron ajustes sutiles y profesionales en las tarjetas de eventos (`ElevatedCard`), y se agregó una discreta franja vertical de color indicativa a la izquierda del evento, similar a los clientes de calendario profesionales (como Google Calendar o Outlook), preservando al mismo tiempo la sencillez del diseño original.

## Versión actual
- **Versión**: 2.4.0 (Code 13)
- **Tipografías Lebeche**: Se han introducido e integrado las tres tipografías del manual de marca corporativa ("Courgette" para logotipos/títulos destacados, "Garet" para subtítulos y "Open Sans" para el cuerpo del texto), en vez de "Inter".
- **Diseño**: Adaptado a la paleta de colores de la PWA Barrioteca Acalencá (`--color-cream: #f5f5f0`, `--color-ink: #141414`, `--color-primary: #8a5a00`, `--color-accent: #e8a33d`).
- **Vista mensual rediseñada**: Adaptación fidedigna de la vista mensual a las "best practices" modernas (como Google Calendar o Outlook), sin fila duplicada de los días de la semana y usando una retícula sutil. Eventos dibujados como píldoras de colores sincronizados.
- **Sincronización robusta de colores de calendario**: Resuelto el problema del solapamiento de etiquetas vacías provenientes del Synology CalDAV Server (se parsea el primer color no vacío disponible en las etiquetas `A:calendar-color` o `CS:calendar-color` ignorando las consiguientes etiquetas vacías).
- **Responsive adaptativo**: Solucionado el problema de diseño en pantallas grandes/horizontales dividiendo el contenido a dos columnas (Mes + Agenda).
- **Cierre del bucle Android Calendar Sync**: Exportación explícita del `EVENT_COLOR` a la base de datos local para que los colores brillen de igual forma en la aplicación general Google Calendar.

---

## Repositorio

- **Local**: `G:\GITHUB\calendario-lebeche\`
- **GitHub**: https://github.com/jesuscastilla/calendario-lebeche
