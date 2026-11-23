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

**Última actualización:** 2026-11-23 03:40 · Claude.

**Proyecto:** NEPTUNO, asistente de IA nativo para Android (Kotlin, Compose, Room, DataStore, Firebase AI Logic con Gemini, Firestore opcional). Arquitectura en `ARCHITECTURE.md`.

**Repos:** original `Nept-Uno` (público, no tocar) · trabajo `Nept-Uno-working` (privado; `main` = copia exacta del original en 1ac4381, `trabajo` = cambios). No tocar `mini-proyectos-potecitos-`.

**Dispositivos de prueba (dato del usuario):** Redmi 10C y Redmi 9C, ambos con 4 GB de RAM. El usuario los considera buenos para el proyecto.

**Compilación y CI:** un solo workflow (`build-apk.yml`) con Java 21: tests, APK debug firmada con llave fija (`ci/ci-debug.jks`), release `apk-latest` ("APK debug (último build)"), verificación de recursos de Firebase dentro de la APK y logs en la rama `ci-logs` (`ultimo-log.txt`). Los commits que solo tocan `*.md` no recompilan. Un build nuevo cancela al anterior.

**Tests:** en el run #12 (70322c1) pasaron 35 de 36; el que falló (STT) ya está corregido en 6d759a3, pendiente de ver el siguiente run.

**Firebase y cuenta:** `app/google-services.json` está en el repo privado con la SHA-1 de la llave de CI. Según el usuario, el inicio de sesión con Google y la sincronización con la nube ya funcionan en el teléfono.

**Gemini:** responde (según el usuario). La cuota gratuita es de 20 solicitudes por modelo y por día. El proveedor prueba en orden `gemini-3.8-flash`, `gemini-4-hydrogen`, `gemini-3.5-flash-lite` ante cuota agotada (429) o saturación (503/"high demand"); recuerda el modelo cuando es por cuota. El nombre del proveedor muestra el último modelo que respondió.

**Voz:** el selector de voces del dispositivo ya existía en Ajustes. Nuevo ajuste "Enviar automáticamente lo que digo" (activado por defecto): al llegar la transcripción final el mensaje se envía solo.

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
3. **"Hey Neptuno" en segundo plano.** Servicio en primer plano con micrófono y notificación fija + detector de palabra clave (Porcupine con clave gratuita de uso personal, u openWakeWord/sherpa-onnx sin clave). Riesgos sin probar: Xiaomi/MIUI suele cerrar servicios en segundo plano (autoinicio y ahorro de batería), consumo de batería, privacidad del micrófono, restricciones de Android 14.
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
