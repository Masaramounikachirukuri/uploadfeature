package com.example.localupload.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import com.example.localupload.domain.model.Photo
import com.example.localupload.viewmodel.LocalUploadViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDetailScreen(
    photoId: String,
    onBack: () -> Unit,
    onContinueToUpload: (Photo) -> Unit = {},
    viewModel: LocalUploadViewModel = viewModel()
) {
    val photo = viewModel.getPhotoById(photoId)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = photo?.author ?: "Photo Details",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack
                    ) {
                        Icon(
                            imageVector =
                                Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor =
                        MaterialTheme.colorScheme.primary,
                    titleContentColor =
                        MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor =
                        MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        if (photo == null) {
            PhotoNotFoundState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                onBack = onBack
            )
        } else {
            PhotoDetailContent(
                photo = photo,
                onContinueToUpload = {
                    onContinueToUpload(photo)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }
    }
}

@Composable
private fun PhotoDetailContent(
    photo: Photo,
    onContinueToUpload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
    ) {
        SubcomposeAsyncImage(
            model = photo.fullUrl,
            contentDescription = "Photo by ${photo.author}",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp),
            loading = {
                PhotoLoadingState()
            },
            error = {
                PhotoLoadingErrorState()
            }
        )

        PhotoInformationCard(
            photo = photo
        )

        Button(
            onClick = onContinueToUpload,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 16.dp,
                    vertical = 8.dp
                )
                .height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector =
                    Icons.Filled.AddPhotoAlternate,
                contentDescription = null
            )

            Spacer(
                modifier = Modifier.padding(
                    horizontal = 4.dp
                )
            )

            Text(
                text = "Continue with This Photo",
                fontWeight = FontWeight.SemiBold
            )
        }

        Text(
            text = "The photo will be reused from cache when available. " +
                    "Otherwise, it will be downloaded once and added to the Upload screen.",
            style = MaterialTheme.typography.bodySmall,
            color =
                MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 24.dp,
                    vertical = 8.dp
                )
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )
    }
}

@Composable
private fun PhotoInformationCard(
    photo: Photo
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 16.dp,
                vertical = 12.dp
            ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = photo.author,
                style =
                    MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            PhotoInformationRow(
                label = "Photo ID",
                value = photo.id
            )

            PhotoInformationRow(
                label = "Dimensions",
                value = "${photo.width} × ${photo.height}"
            )
        }
    }
}

@Composable
private fun PhotoInformationRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement =
            Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color =
                MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PhotoLoadingState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                MaterialTheme.colorScheme.surfaceVariant
            ),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun PhotoLoadingErrorState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                MaterialTheme.colorScheme.errorContainer
            ),
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.BrokenImage,
            contentDescription = null,
            tint =
                MaterialTheme.colorScheme.onErrorContainer
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Text(
            text = "Unable to load this photo",
            color =
                MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

@Composable
private fun PhotoNotFoundState(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.BrokenImage,
            contentDescription = null,
            tint =
                MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        Text(
            text = "Photo not found",
            style =
                MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        Button(
            onClick = onBack
        ) {
            Text(
                text = "Go Back"
            )
        }
    }
}