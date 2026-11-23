package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.neptuno.ai.gemini.configureAppCheck
import com.example.neptuno.app.NeptunoAppShell
import com.example.neptuno.app.NeptunoApplication
import com.example.neptuno.auth.AuthScreen
import com.example.neptuno.auth.authStateFlow
import com.example.neptuno.auth.signOutWithCredentialClear
import com.example.neptuno.core.model.ThemeMode
import com.example.neptuno.core.model.UserPreferences
import com.example.neptuno.feature.cloud.CloudAuthUser
import com.example.ui.theme.NeptunoTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()

        val container = (application as NeptunoApplication).appContainer

        setContent {
            val prefs by container.preferencesRepository.userPreferences
                .collectAsStateWithLifecycle(initialValue = UserPreferences())

            val darkTheme = when (prefs.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            val auth = remember { runCatching { Firebase.auth }.getOrNull() }
            val currentFirebaseUser by remember(auth) {
                auth?.authStateFlow() ?: kotlinx.coroutines.flow.flowOf(null)
            }.collectAsStateWithLifecycle(initialValue = auth?.currentUser)

            val cloudUser = currentFirebaseUser?.let { firebaseUser ->
                CloudAuthUser(
                    userId = firebaseUser.uid,
                    displayName = firebaseUser.displayName ?: "Usuario Neptuno",
                    email = firebaseUser.email
                )
            }

            val credentialManager = remember(this) { CredentialManager.create(this) }
            val appScope = rememberCoroutineScope()
            var showOptionalAuthScreen by rememberSaveable { mutableStateOf(false) }

            NeptunoTheme(darkTheme = darkTheme) {
                if (showOptionalAuthScreen) {
                    AuthScreen(
                        onAuthSuccess = { showOptionalAuthScreen = false },
                        onContinueLocal = { showOptionalAuthScreen = false }
                    )
                } else {
                    NeptunoAppShell(
                        container = container,
                        currentUser = cloudUser,
                        onOpenSignIn = { showOptionalAuthScreen = true },
                        onSignOut = {
                            signOutWithCredentialClear(
                                credentialManager = credentialManager,
                                scope = appScope
                            )
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            (application as? NeptunoApplication)?.appContainer?.releaseAudioEngines()
        }
        super.onDestroy()
    }
}
