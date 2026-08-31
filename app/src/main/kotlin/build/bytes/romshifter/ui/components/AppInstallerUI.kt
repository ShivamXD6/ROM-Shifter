package build.bytes.romshifter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import build.bytes.romshifter.models.AppInstallInfo
import coil.compose.AsyncImage

@Composable
fun AppInstallerDialog(
    app: AppInstallInfo,
    onDismiss: () -> Unit,
    onInstall: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            if (app.isAnalysisComplete) {
                Button(
                    onClick = onInstall,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(getButtonText(app))
                }
            }
        },
        dismissButton = {
            if (!app.status.startsWith("Analyzing")) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = app.iconPath,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    if (!app.isAnalysisComplete) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        if (app.label == "Analyzing...") "Analyzing File..." else app.label,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (app.isAnalysisComplete) app.packageName else "Extracting metadata...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (app.isAnalysisComplete) {
                    Text(
                        "Install apps with blazing fast speed as play store",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        textAlign = TextAlign.Center
                    )

                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            DetailHeader("Selected Version")
                            DetailValue(app.version)
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            DetailHeader("Target Android", textAlign = TextAlign.End)
                            DetailValue(app.targetSdk, textAlign = TextAlign.End)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            DetailHeader("Current Version")
                            if (app.isInstalled) {
                                DetailValue(app.installedVersion ?: "Unknown")
                            } else {
                                DetailValue("Not installed", isDimmed = true)
                            }
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            DetailHeader("Size", textAlign = TextAlign.End)
                            DetailValue(app.size, textAlign = TextAlign.End)
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Please wait while the file is being processed...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun DetailHeader(text: String, textAlign: TextAlign = TextAlign.Start) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
        textAlign = textAlign,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun DetailValue(
    text: String,
    isDimmed: Boolean = false,
    textAlign: TextAlign = TextAlign.Start
) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Bold,
        color = if (isDimmed) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun getButtonText(app: AppInstallInfo): String {
    if (!app.isInstalled) return "Install"
    return when {
        app.installedVersionCode != null && app.versionCode > app.installedVersionCode -> "Update"
        app.installedVersionCode != null && app.versionCode < app.installedVersionCode -> "Downgrade"
        app.installedVersionCode == app.versionCode -> "Reinstall"
        else -> "Install"
    }
}

@Composable
fun BatchInstallerDialog(
    apps: List<AppInstallInfo>,
    isRunning: Boolean,
    isAnalyzing: Boolean,
    currentStep: String,
    totalTime: Long,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onToggleSelect: (String) -> Unit
) {
    val selectedCount = apps.count { it.isSelected && it.isAnalysisComplete }

    AlertDialog(
        onDismissRequest = { if (!isRunning && !isAnalyzing) onCancel() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isRunning) Icons.Default.Update else Icons.Default.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    if (isRunning) "Installing..." else if (isAnalyzing) "Analyzing Apps..." else if (totalTime > 0) "Installation Complete" else "Batch Installer",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        confirmButton = {
            if (!isRunning && !isAnalyzing && totalTime == 0L) {
                Button(onClick = onInstall, enabled = selectedCount > 0) {
                    Text("Install Selected ($selectedCount)")
                }
            } else if (totalTime > 0L) {
                Button(onClick = onCancel) { Text("Close") }
            }
        },
        dismissButton = {
            if (!isRunning && !isAnalyzing && totalTime == 0L) {
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (isRunning) {
                    val installingApps =
                        apps.filter { it.status == "Installing" || it.status == "Pending" }.take(3)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        installingApps.forEach { app ->
                            Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                                AsyncImage(
                                    model = app.iconPath,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                )
                                if (app.status == "Installing") {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(56.dp),
                                        strokeWidth = 3.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))

                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(Modifier.height(8.dp))
                    Text(
                        currentStep,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (totalTime > 0L) {
                    val finalApps = apps.filter { it.isSelected && it.isAnalysisComplete }
                    val errorCount = finalApps.count { it.status == "Error" }
                    val successCount = finalApps.count { it.status == "Done" }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (errorCount > 0) {
                            Icon(
                                Icons.Default.Error,
                                null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Installation finished with errors",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                "Succeeded: $successCount, Failed: $errorCount",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            Icon(
                                Icons.Default.CheckCircle,
                                null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Successfully installed $successCount apps",
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            "Total time taken: ${totalTime}s",
                            style = MaterialTheme.typography.bodySmall
                        )

                        if (errorCount > 0) {
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Failed Apps:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.fillMaxWidth()
                            )
                            LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) {
                                items(finalApps.filter { it.status == "Error" }) { app ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Error,
                                            null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            app.label,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                } else {
                    Text(
                        "Install apps with blazing fast speed as play store",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        textAlign = TextAlign.Center
                    )
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp)
                    ) {
                        items(apps) { app ->
                            AppInstallItem(app, onToggleSelect = { onToggleSelect(app.path) })
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun AppInstallItem(app: AppInstallInfo, onToggleSelect: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.clickable(enabled = app.isAnalysisComplete) { onToggleSelect() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = app.iconPath,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(MaterialTheme.shapes.small)
                )
                if (app.status == "Analyzing") {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        app.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (app.isAnalysisComplete) {
                        Text(
                            " (${app.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val versionText =
                        if (app.isAnalysisComplete) app.version else "Analyzing file..."

                    Text(
                        versionText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (app.isAnalysisComplete && app.isInstalled) {
                        val isUpdate =
                            app.installedVersionCode != null && app.versionCode > app.installedVersionCode
                        val isDowngrade =
                            app.installedVersionCode != null && app.versionCode < app.installedVersionCode

                        if (isUpdate || isDowngrade) {
                            Spacer(Modifier.width(6.dp))
                            val comparisonIcon = Icons.Default.ChevronRight
                            val rotation = if (isDowngrade) 180f else 0f
                            val tint = if (isUpdate) Color(0xFF4CAF50) else Color(0xFFF44336)

                            Icon(
                                imageVector = comparisonIcon,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(14.dp)
                                    .graphicsLayer { rotationZ = rotation },
                                tint = tint
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                app.installedVersion ?: "Unknown",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        } else if (app.installedVersionCode == app.versionCode) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            if (app.isAnalysisComplete) {
                Spacer(Modifier.width(8.dp))
                CircularCheckbox(checked = app.isSelected, onCheckedChange = { onToggleSelect() })
            } else if (app.status == "Done") {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
            } else if (app.status == "Error") {
                Icon(
                    Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
