package com.example.localupload.ui

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.localupload.domain.model.Photo
import com.example.localupload.domain.model.PicsumPhoto
import com.example.localupload.viewmodel.LocalUploadViewModel
import com.example.localupload.viewmodel.PicsumState
import com.example.localupload.viewmodel.UploadState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoPickerScreen(
    viewModel: LocalUploadViewModel,
    onUrisPicked: (List<Uri>) -> Unit,
    onNavigateToHistory: () -> Unit
) {
    val pendingPhotos by viewModel.pendingPhotos.collectAsState()
    val uploadState by viewModel.uploadState.collectAsState()
    val picsumState by viewModel.picsumState.collectAsState()
    val picsumPhotos by viewModel.picsumPhotos.collectAsState()
    val currentPage by viewModel.currentPicsumPage.collectAsState()

    val selectedCount = picsumPhotos.count { photo ->
        photo.isSelected
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Picsum Photo Upload",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToHistory
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = "View upload history"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor =
                        MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Available Photos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            PicsumStatusSection(
                state = picsumState,
                currentPage = currentPage,
                onRetry = {
                    viewModel.retryPicsumLoading()
                }
            )

            if (
                picsumPhotos.isNotEmpty() &&
                picsumState !is PicsumState.Loading
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(
                        items = picsumPhotos,
                        key = { photo -> photo.id }
                    ) { photo ->
                        PicsumPhotoItem(
                            photo = photo,
                            onClick = {
                                viewModel.togglePicsumPhotoSelection(
                                    photoId = photo.id
                                )
                            }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.loadPreviousPicsumPage()
                        },
                        enabled =
                            currentPage > 1 &&
                                    picsumState !is PicsumState.Loading
                    ) {
                        Text(text = "Previous")
                    }

                    Text(
                        text = "Page $currentPage",
                        fontWeight = FontWeight.Medium
                    )

                    OutlinedButton(
                        onClick = {
                            viewModel.loadNextPicsumPage()
                        },
                        enabled =
                            picsumState !is PicsumState.Loading
                    ) {
                        Text(text = "Next")
                    }
                }

                Button(
                    onClick = {
                        viewModel.moveSelectedPicsumPhotosToUploadQueue()
                    },
                    enabled =
                        selectedCount > 0 &&
                                picsumState !is PicsumState.Downloading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = if (selectedCount == 1) {
                            "Move 1 Photo to Upload Folder"
                        } else {
                            "Move $selectedCount Photos to Upload Folder"
                        }
                    )
                }
            }

            HorizontalDivider()

            Text(
                text = "Upload Folder",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (pendingPhotos.isEmpty()) {
                EmptyUploadFolder()
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color =
                        MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = if (pendingPhotos.size == 1) {
                            "1 photo is ready to upload"
                        } else {
                            "${pendingPhotos.size} photos are ready to upload"
                        },
                        modifier = Modifier.padding(12.dp),
                        fontWeight = FontWeight.Medium
                    )
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(
                            min = 100.dp,
                            max = 220.dp
                        ),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    items(
                        items = pendingPhotos,
                        key = { photo -> photo.id }
                    ) { photo ->
                        PendingPhotoThumbnail(
                            photo = photo,
                            onRemove = {
                                viewModel.removePendingPhoto(
                                    id = photo.id
                                )
                            }
                        )
                    }
                }
            }

            UploadStatusSection(
                state = uploadState,
                onCancel = {
                    viewModel.cancelUpload()
                },
                onDismiss = {
                    viewModel.resetState()
                }
            )

            if (
                pendingPhotos.isNotEmpty() &&
                uploadState !is UploadState.Uploading &&
                uploadState !is UploadState.Queued
            ) {
                Button(
                    onClick = {
                        viewModel.uploadPhotos()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector =
                            Icons.AutoMirrored.Filled.Send,
                        contentDescription = null
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = if (pendingPhotos.size == 1) {
                            "Upload 1 Photo"
                        } else {
                            "Upload ${pendingPhotos.size} Photos"
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PicsumStatusSection(
    state: PicsumState,
    currentPage: Int,
    onRetry: () -> Unit
) {
    when (state) {
        PicsumState.Idle -> {
            Text(
                text = "Preparing the photo gallery..."
            )
        }

        PicsumState.Loading -> {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )

                    Text(
                        text = "Loading photos from page $currentPage..."
                    )
                }
            }
        }

        is PicsumState.Downloading -> {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )

                    Text(
                        text = if (state.selectedCount == 1) {
                            "Saving 1 selected photo to local storage..."
                        } else {
                            "Saving ${state.selectedCount} selected photos to local storage..."
                        }
                    )
                }
            }
        }

        is PicsumState.Error -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor =
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = state.message,
                        color =
                            MaterialTheme.colorScheme.onErrorContainer
                    )

                    TextButton(
                        onClick = onRetry
                    ) {
                        Text(text = "Retry")
                    }
                }
            }
        }

        is PicsumState.Success -> {
            if (state.photoCount == 0) {
                Text(
                    text = "No photos were returned for this page.",
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun PicsumPhotoItem(
    photo: PicsumPhoto,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        border = if (photo.isSelected) {
            BorderStroke(
                width = 4.dp,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            null
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            AsyncImage(
                model = photo.downloadUrl,
                contentDescription = "Photo by ${photo.author}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            if (photo.isSelected) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .padding(3.dp)
                            .size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyUploadFolder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Select photos above and move them here.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun PendingPhotoThumbnail(
    photo: Photo,
    onRemove: () -> Unit
) {
    val imageModel = remember(photo.uriString) {
        when {
            photo.uriString.startsWith("content://") -> {
                Uri.parse(photo.uriString)
            }

            photo.uriString.startsWith("file://") -> {
                Uri.parse(photo.uriString)
            }

            else -> {
                File(photo.uriString)
            }
        }
    }

    Box(
        modifier = Modifier.aspectRatio(1f)
    ) {
        AsyncImage(
            model = imageModel,
            contentDescription = photo.displayName,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )

        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(30.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color =
                    MaterialTheme.colorScheme.errorContainer
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    modifier = Modifier
                        .padding(2.dp)
                        .size(20.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun UploadStatusSection(
    state: UploadState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    when (state) {
        UploadState.Idle -> Unit

        UploadState.Queued -> {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Upload queued...",
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        is UploadState.Uploading -> {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement =
                            Arrangement.SpaceBetween
                    ) {
                        Text(
                            text =
                                "Uploading ${state.current} of ${state.total}",
                            fontWeight = FontWeight.Medium
                        )

                        TextButton(
                            onClick = onCancel
                        ) {
                            Text(text = "Cancel")
                        }
                    }

                    val progress =
                        if (state.total > 0) {
                            state.current.toFloat() /
                                    state.total.toFloat()
                        } else {
                            0f
                        }

                    LinearProgressIndicator(
                        progress = {
                            progress.coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        is UploadState.Success -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor =
                        MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = if (state.uploadedCount == 1) {
                            "1 photo uploaded successfully."
                        } else {
                            "${state.uploadedCount} photos uploaded successfully."
                        },
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                    )

                    IconButton(
                        onClick = onDismiss
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss"
                        )
                    }
                }
            }
        }

        is UploadState.Error -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor =
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = state.message,
                        modifier = Modifier
                            .fillMaxWidth(0.85f),
                        color =
                            MaterialTheme.colorScheme.onErrorContainer
                    )

                    IconButton(
                        onClick = onDismiss
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss"
                        )
                    }
                }
            }
        }
    }
}
