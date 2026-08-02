package build.bytes.romshifter.models

import android.graphics.drawable.Drawable

enum class MigratorMode { MENU, BACKUP_APPS, RESTORE_APPS, MANAGE, DEBLOAT, SYSTEMIZE }

data class AppInfo(
    val label: String,
    val packageName: String,
    val version: String = "",
    val isSystem: Boolean = false,
    val isSelected: Boolean = false,
    val icon: Drawable? = null
)

data class FlashZip(val name: String, val path: String, val category: String)

data class AppState(
    val migratorMode: MigratorMode = MigratorMode.MENU,
    val isRunning: Boolean = false,
    val currentAction: String = "Ready to Shift",
    val currentStep: String = "",
    val progress: Int = 0,
    val logs: List<String> = emptyList(),
    val appList: List<AppInfo> = emptyList(),
    val isFetchingApps: Boolean = false,
    val systemAppsFetched: Boolean = false,
    val searchQuery: String = "",
    val showUserApps: Boolean = true,
    val showSystemApps: Boolean = false,
    val isRestoreDebloatMode: Boolean = false,
    val globalComponents: Set<Int> = setOf(1, 2, 3, 4, 5, 6),
    val hasRoot: Boolean = true,
    val forceRemoveEnabled: Boolean = false,
    val flashWizardStep: Int = 0,
    val flashWipeMode: Int = 2,
    val flashZips: List<FlashZip> = emptyList(),
    val isProcessingZips: Boolean = false,
    val hasLockscreen: Boolean = false
)