# UNANZE — puente de comunicación del proyecto Nept-Uno

Archivo central donde **Claude, ChatGPT y Perplexity** dejan constancia de lo que hacen, saben y auditan, para trabajar los tres al mismo ritmo con el mismo estado. Es el archivo que más cambia del repositorio. Lo lee el usuario (julsjailer) y cualquiera de las tres IA.

## 1. Reglas (obligatorias, sin preguntar)

1. **Registrar siempre.** Cada vez que una IA hace, descubre, decide o audita algo del proyecto, lo anota aquí **automáticamente, sin pedir permiso**. Si no quedó en UNANZE, no cuenta como hecho.
2. **Leer antes de actuar.** Al empezar un chat nuevo o retomar tras una pausa, la IA lee este archivo completo (sobre todo "Estado vivo" y las últimas entradas) antes de proponer o cambiar nada.
3. **Solo se añade a la bitácora.** Las entradas de la sección 6 no se borran ni se reescriben; si algo cambia, se añade una entrada nueva que lo corrige. "Estado vivo" y "Pendientes" sí se actualizan.
4. **Nada secreto.** Prohibido escribir aquí claves, tokens, contraseñas, el contenido de `google-services.json` o datos personales. Aunque el repo sea privado.
5. **Honestidad.** Se anota lo verificado como verificado y lo supuesto como supuesto. Si algo no se pudo compilar, probar o leer, se dice.
6. **Zonas protegidas.** No tocar el repositorio original `julsjailer/Nept-Uno` ni `julsjailer/mini-proyectos-potecitos-`. Se trabaja solo en `julsjailer/Nept-Uno-working`, rama `trabajo`.
7. **Sin pisarse.** Antes de editar código, mirar en la bitácora si otra IA está en el mismo tema y, si es así, anotar el cruce.

## 2. Quién puede escribir qué

| IA | Cómo registra | Límite real |
|---|---|---|
| **Claude** | Edita y hace commit de este archivo directamente (cuando la sesión tiene la GitHub App con escritura). | Solo lo hace dentro de una conversación con acceso al repo. |
| **ChatGPT** | Puede registrar directamente cuando su sesión tiene herramienta de escritura (lo hizo en la bitácora); si no, entrega el bloque de entrada para que se registre. | Depende del acceso de cada sesión; confirmar siempre con el SHA del commit. |
| **Perplexity** | Registra directamente cuando su sesión tiene escritura (confirmado con el commit 90c505f); si no, entrega el bloque. | Depende del acceso de cada sesión. |

Cuando una IA no puede escribir, debe terminar su respuesta con el bloque de entrada listo (formato de la sección 5) y decir: "Pásale esto a Claude con `UNANZE registrar`".

## 3. Comandos simples del usuario

El usuario escribe estas órdenes en cualquier chat; todas las IA las entienden igual.

| Comando | Qué debe hacer la IA |
|---|---|
| `UNANZE leer` | Leer este archivo y resumir en pocas líneas el estado, lo último hecho por cada IA y lo pendiente. |
| `UNANZE registrar` | Añadir a la bitácora lo que esa IA hizo o supo desde la última entrada (o entregar el bloque si no puede escribir). |
| `UNANZE estado` | Responder corto: qué está hecho, qué falla, qué sigue. |
| `UNANZE pasar a <IA>` | Preparar un mensaje breve para esa IA con lo que necesita saber de lo que hizo la que responde. |
| `UNANZE auditar` | Revisar el estado real del repo (código, CI, pendientes) contra lo anotado aquí y registrar las diferencias. |

## 4. Presentarse (texto para pegar en ChatGPT o Perplexity)

> Trabajamos en el proyecto Nept-Uno (app Android) con tres IA: Claude, ChatGPT y Perplexity. El puente es el archivo UNANZE.md en el repo privado julsjailer/Nept-Uno-working, rama trabajo. Lee UNANZE.md, sigue sus reglas y, cada vez que hagas algo, termina tu respuesta con un bloque de entrada en el formato de la sección 5, sin que te lo pida. Obedece los comandos `UNANZE leer`, `registrar`, `estado`, `pasar a <IA>` y `auditar`. No pongas secretos en ninguna entrada.

## 5. Formato de entrada de la bitácora

```
### AAAA-MM-DD HH:MM · IA · tipo
- Qué: (hecho, hallazgo, decisión o auditoría, en 1-3 líneas)
- Dónde: (archivos, commit o enlace)
- Estado: verificado / sin verificar / bloqueado
- Para las demás IA: (lo que deben saber o hacer, o "nada")
```

Tipos: `cambio`, `hallazgo`, `decisión`, `auditoría`, `bloqueo`, `nota`. Hora de referencia: America/Caracas.

## 6. Estado vivo (se actualiza)

**Última actualización:** 2026-11-25 02:30 · Claude. **Última versión: 1.0.62 (f88934c)**, CI #62: compilación, APK completa y ARM, análisis y prueba de instalación/actualización en emulador Android 11 correctos; en los tests solo falló el intermitente `NeptunoOfflineFirstSyncTest escenario J` (conocido, sin relación). Todo lo de Bluetooth, avatar y color está **sin probar en teléfono**. Pendientes: que el usuario pruebe el micrófono Bluetooth (KD-06) y pegue el «informe técnico»; probar «Hey Neptuno» por Bluetooth; voz natural; arreglar `escenario J`. Versiones 1.0.55–1.0.62 detalladas en la entrada de la bitácora «2026-11-25 02:30 · Claude · Registro completo 1.0.55–1.0.62».


**Incidencia crítica vigente — instalación de APK (2026-11-23 22:02 Caracas):** el usuario confirma que la APK #43 tampoco se instala. El fallo persiste tanto al intentar actualizar la instalación existente como al intentar una instalación limpia después de desinstalar (reporte anterior del usuario; Android mostró el mensaje genérico «No se pudo instalar la aplicación»). La compilación y publicación correctas NO resuelven ni explican el fallo de instalación. No hay aún código de error de Package Installer, registro de PackageManager ni huella de certificado obtenida del APK instalado. **No afirmar que el problema está resuelto ni cambiar a ciegas `applicationId`, `versionCode`, `minSdk`, firma/keystore, ABI o configuración Gradle.** Claude debe tomar el diagnóstico desde evidencia del APK y del dispositivo.

**Proyecto:** NEPTUNO, asistente de IA nativo para Android (Kotlin, Compose, Room, DataStore, Firebase AI Logic con Gemini, Firestore opcional). Arquitectura en `ARCHITECTURE.md`.

**Repos:** original `Nept-Uno` (público, no tocar) · trabajo `Nept-Uno-working` (privado; `main` = copia exacta del original en 1ac4381, `trabajo` = cambios). No tocar `mini-proyectos-potecitos-`.

**Dispositivos de prueba (dato del usuario):** Redmi 10C y Redmi 9C, ambos con 4 GB de RAM. El usuario los considera buenos para el proyecto.

**Compilación y CI:** un solo workflow (`build-apk.yml`) con Java 21: tests, APK debug firmada con llave fija (`ci/ci-debug.jks`), release `apk-latest` ("APK debug (último build)"), verificación de recursos de Firebase dentro de la APK y logs en la rama `ci-logs` (`ultimo-log.txt`). Los commits que solo tocan `*.md` no recompilan. Un build nuevo cancela al anterior.

**Tests:** en el run #12 (70322c1) pasaron 35 de 36; el que falló (STT) ya está corregido en 6d759a3, pendiente de ver el siguiente run.

**Firebase y cuenta:** `app/google-services.json` está en el repo privado con la SHA-1 de la llave de CI. Según el usuario, el inicio de sesión con Google y la sincronización con la nube ya funcionan en el teléfono.

**Gemini:** responde (según el usuario). La cuota gratuita es de 20 solicitudes por modelo y por día. El proveedor prueba en orden `gemini-3.8-flash`, `gemini-4-hydrogen`, `gemini-3.5-flash-lite` ante cuota agotada (429) o saturación (503/"high demand"); recuerda el modelo cuando es por cuota. El nombre del proveedor muestra el último modelo que respondió.

**Voz:** el selector de voces del dispositivo ya existía en Ajustes. Nuevo ajuste "Enviar automáticamente lo que digo" (activado por defecto): al llegar la transcripción final el mensaje se envía solo.

**"Hey Neptuno" (prueba de usuario, 2026-11-23):** usuario confirma que «Hey Neptuno» y «Oye Neptuno» activan la app; «Hola Neptuno» no se reconoce. La gramática Vosk incluye `hola`; falta diagnosticar transcripción/confianza/duración/canal de audio sin bajar umbrales a ciegas. Se retiró la apertura automática de `MainActivity`, la notificación full-screen y los flags `showWhenLocked`/`turnScreenOn`; ahora la detección actualiza la notificación persistente y el usuario debe tocarla para abrir el chat. Android debe aplicar su bloqueo normal antes de mostrar la actividad. La solicitud de activación queda pendiente hasta 45 s para permitir desbloquear y abrir la notificación. **Limitación vigente:** el dictado, Gemini y TTS todavía dependen de `ChatViewModel`/UI; la conversación completamente manos libres con pantalla bloqueada aún no está implementada. Los cambios de seguridad están subidos a `trabajo`, pero el CI del commit final está en curso y falta prueba física. Bluetooth y estabilidad prolongada tampoco están validados.

**IA local:** `LocalAiProviderStub.kt` es un esqueleto sin modelo. Sin Gemini la app no responde con IA.

## 7. Pendientes y plan (se actualiza)

### Hecho
- Arreglos de clasificación de errores de Gemini y de logs sin datos personales.
- README, CI con APK descargable, logs en `ci-logs`, Java 21, llave de firma fija.
- Compilación sin `google-services.json` y Firebase activo con el archivo del usuario.
- Cambio automático de modelo Gemini ante 429 y saturación, con modelo visible en el nombre del proveedor.
- Envío automático de lo dictado por voz.
- Puente UNANZE entre Claude, ChatGPT y Perplexity.

### Plan acordado con el usuario (orden propuesto por Claude, sin confirmar)
1. **Voz natural y gratuita.** (a) Sin código: instalar en el teléfono las voces de alta calidad del motor de Google y elegirlas en Ajustes. (b) Evaluar un TTS neuronal local (Piper o sherpa-onnx) para los Redmi de 4 GB. (c) Voz de nube (Google Cloud o Gemini): descartada por ahora por cuota/facturación.
2. **Conversación continua tipo Gemini Live.** Primero un modo manos libres con lo existente (escuchar, responder, volver a escuchar). Ojo: cada turno gasta 1 solicitud. Después, evaluar la API Live de Firebase AI Logic (disponibilidad y cuota en plan gratuito sin comprobar).
3. **"Hey Neptuno" en segundo plano.** *(HECHO en código con Vosk el 2026-11-23, pendiente de prueba en teléfono; ver estado vivo.)* Servicio en primer plano con micrófono y notificación fija + detector de palabra clave (Porcupine con clave gratuita de uso personal, u openWakeWord/sherpa-onnx sin clave). Riesgos sin probar: Xiaomi/MIUI suele cerrar servicios en segundo plano (autoinicio y ahorro de batería), consumo de batería, privacidad del micrófono, restricciones de Android 14.
4. **Gastar menos cuota.** Medir cuántas solicitudes consume cada mensaje (22 en un día sugiere reintentos o más de una por mensaje) y reducir historial enviado.

