package build.bytes.romshifter.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.PowerManager
import build.bytes.romshifter.models.AppInfo
import build.bytes.romshifter.models.AppState
import build.bytes.romshifter.models.MigratorMode
import build.bytes.romshifter.models.ShifterEvent
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

object MigratorManager {

    private var userAppsCache: List<AppInfo>? = null
    private var sysAppsCache: List<AppInfo>? = null
    private var uninstalledCache: List<AppInfo>? = null

    fun clearCache() {
        userAppsCache = null
        sysAppsCache = null
        uninstalledCache = null
    }

    suspend fun fetchAppsList(context: Context, currentPath: String, type: String, append: Boolean, currentList: List<AppInfo>): List<AppInfo> = withContext(Dispatchers.IO) {

        if (!append) {
            when (type) {
                "User" -> userAppsCache?.let { return@withContext it }
                "System" -> sysAppsCache?.let { return@withContext it }
                "Uninstalled" -> uninstalledCache?.let { return@withContext it }
            }
        }

        val pm = context.packageManager
        val apps = if (append) currentList.toMutableList() else mutableListOf()

        when (type) {
            "User", "System", "AllInstalled" -> {
                val installedApps = pm.getInstalledApplications(0)
                val fetchedApps = installedApps.map { app ->
                    async(Dispatchers.IO) {
                        val isSys = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        if (type == "AllInstalled" || (type == "System" && isSys) || (type == "User" && !isSys)) {
                            val label = app.loadLabel(pm).toString().replace("|", "").replace("\n", "").trim()
                            val pkg = app.packageName.replace("|", "").replace("\n", "").trim()
                            val version = try { pm.getPackageInfo(app.packageName, 0).versionName?.replace("|", "")?.replace("\n", "")?.trim() ?: "" } catch(_: Exception) { "" }

                            val rawIcon = try { app.loadIcon(pm) } catch(_: Exception) { null }
                            val tinyBitmap = getTinyBitmap(rawIcon)

                            AppInfo(label = label, packageName = pkg, version = version, isSystem = isSys, iconBitmap = tinyBitmap)
                        } else {
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
                apps.addAll(fetchedApps)
            }
            "Uninstalled" -> {
                val allApps = pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)
                val activeApps = pm.getInstalledApplications(0).map { it.packageName }.toSet()

                val fetchedApps = allApps.map { app ->
                    async(Dispatchers.IO) {
                        if (!activeApps.contains(app.packageName)) {
                            val apkExists = try {
                                app.sourceDir != null && File(app.sourceDir).exists()
                            } catch (e: Exception) { false }

                            if (!apkExists) {
                                return@async null
                            }

                            val isSys = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                            val label = app.loadLabel(pm).toString().replace("|", "").replace("\n", "").trim()

                            val rawIcon = try { app.loadIcon(pm) } catch(_: Exception) { null }
                            val tinyBitmap = getTinyBitmap(rawIcon)

                            AppInfo(label = label, packageName = app.packageName, isSystem = isSys, iconBitmap = tinyBitmap)
                        } else {
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
                apps.addAll(fetchedApps)
            }
            "RestoreUser", "RestoreSystem", "AllBackups" -> {
                val pathType = when (type) { "RestoreUser" -> "User"; "RestoreSystem" -> "System"; else -> "*" }
                val command = "su -mm -c 'for f in \"$currentPath\"/Data-Migrated/$pathType/*/Meta.txt; do if [ -f \"\$f\" ]; then dir=\"\$(dirname \"\$f\")\"; sysType=\"\$(basename \"\$(dirname \"\$dir\")\")\"; echo \"\$sysType|\$(grep \"^Name=\" \"\$f\" | cut -d= -f2)|\$(grep \"^Package=\" \"\$f\" | cut -d= -f2)|\$(grep \"^Version=\" \"\$f\" | cut -d= -f2)|\$dir/Icon.png\"; fi done'"

                val result = Shell.cmd(command).exec()
                val iconCacheDir = File(context.cacheDir, "shifter_icons").apply { mkdirs() }

                val deferredBackups = result.out.map { line ->
                    async(Dispatchers.IO) {
                        val parts = line.split("|")
                        if (parts.size >= 4 && parts[1].isNotBlank() && parts[2].isNotBlank()) {
                            val isSys = parts[0] == "System"
                            val pkg = parts[2].replace("\n", "").trim()
                            var iconPath: String? = null
                            if (parts.size >= 5) {
                                val cacheFile = File(iconCacheDir, "${pkg}_icon.png")
                                Shell.cmd("su -c \"cp '${parts[4]}' '${cacheFile.absolutePath}' && chmod 644 '${cacheFile.absolutePath}'\"").exec()
                                if (cacheFile.exists() && cacheFile.length() > 0) iconPath = cacheFile.absolutePath
                            }
                            AppInfo(label = parts[1], packageName = pkg, version = parts[3], isSystem = isSys, iconPath = iconPath)
                        } else {
                            null
                        }
                    }
                }
                apps.addAll(deferredBackups.awaitAll().filterNotNull())
            }
            else -> {}
        }

        val finalApps = apps.distinctBy { it.packageName }.sortedBy { it.label.lowercase(Locale.ROOT) }

        if (!append) {
            when (type) {
                "User" -> userAppsCache = finalApps
                "System" -> sysAppsCache = finalApps
                "Uninstalled" -> uninstalledCache = finalApps
            }
        }

        return@withContext finalApps
    }

    suspend fun runDynamicOperation(
        context: Context, state: AppState, selectedApps: List<AppInfo>, currentPath: String,
        updateProgress: (String, String, Int) -> Unit, onComplete: (String, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        var wakeLock: PowerManager.WakeLock? = null
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ROMShifter::OperationWakelock")
            wakeLock.acquire(15 * 60 * 1000L)
        } catch (_: SecurityException) { }

        try {
            if (state.migratorMode == MigratorMode.MANAGE) {
                var totalFreedKb = 0L
                selectedApps.forEach { app ->
                    val sysType = if (app.isSystem) "System" else "User"
                    val appPath = "$currentPath/Data-Migrated/$sysType/${app.label}"
                    val sizeStr = Shell.cmd("su -mm -c \"du -sk '$appPath' | awk '{print \\\$1}'\"").exec().out.joinToString("").trim()
                    totalFreedKb += sizeStr.toLongOrNull() ?: 0L
                    Shell.cmd("su -mm -c \"rm -rf '$appPath'\"").exec()
                }
                val formattedSize = formatSize(totalFreedKb.toString())
                updateProgress("Backups Deleted", "Data Successfully Removed", 100)
                onComplete("Deletion Complete!", "Freed: $formattedSize") // The ViewModel will automatically append " | Apps: X" to this!
                return@withContext
            }

            // --- SMART PRE-SCAN LOGIC BEGINS ---
            val isRestore = state.migratorMode.name.contains("RESTORE")
            val cApp = state.globalComponents.contains(1)
            val cData = state.globalComponents.contains(2)
            val cExt = state.globalComponents.contains(3)
            val cMed = state.globalComponents.contains(4)
            val cObb = state.globalComponents.contains(5)
            val cId = state.globalComponents.contains(6)
            val appPartsMap = mutableMapOf<String, String>()

            if (isRestore) {
                // Instantly checks the backup directory to see which parts were actually saved!
                selectedApps.forEach { app ->
                    val sysType = if (app.isSystem) "System" else "User"
                    val basePath = "$currentPath/Data-Migrated/$sysType/${app.label}"
                    val parts = mutableListOf<String>()
                    if (cApp && File("$basePath/App.bundle.pack").exists()) parts.add("App")
                    if (cData && (File("$basePath/Data.bundle.pack").exists() || File("$basePath/UserDe.bundle.pack").exists())) parts.add("Data")
                    if (cExt && File("$basePath/ExtData.bundle.pack").exists()) parts.add("ExtData")
                    if (cMed && File("$basePath/Media.bundle.pack").exists()) parts.add("Media")
                    if (cObb && File("$basePath/Obb.bundle.pack").exists()) parts.add("Obb")
                    if (cId) {
                        try {
                            val metaFile = File("$basePath/Meta.txt")
                            // Ensures the Meta.txt has a valid SSAID entry that isn't blank
                            if (metaFile.exists() && metaFile.readText().lines().any { it.startsWith("SSAID=") && it.length > 6 }) {
                                parts.add("Android ID")
                            }
                        } catch (_: Exception) {}
                    }
                    appPartsMap[app.packageName] = parts.joinToString(" • ")
                }
            } else {
                // Runs a hyper-fast root shell script to verify exactly which folders and IDs exist!
                val script = java.lang.StringBuilder()
                selectedApps.forEach { app ->
                    val pkg = app.packageName
                    script.append("res=\"\"\n")
                    if (cApp) script.append("pm path $pkg >/dev/null 2>&1 && res=\"\$res|App\"\n")
                    if (cData) script.append("([ -d \"/data/data/$pkg\" ] || [ -d \"/data/user_de/0/$pkg\" ]) && res=\"\$res|Data\"\n")
                    if (cExt) script.append("[ -d \"/data/media/0/Android/data/$pkg\" ] && res=\"\$res|ExtData\"\n")
                    if (cMed) script.append("[ -d \"/data/media/0/Android/media/$pkg\" ] && res=\"\$res|Media\"\n")
                    if (cObb) script.append("[ -d \"/data/media/0/Android/obb/$pkg\" ] && res=\"\$res|Obb\"\n")
                    // Checks the system XML to see if this specific package has generated an Android ID
                    if (cId) script.append("grep -q \"package=\\\"$pkg\\\"\" /data/system/users/0/settings_ssaid.xml 2>/dev/null && res=\"\$res|Android ID\"\n")
                    script.append("echo \"$pkg==\$res\"\n")
                }
                val out = Shell.cmd("su -mm -c '${script.toString().replace("'", "'\\''")}'").exec().out
                out.forEach { line ->
                    val split = line.split("==")
                    if (split.size == 2) {
                        val comps = split[1].split("|").filter { it.isNotBlank() }.joinToString(" • ")
                        appPartsMap[split[0]] = comps
                    }
                }
            }
            // --- SMART PRE-SCAN LOGIC ENDS ---

            val targetData = selectedApps.joinToString("\n") { app ->
                val sysType = if (app.isSystem) "System" else "User"
                "${app.packageName}|${app.label}|${app.version}|$sysType"
            } + "\n"

            val targetFile = File(context.cacheDir, "shifter_targets.txt")
            targetFile.writeText(targetData)
            Shell.cmd("su -mm -c \"rm -f /data/local/tmp/shifter_targets.txt && cat '${targetFile.absolutePath}' > /data/local/tmp/shifter_targets.txt && chmod 666 /data/local/tmp/shifter_targets.txt\"").exec()
            targetFile.delete()

            val operation = if (isRestore) "--restore" else "--backup"
            val compsString = state.globalComponents.sorted().joinToString(" ")

            val command = "su -mm -c \"sh /data/adb/#Shifter/ROM-Shifter.sh $operation '$compsString'\""
            val actText = if (isRestore) "Restoring Apps" else "Backing up Apps"

            ShellEngine.executeShifterCommand(command).collect { event ->
                when (event) {
                    is ShifterEvent.BackupProgress -> {
                        // Dynamically pull the exact parts found for THIS app!
                        val app = selectedApps.find { it.label == event.label }
                        val activeParts = appPartsMap[app?.packageName] ?: ""
                        val partsString = if (activeParts.isNotEmpty()) "\nParts: $activeParts" else ""

                        // Sent seamlessly to the notification and UI
                        updateProgress(actText, "${event.label} (${event.current}/${event.total})$partsString", event.percent)
                    }
                    is ShifterEvent.GlobalDone -> {
                        val smartSize = formatSize(event.totalKb)
                        val actDone = if (isRestore) "Restore Complete!" else "Backup Complete!"
                        onComplete(actDone, "Total Size: $smartSize | Time: ${event.timeSec}s")
                    }
                    else -> {}
                }
            }

            if (state.migratorMode == MigratorMode.BACKUP_APPS) {
                selectedApps.forEach { app ->
                    try {
                        val rawIcon = context.packageManager.getApplicationIcon(app.packageName)
                        val bitmap = getTinyBitmap(rawIcon)

                        if (bitmap != null) {
                            val tempFile = File(context.cacheDir, "temp_icon.png")
                            FileOutputStream(tempFile).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
                            val sysType = if (app.isSystem) "System" else "User"
                            Shell.cmd("su -c \"cp '${tempFile.absolutePath}' '$currentPath/Data-Migrated/$sysType/${app.label}/Icon.png' && chmod 644 '$currentPath/Data-Migrated/$sysType/${app.label}/Icon.png'\"").exec()
                            tempFile.delete()
                        }
                    } catch (_: Exception) { }
                }
            }
        } finally {
            wakeLock?.let { if (it.isHeld) it.release() }
        }
    }

    private fun getTinyBitmap(drawable: Drawable?): Bitmap? {
        if (drawable == null) return null
        return try {
            val size = 120
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun formatSize(kbString: String): String {
        val kb = kbString.toLongOrNull() ?: return "$kbString KB"
        return when {
            kb >= 1048576 -> String.format(Locale.US, "%.2f GB", kb / 1048576.0)
            kb >= 1024 -> String.format(Locale.US, "%.2f MB", kb / 1024.0)
            else -> "$kb KB"
        }
    }
}