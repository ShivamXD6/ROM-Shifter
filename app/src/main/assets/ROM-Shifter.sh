#!/system/bin/sh
# ==========================================
# ROM Shifter - APP BACKEND ENGINE
# ==========================================

BIN_DIR="/data/adb/#Shifter"
CACHE_DIR="$BIN_DIR/.cache"
CONFIG_FILE="$BIN_DIR/.config"
ZAPDOS="$BIN_DIR/zapdos"
ZAPDOS_HASH="a03b08f73703daf906c1c56316394dd22be8d5b63bec79dfddd430c8bce6af9c"
JOBS=$(nproc 2>/dev/null || echo 4)

AM_TMP="/data/local/tmp/appmgr_tmp"
TARGETS="/data/local/tmp/shifter_targets.txt"

verify_binaries() {
    local actual_zapdos=$(sha256sum "$BIN_DIR/zapdos" 2>/dev/null | awk '{print $1}')
    if [ "$actual_zapdos" != "$ZAPDOS_HASH" ]; then
        echo "ERROR:TAMPER|MSG:zapdos binary is corrupted" >&2
        rm -f "$BIN_DIR/zapdos"
        exit 1
    fi
}

extract_binaries() {
    if [ ! -x "$BIN_DIR/zapdos" ]; then
        echo "INFO:EXTRACT|MSG:Extracting zapdos..."
        awk '/^__ZAPDOS__/{flag=1; next} flag' "$0" | base64 -d | busybox gzip -d > "$BIN_DIR/zapdos" 2>/dev/null
        chmod +x "$BIN_DIR/zapdos"
    fi
    verify_binaries
}

ensure_root() {
    [ "$(id -u)" != "0" ] && { echo "ERROR:ROOT|MSG:Please run as root."; exit 1; }
    mkdir -p "$BIN_DIR" "$CACHE_DIR" "$AM_TMP"
    extract_binaries
}

init_shifter() {
    MAIN_DIR="${1:-/sdcard/#Shifter}"
    BACKUP_BASE="$MAIN_DIR/Data-Migrated"
    LP_DIR="$MAIN_DIR/Live-Partition"
    mkdir -p "$BACKUP_BASE" "$LP_DIR"
}

# --- Utility Functions ---
COOLDOWN() { while [ "$(jobs | grep -c 'Running')" -ge "$1" ] 2>/dev/null; do sleep 0.1; done; }
SANITIZE() { echo "$1" | sed 's/[^a-zA-Z0-9]/_/g'; }

CHK() {
    case " $APP_COMPS " in
        *" $1 "*) return 0 ;;
        *) return 1 ;;
    esac
}

RAW_SIZE() {
    [ -z "$1" ] && { echo 0; return; }
    echo "$1" | while IFS= read -r p; do
        if [ -e "$p" ]; then
            local base=$(du -sk "$p" 2>/dev/null | awk '{print $1}')
            local cache=$(du -sk "$p/cache" "$p/code_cache" 2>/dev/null | awk '{s+=$1} END{print s+0}')
            echo $(( ${base:-0} - ${cache:-0} ))
        fi
    done | awk '{s+=$1} END{print s+0}'
}