### Pendiente de la revisión original
5. Sincronización con la nube: implementados marcadores `cloudSyncedAtMillis` en conversaciones, mensajes y memorias, consulta solo de pendientes y escritura de mensajes por lotes (commit `5eda199`). El primer CI (#14) confirmó tests correctos, pero falló la compilación por un `companion object` duplicado en `NeptunoFirestoreRepository.kt`. Corregido en `bcc9fcc`; CI #15 en curso. La sincronización aún no está validada en CI ni en dispositivo.
6. Room: migración aditiva 1→2 implementada en `5eda199` (tres columnas nullable, sin borrar tablas). Pendiente validar migración desde una base v1 con datos y confirmar CI.
7. Interrupción de Gemini: cancelar el Job en lugar de comprobar una bandera por chunk.
8. Dependencias antiguas (Compose BOM 2024.09, lifecycle 2.8, navigation 2.8); `firebase-appcheck-debug` como `implementation`; release sin minificación.
9. Dividir `ChatViewModel` (810 líneas) y `CloudSessionViewModel` (646); quitar los `!!` restantes.
10. Modo offline con modelo local pequeño (MiniMind u otro): sin decidir; entrenar requiere GPU y la app necesita un motor de inferencia (llama.cpp o MNN).

### Para el usuario
- Probar la última APK de Releases: autoenvío por voz, cambio de modelo y qué modelo muestra.
- Decidir el orden del plan (puntos 1 a 3).

## 7B. Guía de compilación para ChatGPT (y para cualquier IA que sustituya a Claude)

Escrita por Claude tras los fallos de compilación de los runs #14, #17, #18 y #19. Objetivo: que ChatGPT pueda continuar solo sin dejar el proyecto en rojo. Estas reglas salen de errores reales, no de teoría.

### Reglas de oro
1. **Nunca añadas una dependencia sin comprobar que existe.** Los runs #17, #18 y #19 fallaron porque se probaron tres direcciones de Sherpa-ONNX "de memoria" (dos de JitPack y una de Maven Central) y ninguna existía. Se comprueba así: añade la ruta (por ejemplo `com/alphacephei/vosk-android`) a la lista del paso "Diagnóstico de dependencias" de `.github/workflows/build-apk.yml`, sube, y lee la sección "DIAGNÓSTICO DE DEPENDENCIAS" de `ci-logs`. Solo cuando salga `HTTP 200` y la versión, la usas en Gradle con esa versión exacta. Una ficha web que "existe" no prueba que la versión exista en el repositorio que usa Gradle.
2. **Un solo cambio por commit.** No mezcles dependencia, código nuevo e interfaz en el mismo commit. Si falla, no sabrás cuál fue.
3. **Espera el CI antes del siguiente commit de código.** Tras cada push, espera a que `ci-logs/ultimo-log.txt` muestre TU commit en la primera línea (`run #N · commit XXXXXXX`) y léelo completo. No apiles commits encima de un rojo. No des por bueno un run cuyo commit no es el tuyo.
4. **Sincroniza antes de editar.** `git fetch origin trabajo` y avanza a la punta antes de tocar nada; antes de subir, otra vez. UNANZE.md ya tuvo choques por editarse a la vez.
5. **Lee el archivo completo antes de editarlo y busca duplicados.** El run #14 falló porque se añadió un segundo `companion object` en una clase que ya tenía uno. Antes de declarar algo (companion, constantes, funciones, imports) haz `grep` del nombre en el archivo.
6. **Usa solo APIs que hayas visto en la versión exacta que fijaste.** Lee el README o el código fuente de esa versión; no uses nombres de clases o parámetros de memoria. Un `Unresolved reference` casi siempre es esto.
7. **No toques las versiones de Kotlin, AGP, Gradle ni Java** (el CI usa Java 21 y compileSdk 36). Si crees que hace falta, anótalo en UNANZE y pregunta al usuario.
8. **Todo lo experimental debe poder apagarse sin borrar nada.** Patrón usado: una interfaz (`WakeWordEngine`) con un motor de reserva (`PendingWakeWordEngine`) y el motor real aparte. Si el motor real no compila, se cambia una línea para volver al de reserva y se renombra el archivo a `.kt.disabled` (así lo hizo Claude con Sherpa).
9. **Mantén los tests al día.** Los tests que usan `android.*` necesitan `@RunWith(RobolectricTestRunner::class)` y `@Config(sdk = [36])`. Si cambias un comportamiento (como el envío automático por voz), actualiza los tests que lo daban por hecho. El escenario J de `NeptunoOfflineFirstSyncTest` es intermitente: si falla solo, repite el run antes de tocar código.
10. **Los tests no ponen el run en rojo** (el paso tiene `continue-on-error`). Para saber si pasaron, abre la sección TESTS de `ci-logs` y busca `BUILD SUCCESSFUL` o `tests completed, N failed`. La APK sí falla el run si no compila.
11. **Nada de secretos en el repo ni en UNANZE.** Claves de terceros (por ejemplo un AccessKey de Picovoice) van como secreto de GitHub Actions y se inyectan en el workflow; nunca en el código. `google-services.json` ya está en el repo porque es privado: si el repo pasara a público, hay que quitarlo.
12. **Modelos descargados en tiempo de ejecución:** comprueba antes la URL, el tamaño mínimo esperado y la licencia, y anota la licencia en UNANZE (el modelo Moonshine español es de uso no comercial).
13. **No des nada por funcionando hasta probarlo en el teléfono.** "Compila" no es "funciona". En la bitácora usa exactamente: `verificado en CI (run #N, commit XXXXXXX)`, `sin verificar` o `probado en teléfono por el usuario`.

### Cómo leer el resultado del CI
```
git fetch origin ci-logs
git show origin/ci-logs:ultimo-log.txt
```
El archivo trae, en orden: cabecera con run y commit, TESTS (últimas 120 líneas), COMPILACIÓN (últimas 200), DIAGNÓSTICO DE DEPENDENCIAS y VERIFICACIÓN DE FIREBASE. La APK compilada queda en Releases como `apk-latest`. Los commits que solo tocan archivos `*.md` no lanzan CI.

### Lista de comprobación antes de cada push de código
- [ ] Hice `git fetch` y estoy sobre la punta de `trabajo`.
- [ ] Cada dependencia nueva tiene `HTTP 200` y versión en el diagnóstico de `ci-logs`.
- [ ] Cada clase que uso está importada y existe en la versión fijada.
- [ ] No dupliqué declaraciones (hice `grep`).
- [ ] La función nueva se puede apagar sin romper el resto.
- [ ] Actualicé los tests que cambian con mi cambio.
- [ ] Mi cambio es una sola cosa.
- [ ] Después de subir, esperaré a ver mi commit en `ci-logs` y lo leeré entero.

### Qué hacer cuando falla
1. Lee el log entero (no solo la última línea) y copia el primer error real (`e:`, `Could not find`, `Unresolved reference`, `FAILED`).
2. Haz **un** arreglo para **esa** causa.
3. Regístralo en UNANZE con estado `sin verificar` y espera el siguiente run.
4. **Si dos intentos seguidos fallan por lo mismo, para.** Escribe en UNANZE qué sabes, qué supones y qué probaste, y aísla la función (regla 8). Seguir adivinando fue lo que hizo perder tres runs con Sherpa.
5. Si el usuario recibe un aviso de GitHub, la causa casi siempre está en el run más reciente de `ci-logs`; no asumas que es del cambio anterior.

### Límites de ChatGPT, hoy
- Puede leer y escribir en el repo cuando su sesión tiene herramienta de escritura (lo ha hecho); si no la tiene, entrega el parche y el bloque de UNANZE para que Claude lo suba.
- No puede probar en el teléfono ni ejecutar Gradle localmente: toda su verificación es el CI.

## 7C. RELEVO TEMPORAL: ChatGPT sustituye a Claude (desde 2026-11-23 18:10)

Claude no estará disponible un tiempo. **ChatGPT, tú trabajas el proyecto hasta que el usuario diga "pasar a Claude".** Lee primero 7B completa (las 13 reglas de compilación): son obligatorias. Aquí van las reglas extra para este relevo.

**Antes de tocar nada**
1. `git fetch origin && git checkout trabajo && git pull`. Trabaja SOLO en la rama `trabajo` del repo `Nept-Uno-working`. Nunca en `main`, nunca en `Nept-Uno` (original), nunca en `mini-proyectos-potecitos-`.
2. Lee el final de la bitácora (sección 8) y la rama `ci-logs` (`ultimo-log.txt`): debe decir "BUILD SUCCESSFUL" en TESTS y COMPILACIÓN con el commit más reciente. Si no, tu primera tarea es arreglar eso, nada más.
3. Anota en la bitácora que empiezas, con tu nombre (ChatGPT).

**Cómo trabajar sin romper la compilación (resumen, el detalle está en 7B)**
- **Un cambio pequeño por commit.** Haz push, espera a que `ci-logs/ultimo-log.txt` muestre TU SHA corto en la primera línea (`run #N · commit <sha>`), mira que diga BUILD SUCCESSFUL, y solo entonces haz el siguiente cambio. No apiles cambios sin verificar.
- **No uses una librería sin comprobar que existe.** Añade su ruta a `Diagnóstico de dependencias` en `build-apk.yml`, push, lee el resultado en `ci-logs` y usa solo la versión que aparezca. Ya verificadas y en uso: `com.alphacephei:vosk-android:0.3.75`, `net.java.dev.jna:jna:5.19.1@aar`. Verificadas pero sin usar: `xyz.rementia:openwakeword:0.1.5`, `ai.picovoice:porcupine-android:4.0.2`. Sherpa-ONNX: NO existe con ninguna de las 3 coordenadas probadas; no lo reintentes.
- **Antes de crear una clase, función o `companion object`, busca con grep que no exista ya** (el run #14 falló por un `companion object` duplicado).
- Usa solo APIs de Kotlin/Android/Compose que ya aparezcan en el código del repo o en las versiones fijadas en `gradle/libs.versions.toml`. No cambies versiones de Kotlin, AGP, Gradle ni Java.
- Si cambias la firma de un constructor o función pública, busca todos sus usos (incluidos tests en `app/src/test`) y actualízalos en el mismo commit. `ChatViewModel`, `NeptunoAppContainer` y `WakeWordEngine` tienen tests/usos: no cambies sus firmas existentes, **añade** miembros nuevos.
- Los archivos `.kt` de tests con Robolectric llevan `@RunWith(RobolectricTestRunner::class)` y `@Config(sdk=[36])`. Código que usa `android.util.Log` o `org.json` necesita Robolectric; la lógica pura (como `VoskPhraseMatcher.matches`) se prueba con JUnit simple.
- **Dos fallos seguidos de la misma causa → para.** Escribe en la bitácora qué falló, qué probaste y el texto exacto del error de `ci-logs`, y deja la decisión al usuario/Claude. No sigas adivinando.
- Nunca digas "funciona" de algo que no se probó en el teléfono: di "compila y pasa tests, sin probar en teléfono".
- No copies valores de `app/google-services.json` en ningún archivo ni mensaje. No subas claves ni tokens.

**Código que Claude acaba de dejar (no lo rehagas, extiéndelo)**
- Detector: `voice/wake/VoskWakeWordEngine.kt` (contiene `VoskPhraseMatcher`, `VoskSpanishModel`). Parámetros: solo resultados finales, gramática saludo+"neptuno", confianza mínima `0.95 - 0.4*sensibilidad`, frase ≤ 2.2 s, enfriamiento 3 s.
- Puente: `voice/wake/WakeActivationBus.kt`; servicio `WakeWordForegroundService.kt` (`handleDetection`); en el chat `ChatViewModel.startVoiceFromWake()` y `voiceTurnEnded`; conexión en `NeptunoNavHost.kt`.
- `SherpaMoonshineWakeWordEngine.kt.disabled` es solo referencia de estructura; no lo renombres.

**Cola de trabajo para ChatGPT (en este orden; marca cada una en la bitácora al terminar)**
1. **Prueba física de wake word recibida.** «Hey Neptuno» y «Oye Neptuno» funcionan; «Hola Neptuno» falla. Ya se retiró la apertura automática/full-screen y se exige tocar la notificación para entrar al chat. Esperar CI del commit `7a7567e`; después pedir al usuario comprobar que con pantalla bloqueada ya no aparece la interfaz y que al tocar la notificación, desbloquear y entrar dentro de 45 s se inicia el dictado. Diagnosticar «Hola» con transcripción/confianza/duración local no sensible y tests; no bajar umbrales globales sin evidencia.
2. **Conversación continua (manos libres).** En `ChatViewModel`: añadir un ajuste `continuousConversation` (preferencia en DataStore, desactivado por defecto, con interruptor en Ajustes y texto en `strings.xml`). Cuando esté activo y termine de hablar la respuesta (TTS), reabrir el dictado con `startVoiceFromWake()`-like; parar tras 2 turnos sin voz. Un commit para la preferencia, otro para la lógica. Respeta la cuota: cada turno gasta 1 solicitud de Gemini.
3. **Reducir cuota de Gemini.** Contar solicitudes por mensaje en `GeminiFirebaseProvider` (solo log local sin datos personales) y limitar el historial enviado (p. ej. últimos 10 mensajes). Añade test.
4. **Voz natural.** Sin dependencias nuevas: en Ajustes, guiar al usuario a instalar voces de alta calidad del motor de Google y preseleccionar la mejor voz en español disponible (`TextToSpeech.getVoices()`, preferir calidad alta y no `isNetworkConnectionRequired`). Piper/sherpa TTS solo después de verificar la dependencia por CI.
5. Pendientes de la lista 7 (puntos 7 a 9), de uno en uno.

**Al terminar cada tarea**: bitácora (formato de la sección 5), actualizar la sección 6 si cambia el estado, y avisar al usuario en una frase de qué compila y qué falta probar.

## 8. Bitácora (solo se añade al final)

### 2026-11-22 19:49 · Claude · hallazgo
- Qué: Revisión del repo. Arquitectura sólida y reglas de Firestore bien hechas; encontrados los problemas de la sección 7 (sincronización, clasificación de errores, logs con datos personales, Room sin migraciones, dependencias antiguas).
- Dónde: repositorio completo, sin compilar localmente.
- Estado: verificado leyendo el código; no ejecutado.
- Para las demás IA: tomar los pendientes de la sección 7 como punto de partida.

### 2026-11-22 21:00 · Claude · cambio
- Qué: Corregida la clasificación de errores de Gemini (`"rate"` coincidía con "generate"; códigos HTTP ahora como números aislados) y los logs de errores de Firestore ya no guardan UID ni correo en release. Añadidos README y `gradlew` ejecutable.
- Dónde: `GeminiFirebaseProvider.kt`, `FirestoreErrorHandler.kt`, `README.md`, rama `trabajo`.
- Estado: sin verificar con tests (los tests no corren aún en CI por el 429).
- Para las demás IA: no repetir estos arreglos.

### 2026-11-22 21:20 · Claude · cambio
- Qué: CI en un solo workflow (`build-apk.yml`): tests, APK debug, artefacto, release `apk-latest` y logs en rama `ci-logs`. Llave de firma fija en `ci/ci-debug.jks` (llave debug estándar, contraseña "android", no es secreto).
- Dónde: `.github/workflows/build-apk.yml`, `ci/ci-debug.jks`.
- Estado: verificado en CI (run #4: APK compilada y release creado).
- Para las demás IA: los logs de CI se leen en la rama `ci-logs`.

### 2026-11-22 23:00 · Claude · cambio
- Qué: El proyecto ya compila sin `google-services.json` (`default_web_client_id` se resuelve en tiempo de ejecución; si falta, Google Sign-In queda desactivado). Tests de CI con `--no-configuration-cache` y 3 intentos.
- Dónde: `GoogleAuthController.kt`, `build-apk.yml`, commit 853c687.
- Estado: verificado (compilación OK en CI); tests siguen fallando por 429 externo.
- Para las demás IA: la APK de Releases no incluye Firebase hasta tener el `google-services.json`.

### 2026-11-22 23:05 · Claude · nota
- Qué: El usuario compartió MiniMind (github.com/jingyaogong/minimind, Apache-2.0, modelo de 64M entrenable desde cero). Valoración: no sustituye a Gemini; solo sería útil como modo offline opcional y exige motor local en Android. Entrenar requiere GPU, que Claude no tiene.
- Dónde: pendiente 8.
- Estado: sin decidir.
- Para las demás IA: si alguna evalúa viabilidad real en Android (MNN o llama.cpp), anotarlo aquí.

### 2026-11-22 23:30 · Claude · decisión
- Qué: Creado este archivo (UNANZE.md) como puente entre Claude, ChatGPT y Perplexity, con reglas, comandos y formato. El workflow de APK ignora los cambios que solo toquen `*.md`, para no recompilar en cada registro.
- Dónde: `UNANZE.md`, `.github/workflows/build-apk.yml`.
- Estado: verificado (archivo subido a `trabajo`).
- Para las demás IA: usar el texto de la sección 4 para incorporarse.

### 2026-11-22 23:33 · Perplexity · auditoría
- Qué: Leído completo UNANZE.md. Auditoría previa de solo lectura sobre 853c687: confirmados en logs publicados assembleDebug correcto y bloqueo de tests al descargar org.robolectric:nativeruntime-dist-compat:1.0.18 por HTTP 429; confirmado release apk-latest con Neptuno-debug.apk. Revisados contratos AiProvider, proveedor local pendiente, Room y recorrido completo de sincronización. Investigados MiniMind, llama.cpp, MNN y LiteRT-LM; ninguna integración aprobada ni probada en Android.
- Dónde: Nept-Uno-working, rama trabajo; ARCHITECTURE.md, configuración Gradle, NeptunoDatabase.kt, AiProviderContracts.kt, LocalAiProviderStub.kt, GeminiFirebaseProvider.kt, lecturas parciales de ChatViewModel.kt y CloudSessionViewModel.kt, NeptunoAppContainer.kt, build-apk.yml; log publicado de CI run #4 y release apk-latest.
- Estado: verificado por lectura en el alcance indicado; no se ejecutaron builds ni tests ni se instaló el APK por parte de Perplexity. Falta cerrar cancelación extremo a extremo, autenticación y App Check. Modelo y motor locales sin decidir; faltan datos del teléfono y pruebas.
- Para las demás IA: no repetir arreglos ya presentes. Cancelación Gemini ya tiene mecanismos y exige revisar también el Job del llamador. El workflow auditado en 853c687 tenía tres reintentos y permitía publicar apk-latest desde main y trabajo; reevaluar la versión actual antes de cambiarlo. La exclusión de Markdown declarada por Claude no se ha comprobado en esta operación. Esta sesión dispone de herramienta de escritura, pero no se había probado escritura efectiva; cualquier cambio requiere confirmación. Solo se solicita añadir esta entrada, sin cambiar código ni entradas anteriores.


### 2026-11-22 23:49 · ChatGPT · coordinación
- Qué: Reintenté la actualización de este registro después de comprobar que la herramienta de escritura está disponible. Se volvió a leer la versión actual antes de preparar el cambio.
- Dónde: UNANZE.md, repositorio Nept-Uno-working, rama trabajo.
- Estado: pendiente de confirmación de GitHub; no afirmar escritura hasta recibir SHA de commit.
- Para las demás IA: este intento solo añade una entrada de coordinación; no modifica código ni otras secciones.

### 2026-11-22 23:52 · Perplexity · nota
- Qué: A petición del usuario, se aclara que ChatGPT y Perplexity pueden leer y escribir directamente en UNANZE.md cuando sus sesiones dispongan del acceso correspondiente. Cada IA puede registrar su trabajo por su lado, sin necesidad de pasar habitualmente el texto a Claude. Esta nota corrige las limitaciones generales atribuidas a ambas en la sección 2, sin borrar el historial.
- Dónde: julsjailer/Nept-Uno-working, rama trabajo, UNANZE.md. Escritura de Perplexity confirmada por el commit 90c505f8be8c21c8f6e051a1bfb24fb1c09d82b9; la versión leída contiene además una entrada atribuida a ChatGPT de las 23:49.
- Estado: escritura de Perplexity verificada; capacidad de ChatGPT comunicada por el usuario y coherente con la entrada presente, sin inspección independiente de su sesión ni del autor de su commit.
- Para las demás IA: leer la versión actual antes de registrar, conservar las entradas ajenas y usar el SHA vigente para evitar sobrescrituras. Registrar directamente si se dispone de escritura, respetando las confirmaciones exigidas por cada herramienta; delegar a Claude solo cuando falte acceso o exista un bloqueo real. Esta aclaración no autoriza cambios de código ni operaciones en repositorios protegidos.

### 2026-11-23 02:10 · Claude · cambio
- Qué: El usuario entregó `google-services.json`; se guardó como `app/google-services.json` en el repo privado y el workflow ya lo usa (no hace falta el secreto GOOGLE_SERVICES_JSON). El paquete coincide con el `applicationId`. Nota: el cliente OAuth Android del archivo tiene una huella de certificado distinta a la de `ci/ci-debug.jks`, por lo que el inicio de sesión con Google puede fallar en la APK de CI hasta registrar la SHA-1 de CI en Firebase. No se copian aquí valores del archivo.
- Dónde: `app/google-services.json`, `UNANZE.md` (secciones 6 y 7).
- Estado: sin verificar en dispositivo; la compilación con Firebase se comprobará en el siguiente run de CI.
- Para las demás IA: no copiar el contenido de `google-services.json` a ninguna entrada ni respuesta. Si el repo pasara a público, quitar ese archivo del historial antes.

### 2026-11-23 02:20 · Claude · cambio
- Qué: El usuario entregó un `google-services.json` actualizado con la SHA-1 de la llave de CI registrada; sustituye al anterior en `app/google-services.json`. La huella coincide con `ci/ci-debug.jks`, así que el aviso anterior sobre Google Sign-In queda resuelto en teoría. Añadido `concurrency` al workflow de APK para que un build nuevo cancele al anterior y no se publique en `apk-latest` una APK antigua. No se copian aquí valores del archivo.
- Dónde: `app/google-services.json`, `.github/workflows/build-apk.yml`, `UNANZE.md`.
- Estado: sin verificar en dispositivo; el build de CI mostrará si compila con Firebase.
- Para las demás IA: la entrada de las 02:10 sobre la huella distinta ya no aplica. Sigue vigente no copiar el contenido de `google-services.json`.

### 2026-11-23 02:30 · Claude · hallazgo
- Qué: Run #7 (commit 6677ea3): la APK compila con Firebase y `apk-latest` apunta a ese commit. Los tests fallan ya sin el 429: 4 clases dan `UnsupportedOperationException` en `DefaultSdkProvider` (Robolectric). Causa probable: los tests usan `@Config(sdk = [36])` y Robolectric necesita JDK 21 para SDK 36, mientras el CI usaba Java 17. Cambiado el CI a Java 21. El aviso de "Run Failed" del commit d5928b8 corresponde a un run anterior cuyo log se sobrescribió; no se pudo revisar.
- Dónde: `.github/workflows/build-apk.yml`, log en rama `ci-logs`.
- Estado: causa sin confirmar; se verificará en el siguiente run.
- Para las demás IA: si los tests siguen fallando con Java 21, no asumir el 429; leer `ci-logs`.

### 2026-11-23 02:45 · Claude · hallazgo
- Qué: Run #8 (3d409e2): con Java 21 los tests pasan y la APK compila; `apk-latest` apunta a ese commit. El usuario reportó en su teléfono "Falta la configuración de Google Sign-In (default_web_client_id)" y "Default FirebaseApp is not initialized": ambos indican que la APK instalada no traía los recursos de Firebase (probablemente una APK anterior al `google-services.json`; sin confirmar). Añadido al CI un paso que lista los recursos de Firebase de la APK y los deja en `ci-logs` (sección "VERIFICACIÓN FIREBASE"). Confirmado también por Perplexity que el proveedor local es un stub: `LocalAiProviderStub.kt`; sin Gemini la app no responde con IA.
- Dónde: `.github/workflows/build-apk.yml`, `ci-logs`.
- Estado: causa sin confirmar hasta leer la verificación del siguiente run.
- Para las demás IA: no asumir que Firebase funciona en el teléfono hasta ver la verificación y la prueba del usuario.



### 2026-11-23 02:40 · ChatGPT · nota
- Qué: Leído completo UNANZE.md para retomar el proyecto. Estado actualizado: Firebase config fue incorporada y reemplazada con SHA-1 de CI; Run #8 con Java 21 pasó tests y compiló APK. Sigue sin confirmarse en el dispositivo la inicialización Firebase/Google Sign-In; la verificación de recursos añadida al CI está pendiente de revisar en el siguiente run. Proveedor local continúa siendo un stub, por lo que no ofrece IA offline funcional.
- Dónde: UNANZE.md, repositorio Nept-Uno-working, rama trabajo; secciones 2, 6, 7 y bitácora hasta Claude 02:45.
- Estado: verificado por lectura del archivo; no se auditó código ni se ejecutaron builds o pruebas en esta sesión.
- Para las demás IA: la sección 2 aún contiene una descripción general antigua que dice que ChatGPT es solo lectura; las entradas posteriores documentan la capacidad de escritura directa de esta sesión y de Perplexity cuando sus conectores lo permiten. Mantener como referencia la capacidad efectiva de cada sesión y confirmar cada commit. No modificar código en esta sesión.

### 2026-11-23 03:05 · Claude · cambio
- Qué: Con Firebase ya funcionando en el teléfono (inicio de sesión y sincronización OK), Gemini devolvió 429: la cuota gratuita es de 20 solicitudes por modelo y por día y el proyecto la superó con `gemini-3.8-flash`. El proveedor ahora prueba en orden `gemini-3.8-flash`, `gemini-4-hydrogen` y `gemini-3.5-flash-lite` (los tres figuran en la documentación de Firebase como estables y sin facturación) cuando un modelo da 429, y recuerda el que funcionó para no gastar solicitudes en uno agotado. Mensaje de cuota más claro. Añadido `GeminiModelFallbackTest` (3 tests).
- Dónde: `GeminiFirebaseProvider.kt`, `app/src/test/.../GeminiModelFallbackTest.kt`.
- Estado: sin verificar; la compilación y los tests se comprueban en el siguiente run de CI. Que `gemini-4-hydrogen` y `gemini-3.5-flash-lite` tengan cuota propia no está confirmado (dato del usuario: el límite es "por modelo por día").
- Para las demás IA: pendiente medir cuántas solicitudes consume cada mensaje (22 usadas en un día sugiere que algo gasta más de una por mensaje).

### 2026-11-23 03:25 · Claude · cambio
- Qué: Run #10 (52f3f6b): la APK con cambio automático de modelo ante 429 compila y `apk-latest` ya apunta a ese commit. Los 3 tests nuevos fallaban porque eran JUnit puro y el proveedor usa `android.util.Log`; pasados a Robolectric como los demás. Los otros 32 tests pasan.
- Dónde: `GeminiModelFallbackTest.kt`.
- Estado: APK compilada (verificado en CI); tests corregidos sin verificar hasta el siguiente run. La lógica de cambio de modelo no se ha probado en el teléfono.
- Para las demás IA: sigue pendiente medir cuántas solicitudes consume cada mensaje.

### 2026-11-23 03:50 · Claude · cambio
- Qué: El usuario confirmó que Gemini ya responde (primer intento "alta demanda", segundo bien; el modelo se identificó como Gemini 4 Hydrogen). Cambios: (1) el proveedor también pasa al siguiente modelo ante saturación (503/"high demand") sin recordarlo, solo la cuota diaria se recuerda; (2) el nombre del proveedor muestra el último modelo que respondió; (3) nuevo ajuste "Enviar automáticamente lo que digo" (activado por defecto) que envía el mensaje dictado al llegar la transcripción final, sin pulsar enviar. Test nuevo de saturación.
- Dónde: `GeminiFirebaseProvider.kt`, `ChatViewModel.kt`, `SettingsScreen.kt`, `SettingsViewModel.kt`, `UserPreferencesRepository.kt`, `NeptunoModels.kt`, `strings.xml`, `GeminiModelFallbackTest.kt`.
- Estado: sin verificar; compilación y tests se comprueban en el siguiente run de CI. Que el modelo diga "Gemini 4 Hydrogen" no es prueba por sí sola (los modelos pueden equivocarse al nombrarse); coincide con la cadena de modelos.
- Para las demás IA: pendiente evaluar con el usuario: voz más natural (voces neuronales de Google TTS ya instaladas vs. TTS en la nube), conversación continua tipo Gemini Live (Live API de Firebase AI Logic) y activación por "Hey Neptuno" en segundo plano (servicio en primer plano con micrófono + detector de palabra clave, p. ej. Porcupine u openWakeWord/sherpa-onnx). Cada una consume cuota o batería; decidir antes de implementar.

### 2026-11-23 04:10 · Claude · cambio
- Qué: Run #12 (70322c1): la APK compila y `apk-latest` ya incluye el envío automático por voz, el cambio de modelo ante saturación y el modelo visible. Un test existente (STT) fallaba porque ahora la transcripción final se envía sola y vacía el borrador; ajustado para probar con el envío automático desactivado y añadido un test del envío automático activado.
- Dónde: `NeptunoVoiceAndChatTest.kt`.
- Estado: APK verificada en CI; tests corregidos sin verificar hasta el siguiente run. Sin probar en el teléfono.
- Para las demás IA: el ajuste se llama "Enviar automáticamente lo que digo" (activado por defecto).

### 2026-11-23 03:40 · Claude · nota
- Qué: Registro integral pedido por el usuario. HECHO (commits en `trabajo`): 5a2a711 errores de Gemini y logs; c36346b/c2195ce `gradlew` ejecutable; 32aef37, 4a48498, 72ea943 CI con APK, llave fija y logs en `ci-logs`; 853c687 compilar sin Firebase; f9cef3f UNANZE; d5928b8 y 6677ea3 `google-services.json` con SHA-1 de CI; 3d409e2 Java 21; f0ef8c7 verificación de Firebase en la APK; 52f3f6b y 3c08a80 cambio de modelo ante 429; 70322c1 y 6d759a3 autoenvío por voz, cambio ante saturación y tests. SE HARÁ: plan de la sección 7 (voz natural, conversación continua, "Hey Neptuno", ahorro de cuota, sincronización, Room, dependencias). Dato nuevo del usuario: sus teléfonos son Redmi 10C y Redmi 9C de 4 GB de RAM.
- Dónde: `UNANZE.md` secciones 2, 6 y 7; historial de `trabajo`.
- Estado: verificado contra el historial de git; los resultados de CI citados salen de `ci-logs` (run #12). El comportamiento en teléfono se basa en lo que reporta el usuario, sin comprobación independiente.
- Para las demás IA: este registro sustituye al estado anterior de las secciones 6 y 7. Antes de proponer cambios de voz o de servicio en segundo plano, tener en cuenta los dispositivos (4 GB de RAM) y que el orden del plan aún no está confirmado.


### 2026-11-23 03:51 · ChatGPT · hallazgo
- Qué: El cambio de sincronización incremental quedó en el commit `5eda199`, pero el run #14 falló al compilar. La causa inmediata identificada es que `NeptunoFirestoreRepository` terminó con dos `companion object`; eso provoca la redeclaración y hace que aparezcan referencias no resueltas a helpers y constantes del companion original. Los tests unitarios sí pasaron antes de la compilación.
- Dónde: `NeptunoFirestoreRepository.kt` líneas 100 y 604; `ci-logs/ultimo-log.txt`, run #14.
- Estado: fallo de compilación verificado en CI. La corrección todavía no está aplicada ni verificada.
- Para las demás IA: ChatGPT está corrigiendo el companion duplicado y revisará el siguiente resultado de CI. No dar por válida la sincronización incremental ni instalar la APK de este commit.

### 2026-11-23 03:52 · ChatGPT · corrección
- Qué: Corregida la causa inmediata del fallo de compilación del run #14: se eliminó el `companion object` adicional y se trasladaron los dos límites de lote al `companion object` que ya existía, conservando sus helpers y constantes originales.
- Dónde: `NeptunoFirestoreRepository.kt`; commit `bcc9fccb0b4ba8f21771ee5776bfc3dbf19f007f`.
- Estado: corrección subida; run #15 disparado y todavía en curso. Aún sin confirmar compilación ni APK.
- Para las demás IA: comprobar run #15 antes de continuar. El run #14 falló después de pasar los tests, durante `compileDebugKotlin`; no instalar ni distribuir la APK del commit `5eda199`.

### 2026-11-23 15:10 · Claude · auditoría
- Qué: Auditoría pedida por el usuario ("ChatGPT hizo algo y no compila"). El fallo de compilación real fue el run #14 (commit 5eda199: dos `companion object` en `NeptunoFirestoreRepository`); ChatGPT lo corrigió en bcc9fcc y el run #15 SÍ compila: APK generada y `apk-latest` apunta a bcc9fcc. Lo que sigue en rojo en el run #15 son los tests, por HTTP 429 de Maven Central al bajar `nativeruntime-dist-compat` (error externo, ya visto antes, no del código). Revisé a mano el cambio de sincronización incremental (5eda199 + bcc9fcc): migración Room 1→2 correcta (3 columnas `cloudSyncedAtMillis` nullable, coinciden con las entidades); consultas de pendientes y marcas coherentes; escritura por lotes (400 mensajes + 1 update ≤ 500) compatible con `firestore.rules` (`list` permitido, `messageCount` solo validado como entero ≤ 50000, update restringido a title/messageCount/updatedAt). Sin hallazgos de lógica en la lectura. Para el 429: el caché de Gradle estaba en solo lectura en la rama `trabajo`; ahora `cache-read-only: false`, caché propio de Robolectric y 4 reintentos de 45 s.
- Dónde: `.github/workflows/build-apk.yml`; revisión de `NeptunoDatabase.kt`, `NeptunoDaos.kt`, `NeptunoFirestoreRepository.kt`, `CloudSessionViewModel.kt`, `firestore.rules`.
- Estado: compilación verificada en CI (run #15). Que los tests pasen con el código nuevo NO está verificado (el 429 impidió ejecutarlos); la sincronización incremental no se ha probado en el teléfono. El arreglo del caché se comprobará en el siguiente run.
- Para las demás IA: la APK `apk-latest` (bcc9fcc) es instalable en cuanto a compilación. Antes de dar por buena la sincronización incremental, esperar a ver los tests en verde. Si tocan archivos de código, avisar en UNANZE para no pisarse con el mismo commit.

### 2026-11-23 15:25 · Claude · cambio
- Qué: Run #16 (2ab98c0): tests y APK en verde (BUILD SUCCESSFUL en ambos pasos, sin tests fallidos). Con eso quedan verificados en CI la compilación y los tests del cambio de sincronización incremental de ChatGPT (5eda199 + bcc9fcc). El arreglo del caché resolvió el 429 de Maven. `apk-latest` apunta a 2ab98c0.
- Dónde: `ci-logs` (run #16), release `apk-latest`.
- Estado: verificado en CI. La sincronización incremental sigue sin probarse en el teléfono (Room pasa de versión 1 a 2 al instalar encima: la migración está escrita pero no se ha ejecutado sobre datos reales).
- Para las demás IA: pendiente que el usuario instale `apk-latest` encima de la anterior y confirme que la app abre, conserva sus conversaciones y sincroniza.

### 2026-11-23 16:21 · Perplexity · hallazgo
- Qué: El usuario reportó un error transitorio de Gemini durante una respuesta, posiblemente al pedir un texto largo: "Error de Gemini (Firebase AI): Something unexpected happened. Expected end of the object ')', but had 'EOF' instead at path: $ JSON input: {". El mensaje sugiere una respuesta JSON/streaming incompleta o malformada, no un 429 ni saturación. Al reintentar, la conversación siguió normal.
- Dónde: reporte del usuario; posible relación con el manejo de streaming/JSON en `GeminiFirebaseProvider.kt`.
- Estado: sin verificar; no se revisó el código ni se reprodujo el error.
- Para las demás IA: evaluar si los errores de parseo/JSON incompleto deberían reintentarse automáticamente y registrar el modelo mostrado cuando ocurra; no clasificarlo como cuota agotada sin evidencia.

### 2026-11-23 16:24 · ChatGPT · auditoría
- Qué: Investigación inicial para el asistente manos libres y la voz de NEPTUNO. En el código actual existe el contrato `WakeWordEngine`, pero `NeptunoAppContainer` instancia `PendingWakeWordEngine`; por tanto, la detección de palabra de activación todavía no funciona. El manifiesto solo declara `RECORD_AUDIO` y no registra un servicio de micrófono en primer plano. La voz actual usa Android `TextToSpeech`; SettingsViewModel ya consulta las voces instaladas y permite elegirlas. Repositorios candidatos: `msnilsen/openwakeword-android`, `Re-MENTIA/openwakeword-android-kt`, `k2-fsa/sherpa-onnx`, `piper-tts-apps/piper-kotlin`, `devxjitin/ultron-android` y `codeninjavik/voxpilot`. Speechma ofrece generación web y descargas de audio, pero no se ha confirmado un SDK/API para síntesis dinámica dentro de NEPTUNO. Piper-Kotlin es GPL-3.0-or-later; revisar compatibilidad de licencia antes de integrarlo. Los modelos de openWakeWord tienen licencia CC BY-NC-SA 4.0, distinta a la del código Apache-2.0.
- Dónde: `WakeWordContracts.kt`, `NeptunoAppContainer.kt`, `AndroidManifest.xml`, `SettingsScreen.kt`, `SettingsViewModel.kt`, `AndroidTextToSpeechEngine.kt`; repos públicos enlazados en la revisión. Documentación oficial Android sobre foreground services, `VoiceInteractionService`, `ROLE_ASSISTANT` y MediaProjection.
- Estado: verificado por lectura de los archivos y documentación consultada; no se modificó código ni se ejecutó una compilación. Las opciones de escucha continua, control de pantalla y TTS neuronal siguen sin integrar ni probar en los Redmi.
- Para las demás IA: separar detección local de la invocación de Gemini: el detector debe procesar audio en el dispositivo y no generar solicitudes/tokens por espera. Para Android 14+ el servicio de micrófono debe iniciarse desde una actividad visible con permisos y tipo de servicio correctos; no se puede prometer persistencia absoluta ante el sistema/MIUI. Evaluar `VoiceInteractionService` + solicitud voluntaria de `ROLE_ASSISTANT` como vía de integración de asistente del sistema; no confundirlo con una API reutilizable de Google Voice Match. Para lectura/clicks de pantalla, considerar AccessibilityService con activación manual explícita y límites de privacidad; MediaProjection exige consentimiento del usuario para cada sesión. Preferir primero Intents/APIs oficiales para abrir apps y acciones permitidas. Investigar rendimiento/licencias de openWakeWord, sherpa-onnx y TTS Piper/Kokoro antes de elegir; no copiar permisos ni código de Ultron/VoxPilot sin auditoría.


### 2026-11-23 16:43 · ChatGPT · auditoría
- Qué: Retomada la implementación de activación local tras la aprobación del usuario. Confirmado que `WakeWordEngine` sigue siendo solo un contrato, `NeptunoAppContainer` usa `PendingWakeWordEngine`, el manifiesto no declara un servicio de micrófono en primer plano y `SettingsScreen` no contiene controles de activación. No hay referencias funcionales adicionales al motor ni modelos ONNX de activación incorporados. El CI #16 (commit `2ab98c0`) terminó con `success`, incluyendo tests y APK según el run.
- Dónde: `WakeWordContracts.kt` (SHA `75c5669`), `NeptunoAppContainer.kt` (SHA `b134d6b`), `AndroidManifest.xml` (SHA `949c961`), `SettingsScreen.kt` (SHA `accfea0`), `app/build.gradle.kts`, `gradle/libs.versions.toml`, CI run #16.
- Estado: auditoría de lectura verificada; no se modificó código ni se ejecutó una compilación nueva. Re-MENTIA/openwakeword-android-kt ofrece integración Kotlin/ONNX Apache-2.0 y modelos personalizados, pero openWakeWord declara soporte de entrenamiento actualmente centrado en inglés. Sherpa-onnx ofrece KWS de vocabulario abierto y Android, aunque la página oficial lista modelos KWS preentrenados chinos e inglés, sin confirmar un modelo español adecuado. Por tanto, aún no se selecciona ni integra motor: falta validar reconocimiento fiable de «Hey Neptuno» en español y el coste en los teléfonos objetivo.
- Para las demás IA: no sustituir `PendingWakeWordEngine` ni añadir dependencias/modelos hasta resolver la ruta de reconocimiento español. Evitar usar un modelo «Hey Jarvis» como si reconociera «Hey Neptuno». Mantener el primer prototipo en una sola frase canónica y medir falsas activaciones, omisiones, CPU, RAM y batería antes de añadir alias. No iniciar todavía el servicio persistente: primero debe existir un detector real que funcione localmente.


### 2026-11-23 16:43 · ChatGPT · hallazgo
- Qué: Ampliada la comparación de detección en español. Sherpa-onnx documenta un modelo Moonshine base cuantizado para español con reconocimiento streaming en Android; sin embargo, la lista de modelos de keyword spotting preentrenados sigue mostrando chino/inglés, no un modelo KWS español confirmado. Por eso Moonshine es una alternativa experimental de ASR local + comparación de frase, no una solución de wake word ligera ya validada.
- Dónde: documentación oficial sherpa-onnx, páginas de KWS y modelos Moonshine; búsqueda técnica de esta sesión.
- Estado: disponibilidad documental verificada; compatibilidad de ese modelo con el decodificador KWS y rendimiento en los teléfonos objetivo sin verificar.
- Para las demás IA: antes de añadir una dependencia, comparar el tamaño/RTF/RAM de Moonshine Spanish streaming contra entrenar un clasificador pequeño para «Hey Neptuno». No presentar Moonshine ASR como KWS ya compatible. Mantener el procesamiento de espera local y medir batería/falsos positivos.


### 2026-11-23 16:51 · ChatGPT · implementación experimental
- Qué: Integrado un prototipo de activación por voz local con Sherpa-ONNX/Moonshine español. Descarga el modelo bajo demanda (aprox. 63 MB), segmenta audio PCM localmente por energía, transcribe segmentos con un hilo de inferencia y busca «Hey Neptuno» y variantes de pronunciación. Añadido servicio en primer plano de tipo microphone con notificación persistente y controles Activar/Detener en Ajustes. El servicio muestra aviso al detectar la frase; todavía no inicia el chat ni activa Gemini.
- Dónde: commit `adbceaddfaa9aa4ae6c336a048750f8f201ccca0`; `SherpaMoonshineWakeWordEngine.kt`, `WakeWordForegroundService.kt`, `WakeWordContracts.kt`, `NeptunoAppContainer.kt`, `SettingsScreen.kt`, `NeptunoNavHost.kt`, `AndroidManifest.xml`, `app/build.gradle.kts`, `settings.gradle.kts`.
- Estado: cambios subidos a `trabajo`; CI #17 (run `45263576048`) todavía en ejecución al registrar esta nota. No compilado localmente ni probado en los teléfonos. La frase usa ASR local Moonshine, no un KWS especializado; puede tener latencia y coste de CPU/batería. La primera descarga requiere internet. El modelo español se distribuye bajo Moonshine Community License no comercial; no redistribuir el modelo ni publicar una app comercial sin revisar la licencia.
- Para las demás IA: esperar CI #17 y corregir cualquier fallo antes de integrar con ChatViewModel. Probar latencia, falsos positivos, falsos negativos, RAM, CPU y batería en los teléfonos objetivo. Verificar que el audio no sale del dispositivo. Mantener el servicio iniciado por acción explícita del usuario; no añadir autoarranque desde BOOT_COMPLETED. Próxima fase: al detectar la frase, abrir Chat y conectar el flujo de voz con confirmación/estado visible, sin duplicar captura de micrófono.


### 2026-11-23 16:57 · ChatGPT · corrección de dependencia
- Qué: Revisado el CI #17 (run 45263576048). Los tests unitarios terminaron correctamente, pero la compilación de APK falló porque Gradle no encontraba `com.github.k2-fsa:sherpa-onnx:v2.0.3` en JitPack. La documentación oficial de Sherpa-ONNX para Android Kotlin usa el grupo Maven `com.github.k2-fsa.sherpa-onnx`; se corrigió la coordenada a `com.github.k2-fsa.sherpa-onnx:sherpa-onnx:v2.0.3`.
- Dónde: `app/build.gradle.kts`; commit `d271e89ac8a334ad119f72fc7a5821eec19f0ed6`. CI #18: run `45264517862`.
- Estado: corrección subida a `trabajo`; CI #18 está en curso. La resolución de la dependencia, compilación APK y compatibilidad de las APIs Kotlin aún no están verificadas. El run #17 no generó APK ni release.
- Para las demás IA: esperar el resultado completo del CI #18 y leer `ci-logs/ultimo-log.txt` si falla. No conectar todavía la detección con ChatViewModel ni distribuir APK hasta que compilación y tests queden confirmados. Mantener intactos los demás archivos y el repositorio original.

### 2026-11-23 17:30 · Claude · auditoría
- Qué: Aviso de "no compiló" del usuario. Auditoría: el run #17 (adbcead, prototipo "Hey Neptuno" de ChatGPT) y el run #18 (d271e89, su corrección) fallan igual: Gradle no encuentra la librería `sherpa-onnx` (primero `com.github.k2-fsa:sherpa-onnx:v2.0.3`, luego `com.github.k2-fsa.sherpa-onnx:sherpa-onnx:v2.0.3`); ninguna de las dos direcciones de JitPack existe, y esto bloquea tanto los tests como la APK. Nada más del commit falló (el error es solo de resolución de dependencia). `apk-latest` sigue apuntando al último build bueno (2ab98c0), que NO incluye el prototipo de voz. Corrección intentada: la librería figura en Maven Central como `com.k2fsa.sherpa.onnx:sherpa-onnx-android` (verificado en la ficha de Sonatype, sin poder leer su lista de versiones desde aquí); cambiada la dependencia a `com.k2fsa.sherpa.onnx:sherpa-onnx-android:2.0.3`. Añadido paso de diagnóstico en CI que deja en `ci-logs` las versiones publicadas.
- Dónde: `app/build.gradle.kts`, `.github/workflows/build-apk.yml`.
- Estado: SIN VERIFICAR. La versión 2.0.3 en Maven Central es una suposición (viene del número que usó ChatGPT); si no existe, el log de CI mostrará las versiones reales y se ajusta. Tampoco está comprobado que la API Kotlin (`OfflineMoonshineModelConfig`, etc.) coincida con esa versión: pueden aparecer errores de compilación en `SherpaMoonshineWakeWordEngine.kt`.
- Para las demás IA: ChatGPT, no volver a cambiar la coordenada de Sherpa mientras corre este run; leer `ci-logs` (sección DIAGNÓSTICO SHERPA-ONNX) antes de tocar la dependencia. Nadie debe dar el prototipo de voz por funcionando: ni compilado ni probado en teléfono.

### 2026-11-23 18:00 · Claude · cambio
- Qué: Run #19 (4e1fa39): la dirección de Maven Central también falla; el diagnóstico de CI confirma que `com.k2fsa.sherpa.onnx:sherpa-onnx-android` devuelve 404 en repo.maven.apache.org. Son ya tres coordenadas probadas sin éxito (JitPack `com.github.k2-fsa:sherpa-onnx:v2.0.3`, JitPack `com.github.k2-fsa.sherpa-onnx:sherpa-onnx:v2.0.3` y Maven Central), así que dejo de adivinar. Para que el proyecto compile y se pueda seguir probando lo demás, aislé el prototipo SIN borrarlo: el archivo pasó a `SherpaMoonshineWakeWordEngine.kt.disabled`, `NeptunoAppContainer` vuelve a usar `PendingWakeWordEngine` (el motor de reserva que ya existía) y la línea de la dependencia quedó comentada. El resto del trabajo de ChatGPT (servicio en primer plano, controles en Ajustes, estados INITIALIZING/ERROR, frase "Hey Neptuno") sigue en el código; por ahora usa el motor de reserva, que no detecta nada.
- Dónde: `app/build.gradle.kts`, `NeptunoAppContainer.kt`, `voice/wake/SherpaMoonshineWakeWordEngine.kt.disabled`.
- Estado: sin verificar hasta el siguiente run de CI. La activación por "Hey Neptuno" NO funciona en esta versión.
- Para las demás IA: para reactivar el prototipo hace falta primero una fuente VERIFICADA del AAR de Sherpa-ONNX (por ejemplo, descargarlo en el CI desde los releases oficiales de k2-fsa/sherpa-onnx y usarlo como archivo local en `app/libs`, comprobando antes el nombre real del archivo y su licencia), y luego renombrar el `.disabled` a `.kt`, restaurar la línea en `NeptunoAppContainer` y la dependencia. No volver a probar coordenadas de Maven por intuición: comprobar primero que existen.

### 2026-11-23 18:25 · Claude · cambio
- Qué: Resultado final. Run #20 (9cea6f3): compila y la APK se publicó; un test falló (`NeptunoOfflineFirstSyncTest`, escenario J, "recuperación después de timeout"). Run #21 (048e0c6, mismo código): tests y APK en verde, así que el test es intermitente (usa reloj real con un timeout de 150 ms y espera de 4 s; con CI lenta a veces no alcanza). No se tocó el test ni el código de sincronización. `apk-latest` apunta a 048e0c6. Ojo: el paso de tests tiene `continue-on-error`, por lo que un test fallido no pinta el run en rojo; hay que mirar `ci-logs` (sección TESTS) para saberlo.
- Dónde: `ci-logs` (runs #20 y #21), release `apk-latest`.
- Estado: compilación y tests verificados en CI (run #21). La APK incluye sincronización incremental, cambio de modelo Gemini y autoenvío por voz; NO incluye detección de "Hey Neptuno" (motor Sherpa desactivado). Nada de esto está probado en el teléfono.
- Para las demás IA: el escenario J puede fallar de forma aleatoria; si vuelve a pasar con código distinto, mejorar el test (esperas por eventos en vez de tiempos) en lugar de reintentar a ciegas. Prioridad abierta: conseguir una fuente verificada del AAR de Sherpa-ONNX antes de reactivar el prototipo de voz.

### 2026-11-23 19:00 · Claude · decisión
- Qué: Alternativas al motor Sherpa para "Hey Neptuno", tras buscar en GitHub y documentación. (A) **Vosk** (Apache 2.0, modelo pequeño español `vosk-model-small-es-0.42`, 39 MB, Apache 2.0; los modelos pequeños admiten vocabulario dinámico, lo que permite reconocer solo unas pocas frases y descartar el resto): es la opción propuesta, porque sirve sin entrenar nada y es la técnica que usa el proyecto abierto OpenClaw Assistant. (B) **Porcupine** (Picovoice): más preciso y ligero, soporta español, pero exige AccessKey de cuenta y entrenar la frase "Hey Neptuno" en su consola; queda como mejora posterior. (C) **openWakeWord para Kotlin** (`xyz.rementia:openwakeword:0.1.3`, Apache 2.0, 18 estrellas): exige entrenar un modelo propio y el proyecto es pequeño; descartado por ahora. NO se ha comprobado que ninguna coordenada exista en Maven Central; por eso este commit añade al CI el diagnóstico de dependencias y se escribió la guía 7B para ChatGPT.
- Dónde: `UNANZE.md` (sección 7B), `.github/workflows/build-apk.yml` (paso "Diagnóstico de dependencias").
- Estado: sin verificar. Siguiente paso: leer el diagnóstico en `ci-logs` y, si `com.alphacephei:vosk-android` existe, implementar el motor con Vosk.
- Para las demás IA: ChatGPT, leer la sección 7B antes de tocar código. No empezar el motor Vosk en paralelo: lo hace Claude en el siguiente commit.


### 2026-11-23 20:00 · Claude · Motor de activación Vosk (reemplaza a Sherpa)
- Hecho: `VoskWakeWordEngine.kt` (Vosk 0.3.75 + JNA 5.19.1, modelo vosk-model-small-es-0.42 descargado la primera vez, gramática "hey/oye/hola neptuno"), `VoskPhraseMatcherTest` (JUnit puro). Run #23 (c52e104) verde con tests.
- Siguiente commit: conecta el motor en `NeptunoAppContainer` (antes `PendingWakeWordEngine`).
- Estado: **sin verificar en teléfono**. Riesgos: palabra "neptuno" fuera del léxico de Vosk, Android 14 (no se puede abrir la app desde segundo plano), MIUI mata servicios en segundo plano, batería.
- Al detectar, el servicio solo muestra notificación; abrir el chat y auto-enviar voz es la siguiente fase.

### 2026-11-23 18:30 · Claude · "Hey Neptuno" conectado al chat + detector estricto (run #25, 000526c, verde)
- Problema reportado por el usuario: el detector saltaba con cualquier sonido y solo mostraba "Frase detectada…".
- Detector: solo resultados finales (no parciales), gramática solo "hey/ey/oye/hola neptuno" (sin palabra suelta), exige saludo+palabra, confianza por palabra (0.71 con sensibilidad 0.6) y frase ≤ 2.2 s.
- Flujo nuevo: detección → servicio libera el micrófono (`engine.stop()`) → `WakeActivationBus.request()` → intenta abrir MainActivity (directo + notificación full-screen) → ChatViewModel `startVoiceFromWake()` dicta y envía solo → `voiceTurnEnded` → servicio reanuda la escucha (máx. 45 s de espera).
- Manifest: USE_FULL_SCREEN_INTENT; MainActivity singleTop/showWhenLocked/turnScreenOn.
- Estado: **sin verificar en teléfono**. Riesgos: MIUI (permiso "ventanas emergentes en segundo plano" + sin restricción de batería), Android 14, falsos negativos si "neptuno" está fuera del léxico.
- Pendiente de lo pedido a ChatGPT: voz natural (TTS), conversación continua tipo Live (reabrir micrófono tras la respuesta), reducir uso de cuota de Gemini.

### 2026-11-23 18:10 · Claude · Relevo a ChatGPT
- El usuario se ausenta y pide que ChatGPT sea el suplente temporal. Añadida la sección 7C (reglas, código dejado, cola de trabajo) y actualizados el estado vivo (sección 6) y el plan (sección 7, punto 3).
- Estado entregado: rama `trabajo` en bd89b33 (docs) sobre 000526c (último build verde, run #25).


### 2026-11-23 18:28 · ChatGPT · auditoría
- Qué: Retomada la revisión del audio y añadida como requisito del usuario la compatibilidad manos libres con micrófonos Bluetooth: audífonos, bocinas con micrófono y micrófonos externos. NEPTUNO debe identificar el dispositivo y sus capacidades reales, elegir entrada y salida disponibles, reaccionar a conexiones/desconexiones y permitir la activación local «Hola/Hey/Oye Neptuno» desde el micrófono Bluetooth cuando Android lo exponga. La escucha local no debe enviar audio a Gemini ni consumir solicitudes mientras espera.
- Dónde: BluetoothDeviceController.kt, BluetoothViewModel.kt, AudioEngineContracts.kt, AndroidAudioRouteManager, AndroidSpeechToTextEngine.kt, AndroidPcmAudioEngines.kt, VoskWakeWordEngine.kt, WakeWordForegroundService.kt, NeptunoAppContainer.kt, NeptunoNavHost.kt, AndroidManifest.xml.
- Estado: auditoría de lectura verificada; no se modificó código ni se ejecutó un build nuevo. El proyecto ya enumera dispositivos vinculados y rutas disponibles, pero PendingAudioRoutingController no aplica rutas activas; las preferencias de entrada/salida solo se guardan. El detector Vosk y la captura PCM crean AudioRecord sin seleccionar explícitamente un dispositivo Bluetooth, por lo que el uso del micrófono externo no está garantizado. El TTS/STT del sistema tampoco tiene todavía una política visible de selección/seguimiento de ruta. CI #25 (commit 000526c) sigue siendo el último build documentado como verde; el branch HEAD leído es a4e6706 (documentación sobre ese código). Ninguna de estas rutas se ha probado físicamente.
- Para las demás IA: no asumir que A2DP admite micrófono; comprobar perfiles/rutas que Android reporte (SCO/HFP y BLE según versión/dispositivo), selección de dispositivo de comunicación con APIs compatibles y cambios de ruta en tiempo real. Mantener fallback al micrófono/altavoz del teléfono. Antes de cambiar código, esperar la confirmación del usuario sobre la prueba de «Hey Neptuno» en el teléfono, según la cola de 7C; diseñar el soporte Bluetooth como una extensión separada y gradual, sin sustituir el detector Vosk ni cambiar firmas existentes.


### 2026-11-23 18:35 · ChatGPT · hallazgo
- Qué: El usuario probó el detector en teléfono: «Hey Neptuno» y «Oye Neptuno» activan; «Hola Neptuno» no. Además confirmó que con pantalla bloqueada se abre MainActivity y entra al chat, comportamiento que considera incorrecto. La gramática Vosk ya incluye «hola», así que la causa exacta del falso negativo aún no está determinada. El servicio ejecuta bringChatToFront() (startActivity + notificación full-screen) y el manifest declara showWhenLocked=true y turnScreenOn=true; se confirma el origen del comportamiento de pantalla bloqueada.
- Dónde: VoskWakeWordEngine.kt (VoskPhraseMatcher.GREETINGS y grammarJson), WakeWordForegroundService.kt (handleDetection/bringChatToFront), AndroidManifest.xml (MainActivity showWhenLocked/turnScreenOn), UNANZE.md sección 6 y cola 7C.
- Estado: hallazgos verificados por lectura del código y prueba física reportada por el usuario. No se modificó código ni se ejecutó CI. El último log disponible sigue siendo run #25, commit 000526c; no contiene estos cambios (solo se actualizó documentación en commit posterior).
- Para las demás IA: corregir de forma incremental. Prioridad de seguridad: retirar apertura automática y full-screen al detectar; no permitir mostrar chat en pantalla bloqueada ni usar showWhenLocked/turnScreenOn; diseñar un flujo de voz de fondo con consentimiento explícito y notificación persistente, y pedir desbloqueo antes de mostrar información privada. Para «Hola», añadir tests que comprueben la frase/gramática (ya hay GREETINGS=listOf("hey","ey","oye","hola")); no bajar umbrales globales sin evidencia, instrumentar diagnóstico local no sensible de texto reconocido/confianza/razón de descarte y comprobar pronunciación/ruido en teléfono. Mantener compatibilidad Bluetooth como línea de trabajo separada.


### 2026-11-23 18:42 · ChatGPT · cambio
- Qué: Retirada la apertura automática de MainActivity al detectar wake word y eliminada la notificación de pantalla completa. La detección ahora actualiza la notificación persistente normal; el usuario debe tocarla para abrir la app. Retirados del manifest `USE_FULL_SCREEN_INTENT`, `showWhenLocked` y `turnScreenOn`, para que Android aplique el bloqueo normal antes de mostrar contenido. Se amplió a 45 s la validez de la petición pendiente para permitir desbloquear y tocar la notificación.
- Dónde: `WakeWordForegroundService.kt` (commit `e2d3d09`), `AndroidManifest.xml` (commit `c035476`), `NeptunoNavHost.kt` (commit `7a7567e`), rama `trabajo`.
- Estado: cambios confirmados en GitHub. CI del commit final `7a7567e` en curso (run `45282172500`); no compilado ni probado todavía en teléfono. Esta fase no implementa aún conversación totalmente manos libres en segundo plano: STT/Gemini/TTS siguen conectados a ChatViewModel/UI.
- Para las demás IA: no restaurar la apertura automática ni los flags de pantalla bloqueada. Esperar el CI final y la prueba física del usuario. La siguiente fase debe diseñar un servicio de sesión de voz que reutilice repositorios/proveedor/motores existentes sin duplicar solicitudes ni capturas; conservar la autenticación y notificación persistente. «Hola Neptuno» y Bluetooth siguen pendientes por separado.

### 2026-11-23 21:42 · ChatGPT · auditoría
- Qué: Se revisó la publicación de la APK y el problema reportado de instalación/actualización. Se encontró que el release `apk-latest` había sido publicado como `prerelease=true`, por lo que `/releases/latest` no lo reconocía como última versión estable. Se quitó `--prerelease` del comando de publicación. CI #42 terminó correctamente; el release quedó como `prerelease=false`, `draft=false`, con el asset `Neptuno-debug.apk` (125.649.933 bytes), apuntando al commit `791b801`. Se comparó el blob de `ci/ci-debug.jks` del run #29 con la versión usada posteriormente y era idéntico; no se pudo ejecutar `apksigner` para confirmar la huella del certificado. El usuario ya había reportado que la APK #41 no se podía instalar ni actualizar, incluso después de desinstalar; Android solo mostró «No se pudo instalar la aplicación». No se obtuvo el código real del fallo ni se identificó su causa.
- Dónde: workflow `.github/workflows/build-apk.yml`; release `apk-latest`; CI #42 (run `45307044195`); commits `791b801` y `c317752`; archivo `ci/ci-debug.jks`.
- Estado: corrección de publicación y metadatos del release verificados en GitHub/CI. **Problema de instalación sin resolver.** Igualdad del archivo JKS verificada por blob, pero identidad del certificado dentro de los APK y correspondencia exacta con la APK instalada no verificadas.
- Para las demás IA: no confundir «release estable» con «APK instalable». Investigar primero el error concreto del Package Installer, el certificado real de las APK, package name, versionCode/minSdk y compatibilidad del dispositivo. No cambiar firma, `applicationId`, `versionCode`, ABI filters, manifest o Gradle sin evidencia. No pedir al usuario que desinstale o borre datos como prueba inicial.

### 2026-11-23 21:52 · ChatGPT · restauración controlada
- Qué: A petición del usuario se revisó el historial y se eligió como punto de retorno el código del CI #29 (23 nov., commit `8fefb3846810dd885c066e519a43720a5474342f`), el último checkpoint de código que coincide con el período en que el usuario confirmó que la activación/escucha funcionaban con pantalla bloqueada. Se identificaron 21 commits posteriores en la rama activa; no se atribuyó el fallo a ninguno en particular. Antes de mover la rama se creó `respaldo-antes-restauracion-20261124` apuntando al HEAD anterior `90dfedc5d30c1951b5adc76551c75d13a8087e8f`, conservando el trabajo posterior. La rama `trabajo` se restauró al checkpoint #29 y se mantuvo únicamente la corrección de publicación estable (quitar `--prerelease`), creando el commit `769368ffa3c7d92e7ebe4206351c6ea6b7001551`.
- Dónde: rama `trabajo`, commit `769368f`; checkpoint `8fefb38`; respaldo `respaldo-antes-restauracion-20261124`; CI #43, run `45308233683`.
- Estado: restauración de Git verificada. El código de la app en `769368f` es el del checkpoint #29; la diferencia añadida respecto al checkpoint es la corrección del comando de publicación. No se borraron los cambios posteriores: están en la rama de respaldo.
- Para las demás IA: el rollback es un experimento de aislamiento, no prueba que los commits posteriores fueran la causa. Mantener intacta la rama de respaldo. No mezclar nuevas fases funcionales mientras se diagnostica instalación.

### 2026-11-23 21:58 · ChatGPT · auditoría de CI y release
- Qué: CI #43 (run `45308233683`, commit `769368f`) completó correctamente los pasos relevantes: tests unitarios, compilación APK debug, verificación de Firebase, subida del artefacto y publicación del release. GitHub publicó `Neptuno-debug.apk` como release estable `apk-latest` (`prerelease=false`, `draft=false`), con `target_commitish=769368f`, tamaño 125.599.729 bytes y SHA-256 `17fea917588204f982376fd250349ef183f9733cd5537d0e55397fdf36720d62`. Al auditar, los pasos de publicación y logs ya estaban completados; el job seguía en tareas finales de limpieza. Después, el usuario probó la APK #43 y confirmó que tampoco funciona para instalar.
- Dónde: [CI #43](https://github.com/julsjailer/Nept-Uno-working/actions/runs/45308233683); [release apk-latest](https://github.com/julsjailer/Nept-Uno-working/releases/tag/apk-latest); commit `769368f`.
- Estado: compilación, tests (según el paso de CI), artefacto y publicación verificados en GitHub. **Instalación en Android fallida según prueba del usuario.** No equivale a verificación de instalación ni ejecución en dispositivo.
- Para las demás IA: tratar #43 como otra reproducción del fallo, incluso sobre el código del checkpoint #29. El rollback no lo solucionó. Antes de tocar configuración, comparar técnicamente APK #29 y #43 y obtener el motivo concreto del Package Installer. La APK #43 mide 125.599.729 bytes; SHA-256 publicado por GitHub: `17fea917588204f982376fd250349ef183f9733cd5537d0e55397fdf36720d62`.

### 2026-11-23 22:02 · ChatGPT · relevo / bloqueo
- Qué: El usuario confirma explícitamente: «No funciona tampoco» respecto a la APK #43 y solicita que todo el historial reciente quede registrado en `UNANZE.md`; cede a Claude la resolución del problema. Se entrega el diagnóstico a Claude sin proponer otra modificación especulativa.
- Dónde: `UNANZE.md`; rama `trabajo`; CI #43 / release `apk-latest`.
- Estado: fallo de instalación confirmado por el usuario; causa técnica aún desconocida. Este registro y el relevo quedan anotados en este commit.
- Para Claude: asumir el diagnóstico desde cero, leyendo esta bitácora y verificando el estado actual de `trabajo` y `ci-logs`. **No continuar con nuevas fases ni cambiar parámetros de empaquetado por intuición.** Primero obtener/inspeccionar las APK #29 y #43: comparar manifiesto final (`applicationId`/package, `versionCode`, `versionName`, `minSdk`, `targetSdk`, splits/ABI y permisos), firma y huella del certificado; confirmar si ambas APK se firmaron realmente con la misma llave y si la APK instalada corresponde a alguna de ellas. Examinar el APK como ZIP y sus metadatos con herramientas Android apropiadas (`apkanalyzer`, `aapt`, `apksigner`) y comparar los resultados, no inferir identidad de firma por el blob del archivo keystore solamente. Obtener el error exacto de Android Package Installer/PackageManager (código y causa, por ejemplo desde el registro del sistema o un informe de error); el mensaje genérico «No se pudo instalar la aplicación» no basta para identificarlo. Comprobar que el enlace entrega el binario correcto y que no se está intentando instalar un artefacto incompleto. El usuario trabaja solo desde Android: cualquier recolección adicional debe ser viable desde el teléfono y explicar claramente qué dato/screenshot se necesita. No pedir desinstalar otra vez, borrar datos ni cambiar `applicationId`, `versionCode`, `minSdk`, ABI, firma/keystore, manifest o Gradle hasta que la evidencia identifique la causa. Preservar `respaldo-antes-restauracion-20261124`. Informar al usuario de lo verificado y lo que siga sin verificar.
### 2026-11-23 22:03 · ChatGPT · actualización de CI
- Qué: Consulta final posterior a la entrada anterior: GitHub ya marca el run #43 como `completed` con conclusión `success`; no quedó solo en limpieza pendiente. Esto confirma el resultado completo del workflow, pero no cambia el resultado de la prueba real: el usuario informó que la APK #43 no se instala.
- Dónde: run #43 `45308233683`, commit `769368ffa3c7d92e7ebe4206351c6ea6b7001551`.
- Estado: workflow completo y exitoso en CI; instalación Android fallida según el usuario.
- Para las demás IA: conservar la distinción entre CI exitoso e instalación en dispositivo no exitosa.
### 2026-11-23 22:30 · Claude · Diagnóstico de instalación con evidencia (run #44, f4fbbfb)
- Método: el CI ahora analiza la APK (apksigner, aapt2, zipalign, unzip) y la instala en un emulador Android 11 x86_64 (`ci/probar-instalacion.sh`), todo visible en `ci-logs` (secciones ANÁLISIS DE LA APK e INSTALACIÓN EN EMULADOR).
- Hechos medidos en la APK del commit 769368f (idéntica a la #43: SHA-256 `17fea917…720d62`, 125.599.729 bytes):
  - Paquete `com.aistudio.neptuno.k7v9xm`, versionCode 1, minSdk 29, targetSdk 36, debuggable.
  - Firma: solo esquema v2 válido (v1 y v3 no firmados; normal con minSdk ≥ 24). Certificado CN=Android Debug, SHA-256 `c78766a5…476f70`, coincide con `ci/ci-debug.jks`.
  - ZIP íntegro, zipalign correcto (4 bytes y 16 KB). 759 archivos, 21 archivos dex (el mayor 42 MB), libvosk.so ×4 ABIs; contiene también ABIs sobrantes (armeabi, mips, mips64, x86, x86_64) que vienen de JNA/Vosk.
  - **Emulador Android 11: instalación limpia = Success, reinstalación encima (actualización) = Success, la app arranca (proceso vivo).**
- Conclusión: la APK NO está corrupta ni mal firmada ni mal empaquetada; instala y actualiza en Android 11. El rechazo ocurre en el teléfono del usuario (MIUI/almacenamiento/descarga incompleta/instalador) y no en el código: por eso el rollback a #29 no lo arregló. Los cambios de ChatGPT a versionCode/ABIs tampoco eran la causa (no hay nada que arreglar ahí).
- Sospechas ordenadas (sin probar): (1) espacio libre insuficiente: APK de 125 MB + 21 dex que Android optimiza al instalar necesita varios cientos de MB libres, y MIUI muestra solo "No se pudo instalar"; (2) descarga incompleta (repo privado, 125 MB por móvil): comprobar que el archivo mide 125.599.729 bytes; (3) ajustes de MIUI (Protección de instalación, "Instalar apps desconocidas" para la app que abre el archivo, Play Protect).
- Cambio hecho (no afecta al build normal): se publica además `Neptuno-debug-arm.apk` (solo arm64-v8a y armeabi-v7a, activada con `-PsoloArm`), misma firma y paquete, más pequeña. El release incluye el SHA-256 de la APK completa en las notas.
- Estado: APK validada en emulador; **instalación en Redmi sin verificar**. 
- Para las demás IA: no tocar firma, applicationId, versionCode ni manifest por este problema. Si el usuario sigue sin poder instalar, lo útil es el mensaje exacto del instalador, el espacio libre del teléfono y el tamaño en bytes del archivo descargado, no más cambios de código.

### 2026-11-23 22:45 · Claude · Instalación resuelta + versionado automático de la APK
- Resultado: el usuario confirma que **ahora sí se instaló** (tras la guía de almacenamiento/descarga/ajustes MIUI y/o la APK ligera). Causa: del lado del teléfono, no del código (la APK ya estaba validada en emulador Android 11, ver 22:30). No se confirmó cuál de los archivos instaló; se asume la APK `apk-latest`.
- **Regla nueva (pedida por el usuario): cada build tiene versión propia.** `versionCode` = número de ejecución de GitHub (`GITHUB_RUN_NUMBER`), `versionName` = `1.0.<n> (<commit corto>)`; se ve en Ajustes > Información de la aplicación. Cada build publica `Neptuno-v1.0.<n>.apk` en su release `v1.0.<n>` (se conservan las 25 últimas) además de `apk-latest` (enlace fijo al último, con `Neptuno-debug.apk` y `Neptuno-debug-arm.apk`). Las notas llevan commit, mensaje y SHA-256.
- Para volver a una versión anterior: Android no permite instalar un `versionCode` menor encima de uno mayor; hay dos vías: (a) desinstalar e instalar `v1.0.<n>` vieja (se pierden datos locales), o (b) preferida: crear un commit que revierta el cambio malo; saldrá como versión nueva y se instala encima. **Nunca reutilizar ni bajar versionCode, ni cambiar applicationId o la llave de firma.**
- Funciones de la versión instalada (código = checkpoint #29 + CI/versionado): ver sección 6 abajo. Faltan, por estar solo en la rama `respaldo-antes-restauracion-20261124` (commits de ChatGPT de 20:05 a 20:34, nunca instalados en el teléfono): conversación continua, micrófono Bluetooth (escucha local y dictado), corrección de flujos Gemini truncados. Siguiente paso propuesto: reincorporarlas una a una, cada una con su build y versión.
- Para las demás IA: antes de reincorporar cualquier cosa de la rama de respaldo, leer el diff por partes y verificar CI de cada una; el CI #28 de ese período falló.

### 2026-11-24 00:10 · Claude · Reincorporadas las 3 funciones de la rama de respaldo (runs #47, #48, #49, todos verdes)
- Método: `git cherry-pick` de solo los commits de código de `respaldo-antes-restauracion-20261124` (los de documentación se omitieron; en conflictos de UNANZE.md se conservó esta versión). Un build por función; cada uno compiló, pasó tests e instaló/actualizó en el emulador Android 11.
  1. **v1.0.47 (ac2aec5)**: flujos de Gemini truncados (EOF) se clasifican sin reintento y se conservan los fragmentos recibidos + test.
  2. **v1.0.48 (7bc6123)**: conversación continua (preferencia en Ajustes, señal de consumo del wake, el detector espera durante la conversación, cierre tras respuesta de un solo turno).
  3. **v1.0.49 (9d25b66)**: micrófono Bluetooth (`AndroidBluetoothAudioRouter`; escucha local y dictado por micrófonos Bluetooth compatibles; cambio de entrada al conectar/retirar micrófonos).
- Estado: compila, tests y emulador verdes; **sin probar en el teléfono** (conversación continua, Bluetooth y wake). Si algo falla, volver con un commit de revert de la función concreta (sale como versión nueva).

### 2026-11-24 00:15 · Claude · UNANZE registrar (cierre de la sesión)
- Verificado que la bitácora contiene todo lo hecho hoy: diagnóstico de instalación (22:30), versionado (22:45), reincorporación de funciones (00:10). Actualizado el estado vivo (sección 6).
- Cola actual: (1) que el usuario pruebe la 1.0.49 en el teléfono (conversación continua, Bluetooth, "Hey Neptuno"); (2) voz natural y gratuita; (3) reducir cuota de Gemini; (4) pendientes de la sección 7.

### 2026-11-24 00:25 · Perplexity · investigación
- Qué: Investigación inicial para el futuro modo offline de NEPTUNO. Candidatos prioritarios: MNN-LLM/MNN Chat de Alibaba y LiteRT-LM de Google. MNN Chat ya ofrece una app Android de referencia con descarga y ejecución local de modelos; su catálogo menciona Gemma 3 1B, SmolLM3 3B y modelos Qwen. LiteRT-LM declara soporte para Gemma, Qwen y Llama. MiniMind sigue siendo solo un experimento educativo y requiere un motor de inferencia aparte.
- Dónde: https://github.com/alibaba/MNN/tree/master/apps/Android/MnnLlmChat ; https://github.com/google-ai-edge/LiteRT-LM
- Estado: investigación inicial; sin verificar en Android, sin pruebas en Redmi 10C/9C y sin decisión de integración.
- Para las demás IA: no integrar dependencias ni elegir modelo todavía. Próximo paso propuesto: instalar la APK oficial de MNN Chat en un Redmi y probar un modelo pequeño, idealmente Gemma 3 1B, midiendo RAM, velocidad, español y estabilidad. Verificar licencia, tamaño, formato y compatibilidad ARM64 antes de cualquier cambio en NEPTUNO.
### 2026-11-24 01:30 · Claude · Bluetooth autónomo (parcial), cuota de Gemini y voz natural (runs #50, #51, #52)
- Pedido del usuario: los auriculares Bluetooth con micrófono no se detectaban; NEPTUNO debe reconocerlos y gestionarlo solo. Además voz natural gratuita y menos cuota de Gemini.
- **v1.0.50 (94486a9) Bluetooth:** causa probable: `BLUETOOTH_CONNECT` (Android 12+) solo se pedía dentro de la pantalla Bluetooth, así que sin entrar ahí la app no veía los auriculares. Ahora se pide al abrir la app (`NeptunoNavHost`). Nuevo `AudioRouteNotice`: el chat muestra una línea "Usando el micrófono Bluetooth: X" o "Usando el micrófono del teléfono: <motivo>" (sin permiso, sin auriculares HFP, SCO sin activar, etc.). Si falla en el teléfono, **ese motivo es el dato que hay que leer**. No hecho aún: que la IA pregunte/anuncie por voz al conectar unos auriculares (falta un listener de conexión a nivel de app + mensaje del asistente).
- **v1.0.51 (b4970f1) Cuota:** `LocalQuickReply` responde saludos/gracias/hora/fecha sin llamar a Gemini (solo frases cortas exactas, español; test `LocalQuickReplyTest`); historial a Gemini: 8 mensajes, máx. 1500 caracteres c/u; contador local de solicitudes exitosas por modelo y día (reinicio a medianoche hora del Pacífico, `SharedPrefsGeminiUsageStore`) que se ve en el nombre del proveedor: "Gemini · <modelo> · N/20 hoy".
- **v1.0.52 (f913613) Voz:** `VoiceRanking` elige sola la voz española instalada de mayor calidad (empate: español latino, luego voz de red) en lugar de la primera local; botón "Mejorar la voz del asistente" en Ajustes abre los ajustes de síntesis de voz para descargar voces de Google de alta calidad. Un TTS neuronal propio (Piper/sherpa) queda pendiente: requiere verificar dependencia por CI.
- CI #52: APK compilada y emulador OK; tests 53 con 1 fallo, el intermitente conocido `escenario J` (no relacionado; los tests nuevos pasan). Pendiente arreglarlo.
- Estado: **sin probar en el teléfono**.


### 2026-11-24 10:46 · ChatGPT · auditoría
- Qué: Auditoría de UNANZE contra la rama `trabajo` y el CI actual. La bitácora estaba desactualizada: su última entrada era Claude 01:30 y el estado vivo seguía en 1.0.49. Faltaban los runs #53 y #54. #53 (commit `b8150c7`) añadió al CI la prueba de actualización real desde la versión anterior y el registro de la APK ARM. #54 (commit `f7620c2`) añadió modo híbrido Bluetooth: si el micrófono Bluetooth no entrega audio, STT y «Hey Neptuno» cambian al micrófono del teléfono; se marca el micrófono Bluetooth como fallido y se evita volver a probarlo automáticamente durante 15 minutos. Se añadió `BluetoothMicHealthTest`. El CI #54 terminó `success`: tests y compilación correctos, APK completa y ARM generadas, análisis de APK/Firebase correcto y prueba en emulador Android 11 correcta (instaló 1.0.53, actualizó a 1.0.54, instalación limpia y arranque). APK completa: 125.682.725 bytes, SHA-256 `c7a3e19fca259d8e0cf3b4b0328e2e91473e4671efca9da45ee803813eb4b581`; ARM: 104.180.078 bytes. Metadatos: paquete `com.aistudio.neptuno.k7v9xm`, versionCode 54, versionName `1.0.54 (f7620c2)`, minSdk 29, targetSdk 36. El release estable `apk-latest` ya apunta a este commit y ofrece ambas APK; la etiqueta versionada `v1.0.54` aparece como prerelease.
- Dónde: [CI #53](https://github.com/julsjailer/Nept-Uno-working/actions/runs/45328598301), [CI #54](https://github.com/julsjailer/Nept-Uno-working/actions/runs/45414009697), [commit #53](https://github.com/julsjailer/Nept-Uno-working/commit/b8150c7707a87f98fd111deb181e46856bf42f24), [commit #54](https://github.com/julsjailer/Nept-Uno-working/commit/f7620c242706b350c3b2a0cfe5548c1bfb90dd0b), [release estable apk-latest](https://github.com/julsjailer/Nept-Uno-working/releases/tag/apk-latest), `ci-logs/ultimo-log.txt` (run #54), `app/src/main/java/com/example/neptuno/audio/engine/AndroidBluetoothAudioRouter.kt`, `AndroidSpeechToTextEngine.kt`, `VoskWakeWordEngine.kt` y test `BluetoothMicHealthTest.kt`.
- Estado: verificado en GitHub/CI (run #54, commit `f7620c2`) y en emulador Android 11. **No verificado en Redmi ni en otro teléfono físico.** La bitácora no se había actualizado después de 01:30; esta entrada registra las diferencias sin alterar entradas anteriores ni el código de la app.
- Para las demás IA: actualizar el estado vivo y los pendientes en una revisión documental posterior: la versión vigente es 1.0.54, no 1.0.49; #53 y #54 ya están terminados. Próxima prueba física: conectar auriculares Bluetooth y comprobar tanto dictado como escucha «Hey Neptuno», verificando que el chat indique la ruta elegida y que el fallback al micrófono del teléfono ocurra si Bluetooth no entrega audio. No afirmar que Bluetooth funciona en el teléfono hasta esa prueba.
### 2026-11-24 19:50 · Claude · Bluetooth automático + híbrido (v1.0.56) y registro de #53/#54
- Registro pendiente: #53 (b8150c7) añadió al CI la prueba de actualización real (instala la versión anterior y la nueva encima en emulador Android 11) y registra `Neptuno-debug-arm.apk` (104 MB). #54 (f7620c2): `BluetoothMicHealth` + modo híbrido: si el micrófono Bluetooth no envía audio (STT: sin voz ni nivel de RMS; Vosk: ~2 s de silencio digital) se vuelve solo al micrófono del teléfono y no se reintenta Bluetooth en 15 min; una elección manual del usuario se respeta. Problema reportado por el usuario: con auriculares conectados no escuchaba por ningún lado.
- Esta versión: `BluetoothHeadsetWatcher` detecta al conectar/retirar auriculares (SCO/A2DP/BLE), da una oportunidad nueva al Bluetooth, y NEPTUNO lo dice en el chat y en voz alta (`ChatViewModel.announceAssistant`, sin reabrir el micrófono). Pantalla Bluetooth: tarjeta "Modo automático + manual" con el auricular detectado, motivo de la ruta actual, botón **Probar micrófono** (graba 3 s por la ruta real y dice si llega audio y a qué nivel) y **Volver a automático** (quita la elección manual de entrada/salida). Texto obsoleto de la pantalla actualizado. Tests: `HeadsetMessagesTest`.
- CI: #55 (75ffc88) falló por un import faltante (`first`, error mío); corregido en #56 (ceb416d): compila, APK y emulador OK (instalación y actualización), tests 58 con 1 fallo = el intermitente conocido `escenario J` (sin relación). Estado: **sin probar en teléfono**. Lo útil de reportar tras probar: resultado del botón "Probar micrófono" con los auriculares conectados (dice si el problema es silencio total del micrófono Bluetooth).

### 2026-11-24 21:10 · Claude · Causa probada del 0 % con auriculares Bluetooth (Kouders KD-06) y corrección (v1.0.57)
- Hallazgo del usuario (clave): con el micrófono del teléfono se oye bien; con los auriculares Kouders KD-06 conectados el botón "Probar micrófono" da 0 %. Si en los ajustes Bluetooth del teléfono se **desactiva "llamadas telefónicas"** de ese dispositivo, NEPTUNO escucha por el teléfono y responde por los auriculares (modo híbrido OK); al reactivarlo vuelve el 0 %. Es decir: con HFP/SCO activo, el canal de micrófono Bluetooth no estaba entregando audio.
- Causa en el código (ruta `AudioRecord`, usada por la prueba y por "Hey Neptuno"): (1) en Android 12+ se llamaba a `setCommunicationDevice` **sin** poner `MODE_IN_COMMUNICATION`, así que el canal SCO no llegaba a abrirse; (2) se fijaba el dispositivo sobre un `AudioRecord` creado con `VOICE_RECOGNITION`, fuente que por SCO suele dar silencio (hay que usar `VOICE_COMMUNICATION`); (3) en Android ≤11 se daba por abierto el SCO con `isBluetoothScoOn` en vez de esperar el estado CONNECTED.
- Cambios: `connectBluetoothInput` pone `MODE_IN_COMMUNICATION` y espera a que `communicationDevice` sea el auricular (hasta 3 s); en Android ≤11 espera el broadcast `SCO_AUDIO_STATE_CONNECTED`; nuevo `AndroidBluetoothAudioRouter.openRecorder(...)` crea el `AudioRecord` con `VOICE_COMMUNICATION` cuando la ruta es Bluetooth y con `VOICE_RECOGNITION` en el teléfono; lo usan la prueba de micrófono y el detector Vosk (arranque y refresco). El respaldo al teléfono y `BluetoothMicHealth` siguen igual.
- Estado: sin probar en teléfono. Si tras esta versión sigue en 0 %, es limitación del auricular/ROM (no entrega micrófono HFP a apps de terceros): quedaría usar siempre el micrófono del teléfono con salida por los auriculares (ya funciona).

### 2026-11-24 23:00 · Claude · Micrófono Bluetooth adaptativo + color de la interfaz (v1.0.59; #58 falló por un delegado Kotlin, corregido en #59, verde con tests)
- Pedido del usuario: el micrófono Bluetooth DEBE servir (el teléfono puede estar cargando lejos y el usuario lleva los auriculares/bocina con micrófono); "Hey Neptuno" también debe oírse por Bluetooth como por el teléfono. Además, ajustes para cambiar el color de la interfaz (colores predefinidos y/o croma).
- Bluetooth: `AndroidBluetoothAudioRouter.openRecorder` ahora prueba hasta 4 formas de abrir el canal HFP/SCO: A) `MODE_IN_COMMUNICATION` + `setCommunicationDevice` + `startBluetoothSco`; B) solo `setCommunicationDevice`; C) solo SCO legado; D) micrófono normal con dispositivo preferido. Cada una se valida midiendo ~1 s de audio real (no silencio); se usa la primera que entrega audio y se recuerda por dispositivo (`bt_route`). La ruta usada se muestra en el chat y en la prueba ("ruta A/B/C/D"). Si ninguna entrega audio, el aviso lo dice y se usa el teléfono. Lo usan la prueba de micrófono y Vosk ("Hey Neptuno") por Bluetooth.
- Nota de producto: mientras el micrófono Bluetooth está abierto para escuchar "Hey Neptuno", los auriculares trabajan en calidad de llamada (HFP); es una limitación del perfil Bluetooth, no de la app.
- Color: nueva preferencia `themeAccentArgb` (0 = paleta original "Océano"); Ajustes > Apariencia: 8 colores predefinidos y una barra de croma (tono 0–360°) que genera la paleta completa clara/oscura (`accentColorScheme`).
- Estado: sin probar en teléfono; resultado según run correspondiente. Si el micrófono Bluetooth sigue sin audio, el mensaje del chat indicará las rutas probadas: ese es el dato a pasar.

### 2026-11-24 23:40 · Claude · Desconexión silenciosa del micrófono Bluetooth en la 2.ª consulta (v1.0.60)
- Reporte del usuario: la primera vez el micrófono Bluetooth sí escucha; a la segunda consulta o al rato la app vuelve sola al micrófono del teléfono (el teléfono sigue conectado a los auriculares).
- Causas encontradas: (1) dos componentes (dictado STT y "Hey Neptuno"/Vosk) usaban routers distintos sobre el mismo audio del sistema: al terminar el dictado se apagaba el SCO y se restauraba el modo de audio mientras Vosk seguía capturando → micrófono mudo sin aviso; (2) un turno en que el usuario no hablaba (NO_MATCH/SPEECH_TIMEOUT) o un micrófono Bluetooth con compuerta de ruido (silencio digital) se interpretaba como "micrófono muerto": se marcaba `BluetoothMicHealth` y se bloqueaba el Bluetooth 15 min; Vosk hacía lo mismo tras ~2 s de silencio.
- Cambios: ruta Bluetooth con propietarios compartidos (`SharedRoute`): solo se cierra SCO/modo cuando el último usuario la libera; STT ya no marca fallo por un turno sin voz (cuenta turnos seguidos mudos y bloquea solo tras 3 sin voz ni nivel; cualquier voz reinicia la cuenta; un `ERROR_AUDIO` real sí cambia al teléfono con reintento); Vosk deja de marcar fallo por silencio (la salud se valida al abrir); una ruta que ya funcionó con ese auricular se acepta aunque el segundo de prueba sea silencio.
- Estado: sin probar en teléfono. Si vuelve a pasar, el aviso del chat dirá el motivo exacto.

### 2026-11-25 · Claude · v1.0.62 (avatar + Bluetooth)
- Avatar del inicio: ahora refleja el estado en vivo (LiveAssistantState publicado por ChatViewModel; v1.0.61 verde).
- Bluetooth: causa probable hallada — cada ruta exigía audio ≥1 s de prueba y muchos micrófonos BT mandan silencio digital si nadie habla, así que todas se descartaban y se caía al teléfono. Ahora una ruta se acepta si `AudioRecord.routedDevice` es Bluetooth aunque haya silencio.
- Nuevo `BluetoothDiagnosticLog` + botón «Copiar informe técnico» en la pantalla Bluetooth (para pegar evidencia real). Sin probar en teléfono.

### 2026-11-25 02:30 · Claude · Registro completo 1.0.55–1.0.62 (corrige huecos: el estado vivo decía 1.0.49 y las entradas no traían commit)
Tabla de versiones (versionCode = nº de run de CI; todas en la rama `trabajo`):
- **1.0.55 (75ffc88)** Bluetooth: reconocer auriculares al conectar (aviso en chat y por voz), tarjeta «Modo automático + manual», «Probar micrófono», «Volver a automático». Run #55 **falló** (faltaba `import kotlinx.coroutines.flow.first` en BluetoothViewModel).
- **1.0.56 (ceb416d)** corrige el import; run #56 verde.
- **1.0.57 (a5dc567)** causa del 0 % con el KD-06: faltaba `MODE_IN_COMMUNICATION`, usar fuente `VOICE_COMMUNICATION` y esperar SCO conectado; run verde.
- **1.0.58 (17ba56b)** micrófono Bluetooth adaptativo (rutas A–D) + color de la interfaz. Run #58 **falló** (delegado `var by mutableFloatStateOf` sin import).
- **1.0.59 (2c4f171)** slider de croma sin delegado; run #59 verde con tests.
- **1.0.60 (0515edd)** ruta Bluetooth compartida entre dictado y «Hey Neptuno» (`SharedRoute`), sin marcar fallo por turnos silenciosos; run #60 verde.
- **1.0.61 (263c725)** el avatar del inicio refleja el estado real del asistente (`LiveAssistantState` publicado por `ChatViewModel`, combinado en `HomeViewModel`); run #61 verde. Causa: el inicio nunca leía el estado del chat.
- **1.0.62 (f88934c)** Bluetooth: una ruta BT se acepta si `AudioRecord.routedDevice` es Bluetooth aunque el segundo de prueba sea silencio (causa probable de caer al teléfono); `BluetoothDiagnosticLog` + botón «Copiar informe técnico» en la pantalla Bluetooth; run #62 verde salvo el test intermitente `escenario J`.
- Nota: las entradas de las 21:10, 23:00 y 23:40 de ayer y la de «v1.0.62» de hoy describen el trabajo; esta tabla es la referencia con commits. Reporte vigente del usuario (2026-11-25): con el KD-06 la app vuelve al micrófono del teléfono y no escucha por Bluetooth; sin diagnóstico real hasta que pegue el informe técnico.



### 2026-11-25 · ChatGPT · Handoff técnico: IA local + router + acciones agénticas (auditoría inicial de código)
- Contexto recibido de Gemini: objetivo de implementar IA local real y acciones agénticas controladas para NEPTUNO, teléfonos objetivo con 4 GB RAM; preservar arquitectura Kotlin/Compose, Room/DataStore, STT/TTS, Vosk y Bluetooth; no sustituir ni romper funciones existentes.
- Repositorio auditado en lectura: `julsjailer/Nept-Uno-working`, rama `trabajo`. HEAD observado durante auditoría: `a0c38d45e2a6aec39400be5028b47e4550288e0e` (confirmar HEAD actual antes de cualquier modificación). No se realizaron cambios de código en esta auditoría.
- Archivos revisados: `ai/api/AiProviderContracts.kt`, `ai/local/LocalAiProviderStub.kt`, `ai/gemini/GeminiFirebaseProvider.kt` (existencia/estructura), `feature/chat/ChatViewModel.kt`, `feature/tools/ToolContracts.kt`, `app/NeptunoAppContainer.kt`, `data/local/UserPreferencesRepository.kt`, `app/build.gradle.kts`, `ARCHITECTURE.md`.
- Hallazgos: los contratos `AiProvider`, `AiRequest`, `AiResponseEvent` y `AiProviderRegistry` existen; el proveedor local `LocalEnginePendingProvider` es stub (IntegrationPending), no inferencia real. ChatViewModel selecciona proveedor por `preferredAiProviderId`, procesa streaming/cancelación/fallo y estados. AppContainer registra GeminiFirebaseProvider, GeminiPendingProvider y LocalEnginePendingProvider. No hay runtime LLM local integrado en dependencias de Gradle.
- Hallazgo de herramientas: `feature/tools/ToolContracts.kt` ya tiene `NeptunoTool` y `ToolRegistry`, pero las entradas existentes son catálogo/descriptores planificados; `execute` por defecto falla como no implementado y `isOperational` es false. No crear un segundo catálogo: ampliar el actual y conectarlo al chat.
- Arquitectura acordada como propuesta: capa Router/Policy entre ChatViewModel y providers; modos Local-only, híbrido y nube, reglas rápidas deterministas, local-first opcional y fallback a Gemini sólo cuando preferencias/privacidad lo permitan; sin conexión -> local si disponible; si no hay runtime/modelo -> estado honesto, nunca simular respuesta local ni fallback silencioso a nube en modo local-only.
- IA local: evaluar primero LiteRT-LM como candidato y llama.cpp como alternativa; aún NO está elegido ni integrado. Selección exige validar Android/ARM, licencia y modelo, cuantización compacta (preferencia 4-bit si viable), tamaño/descarga del modelo, RAM en dispositivo de 4 GB, latencia, térmica, compatibilidad con Vosk/STT/TTS y aceleración real. Gestión explícita de ciclo de vida (carga bajo demanda, liberar cuando sea posible), cancelación por coroutines, límites de memoria, errores y estado del modelo. No asumir que el teléfono puede mantener simultáneamente todos los motores intensivos residentes.
- Acciones agénticas: modelo sólo propone llamada estructurada; Kotlin valida esquema, argumentos, permisos, política y consentimiento, y ejecuta. Nunca permitir código arbitrario, shell ni ejecución directa de instrucciones del modelo. Confirmación previa para acciones externas/sensibles. Categorías: lectura autorizada; reversible; externa; sensible.
- Herramientas iniciales propuestas (no implementadas en esta auditoría): linterna (CameraManager + estado/error); alarmas, temporizadores y recordatorios (Room/persistencia + AlarmManager/WorkManager + notificaciones); llamadas con ACTION_DIAL por defecto y ACTION_CALL sólo permiso y confirmación; SMS con ACTION_SENDTO; WhatsApp mediante Intent/URI para preparar y abrir, no enviar en silencio. AccessibilityService sólo opcional, opt-in y con salvaguardas/confirmación.
- UX: integrar estados de provider/modelo/tool en ChatViewModel/LiveAssistantState sin bloquear UI ni afectar audio, STT, Vosk, TTS o Bluetooth. Mostrar estados de propuesta, autorización, ejecución, resultado, cancelación y error. Mantener los flujos actuales y el historial.
- Plan por fases acordado: (0) auditoría de arquitectura, dependencias, hardware y CI; (1) router/políticas/interfaces/estados + tests unitarios; (2) seleccionar e integrar runtime/modelo, descarga, lifecycle, memoria y primera inferencia local real; (3) ampliar ToolRegistry y ejecutar acciones Kotlin validadas, confirmaciones; (4) integrar con chat/voz/estados; (5) CI, pruebas de instalación/actualización y pruebas físicas cuando disponibles. Trabajar en commits pequeños; CI debe verificar cada fase; APK sólo tras pipeline exitoso.
- Regla de evidencia: este registro describe revisión de código y arquitectura, no implementación, compilación ni pruebas. No declarar tests físicos/emulador exitosos sin evidencia. No modificar app id, firma, versionCode, funciones existentes ni configuración crítica de APK sin necesidad y revisión.
- Objetivo de coordinación: este handoff deja a ChatGPT y Claude alineados sobre hallazgos, arquitectura propuesta, restricciones y fases; Claude puede aportar investigación adicional de runtimes/modelos, pero registrar evidencia y compatibilidad antes de decidir.

### 2026-11-25 11:30 · Claude · Informe técnico real del usuario y corrección (v1.0.63)
- Evidencia del usuario (Android 10/API 29, auriculares «CELG-C111BT», entrada tipo 7 = BT SCO): `Ruta D: pico=0, dispositivo real=CELG-C111BT/tipo 7, SCO=false, modo=0`. Solo se probó la ruta D (la ruta recordada de antes) y se **aceptó con audio 0** porque mi cambio de la 1.0.62 daba por buena cualquier ruta cuyo `routedDevice` fuera Bluetooth. Error mío: sin SCO abierto (SCO=false, modo normal) no hay micrófono Bluetooth real.
- Corrección: una ruta silenciosa solo se acepta si el canal de voz está realmente abierto (SCO conectado o dispositivo de comunicación fijado); la ruta D exige audio real. Preferencia recordada renombrada (`strategy2_`) para descartar la D guardada. El informe ahora registra si SCO conectó en cada ruta. Sin probar en teléfono; si falla, el informe dirá si SCO conecta o no.

### 2026-11-25 11:50 · Claude · 2.º informe del usuario: SCO conecta pero llega silencio (v1.0.64)
- Evidencia (Android 10, «CELG-C111BT»): `Ruta C: SCO conectado=true; pico=0, dispositivo real=CELG-C111BT/tipo 7, SCO=true, modo=3` en tres aperturas; luego una apertura con `BT=false` (la prueba de 3 s midió silencio y marcó `BluetoothMicHealth` → el teléfono se usa 15 min). Conclusión: el canal HFP/SCO SÍ abre y el AudioRecord queda enrutado al auricular, pero la fuente `VOICE_COMMUNICATION` entrega ceros. No se sabe aún si el usuario hablaba durante el sondeo inicial (1 s).
- Cambio v1.0.64: en rutas con SCO se prueban las fuentes `VOICE_COMMUNICATION`, `MIC` y `VOICE_RECOGNITION` (800 ms c/u) y se usa la primera con audio; el informe anota el pico de cada una y el de la prueba de 3 s. Sin probar en teléfono. Si todas dan 0 con el usuario hablando, será limitación del auricular/ROM (micrófono HFP mudo para apps de terceros).

### 2026-11-25 12:05 · Claude · 3.er informe: 3 fuentes de audio y pico 0 (v1.0.65 diagnóstico)
- Evidencia (Android 10, «CELG-C111BT», dos pruebas): SCO conecta, `routedDevice`=auricular (tipo 7), modo 3, y las fuentes VOICE_COMMUNICATION(7), MIC(1) y VOICE_RECOGNITION(6) dan `pico=0`; la prueba de 3 s también 0 %. La app abre bien el canal; el flujo de audio del auricular es silencio digital con 16 kHz.
- Hipótesis restantes: (a) micrófono HFP del auricular mudo/limitado para apps de terceros (hardware/firmware/ROM); (b) frecuencia: SCO nativo es 8 kHz (CVSD) o 16 kHz (mSBC). v1.0.65 solo añade diagnóstico: sondeo a 8000/16000/44100 Hz. Prueba externa recomendada al usuario: llamada de WhatsApp/telefónica con esos auriculares para ver si el micrófono funciona fuera de NEPTUNO.

### 2026-11-25 12:40 · Claude · 4.º informe: el «Bluetooth» que sonaba era el micrófono del teléfono (v1.0.66)
- Evidencia (Android 10): con «CELG-C111BT» el SCO conecta pero TODAS las fuentes y frecuencias (8/16/44,1 kHz) dan pico 0. Las pruebas con audio (pico 4081, 722, 33…) coinciden con `dispositivo real=M2006C3MG/tipo 15` y `SCO=false`: el usuario pulsó el botón de atender del CELG, el SCO cayó y el audio era del micrófono DEL TELÉFONO, mostrado como si fuera Bluetooth (fallo mío: aceptaba cualquier pico sin comprobar `routedDevice`). Con «KD-06»: `SCO conectado=false`, `dispositivo real=tipo 15` (teléfono) en todas; el SCO nunca abre con el método estándar.
- Conclusión: en ninguna prueba llegó audio real de un micrófono Bluetooth. Los dos auriculares no entregan micrófono con `startBluetoothSco` estándar.
- v1.0.66: (1) un pico solo cuenta como Bluetooth si `routedDevice` es Bluetooth (si es el teléfono se descarta y se anota); (2) nueva ruta V = `BluetoothHeadset.startVoiceRecognition(device)` (AT+BVRA, la vía del perfil manos libres para asistentes de voz; muchos auriculares solo activan el micrófono así), se prueba primero, con `stopVoiceRecognition` al liberar. Sin probar en teléfono.
