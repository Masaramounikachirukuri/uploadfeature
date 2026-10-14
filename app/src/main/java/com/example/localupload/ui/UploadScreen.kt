package com.example.localupload.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.localupload.viewmodel.LocalUploadViewModel
import com.example.localupload.viewmodel.UploadUiState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(
    viewModel: LocalUploadViewModel = viewModel()
) {
    val uploadState by viewModel.uploadState.collectAsState()
    val selectedUris by viewModel.selectedUploadUris.collectAsState()

    val context = LocalContext.current

    var checkedUris by remember {
        mutableStateOf<Set<Uri>>(emptySet())
    }

    var knownUris by remember {
        mutableStateOf<Set<Uri>>(emptySet())
    }

    var pendingCameraUri by remember {
        mutableStateOf<Uri?>(null)
    }

    var cameraPermissionDenied by remember {
        mutableStateOf(false)
    }

    /*
     * Synchronize checked items whenever the shared ViewModel list changes.
     *
     * Newly added Picsum, Camera, Gallery, or Storage photos are automatically
     * selected. Removed and successfully uploaded photos are removed from the
     * checked set.
     */
    LaunchedEffect(selectedUris) {
        val currentUris = selectedUris.toSet()
        val newlyAddedUris = currentUris - knownUris

        checkedUris = checkedUris
            .filter { uri -> uri in currentUris }
            .toSet() + newlyAddedUris

        knownUris = currentUris
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val capturedUri = pendingCameraUri

        if (success && capturedUri != null) {
            viewModel.addUploadUris(
                uris = listOf(capturedUri)
            )
        } else if (capturedUri != null) {
            /*
             * The camera did not complete, so remove the empty temporary file.
             */
            runCatching {
                context.contentResolver.delete(
                    capturedUri,
                    null,
                    null
                )
            }
        }

        pendingCameraUri = null
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(
            maxItems = 10
        )
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addUploadUris(uris)
        }
    }

    val storageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }

            viewModel.addUploadUris(uris)
        }
    }

    fun launchCamera() {
        val temporaryFile = File.createTempFile(
            "camera_photo_",
            ".jpg",
            context.externalCacheDir ?: context.cacheDir
        )

        val photoUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            temporaryFile
        )

        pendingCameraUri = photoUri
        cameraLauncher.launch(photoUri)
    }

    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                cameraPermissionDenied = false
                launchCamera()
            } else {
                cameraPermissionDenied = true
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Upload Photos",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Picsum · Camera · Gallery · Storage",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(
                                alpha = 0.8f
                            )
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            SourcePickerSection(
                enabled = uploadState !is UploadUiState.Loading,
                cameraPermissionDenied = cameraPermissionDenied,
                onCameraClick = {
                    cameraPermissionDenied = false

                    val permissionGranted =
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED

                    if (permissionGranted) {
                        launchCamera()
                    } else {
                        cameraPermissionLauncher.launch(
                            Manifest.permission.CAMERA
                        )
                    }
                },
                onGalleryClick = {
                    cameraPermissionDenied = false

                    galleryLauncher.launch(
                        PickVisualMediaRequest(
                            ActivityResultContracts
                                .PickVisualMedia
                                .ImageOnly
                        )
                    )
                },
                onStorageClick = {
                    cameraPermissionDenied = false
                    storageLauncher.launch(arrayOf("image/*"))
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )

            if (selectedUris.isEmpty()) {
                EmptySelectionPlaceholder(
                    modifier = Modifier.weight(1f)
                )
            } else {
                SelectedPhotosHeader(
                    photoCount = selectedUris.size,
                    selectedCount = checkedUris.size,
                    allSelected =
                        checkedUris.size == selectedUris.size,
                    uploadInProgress =
                        uploadState is UploadUiState.Loading,
                    onClear = {
                        viewModel.clearSelectedUploadUris()
                        checkedUris = emptySet()
                        knownUris = emptySet()
                    },
                    onSelectAll = {
                        checkedUris =
                            if (checkedUris.size == selectedUris.size) {
                                emptySet()
                            } else {
                                selectedUris.toSet()
                            }

                        viewModel.resetUploadState()
                    }
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp),
                    contentPadding = PaddingValues(
                        top = 4.dp,
                        bottom = 12.dp
                    ),
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = selectedUris,
                        key = { uri -> uri.toString() }
                    ) { uri ->
                        SelectedPhotoGridItem(
                            uri = uri,
                            isSelected = uri in checkedUris,
                            enabled =
                                uploadState !is UploadUiState.Loading,
                            onToggle = {
                                checkedUris =
                                    if (uri in checkedUris) {
                                        checkedUris - uri
                                    } else {
                                        checkedUris + uri
                                    }

                                viewModel.resetUploadState()
                            },
                            onRemove = {
                                viewModel.removeUploadUri(uri)
                                checkedUris = checkedUris - uri
                                knownUris = knownUris - uri
                            }
                        )
                    }
                }
            }

            UploadActionBar(
                checkedCount = checkedUris.size,
                uploadState = uploadState,
                onUpload = {
                    if (checkedUris.isNotEmpty()) {
                        viewModel.uploadPhotos(
                            uris = checkedUris.toList()
                        )
                    }
                },
                onCancel = {
                    viewModel.cancelUpload()
                },
                onRetry = {
                    if (checkedUris.isNotEmpty()) {
                        viewModel.resetUploadState()

                        viewModel.uploadPhotos(
                            uris = checkedUris.toList()
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun SourcePickerSection(
    enabled: Boolean,
    cameraPermissionDenied: Boolean,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onStorageClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 16.dp,
                vertical = 14.dp
            )
    ) {
        Text(
            text = "Add Photos From",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SourceCard(
                modifier = Modifier.weight(1f),
                enabled = enabled,
                icon = Icons.Filled.Camera,
                label = "Camera",
                description = "Take photo",
                iconTint = Color(0xFF1565C0),
                backgroundColor = Color(0xFFE3F2FD),
                onClick = onCameraClick
            )

            SourceCard(
                modifier = Modifier.weight(1f),
                enabled = enabled,
                icon = Icons.Filled.PhotoLibrary,
                label = "Gallery",
                description = "Phone photos",
                iconTint = Color(0xFF7B1FA2),
                backgroundColor = Color(0xFFF3E5F5),
                onClick = onGalleryClick
            )

            SourceCard(
                modifier = Modifier.weight(1f),
                enabled = enabled,
                icon = Icons.Filled.FolderOpen,
                label = "Storage",
                description = "Browse files",
                iconTint = Color(0xFF2E7D32),
                backgroundColor = Color(0xFFE8F5E9),
                onClick = onStorageClick
            )
        }

        if (cameraPermissionDenied) {
            Spacer(modifier = Modifier.height(10.dp))

            PermissionWarning(
                message = "Camera permission is required to take a photo."
            )
        }
    }
}

@Composable
private fun SourceCard(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    icon: ImageVector,
    label: String,
    description: String,
    iconTint: Color,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(
            enabled = enabled,
            onClick = onClick
        ),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor =
                if (enabled) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 6.dp,
                    vertical = 14.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(backgroundColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PermissionWarning(
    message: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFFFF3E0))
            .border(
                width = 1.dp,
                color = Color(0xFFFF9800),
                shape = RoundedCornerShape(10.dp)
            )
            .padding(12.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFE65100)
        )
    }
}

