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
| **ChatGPT** | Entrega el bloque de entrada ya redactado; el usuario lo pega o le pide a Claude que lo registre. | Su conector de GitHub es **solo de lectura** (no hace commits). Con Codex sí podría escribir. |
| **Perplexity** | Igual que ChatGPT: entrega el bloque de entrada para que se registre. | Se asume solo lectura/búsqueda mientras no se compruebe lo contrario. |

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

**Proyecto:** NEPTUNO, asistente de IA nativo para Android (Kotlin, Compose, Room, DataStore, Firebase AI Logic con Gemini, Firestore opcional). Arquitectura en `ARCHITECTURE.md`.

**Repos:** original `Nept-Uno` (público, no tocar) · trabajo `Nept-Uno-working` (privado; `main` = copia exacta del original en 1ac4381, `trabajo` = cambios).

**Compilación:** la APK debug compila en CI y se publica en Releases como `apk-latest` ("APK debug (último build)"). Sin `google-services.json` no funcionan Gemini, inicio de sesión ni nube. Los logs del último CI quedan en la rama `ci-logs` (archivo `ultimo-log.txt`).

**Tests:** fallan por un error externo (Maven Central devuelve 429 al bajar una librería de Robolectric). No bloquean la APK.

**Firebase:** `app/google-services.json` está en el repo privado (app `com.aistudio.neptuno.k7v9xm`) y ya incluye el cliente OAuth Android con la huella SHA-1 de la llave de CI (`ci/ci-debug.jks`), así que Google Sign-In debería funcionar en la APK de Releases. Sin probar en dispositivo.

## 7. Pendientes (se actualiza)

1. Usuario: instalar la APK de `apk-latest` y probar Gemini, inicio de sesión con Google y nube; comprobar si App Check exige token de depuración. Informar el resultado.
2. Sincronización con la nube: reenvía todo cada vez, en serie y con timeout global de 20 s; añadir marca de sincronizado en Room y escrituras por lotes. **Es el problema más grave.**
3. Room: versión 1 con `exportSchema = false` y sin migraciones.
4. Interrupción de Gemini: cancelar el Job en lugar de comprobar una bandera por cada chunk.
5. Dependencias antiguas (Compose BOM 2024.09, lifecycle 2.8, navigation 2.8); `firebase-appcheck-debug` como `implementation`; release sin minificación.
6. Tests de CI: resolver el 429 de Maven (otro espejo o reintento).
7. Dividir `ChatViewModel` (810 líneas) y `CloudSessionViewModel` (646); quitar los `!!` restantes.
8. Idea futura: modo offline con modelo local pequeño (MiniMind u otro) sin sustituir a Gemini; requiere motor de inferencia en Android (llama.cpp o MNN). Sin decidir.

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