FORMAT_SIZE() {
    awk -v n="${1:-0}" 'BEGIN{
        if(n>=1048576) printf "%.2f GB\n", n/1048576
        else if(n>=1024) printf "%.2f MB\n", n/1024
        else printf "%.2f KB\n", n
    }'
}
READID() { grep "package=\"$1\"" "/data/system/users/0/settings_ssaid.xml" 2>/dev/null | sed -n 's/.*value="\([^"]*\)".*/\1/p'; }
CHANID() { sed -i "/package=\"$1\"/s/\(value=\"\)[^\"]*\(.*defaultValue=\"\)[^\"]*/\1$2\2$2/" "/data/system/users/0/settings_ssaid.xml"; }
GETPERM() {
    > "$2"; local in=0
    dumpsys package "$1" 2>/dev/null | while IFS= read -r line; do
        case "$line" in *runtime\ permissions:*) in=1; continue ;; [![:space:]]*) in=0 ;; esac
        [ "$in" -eq 1 ] && case "$line" in *granted=true*) perm="${line%%:*}"; echo "${perm#"${perm%%[![:space:]]*}"}" >> "$2" ;; esac
    done
}
SETPERM() { while IFS= read -r perm; do pm grant "$1" "$perm" 2>/dev/null & done < "$2"; }
DELGMS() { rm -f "/data/data/$1/databases/com.google.android.datatransport.events" "/data/data/$1/databases/com.google.android.datatransport.events-journal" "/data/data/$1/no_backup/com.google.android.gms.appid-no-backup" "/data/data/$1/shared_prefs/com.google.android.gms.appid.xml" "/data/data/$1/shared_prefs/com.google.android.gms.measurement.prefs.xml" 2>/dev/null; }
PKG_INSTALLED() {
    pm list packages | grep -q "^package:$1$" || return 1
    [ -z "$2" ] && return 0
    local apkpath="$(pm path "$1" 2>/dev/null | sed -n 's/^package://p' | head -n 1)"
    [ -z "$apkpath" ] && return 1
    local inst_ver=$(dumpsys package "$1" | grep versionName | head -n1 | cut -d= -f2)
    [ "$inst_ver" = "$2" ] || return 1
    return 0
}
BUNDAPP() {
    COOLDOWN "$((JOBS / 2))"
    tar --exclude="$2/cache" --exclude="$2/code_cache" -cf - -C "$1" "$2" 2>/dev/null | "$ZAPDOS" -1 -f -q -o "$3/$4.bundle.pack" &
}
UNBUNDAPP() {
    COOLDOWN "$((JOBS - 2))"
    "$ZAPDOS" -d -q -c "$1" | tar -xf - -C "$2" 2>/dev/null &
}

# --- Systemizer Meta Module Installer ---
install_meta_module() {
    echo "ACTION:INFO|MSG:Downloading Meta-OverlayFS..."
    local DL_PATH="/data/local/tmp/meta.zip"
    curl -LLo "$DL_PATH" "https://github.com/KernelSU-Modules-Repo/meta-overlayfs/releases/download/v1.0.4/meta-overlayfs-v1.0.4.zip" || wget -qO "$DL_PATH" "https://github.com/KernelSU-Modules-Repo/meta-overlayfs/releases/download/v1.0.4/meta-overlayfs-v1.0.4.zip"

    if [ ! -f "$DL_PATH" ]; then
        echo "ACTION:INFO|MSG:Download failed. Check internet."
        echo "ACTION:GLOBAL_DONE|TOTAL:0|TIME:0"
        return
    fi

    echo "ACTION:INFO|MSG:Detecting Root Implementation..."
    local ADBDIR="/data/adb"
    local ROOT="Unknown"
    local CMD=""

    if [ -d "$ADBDIR/magisk" ] && magisk -V >/dev/null 2>&1; then
        ROOT="Magisk"; CMD="magisk --install-module"
    elif [ -d "$ADBDIR/ksu" ] && ksud -V >/dev/null 2>&1; then
        ROOT="KernelSU"; CMD="ksud module install"
    elif [ -d "$ADBDIR/ap" ] && apd -V >/dev/null 2>&1; then
        ROOT="APatch"; CMD="apd module install"
    fi

    if [ "$ROOT" = "Unknown" ]; then
        echo "ACTION:INFO|MSG:Cannot determine root (Magisk/KSU/APatch not found)."
    else
        echo "ACTION:INFO|MSG:Found $ROOT. Installing module..."
        $CMD "$DL_PATH" >/dev/null 2>&1
        echo "ACTION:INFO|MSG:Installation complete! Please Reboot."
    fi
    rm -f "$DL_PATH"
    echo "ACTION:GLOBAL_DONE|TOTAL:0|TIME:0"
}