@Composable
private fun SelectedPhotosHeader(
    photoCount: Int,
    selectedCount: Int,
    allSelected: Boolean,
    uploadInProgress: Boolean,
    onClear: () -> Unit,
    onSelectAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                end = 8.dp,
                top = 6.dp,
                bottom = 4.dp
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Selected Photos",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "$selectedCount of $photoCount selected",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row {
            TextButton(
                onClick = onClear,
                enabled = !uploadInProgress
            ) {
                Text(
                    text = "Clear",
                    color = MaterialTheme.colorScheme.error
                )
            }

            TextButton(
                onClick = onSelectAll,
                enabled = !uploadInProgress
            ) {
                Text(
                    text =
                        if (allSelected) {
                            "Deselect All"
                        } else {
                            "Select All"
                        }
                )
            }
        }
    }
}

@Composable
private fun EmptySelectionPlaceholder(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🖼️",
                fontSize = 52.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "No photos selected",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text =
                    "Select Picsum photos or add photos from Camera, Gallery, or Storage.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SelectedPhotoGridItem(
    uri: Uri,
    isSelected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(
                enabled = enabled,
                onClick = onToggle
            )
    ) {
        AsyncImage(
            model = uri,
            contentDescription = "Selected photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        if (!isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Color.Black.copy(alpha = 0.35f)
                    )
            )
        }

        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription =
                if (isSelected) "Selected" else "Not selected",
            tint =
                if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.White
                },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.White)
        )

        if (enabled) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(23.dp)
                    .clip(CircleShape)
                    .background(
                        Color.Black.copy(alpha = 0.65f)
                    )
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Clear,
                    contentDescription = "Remove photo",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun UploadActionBar(
    checkedCount: Int,
    uploadState: UploadUiState,
    onUpload: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            when (val state = uploadState) {
                is UploadUiState.Loading -> {
                    CleanUploadProgress(
                        phase = state.phase,
                        current = state.current,
                        total = state.total
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Cancel Upload")
                    }
                }

                is UploadUiState.Success -> {
                    UploadStatusMessage(
                        title = "Upload completed",
                        message =
                            "${state.successCount} of ${state.total} photos uploaded successfully.",
                        success = true
                    )

                    if (checkedCount > 0) {
                        Spacer(modifier = Modifier.height(10.dp))

                        UploadButton(
                            checkedCount = checkedCount,
                            onClick = onUpload
                        )
                    }
                }

                is UploadUiState.Error -> {
                    UploadStatusMessage(
                        title = "Upload failed",
                        message = state.message,
                        success = false
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = onRetry,
                        enabled = checkedCount > 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text("Retry Upload")
                    }
                }

                UploadUiState.Idle -> {
                    UploadButton(
                        checkedCount = checkedCount,
                        onClick = onUpload
                    )
                }
            }
        }
    }
}

