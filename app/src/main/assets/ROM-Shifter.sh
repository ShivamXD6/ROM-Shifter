#!/system/bin/sh
# ==========================================
# ROM Shifter - APP BACKEND ENGINE
# ==========================================

BIN_DIR="/data/adb/#Shifter"
ZAPDOS="$BIN_DIR/zapdos"
JOBS=$(nproc 2>/dev/null || echo 4)
AM_TMP="/data/local/tmp/appmgr_tmp"
TARGETS="/data/local/tmp/shifter_targets.txt"
MAIN_DIR="/sdcard/#Shifter"
BACKUP_BASE="$MAIN_DIR/Data-Migrated"
LP_DIR="$MAIN_DIR/Live-Partition"

init_shifter() {
     mkdir -p "$BIN_DIR" "$AM_TMP" "$BACKUP_BASE" "$LP_DIR"
     chmod +x "$ZAPDOS" 2>/dev/null
}

COOLDOWN() { while [ "$(jobs | grep -c 'Running')" -ge "$1" ] 2>/dev/null; do sleep 0.1; done; }
SANITIZE() { echo "$1" | sed 's/[^a-zA-Z0-9]/_/g'; }

CHK() { case " $APP_COMPS " in *" $1 "*) return 0 ;; *) return 1 ;; esac }

