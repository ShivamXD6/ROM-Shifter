package build.bytes.romshifter.ui.screens

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import build.bytes.romshifter.MainViewModel
import build.bytes.romshifter.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(context: Context, viewModel: MainViewModel, isDarkTheme: Boolean, onThemeToggle: () -> Unit) {
    val savedPath by viewModel.savedPath.collectAsState()
    var inputPath by remember { mutableStateOf(savedPath) }
    var isMoving by remember { mutableStateOf(false) }

    LaunchedEffect(savedPath) { if (!isMoving) inputPath = savedPath }
    val isEditing = inputPath != savedPath
    var showResetDialog by remember { mutableStateOf(false) }
    var showAboutSheet by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
            val split = docId.split(":")
            val basePath = android.os.Environment.getExternalStorageDirectory().absolutePath
            val path = if ("primary".equals(split[0], true)) "$basePath/${split.getOrNull(1) ?: ""}" else "/storage/${split[0]}/${split.getOrNull(1) ?: ""}"
            val finalPath = if (path.endsWith("#Shifter")) path else "$path/#Shifter"
            inputPath = finalPath
            isMoving = true
            viewModel.migrateFolder(finalPath) { isMoving = false }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Application Data", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = { Text("This will permanently clear application data, remove binary engines, reset configurations, and restart the app. Are you sure?") },            confirmButton = { Button(onClick = { showResetDialog = false; viewModel.resetApp(context) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Cancel") } }
        )
    }

    if (showAboutSheet) {
        ModalBottomSheet(onDismissRequest = { showAboutSheet = false }) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text("About & Support", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("App info, social links, and donations", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth().clickable { openUriSafely(context, "https://t.me/buildbytes") }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Join Telegram", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Join the Build Bytes community", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth().clickable { openUriSafely(context, "https://www.youtube.com/@BuildBytesX") }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("YouTube Channel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Subscribe to BuildBytesX", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth().clickable { openUriSafely(context, "https://github.com/ShivamXD6/ROM-Shifter-App/") }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Star on GitHub", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("View source code and releases", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth().clickable { openUriSafely(context, "upi://pay?pa=shivamashokdhage6@oksbi&pn=Build%20Bytes&cu=INR") }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Favorite, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Donate via UPI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Support via GPay, PhonePe, Paytm, BHIM", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth().clickable { openUriSafely(context, "https://paypal.me/ShivamXD6") }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Favorite, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Donate via PayPal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Support via international cards", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Made by @ShastikXD | Build Bytes Team", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionHeader("App Configuration", "Manage storage and directories")
        ElevatedCard(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, contentDescription = "Folder", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Shifter Location", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("All backups and files are saved here", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { if (!isMoving) launcher.launch(null) }) { Icon(Icons.Default.FolderOpen, contentDescription = "Browse") }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(value = inputPath, onValueChange = { inputPath = it }, singleLine = true, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth())

                    AnimatedVisibility(visible = isEditing || isMoving, enter = expandVertically(), exit = shrinkVertically()) {
                        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                            Spacer(modifier = Modifier.height(12.dp))
                            if (isMoving) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Saving Directory...", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                }
                            } else {
                                Button(onClick = { isMoving = true; viewModel.migrateFolder(inputPath) { isMoving = false } }, shape = RoundedCornerShape(24.dp)) { Text("Apply Path") }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode, contentDescription = "Theme", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(if (isDarkTheme) "Dark Mode" else "Light Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Switch(checked = isDarkTheme, onCheckedChange = { onThemeToggle() })
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                Row(modifier = Modifier.fillMaxWidth().clickable { showAboutSheet = true }.padding(horizontal = 20.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = "About", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("About & Support", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("App info, social links, and donations", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                Row(modifier = Modifier.fillMaxWidth().clickable { showResetDialog = true }.padding(horizontal = 20.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DeleteForever, contentDescription = "Reset", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Reset Application", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        Text("Clear all data and reset engine", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)                    }
                }
            }
        }
    }
}