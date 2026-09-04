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
    val isSystemized: Boolean = false,
    val appSizeKb: Long = 0,
    val dataSizeKb: Long = 0,
    val mediaSizeKb: Long = 0,
    val diskSizeKb: Long = 0,
    val versionCode: Long = 0,
    val apkPath: String? = null,
    val availableInBackup: Set<Int> = emptySet(),
    val activeComponents: Set<Int>? = null
)

data class AppInstallInfo(
    val label: String = "Analyzing...",
    val packageName: String = "",
    val version: String = "",
    val versionCode: Long = 0,
    val size: String = "",
    val path: String = "",
    val uriString: String? = null,
    val installedVersion: String? = null,
    val installedVersionCode: Long? = null,
    val installedSize: String? = null,
    val isInstalled: Boolean = false,
    val status: String = "Analyzing",
    val iconPath: String? = null,
    val isAnalysisComplete: Boolean = false,
    val isSelected: Boolean = true,
    val minSdk: String = "",
    val targetSdk: String = "",
    val architecture: String = "",
    val signature: String = ""
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
    val currentAction: String = "",
    val currentStep: String = "",
    val progress: Int = 0,
    val isFetchingApps: Boolean = false,
    val systemAppsFetched: Boolean = false,
    val searchQuery: String = "",
    val showUserApps: Boolean = true,
    val showSystemApps: Boolean = false,
    val actionFilterState: Int = 0,
    val globalComponents: Set<Int> = setOf(1, 2, 3, 4, 5),
    val hasRoot: Boolean = true,
    val flashWizardStep: Int = 0,
    val flashWipePartitions: Set<String> = setOf("dalvik", "cache"),
    val flashFormatData: Boolean = false,
    val isProcessingZips: Boolean = false,
    val hasLockscreen: Boolean = false,
    val flashRebootOption: String = "system",
    val totalRequiredKb: Long = 0,
    val totalBackupsSizeKb: Long = 0,
    val storageUsedKb: Long = 0,
    val storageFreeKb: Long = 0,
    val batchInstallApps: List<AppInstallInfo> = emptyList(),
    val showAppInstaller: Boolean = false,
    val isAnalyzingApps: Boolean = false,
    val isInstallerIntent: Boolean = false,
    val installStartTime: Long = 0,
    val totalInstallTimeSeconds: Long = 0,
    val requestDefaultSms: Boolean = false,
    val keepDebloatData: Boolean = false
)
