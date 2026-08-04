package build.bytes.romshifter.utils

import android.content.Context
import android.os.PowerManager
import build.bytes.romshifter.models.AppInfo
import build.bytes.romshifter.models.ShifterEvent
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ExtrasManager {

    suspend fun runDebloatOperation(
        context: Context, selectedApps: List<AppInfo>, isRestore: Boolean, forceRemove: Boolean,
        updateLog: (String) -> Unit, updateProgress: (String, String, Int) -> Unit, onComplete: (String, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        var wakeLock: PowerManager.WakeLock? = null
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ROMShifter::DebloatWakelock")
            wakeLock.acquire(15 * 60 * 1000L)

            if (isRestore) {
                selectedApps.forEachIndexed { index, app ->
                    updateProgress("Restoring Apps", "${app.label} (${index + 1}/${selectedApps.size})", ((index + 1) * 100) / selectedApps.size)
                    Shell.cmd("su -mm -c \"sh /data/adb/#Shifter/ROM-Shifter.sh --restore-debloat '${app.packageName}'\"").exec()
                    updateLog("Restored: ${app.label}")
                }
                onComplete("Restore Complete!", "Successfully restored ${selectedApps.size} apps.")
            } else {
                selectedApps.forEachIndexed { index, app ->
                    updateProgress("Debloating Apps", "${app.label} (${index + 1}/${selectedApps.size})", ((index + 1) * 100) / selectedApps.size)
                    val isForce = if (forceRemove) "true" else "false"
                    val result = Shell.cmd("su -mm -c \"sh /data/adb/#Shifter/ROM-Shifter.sh --remove '${app.packageName}' '$isForce'\"").exec().out.joinToString("").trim()

                    if (result == "FORCE_REMOVED") updateLog("Force Removed (rm -rf): ${app.label}")
                    else updateLog("Uninstalled: ${app.label}")
                }
                onComplete("Debloat Complete!", "Successfully removed ${selectedApps.size} apps.")
            }
        } finally {
            wakeLock?.let { if (it.isHeld) it.release() }
        }
    }

    suspend fun runSystemizeOperation(
        context: Context, selectedApps: List<AppInfo>, isPrivileged: Boolean,
        updateLog: (String) -> Unit, updateProgress: (String, String, Int) -> Unit, onComplete: (String, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        var wakeLock: PowerManager.WakeLock? = null
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ROMShifter::SystemizeWakelock")
            wakeLock.acquire(15 * 60 * 1000L)

            selectedApps.forEachIndexed { index, app ->
                updateProgress("Systemizing Apps", "${app.label} (${index + 1}/${selectedApps.size})", ((index + 1) * 100) / selectedApps.size)

                val isPriv = if (isPrivileged) "true" else "false"
                val result = Shell.cmd("su -mm -c \"sh /data/adb/#Shifter/ROM-Shifter.sh --systemize '${app.packageName}' '${app.label}' '$isPriv'\"").exec().out.joinToString("").trim()

                if (result == "SYSTEMIZED") updateLog("Systemized: ${app.label}")
                else updateLog("Failed to locate APK path for: ${app.label}")
            }
            onComplete("Systemization Complete!", "Please REBOOT to apply System Apps.")
        } finally {
            wakeLock?.let { if (it.isHeld) it.release() }
        }
    }
}