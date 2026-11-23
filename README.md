# NEPTUNO

Asistente personal de IA nativo para Android (Kotlin, Jetpack Compose, Room, DataStore) con voz STT/TTS, Gemini vía Firebase AI Logic y sincronización opcional con Cloud Firestore. Funciona offline-first: la nube es opcional.

La arquitectura está descrita en [ARCHITECTURE.md](ARCHITECTURE.md).

## Requisitos

- Android Studio reciente (AGP 9.1) y JDK 17
- Dispositivo o emulador con Android 10 (API 29) o superior

## Configuración local

Estos archivos no están en el repositorio y hay que aportarlos:

| Archivo | Para qué | Si falta |
|---|---|---|
| `app/google-services.json` | Conectar la app a tu proyecto Firebase | Compila (estrategia `WARN`), pero Gemini, Auth y Firestore no funcionan |
| `debug.keystore` (raíz) | Firma de la variante debug | Falla el empaquetado debug |
| `.env` | Variables locales (opcional, ver `.env.example`) | Se usan los valores por defecto |
| `my-upload-key.jks` + `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD` | Firma de release | No se puede firmar release |

Para generar un `debug.keystore` local:

```bash
keytool -genkeypair -v -keystore debug.keystore -storepass android -alias androiddebugkey \
  -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
```

## Compilar y probar

```bash
./gradlew testDebugUnitTest   # tests unitarios (Robolectric)
./gradlew assembleDebug       # APK debug
```

Las reglas de Firestore (`firestore.rules`) tienen tests en `firestore.test.js`, pensados para el emulador de Firestore (puerto 8085, ver `firebase.json`).

## Seguridad

- Room y DataStore están excluidos de los respaldos automáticos.
- No se guardan claves de Gemini en el cliente; se usa Firebase App Check.