# --- ROM Specific Backups (Settings DBs, Wallpaper, Ringtones) ---
do_rom_backup() {
    local DIR="$BACKUP_BASE/ROM_Data"
    mkdir -p "$DIR" "$DIR/ringtones" "$DIR/notifications"

    echo "ACTION:INFO|MSG:Generating Meta lock..."
    getprop ro.build.display.id > "$DIR/meta_rom.txt"

    [ "$3" = "1" ] && { echo "ACTION:INFO|MSG:Backing up XML Settings..."; cp /data/system/users/0/settings_*.xml "$DIR/" 2>/dev/null; }
    [ "$4" = "1" ] && { echo "ACTION:INFO|MSG:Backing up Call Ringtones..."; cp /data/system_ce/0/ringtones/* "$DIR/ringtones/" 2>/dev/null; }
    [ "$5" = "1" ] && { echo "ACTION:INFO|MSG:Backing up SMS Ringtones..."; cp /data/system_ce/0/notifications/* "$DIR/notifications/" 2>/dev/null; }
    [ "$6" = "1" ] && { echo "ACTION:INFO|MSG:Backing up Wallpaper..."; cp /data/system/users/0/wallpaper* "$DIR/" 2>/dev/null; }

    echo "ACTION:GLOBAL_DONE|TOTAL:0|TIME:0"
}

do_rom_restore() {
    local DIR="$BACKUP_BASE/ROM_Data"

    if [ ! -f "$DIR/meta_rom.txt" ]; then
        echo "ACTION:INFO|MSG:No backup found!"
        echo "ACTION:GLOBAL_DONE|TOTAL:0|TIME:0"
        return
    fi

    local CUR_ROM=$(getprop ro.build.display.id)
    local BAK_ROM=$(cat "$DIR/meta_rom.txt")

    if [ "$CUR_ROM" != "$BAK_ROM" ]; then
        echo "ACTION:INFO|MSG:ROM mismatch! Backup: $BAK_ROM | Current: $CUR_ROM"
        echo "ACTION:INFO|MSG:Restore aborted to prevent bootloops."
        echo "ACTION:GLOBAL_DONE|TOTAL:0|TIME:0"
        return
    fi

    [ "$3" = "1" ] && { echo "ACTION:INFO|MSG:Restoring XML Settings..."; cp "$DIR"/settings_*.xml /data/system/users/0/ 2>/dev/null; chmod 600 /data/system/users/0/settings_*.xml; chown system:system /data/system/users/0/settings_*.xml; }
    [ "$4" = "1" ] && { echo "ACTION:INFO|MSG:Restoring Call Ringtones..."; cp "$DIR/ringtones/"* /data/system_ce/0/ringtones/ 2>/dev/null; }
    [ "$5" = "1" ] && { echo "ACTION:INFO|MSG:Restoring SMS Ringtones..."; cp "$DIR/notifications/"* /data/system_ce/0/notifications/ 2>/dev/null; }
    [ "$6" = "1" ] && { echo "ACTION:INFO|MSG:Restoring Wallpaper..."; cp "$DIR"/wallpaper* /data/system/users/0/ 2>/dev/null; chmod 600 /data/system/users/0/wallpaper*; chown system:system /data/system/users/0/wallpaper*; }

    echo "ACTION:INFO|MSG:Please REBOOT to apply ROM settings."
    echo "ACTION:GLOBAL_DONE|TOTAL:0|TIME:0"
}

# --- Live Partitions Engine ---
do_live_backup() {
    local part="$1"
    local BLOCK_PATH="/dev/block/by-name/$part"
    [ ! -e "$BLOCK_PATH" ] && BLOCK_PATH="/dev/block/bootdevice/by-name/$part"
    if [ -e "$BLOCK_PATH" ]; then
        dd if="$BLOCK_PATH" of="$LP_DIR/${part}_backup.img" bs=4M
    fi
}

do_live_restore() {
    local part="$1"
    local IMG_PATH="$2"
    local BLOCK_PATH="/dev/block/by-name/$part"
    [ ! -e "$BLOCK_PATH" ] && BLOCK_PATH="/dev/block/bootdevice/by-name/$part"
    if [ -e "$BLOCK_PATH" ] && [ -f "$IMG_PATH" ]; then
        dd if="$IMG_PATH" of="$BLOCK_PATH" bs=4M
    fi
}

# --- Data Migrator Engine ---
DO_BACKUP() {
    PKG="$1"; LABEL="$2"; VER="$3"; TYPE="$4"; CUR_IDX="$5"; TOT_IDX="$6"; PCT="$7"; SIZE="$8"
    APP_DIR="$BACKUP_BASE/$TYPE/$LABEL"; mkdir -p "$APP_DIR"
    local formatted_size=$(FORMAT_SIZE "$8")

    echo "ACTION:BACKUP_START|PKG:$PKG|LABEL:$LABEL|VER:$VER|CUR:$CUR_IDX|TOT:$TOT_IDX|PCT:$PCT|SIZE:$formatted_size"

    OLD_APP=0; OLD_DATA=0; OLD_EXT=0; OLD_MED=0; OLD_OBB=0; OLD_SSAID=""
    if [ -f "$APP_DIR/Meta.txt" ]; then
        OLD_APP=$(grep "^AppSize=" "$APP_DIR/Meta.txt" | cut -d= -f2); OLD_APP=${OLD_APP:-0}
        OLD_DATA=$(grep "^DataSize=" "$APP_DIR/Meta.txt" | cut -d= -f2); OLD_DATA=${OLD_DATA:-0}
        OLD_EXT=$(grep "^ExtDataSize=" "$APP_DIR/Meta.txt" | cut -d= -f2); OLD_EXT=${OLD_EXT:-0}
        OLD_MED=$(grep "^MediaSize=" "$APP_DIR/Meta.txt" | cut -d= -f2); OLD_MED=${OLD_MED:-0}
        OLD_OBB=$(grep "^ObbSize=" "$APP_DIR/Meta.txt" | cut -d= -f2); OLD_OBB=${OLD_OBB:-0}
        OLD_SSAID=$(grep "^SSAID=" "$APP_DIR/Meta.txt" | cut -d= -f2)
    fi

    TMP_SIZES="$AM_TMP/${PKG}_sizes"; mkdir -p "$TMP_SIZES"
    apks="$(pm path "$PKG" 2>/dev/null | sed 's/^package://')"

    CHK 1 && ( echo $(RAW_SIZE "$apks") > "$TMP_SIZES/app" ) &
    CHK 2 && ( echo $(( $(RAW_SIZE "/data/data/$PKG") + $(RAW_SIZE "/data/user_de/0/$PKG") )) > "$TMP_SIZES/data" ) &
    CHK 3 && ( echo $(RAW_SIZE "/data/media/0/Android/data/$PKG") > "$TMP_SIZES/ext" ) &
    CHK 4 && ( echo $(RAW_SIZE "/data/media/0/Android/media/$PKG") > "$TMP_SIZES/med" ) &
    CHK 5 && ( echo $(RAW_SIZE "/data/media/0/Android/obb/$PKG") > "$TMP_SIZES/obb" ) &
    wait

    CUR_APP=$(cat "$TMP_SIZES/app" 2>/dev/null); CUR_APP=${CUR_APP:-0}
    CUR_DATA=$(cat "$TMP_SIZES/data" 2>/dev/null); CUR_DATA=${CUR_DATA:-0}
    CUR_EXT=$(cat "$TMP_SIZES/ext" 2>/dev/null); CUR_EXT=${CUR_EXT:-0}
    CUR_MED=$(cat "$TMP_SIZES/med" 2>/dev/null); CUR_MED=${CUR_MED:-0}
    CUR_OBB=$(cat "$TMP_SIZES/obb" 2>/dev/null); CUR_OBB=${CUR_OBB:-0}
    rm -rf "$TMP_SIZES"; ACT=0

    if CHK 1; then
        if [ "$CUR_APP" != "$OLD_APP" ] || { [ "$CUR_APP" -gt 0 ] && [ ! -f "$APP_DIR/App.bundle.pack" ]; }; then
            if [ "$CUR_APP" -gt 0 ] && [ -n "$apks" ]; then
                echo "INFO:STEP|MSG:App (Base & Splits)"
                echo "$apks" | sed 's|^/||' | tar -cf - -C / -T - 2>/dev/null | "$ZAPDOS" -1 -f -q -o "$APP_DIR/App.bundle.pack" & ACT=1
            else rm -f "$APP_DIR/App.bundle.pack"; fi
            OLD_APP=$CUR_APP
        fi
    fi
    if CHK 2; then
        if [ "$CUR_DATA" != "$OLD_DATA" ] || { [ "$CUR_DATA" -gt 0 ] && [ ! -f "$APP_DIR/Data.bundle.pack" ] && [ ! -f "$APP_DIR/UserDe.bundle.pack" ]; }; then
            if [ "$CUR_DATA" -gt 0 ]; then
                echo "INFO:STEP|MSG:Data (/data & user_de)"
                [ -d "/data/data/$PKG" ] && BUNDAPP "/data/data" "$PKG" "$APP_DIR" "Data"
                [ -d "/data/user_de/0/$PKG" ] && BUNDAPP "/data/user_de/0" "$PKG" "$APP_DIR" "UserDe"
                ACT=1
            else rm -f "$APP_DIR/Data.bundle.pack" "$APP_DIR/UserDe.bundle.pack"; fi
            OLD_DATA=$CUR_DATA
        fi
    fi
    if CHK 3; then
        if [ "$CUR_EXT" != "$OLD_EXT" ] || { [ "$CUR_EXT" -gt 0 ] && [ ! -f "$APP_DIR/ExtData.bundle.pack" ]; }; then
            if [ "$CUR_EXT" -gt 0 ]; then
                echo "INFO:STEP|MSG:ExtData (Android/data)"
                BUNDAPP "/data/media/0/Android/data" "$PKG" "$APP_DIR" "ExtData"
                ACT=1
            else rm -f "$APP_DIR/ExtData.bundle.pack"; fi
            OLD_EXT=$CUR_EXT
        fi
    fi
    if CHK 4; then
        if [ "$CUR_MED" != "$OLD_MED" ] || { [ "$CUR_MED" -gt 0 ] && [ ! -f "$APP_DIR/Media.bundle.pack" ]; }; then
            if [ "$CUR_MED" -gt 0 ]; then
                echo "INFO:STEP|MSG:Media (Android/media)"
                BUNDAPP "/data/media/0/Android/media" "$PKG" "$APP_DIR" "Media"
                ACT=1
            else rm -f "$APP_DIR/Media.bundle.pack"; fi
            OLD_MED=$CUR_MED
        fi
    fi
    if CHK 5; then
        if [ "$CUR_OBB" != "$OLD_OBB" ] || { [ "$CUR_OBB" -gt 0 ] && [ ! -f "$APP_DIR/Obb.bundle.pack" ]; }; then
            if [ "$CUR_OBB" -gt 0 ]; then
                echo "INFO:STEP|MSG:OBB (Android/obb)"
                BUNDAPP "/data/media/0/Android/obb" "$PKG" "$APP_DIR" "Obb"
                ACT=1
            else rm -f "$APP_DIR/Obb.bundle.pack"; fi
            OLD_OBB=$CUR_OBB
        fi
    fi
    if CHK 6; then
        CUR_SSAID=$(READID "$PKG")
        if [ -n "$CUR_SSAID" ] && [ "$CUR_SSAID" != "$OLD_SSAID" ]; then
            echo "INFO:STEP|MSG:Android ID"; OLD_SSAID=$CUR_SSAID; ACT=1
        fi
    fi

    wait
    [ "$ACT" -eq 0 ] && echo "INFO:STEP|MSG:Up to date (Skipped)"
    local APP_TOTAL_KB=$(( OLD_APP + OLD_DATA + OLD_EXT + OLD_MED + OLD_OBB ))
    SYS_PATH=""; [ "$TYPE" = "System" ] && SYS_PATH=$(dumpsys package "$PKG" 2>/dev/null | awk -F= '/codePath=\/(system|product|vendor|oem|odm)/{print $2; exit}')

    cat <<EOF > "$APP_DIR/Meta.txt"
Name=$LABEL
Version=$VER
Package=$PKG
TotalSize=$APP_TOTAL_KB
AppSize=$OLD_APP
DataSize=$OLD_DATA
ExtDataSize=$OLD_EXT
MediaSize=$OLD_MED
ObbSize=$OLD_OBB
SSAID=$OLD_SSAID
SysPath=$SYS_PATH
EOF
    GETPERM "$PKG" "$APP_DIR/Permissions.txt" &
    echo "ACTION:BACKUP_DONE|PKG:$PKG"
}

DO_RESTORE() {
    LABEL="$1"; TYPE="$2"; CUR_IDX="$3"; TOT_IDX="$4"; PCT="$5"; SIZE="$6"
    APP_DIR="$BACKUP_BASE/$TYPE/$LABEL"
    [ -f "$APP_DIR/Meta.txt" ] || { echo "ERROR:RESTORE|MSG:Meta.txt missing for $LABEL"; return; }
    PKG=$(grep "Package=" "$APP_DIR/Meta.txt" | cut -d= -f2); [ -z "$PKG" ] && return
    VER=$(grep "Version=" "$APP_DIR/Meta.txt" | cut -d= -f2)
    TMP_PKG="$AM_TMP/$PKG"; mkdir -p "$TMP_PKG"

    local formatted_size=$(FORMAT_SIZE "$6")
    echo "ACTION:RESTORE_START|PKG:$PKG|LABEL:$LABEL|VER:$VER|CUR:$CUR_IDX|TOT:$TOT_IDX|PCT:$PCT|SIZE:$formatted_size"

    OLD_APP=$(grep "^AppSize=" "$APP_DIR/Meta.txt" | cut -d= -f2); OLD_APP=${OLD_APP:-0}
    OLD_DATA=$(grep "^DataSize=" "$APP_DIR/Meta.txt" | cut -d= -f2); OLD_DATA=${OLD_DATA:-0}
    OLD_EXT=$(grep "^ExtDataSize=" "$APP_DIR/Meta.txt" | cut -d= -f2); OLD_EXT=${OLD_EXT:-0}
    OLD_MED=$(grep "^MediaSize=" "$APP_DIR/Meta.txt" | cut -d= -f2); OLD_MED=${OLD_MED:-0}
    OLD_OBB=$(grep "^ObbSize=" "$APP_DIR/Meta.txt" | cut -d= -f2); OLD_OBB=${OLD_OBB:-0}
    OLD_SSAID=$(grep "^SSAID=" "$APP_DIR/Meta.txt" | cut -d= -f2)
    ACT=0; FORCE_DATA=0

    if CHK 1 && [ -f "$APP_DIR/App.bundle.pack" ]; then
        if ! PKG_INSTALLED "$PKG" "$VER"; then
            echo "INFO:STEP|MSG:App (Base & Splits)"
            "$ZAPDOS" -d -q -c "$APP_DIR/App.bundle.pack" | tar -xf - -C "$TMP_PKG" 2>/dev/null
            apks_to_install=$(find "$TMP_PKG" -type f -name "*.apk" | sort)
            [ -n "$apks_to_install" ] && pm install -g --dexopt-compiler-filter skip $apks_to_install >/dev/null 2>&1
            ACT=1; FORCE_DATA=1
        fi
    fi

    pm disable "$PKG" >/dev/null 2>&1
    NEW_UID=$(stat -c '%u' "/data/data/$PKG" 2>/dev/null)
    [ -z "$NEW_UID" ] && NEW_UID=$(dumpsys package "$PKG" | grep -m1 "userId=" | cut -d= -f2 | awk '{print $1}')

    CUR_DATA=0; CUR_EXT=0; CUR_MED=0; CUR_OBB=0

    if [ "$FORCE_DATA" -eq 0 ]; then
        TMP_SIZES="$AM_TMP/${PKG}_sizes"; mkdir -p "$TMP_SIZES"
        CHK 2 && { [ -f "$APP_DIR/Data.bundle.pack" ] || [ -f "$APP_DIR/UserDe.bundle.pack" ]; } && ( echo $(( $(RAW_SIZE "/data/data/$PKG") + $(RAW_SIZE "/data/user_de/0/$PKG") )) > "$TMP_SIZES/data" ) &
        CHK 3 && [ -f "$APP_DIR/ExtData.bundle.pack" ] && ( echo $(RAW_SIZE "/data/media/0/Android/data/$PKG") > "$TMP_SIZES/ext" ) &
        CHK 4 && [ -f "$APP_DIR/Media.bundle.pack" ] && ( echo $(RAW_SIZE "/data/media/0/Android/media/$PKG") > "$TMP_SIZES/med" ) &
        CHK 5 && [ -f "$APP_DIR/Obb.bundle.pack" ] && ( echo $(RAW_SIZE "/data/media/0/Android/obb/$PKG") > "$TMP_SIZES/obb" ) &
        wait
        CUR_DATA=$(cat "$TMP_SIZES/data" 2>/dev/null); CUR_DATA=${CUR_DATA:-0}; CUR_EXT=$(cat "$TMP_SIZES/ext" 2>/dev/null); CUR_EXT=${CUR_EXT:-0}; CUR_MED=$(cat "$TMP_SIZES/med" 2>/dev/null); CUR_MED=${CUR_MED:-0}; CUR_OBB=$(cat "$TMP_SIZES/obb" 2>/dev/null); CUR_OBB=${CUR_OBB:-0}
        rm -rf "$TMP_SIZES"
    fi

    if CHK 2 && { [ -f "$APP_DIR/Data.bundle.pack" ] || [ -f "$APP_DIR/UserDe.bundle.pack" ]; }; then
        if [ "$FORCE_DATA" -eq 1 ] || [ "$CUR_DATA" != "$OLD_DATA" ]; then
            echo "INFO:STEP|MSG:Data (/data & user_de)"
            [ -f "$APP_DIR/Data.bundle.pack" ] && UNBUNDAPP "$APP_DIR/Data.bundle.pack" "/data/data"
            [ -f "$APP_DIR/UserDe.bundle.pack" ] && UNBUNDAPP "$APP_DIR/UserDe.bundle.pack" "/data/user_de/0"
            ACT=1
        fi
    fi
    if CHK 3 && [ -f "$APP_DIR/ExtData.bundle.pack" ]; then
        if [ "$FORCE_DATA" -eq 1 ] || [ "$CUR_EXT" != "$OLD_EXT" ]; then
            echo "INFO:STEP|MSG:ExtData (Android/data)"; UNBUNDAPP "$APP_DIR/ExtData.bundle.pack" "/data/media/0/Android/data"; ACT=1
        fi
    fi
    if CHK 4 && [ -f "$APP_DIR/Media.bundle.pack" ]; then
        if [ "$FORCE_DATA" -eq 1 ] || [ "$CUR_MED" != "$OLD_MED" ]; then
            echo "INFO:STEP|MSG:Media (Android/media)"; UNBUNDAPP "$APP_DIR/Media.bundle.pack" "/data/media/0/Android/media"; ACT=1
        fi
    fi
    if CHK 5 && [ -f "$APP_DIR/Obb.bundle.pack" ]; then
        if [ "$FORCE_DATA" -eq 1 ] || [ "$CUR_OBB" != "$OLD_OBB" ]; then
            echo "INFO:STEP|MSG:OBB (Android/obb)"; UNBUNDAPP "$APP_DIR/Obb.bundle.pack" "/data/media/0/Android/obb"; ACT=1
        fi
    fi
    wait

    if CHK 6; then
        CUR_SSAID=$(READID "$PKG")
        if [ -n "$OLD_SSAID" ] && [ "$CUR_SSAID" != "$OLD_SSAID" ]; then
            echo "INFO:STEP|MSG:Android ID & Permissions"; CHANID "$PKG" "$OLD_SSAID"; ACT=1
        fi
    fi

    [ "$ACT" -eq 1 ] && [ -f "$APP_DIR/Permissions.txt" ] && SETPERM "$PKG" "$APP_DIR/Permissions.txt"
    [ "$ACT" -eq 0 ] && echo "INFO:STEP|MSG:Up to date (Skipped)"

    if [ -n "$NEW_UID" ] && [ "$ACT" -eq 1 ]; then
        chown -R "$NEW_UID:$NEW_UID" "/data/data/$PKG" "/data/user_de/0/$PKG" 2>/dev/null
        restorecon -R "/data/data/$PKG" "/data/user_de/0/$PKG" 2>/dev/null
        [ -n "$ADGID" ] && chown -R "$NEW_UID:$ADGID" "/data/media/0/Android/data/$PKG" 2>/dev/null
        [ -n "$AMGID" ] && chown -R "$NEW_UID:$AMGID" "/data/media/0/Android/media/$PKG" 2>/dev/null
        [ -n "$AOGID" ] && chown -R "$NEW_UID:$AOGID" "/data/media/0/Android/obb/$PKG" 2>/dev/null
        DELGMS "$PKG"
    fi
    pm enable "$PKG" >/dev/null 2>&1; rm -rf "$TMP_PKG"
    echo "ACTION:RESTORE_DONE|PKG:$PKG"
}

do_backup() {
    export APP_COMPS="$1"
    rm -rf "$AM_TMP/precalc" "$AM_TMP/selected_apps_sizes.txt" "$AM_TMP/selected_apps_sorted.txt" 2>/dev/null
    mkdir -p "$AM_TMP/precalc"
    echo "ACTION:CALCULATING|MSG:Calculating sizes..."

    while IFS='|' read -r pkg label ver type || [ -n "$pkg" ]; do
        [ -z "$pkg" ] && continue
        pkg=$(echo "$pkg" | tr -d '\r'); label=$(echo "$label" | tr -d '\r')
        ver=$(echo "$ver" | tr -d '\r'); type=$(echo "$type" | tr -d '\r')

        size=0
        CHK 1 && apks=$(pm path "$pkg" 2>/dev/null | sed 's/^package://') && [ -n "$apks" ] && size=$((size + $(RAW_SIZE "$apks") ))
        CHK 2 && size=$((size + $(RAW_SIZE "/data/data/$pkg") + $(RAW_SIZE "/data/user_de/0/$pkg") ))
        CHK 3 && size=$((size + $(RAW_SIZE "/data/media/0/Android/data/$pkg") ))
        CHK 4 && size=$((size + $(RAW_SIZE "/data/media/0/Android/media/$pkg") ))
        CHK 5 && size=$((size + $(RAW_SIZE "/data/media/0/Android/obb/$pkg") ))

        echo "${size}|${label}|${pkg}|${ver}|${type}" > "$AM_TMP/precalc/$pkg.txt"
    done < "$TARGETS"

    cat "$AM_TMP/precalc/"*.txt > "$AM_TMP/selected_apps_sizes.txt" 2>/dev/null
    TOTAL_KB=$(awk -F'|' '{s+=$1} END{print s+0}' "$AM_TMP/selected_apps_sizes.txt")
    sort -t'|' -k1 -n -r "$AM_TMP/selected_apps_sizes.txt" > "$AM_TMP/selected_apps_sorted.txt"

    START=$(date +%s); TOTAL_APPS=$(wc -l < "$AM_TMP/selected_apps_sorted.txt"); CURRENT_APP=0

    while IFS='|' read -r size label pkg ver type || [ -n "$size" ]; do
        CURRENT_APP=$((CURRENT_APP + 1))
        DO_BACKUP "$pkg" "$label" "$ver" "$type" "$CURRENT_APP" "$TOTAL_APPS" "$((CURRENT_APP * 100 / TOTAL_APPS))" "$size"
    done < "$AM_TMP/selected_apps_sorted.txt"
    wait
    echo "ACTION:GLOBAL_DONE|TOTAL:$TOTAL_KB|TIME:$((( $(date +%s) - START )))"
}

do_restore() {
    export APP_COMPS="$1"
    rm -rf "$AM_TMP/selected_restores.txt" "$AM_TMP/selected_restores_sorted.txt" 2>/dev/null
    > "$AM_TMP/selected_restores.txt"

    while IFS='|' read -r pkg label ver type || [ -n "$pkg" ]; do
        [ -z "$pkg" ] && continue
        pkg=$(echo "$pkg" | tr -d '\r'); label=$(echo "$label" | tr -d '\r'); type=$(echo "$type" | tr -d '\r')

        APP_PATH="$BACKUP_BASE/$type/$label/Meta.txt"
        if [ -f "$APP_PATH" ]; then
            size=$(grep "^TotalSize=" "$APP_PATH" | cut -d= -f2)
            echo "${size:-0}|${label}|${type}" >> "$AM_TMP/selected_restores.txt"
        fi
    done < "$TARGETS"

    sort -t'|' -k1 -n -r "$AM_TMP/selected_restores.txt" > "$AM_TMP/selected_restores_sorted.txt"
    TOTAL_KB=$(awk -F'|' '{s+=$1} END{print s+0}' "$AM_TMP/selected_restores_sorted.txt")
    START=$(date +%s); TOTAL_APPS=$(wc -l < "$AM_TMP/selected_restores_sorted.txt"); CURRENT_APP=0

    settings put global verifier_verify_adb_installs 0
    while IFS='|' read -r size label type || [ -n "$size" ]; do
        CURRENT_APP=$((CURRENT_APP + 1))
        DO_RESTORE "$label" "$type" "$CURRENT_APP" "$TOTAL_APPS" "$((CURRENT_APP * 100 / TOTAL_APPS))" "$size"
    done < "$AM_TMP/selected_restores_sorted.txt"
    settings put global verifier_verify_adb_installs 1

    echo "ACTION:GLOBAL_DONE|TOTAL:$TOTAL_KB|TIME:$((( $(date +%s) - START )))"
}

ensure_root
init_shifter "$2"

case "$1" in
    --backup) do_backup "$3" ;;
    --restore) do_restore "$3" ;;
    --rom-backup) do_rom_backup "$3" "$4" "$5" "$6" ;;
    --rom-restore) do_rom_restore "$3" "$4" "$5" "$6" ;;
    --install-meta) install_meta_module ;;
    --live-backup) do_live_backup "$3" ;;
    --live-restore) do_live_restore "$3" "$4" ;;
esac