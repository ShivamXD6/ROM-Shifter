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
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

object MigratorManager {

    suspend fun fetchAppsList(context: Context, currentPath: String, type: String, append: Boolean, currentList: List<AppInfo>): List<AppInfo> = withContext(Dispatchers.IO) {
        val apps = if (append) currentList.toMutableList() else mutableListOf()
        val pm = context.packageManager

        when (type) {
            "User", "System", "AllInstalled" -> {
                val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                for (app in installedApps) {
                    val isSys = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    if (type == "AllInstalled" || (type == "System" && isSys) || (type == "User" && !isSys)) {
                        val label = app.loadLabel(pm).toString().replace("|", "").replace("\n", "").trim()
                        val pkg = app.packageName.replace("|", "").replace("\n", "").trim()
                        val icon = app.loadIcon(pm)
                        val version = try { pm.getPackageInfo(app.packageName, 0).versionName?.replace("|", "")?.replace("\n", "")?.trim() ?: "" } catch(_: Exception) { "" }
                        apps.add(AppInfo(label = label, packageName = pkg, version = version, isSystem = isSys, icon = icon))
                    }
                }
            }
            "Uninstalled" -> {
                val allApps = pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)
                val activeApps = pm.getInstalledApplications(0).map { it.packageName }.toSet()
                for (app in allApps) {
                    if (!activeApps.contains(app.packageName)) {
                        val isSys = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        val label = app.loadLabel(pm).toString().replace("|", "").replace("\n", "").trim()
                        val icon = try { app.loadIcon(pm) } catch(_: Exception) { null }
                        apps.add(AppInfo(label = label, packageName = app.packageName, isSystem = isSys, icon = icon))
                    }
                }
            }
            "RestoreUser", "RestoreSystem", "AllBackups" -> {
                val pathType = when (type) { "RestoreUser" -> "User"; "RestoreSystem" -> "System"; else -> "*" }

                // Note: You might see minor yellow warnings on the next line about "escaped dollar characters".
                // Ignore them! This is perfectly valid and fixes the compiler crash.
                val command = "su -mm -c 'for f in \"$currentPath\"/Data-Migrated/$pathType/*/Meta.txt; do if [ -f \"\$f\" ]; then dir=\"\$(dirname \"\$f\")\"; sysType=\"\$(basename \"\$(dirname \"\$dir\")\")\"; echo \"\$sysType|\$(grep \"^Name=\" \"\$f\" | cut -d= -f2)|\$(grep \"^Package=\" \"\$f\" | cut -d= -f2)|\$(grep \"^Version=\" \"\$f\" | cut -d= -f2)|\$dir/Icon.png\"; fi done'"

                val result = Shell.cmd(command).exec()
                val iconCacheDir = File(context.cacheDir, "shifter_icons").apply { mkdirs() }

                result.out.forEach { line ->
                    val parts = line.split("|")
                    if (parts.size >= 4 && parts[1].isNotBlank() && parts[2].isNotBlank()) {
                        val isSys = parts[0] == "System"
                        val pkg = parts[2].replace("\n", "").trim()
                        var icon: Drawable? = try { pm.getApplicationIcon(pkg) } catch (_: Exception) { null }

                        if (icon == null && parts.size >= 5) {
                            val cacheFile = File(iconCacheDir, "${pkg}_icon.png")
                            Shell.cmd("su -mm -c \"cp '${parts[4]}' '${cacheFile.absolutePath}' && chmod 644 '${cacheFile.absolutePath}'\"").exec()
                            if (cacheFile.exists() && cacheFile.length() > 0) icon = Drawable.createFromPath(cacheFile.absolutePath)
                        }
                        apps.add(AppInfo(label = parts[1], packageName = pkg, version = parts[3], isSystem = isSys, icon = icon))
                    }
                }
            }
        }
        return@withContext apps.distinctBy { it.packageName }.sortedBy { it.label.lowercase(Locale.ROOT) }
    }

    suspend fun runDynamicOperation(
        context: Context, state: AppState, selectedApps: List<AppInfo>, currentPath: String,
        updateLog: (String) -> Unit, updateProgress: (String, String, Int) -> Unit, onComplete: (String, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        var wakeLock: PowerManager.WakeLock? = null
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ROMShifter::OperationWakelock")
            wakeLock.acquire(15 * 60 * 1000L)
        } catch (_: SecurityException) { updateLog("Warning: WAKE_LOCK permission missing!") }

        try {
            if (state.migratorMode == MigratorMode.DEBLOAT) {
                if (state.isRestoreDebloatMode) {
                    selectedApps.forEachIndexed { index, app ->
                        updateProgress("Restoring ${app.label}", "", ((index + 1) * 100) / selectedApps.size)
                        Shell.cmd("su -mm -c \"cmd package install-existing ${app.packageName}\"").exec()
                        updateLog("Restored: ${app.label}")
                    }
                    onComplete("Restore Complete!", "Apps restored for User 0.")
                    return@withContext
                } else {
                    selectedApps.forEachIndexed { index, app ->
                        updateProgress("Debloating ${app.label}", "", ((index + 1) * 100) / selectedApps.size)
                        if (state.forceRemoveEnabled) {
                            val pathOut = Shell.cmd("su -mm -c \"pm path ${app.packageName}\"").exec().out.joinToString("")
                            val apkPath = pathOut.substringAfter("package:").trim()
                            if (apkPath.startsWith("/system") || apkPath.startsWith("/product") || apkPath.startsWith("/vendor")) {
                                val dir = apkPath.substringBeforeLast("/")
                                Shell.cmd("su -mm -c \"mount -o rw,remount /; mount -o rw,remount /system; mount -o rw,remount /product; mount -o rw,remount /vendor; rm -rf '$dir'\"").exec()
                                Shell.cmd("su -mm -c \"pm uninstall --user 0 ${app.packageName}\"").exec()
                                updateLog("Force Removed (rm -rf): ${app.label}")
                            } else {
                                Shell.cmd("su -mm -c \"pm uninstall --user 0 ${app.packageName}\"").exec()
                                updateLog("Uninstalled (User App): ${app.label}")
                            }
                        } else {
                            Shell.cmd("su -mm -c \"pm uninstall --user 0 ${app.packageName}\"").exec()
                            updateLog("Uninstalled: ${app.label}")
                        }
                    }
                    onComplete("Debloat Complete!", "Apps removed.")
                    return@withContext
                }
            }

            if (state.migratorMode == MigratorMode.MANAGE) {
                selectedApps.forEach { app ->
                    val sysType = if (app.isSystem) "System" else "User"
                    Shell.cmd("su -mm -c \"rm -rf '$currentPath/Data-Migrated/$sysType/${app.label}'\"").exec()
                }
                updateProgress("Backups Deleted Successfully!", "", 100)
                onComplete("Backups Deleted Successfully!", "Freed up storage space.")
                return@withContext
            }

            if (state.migratorMode == MigratorMode.SYSTEMIZE) {
                val modDir = "/data/adb/modules/romshifter_systemized"
                Shell.cmd("su -c 'mkdir -p $modDir/system/priv-app'").exec()
                Shell.cmd("su -c 'echo \"id=romshifter_systemized\nname=ROM Shifter Systemized Apps\nversion=1.0\nversionCode=1\nauthor=ROM Shifter\ndescription=Systemlessly makes selected user apps un-uninstallable.\" > $modDir/module.prop'").exec()

                selectedApps.forEachIndexed { index, app ->
                    updateProgress("Systemizing ${app.label}", "", ((index + 1) * 100) / selectedApps.size)
                    val apkPath = Shell.cmd("su -c \"pm path ${app.packageName}\"").exec().out.joinToString("").substringAfter("package:").trim()
                    if (apkPath.isNotEmpty()) {
                        val safeLabel = app.label.replace(Regex("[^a-zA-Z0-9]"), "_")
                        Shell.cmd("su -c 'mkdir -p \"$modDir/system/priv-app/$safeLabel\" && cp \"$apkPath\" \"$modDir/system/priv-app/$safeLabel/base.apk\"'").exec()
                        updateLog("Systemized: ${app.label}")
                    }
                }
                onComplete("Systemization Complete!", "Please REBOOT to apply System Apps.")
                return@withContext
            }

            val targetData = selectedApps.joinToString("\n") { app ->
                val sysType = if (app.isSystem) "System" else "User"
                "${app.packageName}|${app.label}|${app.version}|$sysType"
            } + "\n"

            val targetFile = File(context.cacheDir, "shifter_targets.txt")
            targetFile.writeText(targetData)
            Shell.cmd("su -mm -c \"rm -f /data/local/tmp/shifter_targets.txt && cat '${targetFile.absolutePath}' > /data/local/tmp/shifter_targets.txt && chmod 666 /data/local/tmp/shifter_targets.txt\"").exec()
            targetFile.delete()

            val operation = if (state.migratorMode.name.contains("RESTORE")) "--restore" else "--backup"
            val compsString = state.globalComponents.sorted().joinToString(" ")
            val command = "su -mm -c \"sh /data/adb/#Shifter/ROM-Shifter.sh $operation '$compsString'\""

            ShellEngine.executeShifterCommand(command).collect { event ->
                when (event) {
                    is ShifterEvent.BackupProgress -> {
                        val actText = if (state.migratorMode.name.contains("RESTORE")) "Restoring" else "Backing up"
                        updateLog("[${event.percent}%] $actText ${event.label} - ${event.size}")
                        updateProgress("$actText ${event.label} (${event.current}/${event.total})", "", event.percent)
                    }
                    is ShifterEvent.InfoStep -> { updateProgress("", event.msg, -1); updateLog(" -> ${event.msg}") }
                    is ShifterEvent.GlobalDone -> {
                        val smartSize = formatSize(event.totalKb)
                        updateLog("DONE! Total Size: $smartSize. Time: ${event.timeSec}s")
                        onComplete("Operation Completed!", "Total Size: $smartSize | Time: ${event.timeSec}s")
                    }
                    is ShifterEvent.RawLog -> updateLog(event.line)
                    else -> {}
                }
            }

            if (state.migratorMode == MigratorMode.BACKUP_APPS) {
                selectedApps.forEach { app ->
                    app.icon?.let { icon ->
                        try {
                            val bitmap = icon.toSafeBitmap()
                            val tempFile = File(context.cacheDir, "temp_icon.png")
                            FileOutputStream(tempFile).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
                            val sysType = if (app.isSystem) "System" else "User"
                            Shell.cmd("su -mm -c \"cp '${tempFile.absolutePath}' '$currentPath/Data-Migrated/$sysType/${app.label}/Icon.png' && chmod 644 '$currentPath/Data-Migrated/$sysType/${app.label}/Icon.png'\"").exec()
                            tempFile.delete()
                        } catch (_: Exception) { }
                    }
                }
            }
        } finally {
            wakeLock?.let { if (it.isHeld) it.release() }
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