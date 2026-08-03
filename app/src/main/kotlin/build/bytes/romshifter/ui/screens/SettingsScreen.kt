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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.OpenInNew
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
    val currentFolderName = savedPath.trimEnd('/').substringAfterLast('/')
    var showResetDialog by remember { mutableStateOf(false) }

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
            text = { Text("This will permanently clear application caches, remove binary engines, reset configurations, and restart the app. Are you sure?") },
            confirmButton = { Button(onClick = { showResetDialog = false; viewModel.resetApp(context) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Wipe & Restart") } },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Cancel") } }
        )
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
                            Text("Will automatically append /$currentFolderName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Row(modifier = Modifier.fillMaxWidth().clickable { showResetDialog = true }.padding(horizontal = 20.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DeleteForever, contentDescription = "Reset", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Reset Application", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        Text("Clear all caches and reset engine", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        SectionHeader("About & Support", "App info, social links, and donations")
        ElevatedCard(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Row(modifier = Modifier.fillMaxWidth().clickable { openUriSafely(context, "https://t.me/buildbytes") }.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.OpenInNew, contentDescription = "Telegram", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Join Telegram", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Join the Build Bytes community", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                Row(modifier = Modifier.fillMaxWidth().clickable { openUriSafely(context, "https://github.com/ShivamXD6/ROM-Shifter-App/") }.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.OpenInNew, contentDescription = "GitHub", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Star on GitHub", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("View source code and releases", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                Row(modifier = Modifier.fillMaxWidth().clickable { openUriSafely(context, "upi://pay?pa=shivam.dhage@superyes&pn=Build%20Bytes&cu=INR") }.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Favorite, contentDescription = "UPI", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Donate via UPI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Support via GPay, PhonePe, Paytm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                Row(modifier = Modifier.fillMaxWidth().clickable { openUriSafely(context, "https://paypal.me/ShivamXD6") }.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Favorite, contentDescription = "PayPal", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Donate via PayPal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Support via international cards", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text("Developed with ♥ by all the Build Bytes team.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}