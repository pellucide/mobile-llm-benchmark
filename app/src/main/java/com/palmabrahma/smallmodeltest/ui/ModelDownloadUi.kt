package com.palmabrahma.smallmodeltest.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.flowWithLifecycle
import com.palmabrahma.smallmodeltest.models.ModelManager
import com.palmabrahma.smallmodeltest.models.ModelType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

@Composable
fun ModelDownloadDialog(
    modelType: ModelType,
    modelManager: ModelManager,
    onDownloadComplete: () -> Unit,
    onDismiss: () -> Unit
) {
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadStatus by remember { mutableStateOf("Preparing download...") }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !isDownloading,
            dismissOnClickOutside = !isDownloading
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Download ${modelType.displayName}",
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Model info
                Text(
                    text = "Model size: ${modelType.sizeMB}MB",
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = "Parameters: ${modelType.parameters}",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Progress indicator
                if (isDownloading || downloadProgress > 0) {
                    LinearProgressIndicator(
                        progress = {downloadProgress / 100f},
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = downloadStatus,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "${downloadProgress.toInt()}%",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }

                // Error message
                downloadError?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!isDownloading) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                isDownloading = true
                                downloadError = null
                                coroutineScope.launch {
                                    try {
                                        modelManager.downloadModel(modelType).collect { progress ->
                                            downloadProgress = progress.percentComplete
                                            downloadStatus = "${progress.fileName}: ${progress.bytesDownloaded / (1024 * 1024)}MB / ${progress.totalBytes / (1024 * 1024)}MB"

                                            if (progress.isComplete) {
                                                onDownloadComplete()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        downloadError = e.message ?: "Download failed"
                                        isDownloading = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Download")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModelStatusCard(
    modelType: ModelType,
    modelManager: ModelManager,
    onDownloadClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val isDownloaded = remember(modelType) {
        modelManager.isModelDownloaded(modelType)
    }
    val modelSize = remember(modelType) {
        if (isDownloaded) modelManager.getModelSize(modelType) else 0L
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = modelType.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = if (isDownloaded) {
                        "Downloaded (${modelSize}MB)"
                    } else {
                        "Not downloaded (${modelType.sizeMB}MB)"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDownloaded) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            if (isDownloaded) {
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete model"
                    )
                }
            } else {
                Button(onClick = onDownloadClick) {
                    Text("Download")
                }
            }
        }
    }
}

@Composable
fun ModelManagementScreen(
    modelManager: ModelManager
) {
    var selectedModelForDownload by remember { mutableStateOf<ModelType?>(null) }
    val totalStorageUsed = remember { modelManager.getTotalStorageUsed() }
    val availableStorage = remember { modelManager.getAvailableStorage() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Model Management",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Storage info
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Storage", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Used: ${totalStorageUsed}MB")
                Text("Available: ${availableStorage}MB")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Model list
        Text(
            text = "Models",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        ModelType.values().forEach { modelType ->
            ModelStatusCard(
                modelType = modelType,
                modelManager = modelManager,
                onDownloadClick = {
                    if (modelManager.hasEnoughSpace(modelType)) {
                        selectedModelForDownload = modelType
                    } else {
                        // Show error about insufficient space
                    }
                },
                onDeleteClick = {
                    modelManager.deleteModel(modelType)
                }
            )
        }
    }

    // Download dialog
    selectedModelForDownload?.let { modelType ->
        ModelDownloadDialog(
            modelType = modelType,
            modelManager = modelManager,
            onDownloadComplete = {
                selectedModelForDownload = null
            },
            onDismiss = {
                selectedModelForDownload = null
            }
        )
    }
}