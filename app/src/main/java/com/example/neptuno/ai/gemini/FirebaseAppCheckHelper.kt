package com.example.neptuno.ai.gemini

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

data class AppCheckDiagnosticState(
    val isInitialized: Boolean = false,
    val providerType: String = "Sin inicializar",
    val detail: String = "App Check no configurado todavía",
    val error: String? = null
)

object AppCheckStatusHolder {
    private val _state = MutableStateFlow(AppCheckDiagnosticState())
    val state: StateFlow<AppCheckDiagnosticState> = _state.asStateFlow()

    internal fun update(newState: AppCheckDiagnosticState) {
        _state.value = newState
    }
}

/**
 * Configura Firebase App Check antes de inicializar UI, ViewModels o llamadas a Firebase AI Logic.
 * No desactiva App Check; registra diagnóstico claro si falla el proveedor en debug o release.
 */
fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        if (BuildConfig.DEBUG) {
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
            if (!debugToken.isNullOrEmpty()) {
                val persistenceKey = FirebaseApp.getInstance().persistenceKey
                val prefs = context.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                )
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                Log.d(TAG, "App Check debug secret configured successfully.")
            } else {
                Log.w(TAG, "No debug token found in Intent extra.")
            }

            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            AppCheckStatusHolder.update(
                AppCheckDiagnosticState(
                    isInitialized = true,
                    providerType = "DebugAppCheckProviderFactory",
                    detail = if (!debugToken.isNullOrEmpty()) {
                        "App Check debug activo con token de sesión"
                    } else {
                        "App Check debug activo (token automático)"
                    }
                )
            )
            Log.d(TAG, "DebugAppCheckProviderFactory installed.")
        } else {
            val resId = context.resources.getIdentifier(
                RECAPTCHA_SITE_KEY_RES,
                "string",
                context.packageName
            )
            if (resId != 0) {
                val siteKey = context.getString(resId)
                if (siteKey.isNotBlank()) {
                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                    )
                    AppCheckStatusHolder.update(
                        AppCheckDiagnosticState(
                            isInitialized = true,
                            providerType = "RecaptchaAppCheckProviderFactory",
                            detail = "App Check reCAPTCHA Enterprise configurado"
                        )
                    )
                    Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
                } else {
                    val msg = "recaptcha_site_key resource string is blank."
                    Log.e(TAG, msg)
                    AppCheckStatusHolder.update(
                        AppCheckDiagnosticState(
                            isInitialized = false,
                            providerType = "RecaptchaAppCheckProviderFactory",
                            detail = "Falta clave reCAPTCHA para App Check",
                            error = msg
                        )
                    )
                }
            } else {
                val msg = "recaptcha_site_key string resource not found in package."
                Log.e(TAG, msg)
                AppCheckStatusHolder.update(
                    AppCheckDiagnosticState(
                        isInitialized = false,
                        providerType = "RecaptchaAppCheckProviderFactory",
                        detail = "Recurso recaptcha_site_key ausente",
                        error = msg
                    )
                )
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
        AppCheckStatusHolder.update(
            AppCheckDiagnosticState(
                isInitialized = false,
                providerType = if (BuildConfig.DEBUG) "Debug" else "Release",
                detail = "Error inicializando Firebase App Check",
                error = e.localizedMessage ?: e.javaClass.simpleName
            )
        )
    }
}
