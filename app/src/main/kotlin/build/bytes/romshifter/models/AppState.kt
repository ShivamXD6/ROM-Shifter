package build.bytes.romshifter.models

import androidx.compose.runtime.Stable

enum class MigratorMode { MENU, BACKUP_APPS, RESTORE_APPS, MANAGE, DEBLOAT, SYSTEMIZE }

@Stable
data class AppInfo(
    val label: String,
    val packageName: String,
    val version: String = "",
    val isSystem: Boolean = false,
    val isSelected: Boolean = false,
    val iconPath: String? = null,
    val backupTime: String = "",
    val size: String = "",
    val isInstalled: Boolean = true,
    val isSystemized: Boolean = false
)

data class FlashZip(val name: String, val path: String, val category: String)

sealed class FlashAction {
    data class InstallZip(val zip: FlashZip) : FlashAction()
    data class Wipe(val partitions: Set<String>) : FlashAction()
    object FormatData : FlashAction()

    val id: String
        get() = when (this) {
            is InstallZip -> zip.path
            is Wipe -> "WIPE_${partitions.joinToString(",")}"
            is FormatData -> "FORMAT_DATA"
        }
}

data class AppState(
    val migratorMode: MigratorMode = MigratorMode.MENU,
    val isRunning: Boolean = false,
    val currentAction: String = "Ready to Shift",
    val currentStep: String = "",
    val progress: Int = 0,
    val isFetchingApps: Boolean = false,
    val systemAppsFetched: Boolean = false,
    val searchQuery: String = "",
    val showUserApps: Boolean = true,
    val showSystemApps: Boolean = false,
    val actionFilterState: Int = 0,
    val globalComponents: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
    val hasRoot: Boolean = true,
    val forceRemoveEnabled: Boolean = false,
    val flashWizardStep: Int = 0,
    val flashWipePartitions: Set<String> = setOf("dalvik", "cache"),
    val flashFormatData: Boolean = false,
    val isProcessingZips: Boolean = false,
    val hasLockscreen: Boolean = false,
    val flashRebootOption: String = "system"
)