package build.bytes.romshifter.utils

import build.bytes.romshifter.models.ShifterEvent
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ExtrasManager {

    suspend fun runRomDataOperation(
        isBackup: Boolean,
        settings: Boolean,
        callRing: Boolean,
        smsRing: Boolean,
        wall: Boolean,
        savedPath: String,
        onEvent: (ShifterEvent) -> Unit
    ) = withContext(Dispatchers.IO) {
        val action = if (isBackup) "--rom-backup" else "--rom-restore"
        val s1 = if (settings) "1" else "0"
        val s2 = if (callRing) "1" else "0"
        val s3 = if (smsRing) "1" else "0"
        val s4 = if (wall) "1" else "0"

        // FIX: Changed "su -mm" to "su". Mount Master breaks Android's Binder IPC,
        // which caused the 'Failed transaction (2147483646)' error for the settings command.
        val command = "su -c \"sh /data/adb/#Shifter/ROM-Shifter.sh $action '$s1' '$s2' '$s3' '$s4'\""

        ShellEngine.executeShifterCommand(command).collect { event ->
            withContext(Dispatchers.Main) { onEvent(event) }
        }
    }

    suspend fun checkAndInstallMetaModule(onEvent: (ShifterEvent) -> Unit) = withContext(Dispatchers.IO) {
        val command = "su -c \"sh /data/adb/#Shifter/ROM-Shifter.sh --install-meta\""

        try {
            ShellEngine.executeShifterCommand(command).collect { event ->
                withContext(Dispatchers.Main) { onEvent(event) }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onEvent(ShifterEvent.RawLog("CRITICAL ERROR: ${e.message}"))
                onEvent(ShifterEvent.GlobalDone("0", "0"))
            }
        }
    }

    suspend fun isMetaModuleInstalled(): Boolean = withContext(Dispatchers.IO) {
        Shell.cmd("su -c '[ -d /data/adb/modules/meta-overlayfs ] && echo YES'").exec().out.joinToString("").trim() == "YES"
    }
}