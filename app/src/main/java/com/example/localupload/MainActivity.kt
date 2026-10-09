package com.example.localupload

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.localupload.ui.PhotoHistoryScreen
import com.example.localupload.ui.PhotoPickerScreen
import com.example.localupload.ui.theme.LocalUploadTheme
import com.example.localupload.viewmodel.LocalUploadViewModel

/**
 * Single Activity — hosts the entire Compose UI.
 *
 * Key responsibilities:
 *  1. When the Photo Picker returns URIs, call takePersistableUriPermission() on EACH URI
 *     before passing them to the ViewModel. This makes URIs permanently readable across
 *     app restarts and reboots — required because WorkManager runs after the app is killed.
 *  2. Request POST_NOTIFICATIONS permission on Android 13+ so WorkManager can post
 *     upload progress and result notifications.
 *
 * Navigation:
 *   "photo_picker"   → PhotoPickerScreen  (select & upload photos)
 *   "photo_history"  → PhotoHistoryScreen (view upload history from Room DB)
 */
class MainActivity : ComponentActivity() {

    // ViewModel is created at Activity scope — shared with both Compose screens
    private lateinit var uploadViewModel: LocalUploadViewModel

    // Launcher to request POST_NOTIFICATIONS permission (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or denied — WorkManager will still run, just without notifications if denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request notification permission on Android 13+ (API 33+)
        requestNotificationPermissionIfNeeded()

        setContent {
            LocalUploadTheme {
                // AndroidViewModel — Compose uses the Activity's ViewModelStoreOwner
                uploadViewModel = viewModel()
                AppNavigation(
                    viewModel = uploadViewModel,
                    onUrisPicked = { uris -> grantPersistablePermissions(uris) }
                )
            }
        }
    }

    /**
     * Requests POST_NOTIFICATIONS permission on Android 13+ if not already granted.
     * On Android 12 and below, notifications don't require explicit permission.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /**
     * Grants persistable read permission for each URI returned by the Photo Picker.
     *
     * Without this call, Photo Picker URIs are only valid for the current session.
     * After this call, URIs remain readable even after:
     *   - App is killed and reopened
     *   - Phone is restarted
     *   - WorkManager runs the upload in background after app kill
     *
     * After granting permissions, URIs are saved to Room DB pending_photos table
     * via ViewModel.addPendingPhotos().
     */
    private fun grantPersistablePermissions(uris: List<Uri>) {
        val persistedUris = uris.mapNotNull { uri ->
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                uri
            } catch (e: SecurityException) {
                // Some URIs (e.g. file:// URIs) don't support persistable permissions
                // Still pass them through — they'll work for the current session
                uri
            }
        }
        uploadViewModel.addPendingPhotos(persistedUris)
    }
}

@Composable
fun AppNavigation(
    viewModel: LocalUploadViewModel,
    onUrisPicked: (List<Uri>) -> Unit
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "photo_picker"
    ) {
        composable("photo_picker") {
            PhotoPickerScreen(
                viewModel = viewModel,
                onUrisPicked = onUrisPicked,
                onNavigateToHistory = {
                    navController.navigate("photo_history")
                }
            )
        }

        composable("photo_history") {
            PhotoHistoryScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
