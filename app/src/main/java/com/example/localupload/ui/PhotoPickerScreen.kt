package com.example.localupload.ui

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import com.example.localupload.domain.model.Photo
import com.example.localupload.viewmodel.GalleryState
import com.example.localupload.viewmodel.LocalUploadViewModel

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterialApi::class
)
@Composable
fun PhotoPickerScreen(
    onPhotoClick: (photoId: String) -> Unit,

    /*
     * This callback only prepares the selected Picsum photos and navigates
     * to the Upload screen. It must not start WorkManager directly.
     */
    onContinueToUpload: (photos: List<Photo>) -> Unit = {},

    /*
     * MainActivity can pass the ViewModel preparation state here.
     * It disables repeated clicks while the cache is being checked.
     */
    isPreparingPhotos: Boolean = false,

    viewModel: LocalUploadViewModel = viewModel()
) {
    val galleryState by viewModel.galleryState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    var selectedPhotoIds by remember {
        mutableStateOf<Set<String>>(emptySet())
    }

    val availablePhotos = when (val state = galleryState) {
        is GalleryState.Success -> state.photos
        else -> emptyList()
    }

    val selectedPhotos = availablePhotos.filter { photo ->
        photo.id in selectedPhotoIds
    }

    val allPhotosSelected =
        availablePhotos.isNotEmpty() &&
                selectedPhotoIds.size == availablePhotos.size

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            if (!isPreparingPhotos) {
                selectedPhotoIds = emptySet()
                viewModel.fetchGallery(forceRefresh = true)
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Picsum Photos",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Select photos and continue to upload",
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
        },
        bottomBar = {
            if (availablePhotos.isNotEmpty()) {
                PicsumContinueActionBar(
                    selectedCount = selectedPhotos.size,
                    isPreparing = isPreparingPhotos,
                    onContinueClick = {
                        if (
                            selectedPhotos.isNotEmpty() &&
                            !isPreparingPhotos
                        ) {
                            onContinueToUpload(selectedPhotos)
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pullRefresh(pullRefreshState)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 8.dp,
                    top = 8.dp,
                    end = 8.dp,
                    bottom = 12.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (val state = galleryState) {
                    is GalleryState.Loading -> {
                        item(
                            span = {
                                GridItemSpan(maxLineSpan)
                            }
                        ) {
                            GalleryLoadingState()
                        }
                    }

                    is GalleryState.Error -> {
                        item(
                            span = {
                                GridItemSpan(maxLineSpan)
                            }
                        ) {
                            GalleryErrorState(
                                message = state.message,
                                onRetry = {
                                    viewModel.fetchGallery(
                                        forceRefresh = true
                                    )
                                }
                            )
                        }
                    }

                    is GalleryState.Success -> {
                        if (state.photos.isEmpty()) {
                            item(
                                span = {
                                    GridItemSpan(maxLineSpan)
                                }
                            ) {
                                EmptyGalleryState()
                            }
                        } else {
                            item(
                                span = {
                                    GridItemSpan(maxLineSpan)
                                }
                            ) {
                                GallerySelectionHeader(
                                    totalCount = state.photos.size,
                                    selectedCount = selectedPhotoIds.size,
                                    allSelected = allPhotosSelected,
                                    enabled = !isPreparingPhotos,
                                    onClearSelection = {
                                        selectedPhotoIds = emptySet()
                                    },
                                    onSelectAll = {
                                        selectedPhotoIds =
                                            if (allPhotosSelected) {
                                                emptySet()
                                            } else {
                                                state.photos
                                                    .map { photo -> photo.id }
                                                    .toSet()
                                            }
                                    }
                                )
                            }

                            items(
                                items = state.photos,
                                key = { photo -> photo.id }
                            ) { photo ->
                                ApiPhotoGridItem(
                                    photo = photo,
                                    isSelected =
                                        photo.id in selectedPhotoIds,
                                    enabled = !isPreparingPhotos,
                                    onToggleSelection = {
                                        selectedPhotoIds =
                                            if (
                                                photo.id in selectedPhotoIds
                                            ) {
                                                selectedPhotoIds - photo.id
                                            } else {
                                                selectedPhotoIds + photo.id
                                            }
                                    },
                                    onOpenDetails = {
                                        onPhotoClick(photo.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                contentColor = MaterialTheme.colorScheme.primary
            )

            if (isPreparingPhotos) {
                PreparingPhotosOverlay()
            }
        }
    }
}

@Composable
private fun GallerySelectionHeader(
    totalCount: Int,
    selectedCount: Int,
    allSelected: Boolean,
    enabled: Boolean,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 8.dp,
                    end = 2.dp,
                    top = 2.dp,
                    bottom = 4.dp
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "$totalCount Picsum photos",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = "$selectedCount selected",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row {
                if (selectedCount > 0) {
                    TextButton(
                        onClick = onClearSelection,
                        enabled = enabled
                    ) {
                        Text(
                            text = "Clear",
                            color = if (enabled) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                TextButton(
                    onClick = onSelectAll,
                    enabled = enabled
                ) {
                    Text(
                        text = if (allSelected) {
                            "Deselect All"
                        } else {
                            "Select All"
                        }
                    )
                }
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
private fun ApiPhotoGridItem(
    photo: Photo,
    isSelected: Boolean,
    enabled: Boolean,
    onToggleSelection: () -> Unit,
    onOpenDetails: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(
                enabled = enabled,
                onClick = onToggleSelection
            ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            SubcomposeAsyncImage(
                model = photo.thumbnailUrl,
                contentDescription = "Picsum photo by ${photo.author}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    ImageLoadingPlaceholder()
                },
                error = {
                    ImageErrorPlaceholder()
                }
            )

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.primary.copy(
                                alpha = 0.12f
                            )
                        )
                )
            }

            Icon(
                imageVector = if (isSelected) {
                    Icons.Filled.CheckCircle
                } else {
                    Icons.Outlined.Circle
                },
                contentDescription = if (isSelected) {
                    "Photo selected"
                } else {
                    "Photo not selected"
                },
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.White
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) {
                            Color.White
                        } else {
                            Color.Black.copy(alpha = 0.45f)
                        }
                    )
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Color.Black.copy(alpha = 0.60f)
                    )
                    .clickable(
                        enabled = enabled,
                        onClick = onOpenDetails
                    )
                    .padding(
                        horizontal = 6.dp,
                        vertical = 5.dp
                    )
            ) {
                Text(
                    text = photo.author,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PicsumContinueActionBar(
    selectedCount: Int,
    isPreparing: Boolean,
    onContinueClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
        tonalElevation = 3.dp
    ) {
        Button(
            onClick = onContinueClick,
            enabled = selectedCount > 0 && !isPreparing,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 16.dp,
                    vertical = 12.dp
                )
                .height(54.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isPreparing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )

                Spacer(modifier = Modifier.size(8.dp))

                Text(
                    text = "Preparing photos...",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.CloudDownload,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )

                Spacer(modifier = Modifier.size(8.dp))

                Text(
                    text = if (selectedCount == 0) {
                        "Select Picsum photos"
                    } else {
                        "Continue with $selectedCount Photo${
                            if (selectedCount == 1) "" else "s"
                        }"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun PreparingPhotosOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 8.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = 24.dp,
                    vertical = 20.dp
                ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(38.dp),
                    strokeWidth = 3.dp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Checking photo cache",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Missing photos will be downloaded once.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ImageLoadingPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                MaterialTheme.colorScheme.surfaceVariant
            ),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(26.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ImageErrorPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                MaterialTheme.colorScheme.surfaceVariant
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.BrokenImage,
            contentDescription = "Image could not be loaded",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(30.dp)
        )

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = "Image unavailable",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun GalleryLoadingState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Loading Picsum photos...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyGalleryState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.BrokenImage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "No Picsum photos found",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(5.dp))

            Text(
                text = "Pull down to refresh.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GalleryErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.BrokenImage,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Failed to load Picsum photos",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onRetry) {
            Text(text = "Retry")
        }
    }
}