@Composable
private fun UploadButton(
    checkedCount: Int,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = checkedCount > 0,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.CloudUpload,
            contentDescription = null,
            modifier = Modifier.size(21.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text =
                if (checkedCount == 0) {
                    "Select photos to upload"
                } else {
                    "Upload $checkedCount Photo${
                        if (checkedCount == 1) "" else "s"
                    }"
                },
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )
    }
}

@Composable
private fun CleanUploadProgress(
    phase: String,
    current: Int,
    total: Int
) {
    val waitingForNetwork = phase.startsWith(
        prefix = "Waiting",
        ignoreCase = true
    )

    val safeTotal = total.coerceAtLeast(1)
    val safeCurrent = current.coerceIn(0, safeTotal)

    val progress =
        if (waitingForNetwork) {
            0f
        } else {
            safeCurrent.toFloat() / safeTotal.toFloat()
        }

    val percentage =
        (progress * 100)
            .toInt()
            .coerceIn(0, 100)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.primaryContainer.copy(
                    alpha = 0.45f
                )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {
                Text(
                    text = when {
                        waitingForNetwork ->
                            "Waiting for network"

                        phase.equals(
                            other = "Compressing",
                            ignoreCase = true
                        ) ->
                            "Preparing photos"

                        else ->
                            "Uploading photos"
                    },
                    fontWeight = FontWeight.SemiBold
                )

                if (!waitingForNetwork) {
                    Text(
                        text = "$percentage%",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (waitingForNetwork) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text =
                    if (waitingForNetwork) {
                        "Upload will continue when a network connection is available."
                    } else {
                        "$safeCurrent of $total photos processed"
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UploadStatusMessage(
    title: String,
    message: String,
    success: Boolean
) {
    val backgroundColor =
        if (success) {
            Color(0xFFE8F5E9)
        } else {
            Color(0xFFFFEBEE)
        }

    val contentColor =
        if (success) {
            Color(0xFF2E7D32)
        } else {
            Color(0xFFC62828)
        }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .padding(12.dp)
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = contentColor
        )
    }
}