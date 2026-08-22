package build.bytes.romshifter.utils

import android.annotation.SuppressLint
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.PowerManager
import android.os.storage.StorageManager.UUID_DEFAULT
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.graphics.createBitmap
import build.bytes.romshifter.BuildConfig
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

    @SuppressLint("SimpleDateFormat")
    suspend fun fetchAppsList(
        context: Context, currentPath: String, type: String,
        append: Boolean = false, currentList: List<AppInfo> = emptyList(),
        isManage: Boolean = false, includeOverhead: Boolean = true
    ): List<AppInfo> = withContext(Dispatchers.IO) {
        if (!append) {
            when (type) {
                "User" -> userAppsCache?.let { return@withContext it }
                "System" -> sysAppsCache?.let { return@withContext it }
                "Uninstalled" -> uninstalledCache?.let { return@withContext it }
            }
        }

        val pm = context.packageManager
        val apps = if (append) currentList.toMutableList() else mutableListOf()
        val sdf = java.text.SimpleDateFormat("hh:mm a • dd MMM, yyyy", Locale.getDefault())

        when (type) {
            "User", "System", "AllInstalled", "Uninstalled" -> {
                val sysModCmd =
                    "ls -1 /data/adb/modules/ROM-Shifter/system/product/app /data/adb/modules_update/ROM-Shifter/system/product/app 2>/dev/null"
                val systemizedLabels = Shell.cmd(sysModCmd).exec().out.toSet()

                val iconCacheDir = File(context.cacheDir, "shifter_icons").apply { mkdirs() }

                val (extDataSizes, mediaSizes) = fetchExternalSizes()

                val installedApps = if (type == "Uninstalled") {
                    pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)
                } else {
                    pm.getInstalledApplications(0)
                }
                val fetchedApps = installedApps.map { app ->
                    async(Dispatchers.IO) {
                        val isSys = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        val isInst = (app.flags and ApplicationInfo.FLAG_INSTALLED) != 0

                        val shouldInclude = when (type) {
                            "AllInstalled" -> isInst
                            "System" -> isInst && isSys
                            "User" -> isInst && !isSys
                            "Uninstalled" -> !isInst
                            else -> false
                        }

                        if (shouldInclude) {
                            val label = app.loadLabel(pm).toString().replace("|", "").replace("\n", "").trim()
                            val pkg = app.packageName.replace("|", "").replace("\n", "").trim()
                            val version = try {
                                val pi = pm.getPackageInfo(app.packageName, 0)
                                PackageInfoCompat.getLongVersionCode(pi).toString()
                            } catch (_: Exception) {
                                ""
                            }

                            val iconFile = File(iconCacheDir, "${pkg}_icon.png")
                            if (!iconFile.exists()) {
                                try {
                                    val rawIcon = app.loadIcon(pm)
                                    val bitmap = getTinyBitmap(rawIcon)
                                    if (bitmap != null) {
                                        FileOutputStream(iconFile).use { out ->
                                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                                        }
                                    }
                                } catch (_: Exception) {
                                }
                            }
                            val iconPath = if (iconFile.exists()) iconFile.absolutePath else null

                            val metaFile =
                                File("$currentPath/Apps/${if (isSys) "System" else "User"}/$label/Meta.txt")
                            val bTime = if (metaFile.exists()) {
                                sdf.format(java.util.Date(metaFile.lastModified()))
                            } else "No backup on device"

                            val stats = getDetailedPackageSizes(context, pkg)
                            val appSizeKb = stats.first
                            val dataSizeKb = stats.second + (extDataSizes[pkg] ?: 0L)
                            val mediaSizeKb = mediaSizes[pkg] ?: 0L

                            val displaySizeKb =
                                appSizeKb + dataSizeKb + mediaSizeKb + if (includeOverhead) 31 else 0

                            val safeLabel = label.replace(Regex("[^a-zA-Z0-9_]"), "")
                            val isSystemizedApp = systemizedLabels.contains(safeLabel)

                            AppInfo(
                                label = label,
                                packageName = pkg,
                                version = version,
                                isSystem = isSys,
                                iconPath = iconPath,
                                backupTime = bTime,
                                size = formatSize(displaySizeKb.toString()),
                                isSystemized = isSystemizedApp,
                                appSizeKb = appSizeKb,
                                dataSizeKb = dataSizeKb,
                                mediaSizeKb = mediaSizeKb,
                                isInstalled = isInst
                            )
                        } else {
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
                apps.addAll(fetchedApps)
            }
            "RestoreUser", "RestoreSystem", "AllBackups" -> {
                val pathType = when (type) { "RestoreUser" -> "User"; "RestoreSystem" -> "System"; else -> "*" }

                val command =
                    "su -mm -c 'grep -H -e \"^Name=\" -e \"^Package=\" -e \"^Version=\" -e \"^AppSize=\" -e \"^DataSize=\" -e \"^ExtDataSize=\" -e \"^MediaSize=\" -e \"^ObbSize=\" \"$currentPath\"/Apps/$pathType/*/Meta.txt 2>/dev/null'"
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

                val diskSizes = mutableMapOf<String, Long>()
                if (isManage && appMap.isNotEmpty()) {
                    appMap.keys.chunked(100).forEach { batch ->
                        val paths = batch.joinToString(" ") { "'$it'" }
                        Shell.cmd("su -mm -c \"du -sk $paths 2>/dev/null\"")
                            .exec().out.forEach { line ->
                                val parts = line.trim().split(Regex("\\s+"), 2)
                                if (parts.size == 2) {
                                    diskSizes[parts[1]] = parts[0].toLongOrNull() ?: 0L
                                }
                        }
                    }
                }

                val iconScript = StringBuilder()
                appMap.forEach { (basePath, data) ->
                    val pkg = data["Package"] ?: return@forEach
                    val cacheFile = File(iconCacheDir, "${pkg}_icon.png")
                    if (!cacheFile.exists()) {
                        iconScript.append("cp '$basePath/Icon.png' '${cacheFile.absolutePath}' && chmod 644 '${cacheFile.absolutePath}'\n")
                    }
                }
                if (iconScript.isNotEmpty()) {
                    Shell.cmd(iconScript.toString()).exec()
                }

                val deferredBackups = appMap.map { (basePath, data) ->
                    async(Dispatchers.IO) {
                        val sysType = basePath.split("/").lastOrNull { it == "System" || it == "User" } ?: "User"
                        val isSys = sysType == "System"
                        val label = data["Name"] ?: ""
                        val pkg = data["Package"] ?: ""
                        val version = data["Version"] ?: ""

                        val appSizeKb: Long
                        val dataSizeKb: Long
                        val mediaSizeKb: Long

                        if (isManage) {
                            appSizeKb = diskSizes[basePath] ?: 0L
                            dataSizeKb = 0
                            mediaSizeKb = 0
                        } else {
                            val aSize = data["AppSize"]?.toLongOrNull() ?: 0L
                            val dSize = data["DataSize"]?.toLongOrNull() ?: 0L
                            val eSize = data["ExtDataSize"]?.toLongOrNull() ?: 0L
                            val mSize = data["MediaSize"]?.toLongOrNull() ?: 0L
                            val oSize = data["ObbSize"]?.toLongOrNull() ?: 0L

                            appSizeKb = aSize
                            dataSizeKb = dSize + eSize
                            mediaSizeKb = mSize + oSize
                        }

                        val displaySizeKb =
                            appSizeKb + dataSizeKb + mediaSizeKb + if (includeOverhead) 31 else 0

                        if (label.isNotBlank() && pkg.isNotBlank()) {
                            val cacheFile = File(iconCacheDir, "${pkg}_icon.png")
                            val iconPath =
                                if (cacheFile.exists() && cacheFile.length() > 0) cacheFile.absolutePath else null

                            val metaFile = File("$basePath/Meta.txt")
                            val bTime = if (metaFile.exists()) {
                                sdf.format(java.util.Date(metaFile.lastModified()))
                            } else "No backup on device"

                            val isInst = try {
                                pm.getPackageInfo(pkg, 0); true
                            } catch (_: Exception) {
                                false
                            }

                            AppInfo(
                                label = label,
                                packageName = pkg,
                                version = version,
                                isSystem = isSys,
                                iconPath = iconPath,
                                backupTime = bTime,
                                size = formatSize(displaySizeKb.toString()),
                                isInstalled = isInst,
                                appSizeKb = appSizeKb,
                                dataSizeKb = dataSizeKb,
                                mediaSizeKb = mediaSizeKb
                            )
                        } else null
                    }
                }
                apps.addAll(deferredBackups.awaitAll().filterNotNull())
            }
            else -> {}
        }

        val finalApps = apps.filter { it.packageName != BuildConfig.APPLICATION_ID }
            .distinctBy { it.packageName }.sortedBy { it.label.lowercase(Locale.ROOT) }

        if (!append) {
            when (type) {
                "User" -> userAppsCache = finalApps
                "System" -> sysAppsCache = finalApps
                "Uninstalled" -> uninstalledCache = finalApps
            }
        }

        return@withContext finalApps
    }

    @SuppressLint("SdCardPath")
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
            val isRestore = state.migratorMode.name.contains("RESTORE")

            if (state.migratorMode == MigratorMode.MANAGE) {
                val baseDir = File(currentPath)
                if (!baseDir.exists()) {
                    Shell.cmd("su -c \"mkdir -p '$currentPath/Apps' '$currentPath/Partitions' && touch '$currentPath/.shifter_dir'\"")
                        .exec()
                }
                val appPaths =
                    selectedApps.joinToString(" ") { "'$currentPath/Apps/${if (it.isSystem) "System" else "User"}/${it.label}'" }

                val sizeCmd = "su -mm -c \"du -sk $appPaths 2>/dev/null\""
                val sizeOut = Shell.cmd(sizeCmd).exec().out
                val totalFreedKb = sizeOut.sumOf {
                    it.trim().split("\\s+".toRegex()).firstOrNull()?.toLongOrNull() ?: 0L
                }

                Shell.cmd("rm -rf $appPaths").exec()

                val formattedSize = formatSize(totalFreedKb.toString())
                updateProgress("Backups Deleted", "Data Successfully Removed", 100)
                onComplete("Deletion Complete!", "Freed: $formattedSize")
                return@withContext
            }

            val cApp = state.globalComponents.contains(1)
            val cData = state.globalComponents.contains(2)
            val cPerm = state.globalComponents.contains(3)
            val cMedia = state.globalComponents.contains(4)
            val cId = state.globalComponents.contains(5)
            val appPartsMap = mutableMapOf<String, String>()

            if (isRestore) {
                selectedApps.forEachIndexed { index, app ->
                    updateProgress(
                        "",
                        "Analyzing backups (${index + 1}/${selectedApps.size})...",
                        -1
                    )
                    val sysType = if (app.isSystem) "System" else "User"
                    val basePath = "$currentPath/Apps/$sysType/${app.label}"
                    val parts = mutableListOf<String>()
                    if (cApp && File("$basePath/App.shift").exists()) parts.add("App")
                    if (cData && (File("$basePath/Data.shift").exists() || File("$basePath/UserDe.shift").exists() || File(
                            "$basePath/ExtData.shift"
                        ).exists())
                    ) parts.add("Data")
                    if (cPerm && File("$basePath/Permissions.txt").exists()) parts.add("Perm")
                    if (cMedia && (File("$basePath/Media.shift").exists() || File("$basePath/Obb.shift").exists())) parts.add(
                        "Media"
                    )
                    if (cId) {
                        try {
                            val metaPath = "$currentPath/Apps/$sysType/${app.label}/Meta.txt"
                            val hasAndId = Shell.cmd("grep -q '^SSAID=' \"$metaPath\" && echo YES")
                                .exec().out.joinToString("").trim() == "YES"
                            if (hasAndId) parts.add("AndID")
                        } catch (_: Exception) {}
                    }
                    appPartsMap[app.packageName] = parts.joinToString(" • ")
                }
            } else {
                updateProgress("", "Checking app components...", -1)
                val d = "/data"
                val dlr = "$"
                val script = buildString {
                    selectedApps.forEach { app ->
                        val pkg = app.packageName
                        appendLine("res=\"\"")
                        if (cApp) appendLine("res=\"${dlr}res|App\"")
                        if (cData) {
                            appendLine("res=\"${dlr}res|Data\"")
                            appendLine("if [ -d \"$d/media/0/Android/data/$pkg\" ] && [ \"${dlr}(ls -A $d/media/0/Android/data/$pkg 2>/dev/null)\" ]; then res=\"${dlr}res|ExtData\"; fi")
                        }
                        if (cPerm) appendLine("res=\"${dlr}res|Perm\"")
                        if (cMedia) {
                            appendLine("if [ -d \"$d/media/0/Android/media/$pkg\" ] && [ \"${dlr}(ls -A $d/media/0/Android/media/$pkg 2>/dev/null)\" ]; then res=\"${dlr}res|Media\"; fi")
                            appendLine("if [ -d \"$d/media/0/Android/obb/$pkg\" ] && [ \"${dlr}(ls -A $d/media/0/Android/obb/$pkg 2>/dev/null)\" ]; then res=\"${dlr}res|Obb\"; fi")
                        }
                        if (cId) appendLine("if grep -q 'package=\"$pkg\"' $d/system/users/0/settings_ssaid.xml 2>/dev/null; then res=\"${dlr}res|AndID\"; fi")
                        appendLine("echo \"$pkg==${dlr}res\"")
                    }
                }

                val scriptFile = File(context.cacheDir, "check_comps.sh")
                scriptFile.writeText(script)
                val out = Shell.cmd("su -mm -c 'sh \"${scriptFile.absolutePath}\"'").exec().out
                scriptFile.delete()

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
            Shell.cmd("rm -f /data/local/tmp/shifter_targets.txt && cat '${targetFile.absolutePath}' > /data/local/tmp/shifter_targets.txt && chmod 666 /data/local/tmp/shifter_targets.txt")
                .exec()
            targetFile.delete()
            val operation = if (isRestore) "--restore" else "--backup"
            val compsString = state.globalComponents.sorted().joinToString(" ")

            val command =
                "su -mm -c \"sh /data/adb/Shifter/ROM-Shifter.sh $operation '$compsString' '$currentPath'\""
            val actText = if (isRestore) "Restoring Apps" else "Backing up Apps"
            if (state.migratorMode == MigratorMode.BACKUP_APPS) {
                val iconScript = java.lang.StringBuilder()
                val iconCacheDir = File(context.cacheDir, "shifter_icons")

                selectedApps.forEachIndexed { index, app ->
                    updateProgress("", "Preparing icons (${index + 1}/${selectedApps.size})...", -1)
                    val sysType = if (app.isSystem) "System" else "User"
                    val destDir = "$currentPath/Apps/$sysType/${app.label}"
                    val destIcon = File(destDir, "Icon.png")

                    if (destIcon.exists() && destIcon.length() > 0) return@forEachIndexed

                    try {
                        val cacheFile = File(iconCacheDir, "${app.packageName}_icon.png")

                        if (cacheFile.exists() && cacheFile.length() > 0) {
                            iconScript.append("mkdir -p '$destDir'\n")
                            iconScript.append("cp '${cacheFile.absolutePath}' '${destIcon.absolutePath}'\n")
                            iconScript.append("chmod 644 '${destIcon.absolutePath}'\n")
                        } else {
                            val rawIcon = context.packageManager.getApplicationIcon(app.packageName)
                            val bitmap = getTinyBitmap(rawIcon)

                            if (bitmap != null) {
                                val tempFile = File(context.cacheDir, "icon_${app.packageName}.png")
                                FileOutputStream(tempFile).use { out ->
                                    bitmap.compress(
                                        Bitmap.CompressFormat.PNG,
                                        100,
                                        out
                                    )
                                }

                                try {
                                    FileOutputStream(cacheFile).use { out ->
                                        bitmap.compress(
                                            Bitmap.CompressFormat.PNG,
                                            100,
                                            out
                                        )
                                    }
                                } catch (_: Exception) {
                                }

                                iconScript.append("mkdir -p '$destDir'\n")
                                iconScript.append("cp '${tempFile.absolutePath}' '${destIcon.absolutePath}'\n")
                                iconScript.append("chmod 644 '${destIcon.absolutePath}'\n")
                                iconScript.append("rm -f '${tempFile.absolutePath}'\n")
                            }
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
            updateProgress("", "Calculating sizes...", -1)

            ShellEngine.executeShifterCommand(command).collect { event ->
                when (event) {
                    is ShifterEvent.BackupProgress -> {
                        val app = selectedApps.find { it.label == event.label }
                        val activeParts = appPartsMap[app?.packageName] ?: ""
                        val partsString = if (activeParts.isNotEmpty()) "\n• $activeParts" else ""

                        val rawKb = event.size.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0L
                        val formattedSize = if (rawKb > 0) formatSize(rawKb.toString()) else ""
                        val sizeInfo = if (formattedSize.isNotEmpty()) " [Size: $formattedSize]" else ""

                        updateProgress(actText, "${event.label}$sizeInfo (${event.current}/${event.total})$partsString", event.percent)
                    }

                    is ShifterEvent.InfoStep -> {
                        updateProgress("", event.msg, -1)
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
            val bitmap = createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    fun getAvailableSpaceKb(path: String): Long {
        return try {
            var file: File? = File(path)
            while (file != null && !file.exists()) {
                file = file.parentFile
            }
            val stat = android.os.StatFs(file?.absolutePath ?: "/")
            (stat.availableBlocksLong * stat.blockSizeLong) / 1024
        } catch (_: Exception) {
            0L
        }
    }

    private fun getDetailedPackageSizes(context: Context, packageName: String): Pair<Long, Long> {
        try {
            val storageStatsManager =
                context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
            val stats = storageStatsManager.queryStatsForPackage(
                UUID_DEFAULT,
                packageName,
                android.os.Process.myUserHandle()
            )

            val appSize = stats.appBytes / 1024
            val dataSize = (stats.dataBytes + stats.cacheBytes) / 1024

            return appSize to dataSize
        } catch (_: Exception) {
            try {
                val pathCmd = "pm path $packageName"
                val apkPath = Shell.cmd(pathCmd).exec().out.firstOrNull()?.removePrefix("package:")
                if (!apkPath.isNullOrBlank() && (apkPath.startsWith("/data/app") || apkPath.startsWith(
                        "/data/priv-app"
                    ))
                ) {
                    val sizeCmd = "du -sk $(dirname \"$apkPath\") 2>/dev/null | awk '{print $1}'"
                    val sizeKb = Shell.cmd(sizeCmd).exec().out.firstOrNull()?.toLongOrNull() ?: 0L
                    return sizeKb to 0L
                }
            } catch (_: Exception) {
            }
            return 0L to 0L
        }
    }

    private fun fetchExternalSizes(): Pair<Map<String, Long>, Map<String, Long>> {
        val dataSizes = mutableMapOf<String, Long>()
        val mediaSizes = mutableMapOf<String, Long>()

        val outData =
            Shell.cmd("su -mm -c \"du -sk /data/media/0/Android/data/* 2>/dev/null\"").exec().out
        outData.forEach { line ->
            val parts = line.trim().split(Regex("\\s+"), 2)
            if (parts.size == 2) {
                val size = parts[0].toLongOrNull() ?: 0L
                val pkg = parts[1].split("/").lastOrNull() ?: ""
                if (pkg.isNotEmpty()) dataSizes[pkg] = size
            }
        }

        val outMedia =
            Shell.cmd("su -mm -c \"du -sk /data/media/0/Android/media/* /data/media/0/Android/obb/* 2>/dev/null\"")
                .exec().out
        outMedia.forEach { line ->
            val parts = line.trim().split(Regex("\\s+"), 2)
            if (parts.size == 2) {
                val size = parts[0].toLongOrNull() ?: 0L
                val pkg = parts[1].split("/").lastOrNull() ?: ""
                if (pkg.isNotEmpty()) mediaSizes[pkg] = (mediaSizes[pkg] ?: 0L) + size
            }
        }
        return dataSizes to mediaSizes
    }

    fun formatSize(kbString: String): String {
        val kb = kbString.toLongOrNull() ?: return "$kbString KB"
        return when {
            kb >= 1048576 -> String.format(Locale.US, "%.2f GB", kb / 1048576.0)
            kb >= 1024 -> String.format(Locale.US, "%.2f MB", kb / 1024.0)
            else -> "$kb KB"
        }
    }
}