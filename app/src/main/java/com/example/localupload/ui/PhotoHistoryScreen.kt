package com.example.localupload.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.localupload.domain.model.UploadHistory
import com.example.localupload.domain.model.UploadStatus
import com.example.localupload.viewmodel.LocalUploadViewModel

/**
 * PhotoHistoryScreen
 *
 * Shows all upload history items (most recent first).
 * Each item shows:
 *  - Thumbnail (from local URI)
 *  - File name + size
 *  - Status badge (Pending / Uploading / Uploaded / Failed)
 *  - Delete button
 *  - Retry button (only for FAILED items)
 *
 * TopAppBar has a "Clear All" action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoHistoryScreen(
    viewModel: LocalUploadViewModel = viewModel()
) {
    val history by viewModel.history.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    // Load history when this screen becomes active
    LaunchedEffect(Unit) {
        viewModel.refreshHistory()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text       = "Upload History",
                            style      = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text  = "${history.size} item(s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        )
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                imageVector        = Icons.Filled.DeleteSweep,
                                contentDescription = "Clear All",
                                tint               = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        if (history.isEmpty()) {
            // ── Empty state ───────────────────────────────────────────────────
            Box(
                modifier         = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "📂", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text      = "No upload history yet",
                        style     = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text      = "Pick and upload photos from the Gallery tab",
                        style     = MaterialTheme.typography.bodySmall,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier  = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier       = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(history, key = { it.id }) { item ->
                    HistoryItemCard(
                        item     = item,
                        onDelete = { viewModel.deleteHistoryItem(item.id) },
                        onRetry  = { viewModel.retryUpload(item) }
                    )
                }
            }
        }
    }

    // ── Clear All confirmation dialog ─────────────────────────────────────────
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title            = { Text("Clear History") },
            text             = { Text("Remove all ${history.size} upload record(s)? This cannot be undone.") },
            confirmButton    = {
                TextButton(onClick = {
                    viewModel.clearHistory()
                    showClearDialog = false
                }) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton    = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ── History item card ─────────────────────────────────────────────────────────

@Composable
private fun HistoryItemCard(
    item     : UploadHistory,
    onDelete : () -> Unit,
    onRetry  : () -> Unit
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // ── Thumbnail ─────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model              = android.net.Uri.parse(item.localUri),
                    contentDescription = "Upload thumbnail",
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
                )
                // Status overlay badge
                StatusBadge(
                    status   = item.status,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            // ── Info ──────────────────────────────────────────────────────────
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = item.fileName,
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(status = item.status)
                    if (item.fileSizeBytes > 0) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text  = item.readableSize,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (item.status == UploadStatus.FAILED && item.errorMessage != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text     = item.errorMessage,
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (item.status == UploadStatus.SUCCESS && item.uploadedUrl != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text     = item.uploadedUrl,
                        style    = MaterialTheme.typography.labelSmall,
                        color    = Color(0xFF1976D2),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(4.dp))

            // ── Actions ───────────────────────────────────────────────────────
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (item.status == UploadStatus.FAILED) {
                    IconButton(onClick = onRetry, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector        = Icons.Filled.Refresh,
                            contentDescription = "Retry upload",
                            tint               = MaterialTheme.colorScheme.primary,
                            modifier           = Modifier.size(20.dp)
                        )
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector        = Icons.Filled.Delete,
                        contentDescription = "Delete",
                        tint               = MaterialTheme.colorScheme.error,
                        modifier           = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ── Status chip ───────────────────────────────────────────────────────────────

@Composable
private fun StatusChip(status: UploadStatus) {
    val (bgColor, textColor, label) = when (status) {
        UploadStatus.PENDING   -> Triple(Color(0xFFFFF9C4), Color(0xFFF9A825), "Pending")
        UploadStatus.UPLOADING -> Triple(Color(0xFFE3F2FD), Color(0xFF1565C0), "Uploading…")
        UploadStatus.SUCCESS   -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "Uploaded")
        UploadStatus.FAILED    -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), "Failed")
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ── Status badge icon ─────────────────────────────────────────────────────────

@Composable
private fun StatusBadge(status: UploadStatus, modifier: Modifier = Modifier) {
    val (icon, tint) = when (status) {
        UploadStatus.PENDING   -> Icons.Filled.HourglassEmpty to Color(0xFFF9A825)
        UploadStatus.UPLOADING -> null to Color.Transparent
        UploadStatus.SUCCESS   -> Icons.Filled.CheckCircle to Color(0xFF43A047)
        UploadStatus.FAILED    -> Icons.Filled.Error to Color(0xFFC62828)
    }

    if (status == UploadStatus.UPLOADING) {
        Box(
            modifier         = modifier.size(20.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier    = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color       = Color.White
            )
        }
    } else if (icon != null) {
        Box(
            modifier = modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = status.name,
                tint               = tint,
                modifier           = Modifier.size(16.dp)
            )
        }
    }
}