RAW_SIZE() {
    local sum=0
    for p in $1; do
        p=$(echo "$p" | tr -d '\r')
        if [ -n "$p" ] && [ -e "$p" ]; then
            local base=$(du -sk "$p" 2>/dev/null | head -n1 | grep -o '^[0-9]*')
            local c1=$(du -sk "$p/cache" 2>/dev/null | head -n1 | grep -o '^[0-9]*')
            local c2=$(du -sk "$p/code_cache" 2>/dev/null | head -n1 | grep -o '^[0-9]*')
            sum=$(( sum + ${base:-0} - ${c1:-0} - ${c2:-0} ))
        fi
    done
    echo "$sum"
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
    local apkpath="$(pm path "$1" 2>/dev/null | sed -n 's/^package://p' | head -n 1 | tr -d '\r')"
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
    apks="$(pm path "$PKG" 2>/dev/null | sed 's/^package://' | tr -d '\r')"

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
    rm -rf "$TMP_SIZES"

    if CHK 1; then
        if [ "$CUR_APP" != "$OLD_APP" ] || { [ "$CUR_APP" -gt 0 ] && [ ! -f "$APP_DIR/App.bundle.pack" ]; }; then
            if [ "$CUR_APP" -gt 0 ] && [ -n "$apks" ]; then
                echo "$apks" | sed 's|^/||' | tar -cf - -C / -T - 2>/dev/null | "$ZAPDOS" -1 -f -q -o "$APP_DIR/App.bundle.pack" &
            else rm -f "$APP_DIR/App.bundle.pack"; fi
            OLD_APP=$CUR_APP
        fi
    fi
    if CHK 2; then
        if [ "$CUR_DATA" != "$OLD_DATA" ] || { [ "$CUR_DATA" -gt 0 ] && [ ! -f "$APP_DIR/Data.bundle.pack" ] && [ ! -f "$APP_DIR/UserDe.bundle.pack" ]; }; then
            if [ "$CUR_DATA" -gt 0 ]; then
                [ -d "/data/data/$PKG" ] && BUNDAPP "/data/data" "$PKG" "$APP_DIR" "Data"
                [ -d "/data/user_de/0/$PKG" ] && BUNDAPP "/data/user_de/0" "$PKG" "$APP_DIR" "UserDe"
            else rm -f "$APP_DIR/Data.bundle.pack" "$APP_DIR/UserDe.bundle.pack"; fi
            OLD_DATA=$CUR_DATA
        fi
    fi
    if CHK 3; then
        if [ "$CUR_EXT" != "$OLD_EXT" ] || { [ "$CUR_EXT" -gt 0 ] && [ ! -f "$APP_DIR/ExtData.bundle.pack" ]; }; then
            if [ "$CUR_EXT" -gt 0 ]; then
                BUNDAPP "/data/media/0/Android/data" "$PKG" "$APP_DIR" "ExtData"
            else rm -f "$APP_DIR/ExtData.bundle.pack"; fi
            OLD_EXT=$CUR_EXT
        fi
    fi
    if CHK 4; then
        if [ "$CUR_MED" != "$OLD_MED" ] || { [ "$CUR_MED" -gt 0 ] && [ ! -f "$APP_DIR/Media.bundle.pack" ]; }; then
            if [ "$CUR_MED" -gt 0 ]; then
                BUNDAPP "/data/media/0/Android/media" "$PKG" "$APP_DIR" "Media"
            else rm -f "$APP_DIR/Media.bundle.pack"; fi
            OLD_MED=$CUR_MED
        fi
    fi
    if CHK 5; then
        if [ "$CUR_OBB" != "$OLD_OBB" ] || { [ "$CUR_OBB" -gt 0 ] && [ ! -f "$APP_DIR/Obb.bundle.pack" ]; }; then
            if [ "$CUR_OBB" -gt 0 ]; then
                BUNDAPP "/data/media/0/Android/obb" "$PKG" "$APP_DIR" "Obb"
            else rm -f "$APP_DIR/Obb.bundle.pack"; fi
            OLD_OBB=$CUR_OBB
        fi
    fi
    if CHK 6; then
        CUR_SSAID=$(READID "$PKG")
        if [ -n "$CUR_SSAID" ] && [ "$CUR_SSAID" != "$OLD_SSAID" ]; then
            OLD_SSAID=$CUR_SSAID
        fi
    fi

    wait
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
    [ -f "$APP_DIR/Meta.txt" ] || return
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
    FORCE_DATA=0

    if CHK 1 && [ -f "$APP_DIR/App.bundle.pack" ]; then
        if ! PKG_INSTALLED "$PKG" "$VER"; then
            "$ZAPDOS" -d -q -c "$APP_DIR/App.bundle.pack" | tar -xf - -C "$TMP_PKG" 2>/dev/null
            apks_to_install=$(find "$TMP_PKG" -type f -name "*.apk" | sort)
            [ -n "$apks_to_install" ] && pm install -g --dexopt-compiler-filter skip $apks_to_install >/dev/null 2>&1
            FORCE_DATA=1
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
            [ -f "$APP_DIR/Data.bundle.pack" ] && UNBUNDAPP "$APP_DIR/Data.bundle.pack" "/data/data"
            [ -f "$APP_DIR/UserDe.bundle.pack" ] && UNBUNDAPP "$APP_DIR/UserDe.bundle.pack" "/data/user_de/0"
        fi
    fi
    if CHK 3 && [ -f "$APP_DIR/ExtData.bundle.pack" ]; then
        if [ "$FORCE_DATA" -eq 1 ] || [ "$CUR_EXT" != "$OLD_EXT" ]; then
            UNBUNDAPP "$APP_DIR/ExtData.bundle.pack" "/data/media/0/Android/data"
        fi
    fi
    if CHK 4 && [ -f "$APP_DIR/Media.bundle.pack" ]; then
        if [ "$FORCE_DATA" -eq 1 ] || [ "$CUR_MED" != "$OLD_MED" ]; then
            UNBUNDAPP "$APP_DIR/Media.bundle.pack" "/data/media/0/Android/media"
        fi
    fi
    if CHK 5 && [ -f "$APP_DIR/Obb.bundle.pack" ]; then
        if [ "$FORCE_DATA" -eq 1 ] || [ "$CUR_OBB" != "$OLD_OBB" ]; then
            UNBUNDAPP "$APP_DIR/Obb.bundle.pack" "/data/media/0/Android/obb"
        fi
    fi
    wait

    if CHK 6; then
        CUR_SSAID=$(READID "$PKG")
        if [ -n "$OLD_SSAID" ] && [ "$CUR_SSAID" != "$OLD_SSAID" ]; then
            CHANID "$PKG" "$OLD_SSAID"
        fi
    fi

    [ -f "$APP_DIR/Permissions.txt" ] && SETPERM "$PKG" "$APP_DIR/Permissions.txt"

    if [ -n "$NEW_UID" ]; then
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

    while IFS='|' read -r pkg label ver type || [ -n "$pkg" ]; do
        [ -z "$pkg" ] && continue
        pkg=$(echo "$pkg" | tr -d '\r'); label=$(echo "$label" | tr -d '\r')
        ver=$(echo "$ver" | tr -d '\r'); type=$(echo "$type" | tr -d '\r')

        size=0
        CHK 1 && apks=$(pm path "$pkg" 2>/dev/null | sed 's/^package://' | tr -d '\r') && [ -n "$apks" ] && size=$((size + $(RAW_SIZE "$apks") ))
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

do_remove() {
    local PKG="$1"
    local FORCE="$2"
    if [ "$FORCE" == "true" ]; then
        local APK_PATH=$(pm path "$PKG" | sed 's/^package://')
        if [[ "$APK_PATH" == /system/* ]] || [[ "$APK_PATH" == /product/* ]] || [[ "$APK_PATH" == /vendor/* ]]; then
            local DIR_PATH=$(dirname "$APK_PATH")
            mount -o rw,remount /
            mount -o rw,remount /system
            mount -o rw,remount /product
            mount -o rw,remount /vendor
            rm -rf "$DIR_PATH"
            pm uninstall --user 0 "$PKG" >/dev/null 2>&1
            return
        fi
    fi
    pm uninstall --user 0 "$PKG" >/dev/null 2>&1
}

do_restore_debloat() {
    cmd package install-existing "$1" >/dev/null 2>&1
}

do_systemize() {
    local PKG="$1"
    local LABEL="$2"
    local IS_PRIV="$3"

    local MOD_DIR="/data/adb/modules/ROM-Shifter"
    local UP_DIR="/data/adb/modules_update/ROM-Shifter"
    local PROP="id=ROM-Shifter\nname=ROM Shifter Systemized Apps\nversion=1.0\nversionCode=1\nauthor=ROM Shifter\ndescription=Systemlessly makes selected user apps system apps."

    mkdir -p "$MOD_DIR" && printf "$PROP\n" > "$MOD_DIR/module.prop" && chmod 644 "$MOD_DIR/module.prop"
    mkdir -p "$UP_DIR" && printf "$PROP\n" > "$UP_DIR/module.prop" && chmod 644 "$UP_DIR/module.prop"

    local APK_PATH=$(pm path "$PKG" | sed 's/^package://' | head -n 1 | tr -d '\r')

    if [ -n "$APK_PATH" ]; then
        local SAFE_LABEL=$(echo "$LABEL" | tr -cd 'a-zA-Z0-9_')
        local TARGET_DIR=""
        if [ "$IS_PRIV" == "true" ]; then
            TARGET_DIR="$UP_DIR/system/product/priv-app/$SAFE_LABEL"
        else
            TARGET_DIR="$UP_DIR/system/product/app/$SAFE_LABEL"
        fi
        local SOURCE_DIR=$(dirname "$APK_PATH")

        mkdir -p "$TARGET_DIR"
        cp -f "$SOURCE_DIR"/*.apk "$TARGET_DIR/"
        chmod 755 "$TARGET_DIR"
        chmod 644 "$TARGET_DIR"/*.apk
    fi
}

init_shifter

case "$1" in
    --backup) do_backup "$2" ;;
    --restore) do_restore "$2" ;;
    --live-backup) do_live_backup "$3" ;;
    --live-restore) do_live_restore "$3" "$4" ;;
    --remove) do_remove "$2" "$3" ;;
    --restore-debloat) do_restore_debloat "$2" ;;
    --systemize) do_systemize "$2" "$3" "$4" ;;
esac