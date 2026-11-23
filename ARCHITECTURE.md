# Arquitectura de NEPTUNO (Android Nativo — Offline-First, Voz STT/TTS, Gemini y Cloud Sync Opcional)

NEPTUNO es una aplicación Android nativa modular en Kotlin + Jetpack Compose + Material 3, compatible desde **Android 10 (API 29)** hasta **Android 16 (API 36)**. Opera **offline-first** sobre Room y Jetpack DataStore, con reconocimiento/síntesis de voz reales, proveedor Gemini vía Firebase AI Logic y sincronización opcional con Google Sign-In y Cloud Firestore.

## 1. Separación de módulos / paquetes

| Paquete | Responsabilidad |
|---|---|
| `app` | `NeptunoApplication`, `NeptunoAppContainer`, `MainActivity` (entrada directa en modo local sin bloqueo por autenticación) y `NeptunoNavHost` adaptativo con ViewModels estables entre transiciones de sesión. |
| `core` | `NetworkMonitor` no bloqueante (`NET_CAPABILITY_INTERNET` + `NET_CAPABILITY_VALIDATED` para distinguir Internet validado, red conectada sin Internet y estado offline), `PermissionHelper`, `DispatcherProvider`. |
| `core.model` | Modelos de dominio (`AssistantState`, `ChatMessage`, `ConversationSession`, `MemoryEntry`, `BluetoothDeviceInfo`, `AudioRouteInfo`, `VoiceProfile`, `DeviceVoiceInfo`, `UserPreferences`, `ToolDescriptor`, `AiProviderStatus` con estado `Configured`). |
| `data.local` | Fuente principal de verdad local mediante **Room** (`NeptunoDatabase`, `ConversationDao`, `MessageDao`, `MemoryDao`, repositorios) y **Jetpack DataStore** (`UserPreferencesRepository`). |
| `data.remote` | Capa opcional desacoplada para nube: `CloudRemoteStore` / `NeptunoFirestoreRepository`, `SwitchableCloudSyncGateway` (`NoOpCloudSyncGateway` en modo local y `FirestoreCloudSyncGateway` acotado por timeouts en sesión autenticada), modelos Firestore y `FirestoreErrorHandler`. |
| `auth` | Flujo opcional de cuenta Google (`GoogleAuthController` con Jetpack Credential Manager + `AuthScreen` con salida explícita a modo local y manejo seguro de cancelación). |
| `feature.cloud` | `CloudSessionViewModel`: máquina de estados explícita (`NO_FIREBASE_SESSION`, `LOCAL_ONLY`, `CONNECTING`, `SYNCING`, `SYNCED`, `PARTIAL_SYNC`, `AUTH_ERROR`, `PERMISSION_ERROR`, `OFFLINE`, `TIMEOUT`), control single-flight (`AtomicBoolean`), timeouts por operación/global, reporte de error por sección y garantía `finally`/`invokeOnCompletion` para no quedar nunca atascado en `Sincronizando`. |
| `ai.api` | Contratos desacoplados `AiProvider`, `AiRequest`, `AiResponseEvent` y `AiProviderRegistry`. |
| `ai.gemini` | `GeminiFirebaseProvider` (`Firebase.ai`, `generateContentStream`, `GeminiStreamClient`) con transición honesta `Configured -> Ready` solo tras respuesta real, clasificación diferenciada de errores (App Check HTTP 403, autenticación, configuración, cuota 429 y red) y diagnóstico en `FirebaseAppCheckHelper`. |
| `ai.local` | `LocalEnginePendingProvider` preparado para futuros motores on-device sin conexión. |
| `audio.engine` | Implementaciones reales `AndroidSpeechToTextEngine`, `AndroidTextToSpeechEngine`, `AndroidAudioCaptureEngine`, `AndroidAudioPlaybackEngine`, `BidirectionalAudioCoordinator`, `VoiceProfileCatalog`, `AndroidAudioRouteManager` y contrato `AudioRoutingController`. |
| `voice.wake` | Contrato `WakeWordEngine` para detección futura de palabra de activación. |
| `android.services` | `BluetoothDeviceController` y `AssistantServiceContract`. |
| `feature.avatar` | Personaje 2D `NeptunoAvatar` reactivo a los 6 estados: `IDLE`, `LISTENING`, `PROCESSING`, `SPEAKING`, `ERROR`, `OFFLINE`. |
| `feature.home` | Pantalla de Inicio con avatar 2D, estado detallado de red, píldora no bloqueante de cuenta/nube opcional, Bluetooth, estado honesto del proveedor IA y accesos rápidos. |
| `feature.chat` | Conversación por texto y voz con guardado local prioritario, streaming progresivo, TTS e interrupción inmediata (`barge-in`). |
| `feature.bluetooth` | Estado real de Bluetooth, permisos Android 12+, dispositivos vinculados y rutas de audio (`AudioManager`). |
| `feature.memory` | Gestión de memoria autorizada e historial de conversaciones en Room (sincronización opcional cuando hay cuenta). |
| `feature.tools` | Catálogo extensible `ToolRegistry` y contrato `NeptunoTool`. |
| `feature.settings` | Personalización y Configuración, incluyendo tarjeta de cuenta Google opcional (`Iniciar sesión`, `Sincronizar / Reintentar`, `Cerrar sesión` sin borrar Room). |

## 2. Arquitectura Offline-First y Sincronización Segura

1. **Arranque inmediato sin bloqueo**: `MainActivity` abre `NeptunoAppShell` directamente en modo local (`user == null` -> `NoOpCloudSyncGateway`). Si existe una sesión Firebase válida en el dispositivo, se reconoce reactivamente sin detener la UI.
2. **Transición no destructiva entre modo local y nube**: `SwitchableCloudSyncGateway` y los ViewModels con clave estable evitan recrear la navegación o borrar Room/DataStore al iniciar o cerrar sesión.
3. **Sincronización idempotente y acotada**:
   - IDs deterministas en Firestore (`local_<id>` para memorias, `conv_<id>` para sesiones y `local_msg_<id>` para mensajes inmutables), evitando duplicados tras reconexión o reintentos.
   - Aislamiento estricto por UID en `firestore.rules` y en `NeptunoFirestoreRepository`.
   - Timeouts por operación (8 s) y global (20 s) con `try / catch / finally` + `invokeOnCompletion` en `CloudSessionViewModel`, garantizando `isSyncing = false` ante cualquier fallo o timeout.

## 3. Persistencia y Compilación

- **Room (`neptuno_local.db`)** y **Jetpack DataStore (`neptuno_user_preferences`)** están excluidos de respaldos automáticos (`android:allowBackup="false"`).
- Gradle configurado con firma `debugConfig` para Google AI Studio y variables de entorno externas para `release`, sin claves ni secretos en el repositorio.
