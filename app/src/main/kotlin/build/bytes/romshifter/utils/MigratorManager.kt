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
                val sysModCmd = "ls -1 /data/adb/modules/ROM-Shifter/system/product/app /data/adb/modules/ROM-Shifter/system/product/priv-app /data/adb/modules_update/ROM-Shifter/system/product/app /data/adb/modules_update/ROM-Shifter/system/product/priv-app 2>/dev/null"
                val systemizedLabels = Shell.cmd(sysModCmd).exec().out.toSet()

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

                            val metaFile = File("$currentPath/Data-Migrated/${if (isSys) "System" else "User"}/$label/Meta.txt")
                            val bTime = if (metaFile.exists()) {
                                val sdf = java.text.SimpleDateFormat("hh:mm a • dd MMM, yyyy", Locale.getDefault())
                                "Last backup: " + sdf.format(java.util.Date(metaFile.lastModified()))
                            } else "No backup on device"
                            val safeLabel = label.replace(Regex("[^a-zA-Z0-9_]"), "")
                            val isSystemizedApp = systemizedLabels.contains(safeLabel)

                            AppInfo(label = label, packageName = pkg, version = version, isSystem = isSys, iconBitmap = tinyBitmap, backupTime = bTime, isSystemized = isSystemizedApp)
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

                            val metaFile = File("$currentPath/Data-Migrated/${if (isSys) "System" else "User"}/$label/Meta.txt")
                            val bTime = if (metaFile.exists()) {
                                val sdf = java.text.SimpleDateFormat("hh:mm a • dd MMM, yyyy", Locale.getDefault())
                                "Last backup: " + sdf.format(java.util.Date(metaFile.lastModified()))
                            } else "No backup on device"

                            AppInfo(label = label, packageName = app.packageName, isSystem = isSys, iconBitmap = tinyBitmap, backupTime = bTime, isInstalled = false)                        } else {
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
                apps.addAll(fetchedApps)
            }
            "RestoreUser", "RestoreSystem", "AllBackups" -> {
                val pathType = when (type) { "RestoreUser" -> "User"; "RestoreSystem" -> "System"; else -> "*" }

                val command = "su -mm -c 'grep -H -e \"^Name=\" -e \"^Package=\" -e \"^Version=\" \"$currentPath\"/Data-Migrated/$pathType/*/Meta.txt 2>/dev/null'"
                val result = Shell.cmd(command).exec()
                val iconCacheDir = File(context.cacheDir, "shifter_icons").apply { mkdirs() }

                val appMap = mutableMapOf<String, MutableMap<String, String>>()
                result.out.forEach { line ->
                    val delimiterIdx = line.indexOf("/Meta.txt:")
                    if (delimiterIdx != -1) {
                        val filePath = line.substring(0, delimiterIdx)
                        val kv = line.substring(delimiterIdx + 10)
                        val splitKv = kv.split("=", limit = 2)
                        if (splitKv.size == 2) {
                            appMap.getOrPut(filePath) { mutableMapOf() }[splitKv[0].trim()] = splitKv[1].trim()
                        }
                    }
                }

                val deferredBackups = appMap.map { (basePath, data) ->
                    async(Dispatchers.IO) {
                        val sysType = basePath.split("/").lastOrNull { it == "System" || it == "User" } ?: "User"
                        val isSys = sysType == "System"
                        val label = data["Name"] ?: ""
                        val pkg = data["Package"] ?: ""
                        val version = data["Version"] ?: ""

                        if (label.isNotBlank() && pkg.isNotBlank()) {
                            var iconPath: String? = null
                            val cacheFile = File(iconCacheDir, "${pkg}_icon.png")
                            if (!cacheFile.exists()) {
                                Shell.cmd("su -c \"cp '$basePath/Icon.png' '${cacheFile.absolutePath}' && chmod 644 '${cacheFile.absolutePath}'\"").exec()
                            }
                            if (cacheFile.exists() && cacheFile.length() > 0) iconPath = cacheFile.absolutePath

                            val metaFile = File("$basePath/Meta.txt")
                            val bTime = if (metaFile.exists()) {
                                val sdf = java.text.SimpleDateFormat("hh:mm a • dd MMM, yyyy", Locale.getDefault())
                                "Last backup: " + sdf.format(java.util.Date(metaFile.lastModified()))
                            } else "No backup on device"

                            val isInst = try { pm.getPackageInfo(pkg, 0); true } catch (e: Exception) { false }

                            AppInfo(label = label, packageName = pkg, version = version, isSystem = isSys, iconPath = iconPath, backupTime = bTime, isInstalled = isInst)                        } else null
                    }
                }
                apps.addAll(deferredBackups.awaitAll().filterNotNull())
            }
            else -> {}
        }

        val finalApps = apps.filter { it.packageName != context.packageName }.distinctBy { it.packageName }.sortedBy { it.label.lowercase(Locale.ROOT) }

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
                val baseDir = File(currentPath)
                if (!baseDir.exists()) {
                    Shell.cmd("su -c \"mkdir -p '$currentPath/Data-Migrated' '$currentPath/Live-Partition' && touch '$currentPath/.shifter_dir' && touch '$currentPath/.nomedia'\"").exec()
                }
                val appPaths = selectedApps.joinToString(" ") { "'$currentPath/Data-Migrated/${if (it.isSystem) "System" else "User"}/${it.label}'" }

                val sizeCmd = "su -mm -c \"du -sk $appPaths 2>/dev/null | awk '{s+=\\\$1} END {print s}'\""
                val sizeStr = Shell.cmd(sizeCmd).exec().out.joinToString("").trim()
                val totalFreedKb = sizeStr.toLongOrNull() ?: 0L

                Shell.cmd("su -mm -c \"rm -rf $appPaths\"").exec()

                val formattedSize = formatSize(totalFreedKb.toString())
                updateProgress("Backups Deleted", "Data Successfully Removed", 100)
                onComplete("Deletion Complete!", "Freed: $formattedSize")
                return@withContext
            }

            val isRestore = state.migratorMode.name.contains("RESTORE")
            val cApp = state.globalComponents.contains(1)
            val cData = state.globalComponents.contains(2)
            val cExt = state.globalComponents.contains(3)
            val cMed = state.globalComponents.contains(4)
            val cObb = state.globalComponents.contains(5)
            val cId = state.globalComponents.contains(6)
            val appPartsMap = mutableMapOf<String, String>()

            if (isRestore) {
                selectedApps.forEach { app ->
                    val sysType = if (app.isSystem) "System" else "User"
                    val basePath = "$currentPath/Data-Migrated/$sysType/${app.label}"
                    val parts = mutableListOf<String>()
                    if (cApp && File("$basePath/App.shift").exists()) parts.add("App")
                    if (cData && (File("$basePath/Data.shift").exists() || File("$basePath/UserDe.shift").exists())) parts.add("Data")
                    if (cExt && File("$basePath/ExtData.shift").exists()) parts.add("ExtData")
                    if (cMed && File("$basePath/Media.shift").exists()) parts.add("Media")
                    if (cObb && File("$basePath/Obb.shift").exists()) parts.add("Obb")
                    if (cId) {
                        try {
                            val metaFile = File("$basePath/Meta.txt")
                            if (metaFile.exists() && metaFile.readText().lines().any { it.startsWith("SSAID=") && it.length > 6 }) {
                                parts.add("Android ID")
                            }
                        } catch (_: Exception) {}
                    }
                    appPartsMap[app.packageName] = parts.joinToString(" • ")
                }
            } else {
                val script = java.lang.StringBuilder()
                selectedApps.forEach { app ->
                    val pkg = app.packageName
                    script.append("(\n")
                    script.append("res=\"\"\n")
                    if (cApp) script.append("pm path $pkg >/dev/null 2>&1 && res=\"\$res|App\"\n")
                    if (cData) script.append("([ -d \"/data/data/$pkg\" ] || [ -d \"/data/user_de/0/$pkg\" ]) && res=\"\$res|Data\"\n")
                    if (cExt) script.append("[ -d \"/data/media/0/Android/data/$pkg\" ] && res=\"\$res|ExtData\"\n")
                    if (cMed) script.append("[ -d \"/data/media/0/Android/media/$pkg\" ] && res=\"\$res|Media\"\n")
                    if (cObb) script.append("[ -d \"/data/media/0/Android/obb/$pkg\" ] && res=\"\$res|Obb\"\n")

                    if (cId) script.append("grep -q \"package=\\\"$pkg\\\"\" /data/system/users/0/settings_ssaid.xml 2>/dev/null && res=\"\$res|Android ID\"\n")
                    script.append("echo \"$pkg==\$res\"\n")
                    script.append(") &\n")
                }
                script.append("wait\n")
                val out = Shell.cmd("su -mm -c '${script.toString().replace("'", "'\\''")}'").exec().out
                out.forEach { line ->
                    val split = line.split("==")
                    if (split.size == 2) {
                        val comps = split[1].split("|").filter { it.isNotBlank() }.joinToString(" • ")
                        appPartsMap[split[0]] = comps
                    }
                }
            }

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

            val command = "su -mm -c \"sh /data/adb/#Shifter/ROM-Shifter.sh $operation '$compsString' '$currentPath'\""
            val actText = if (isRestore) "Restoring Apps" else "Backing up Apps"
            if (state.migratorMode == MigratorMode.BACKUP_APPS) {
                val iconScript = java.lang.StringBuilder()
                selectedApps.forEach { app ->
                    try {
                        val rawIcon = context.packageManager.getApplicationIcon(app.packageName)
                        val bitmap = getTinyBitmap(rawIcon)

                        if (bitmap != null) {
                            val tempFile = File(context.cacheDir, "icon_${app.packageName}.png")
                            FileOutputStream(tempFile).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
                            val sysType = if (app.isSystem) "System" else "User"
                            val destDir = "$currentPath/Data-Migrated/$sysType/${app.label}"

                            iconScript.append("mkdir -p '$destDir'\n")
                            iconScript.append("cp '${tempFile.absolutePath}' '$destDir/Icon.png'\n")
                            iconScript.append("chmod 644 '$destDir/Icon.png'\n")
                            iconScript.append("rm -f '${tempFile.absolutePath}'\n")
                        }
                    } catch (_: Exception) { }
                }
                if (iconScript.isNotEmpty()) {
                    val scriptFile = File(context.cacheDir, "copy_icons.sh")
                    scriptFile.writeText(iconScript.toString())
                    Shell.cmd("su -c 'sh \"${scriptFile.absolutePath}\"'").exec()
                    scriptFile.delete()
                }
            }

            ShellEngine.executeShifterCommand(command).collect { event ->
                when (event) {
                    is ShifterEvent.BackupProgress -> {
                        val app = selectedApps.find { it.label == event.label }
                        val activeParts = appPartsMap[app?.packageName] ?: ""
                        val partsString = if (activeParts.isNotEmpty()) "\n- $activeParts" else ""

                        val rawKb = event.size.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0L
                        val formattedSize = if (rawKb > 0) formatSize(rawKb.toString()) else ""
                        val sizeInfo = if (formattedSize.isNotEmpty()) " [Size: $formattedSize]" else ""

                        updateProgress(actText, "${event.label}$sizeInfo (${event.current}/${event.total})$partsString", event.percent)
                    }
                    is ShifterEvent.GlobalDone -> {
                        val smartSize = formatSize(event.totalKb)
                        val actDone = if (isRestore) "Restore Complete!" else "Backup Complete!"

                        val timeSecInt = event.timeSec.toIntOrNull() ?: 0
                        val timeStr = if (timeSecInt >= 60) "${timeSecInt / 60}m ${timeSecInt % 60}s" else "${timeSecInt}s"

                        onComplete(actDone, "Total Size: $smartSize | Time: $timeStr")
                    }
                    else -> {}
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