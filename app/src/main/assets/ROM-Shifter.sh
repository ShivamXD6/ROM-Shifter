#!/system/bin/sh
# ==========================================
# ROM Shifter - APP BACKEND ENGINE
# ==========================================

export PATH="/system/bin:/vendor/bin:/system/xbin:/sbin:/data/adb/magisk:/data/adb/ksu/bin:/data/adb/ap/bin:$PATH"
BIN_DIR="/data/adb/Shifter"
ZAPDOS="$BIN_DIR/zapdos"
AM_TMP="/data/local/tmp/shifter_apps"
TARGETS="/data/local/tmp/shifter_targets.txt"
TOTAL_KB_JOB=0; TOTAL_KB_DONE=0
AWK_BIN="awk"; TAR_BIN="tar"; SED_BIN="sed"; GREP_BIN="grep"; STAT_BIN="stat"

find_tool() {
    local tool="$1"
    if command -v "$tool" >/dev/null 2>&1; then
        echo "$tool"
        return 0
    else
        echo "busybox $tool"
        return 0
    fi
    return 1
}

init_shifter() {
     AWK_BIN=$(find_tool awk); TAR_BIN=$(find_tool tar); SED_BIN=$(find_tool sed); GREP_BIN=$(find_tool grep); STAT_BIN=$(find_tool stat)
     mkdir -p "$BIN_DIR" "$AM_TMP"
     [ -n "$BACKUP_BASE" ] && mkdir -p "$BACKUP_BASE"
     [ -n "$LP_DIR" ] && mkdir -p "$LP_DIR"
     chmod +x "$ZAPDOS" 2>/dev/null
}

COOLDOWN() { while [ $(jobs | wc -l) -ge "$1" ] 2>/dev/null; do sleep 0.1; done; }
SANITIZE() { echo "$1" | $SED_BIN 's/[^a-zA-Z0-9]/_/g'; }

CHK() { case " $APP_COMPS " in *" $1 "*) return 0 ;; *) return 1 ;; esac }

RAW_SIZE() {
    du -sk "$@" 2>/dev/null | $AWK_BIN '{sum+=$1} END{print sum+0}'
}

FORMAT_SIZE() {
    local raw=$(echo "$1" | tr -d '\r\n ')
    $AWK_BIN -v n="${raw:-0}" 'BEGIN{
        n = n + 0
        if(n >= 1048576) printf "%.2f GB", n/1048576
        else if(n >= 1024) printf "%.2f MB", n/1024
        else printf "%d KB", n
    }'
}
READID() { $SED_BIN -n "/package=\"$1\"/s/.*value=\"\([^\"]*\)\".*/\1/p" "/data/system/users/0/settings_ssaid.xml" 2>/dev/null; }
CHANID() { $SED_BIN -i "/package=\"$1\"/s/\(value=\"\)[^\"]*\(.*defaultValue=\"\)[^\"]*/\1$2\2$2/" "/data/system/users/0/settings_ssaid.xml"; }
GETPERM() {
    local pkg="$1" out="$2"
    {
        dumpsys package "$pkg" 2>/dev/null | $AWK_BIN '
            /runtime permissions:/,/(requested|install) permissions:/ {
                if ($0 ~ /granted=true/) {
                    split($1, a, ":")
                    print "PERM:" a[1] "=true"
                } else if ($0 ~ /granted=false/) {
                    split($1, a, ":")
                    print "PERM:" a[1] "=false"
                }
            }
        '
        cmd appops get "$pkg" 2>/dev/null | $AWK_BIN -F': ' '
            /:/ && !/Uid mode/ {
                op = $1; sub(/^[ \t]+/, "", op)
                val = $2; sub(/; .*/, "", val)
                print "APPOP:" op "=" val
            }
        '
    } > "$out" &
}

SETPERM() {
    [ -f "$2" ] || return
    $AWK_BIN -v pkg="$1" -F'[:=]' '
        /^PERM:/ { print "cmd package " ($3=="true"?"grant ":"revoke ") pkg " " $2 " >/dev/null 2>&1" }
        /^APPOP:/ { print "cmd appops set " pkg " " $2 " " $3 " >/dev/null 2>&1" }
    ' "$2" | sh &
}

DELGMS() { rm -f "/data/data/$1/databases/com.google.android.datatransport.events" "/data/data/$1/databases/com.google.android.datatransport.events-journal" "/data/data/$1/no_backup/com.google.android.gms.appid-no-backup" "/data/data/$1/shared_prefs/com.google.android.gms.appid.xml" "/data/data/$1/shared_prefs/com.google.android.gms.measurement.prefs.xml" 2>/dev/null; }

FIND_BLOCK() {
    for p in "/dev/block/mapper/$1" "/dev/block/by-name/$1" "/dev/block/bootdevice/by-name/$1"; do
        [ -e "$p" ] && echo "$p" && return 0
    done
    return 1
}

PKG_INSTALLED() {
     local p_path=$(cmd package path "$1" 2>/dev/null || pm path "$1" 2>/dev/null)
     [ -z "$p_path" ] && return 1
     [ -z "$2" ] && return 0
     local inst_ver=$(dumpsys package "$1" 2>/dev/null | $SED_BIN -n '/versionCode=/ {s/.*versionCode=\([0-9]*\).*/\1/p; q}')
     [ "$inst_ver" = "$2" ] && return 0
     return 1
 }

BUNDAPP() {
    COOLDOWN 4
    $TAR_BIN --exclude="$2/cache" --exclude="$2/code_cache" -cpf - -C "$1" "$2" 2>/dev/null | "$ZAPDOS" -1 -f -q -o "$3/$4.shift" &
}

UNBUNDAPP() {
    COOLDOWN 5
    "$ZAPDOS" -d -q -c "$1" | $TAR_BIN -pxf - -C "$2" 2>/dev/null &
}

do_live_backup() {
    local part="$1"
    local BLOCK_PATH=$(FIND_BLOCK "$part")
    if [ -n "$BLOCK_PATH" ]; then
      dd if="$BLOCK_PATH" of="$LP_DIR/${part}_backup.img" bs=4M
    fi
}

do_live_restore() {
    local BLOCK_PATH=$(FIND_BLOCK "$1")
    [ -n "$BLOCK_PATH" ] && [ -f "$2" ] && [ "${2##*.}" = "img" ] && dd if="$2" of="$BLOCK_PATH" bs=4M && sync
}

DO_BACKUP() {
    PKG="$1"; LABEL="$2"; VER="$3"; VCODE="$4"; TYPE="$5"; APK_PATH="$6"
    CUR_IDX="$7"; TOT_IDX="$8"; SIZE="$9"
    S_APP="${10}"; S_DATA="${11}"; S_MED="${12}"

    touch "$BACKUP_BASE/.nomedia"
    APP_DIR="$BACKUP_BASE/$TYPE/$LABEL"; mkdir -p "$APP_DIR"

    OVERHEAD=25
    CHK 3 && OVERHEAD=$((OVERHEAD + 5))
    CHK 5 && OVERHEAD=$((OVERHEAD + 1))
    TOTAL_KB_DONE=$((TOTAL_KB_DONE + OVERHEAD))

    [ "${TOTAL_KB_JOB:-0}" -eq 0 ] && TOTAL_KB_JOB=1
    START_PCT=$(( TOTAL_KB_DONE * 100 / TOTAL_KB_JOB ))
    echo "ACTION:BACKUP_START|PKG:$PKG|LABEL:$LABEL|VER:$VER|CUR:$CUR_IDX|TOT:$TOT_IDX|PCT:$START_PCT|SIZE:$SIZE|JOBS:$(jobs | wc -l)"

    OLD_APP=0; OLD_DATA=0; OLD_MED=0; OLD_SSAID=""
    if [ -f "$APP_DIR/Meta.txt" ]; then
        while IFS='=' read -r key value || [ -n "$key" ]; do
            case "$key" in
                AppSize) OLD_APP=$value ;;
                DataExtSize) OLD_DATA=$value ;;
                MediaOBBSize) OLD_MED=$value ;;
                SSAID) OLD_SSAID=$value ;;
            esac
        done < "$APP_DIR/Meta.txt"
    fi

    if CHK 1; then
        if [ "$S_APP" != "$OLD_APP" ] || [ ! -f "$APP_DIR/App.shift" ]; then
            if [ -n "$APK_PATH" ]; then
                local apk_dir=$(dirname "$APK_PATH")
                find "$apk_dir" -maxdepth 1 -name "*.apk" 2>/dev/null | $SED_BIN 's|^/||' | $TAR_BIN -cf - -C / -T - 2>/dev/null | "$ZAPDOS" -1 -f -q -o "$APP_DIR/App.shift" &
            fi
            OLD_APP=$S_APP
        fi
    fi

    if CHK 2; then
        local m_data=0
        [ -d "/data/data/$PKG" ] && [ ! -f "$APP_DIR/Data.shift" ] && m_data=1
        [ -d "/data/user_de/0/$PKG" ] && [ ! -f "$APP_DIR/UserDe.shift" ] && m_data=1
        [ -d "/data/media/0/Android/data/$PKG" ] && [ ! -f "$APP_DIR/ExtData.shift" ] && m_data=1

        if [ "$S_DATA" != "$OLD_DATA" ] || [ "$m_data" -eq 1 ]; then
            [ -d "/data/data/$PKG" ] && BUNDAPP "/data/data" "$PKG" "$APP_DIR" "Data"
            [ -d "/data/user_de/0/$PKG" ] && BUNDAPP "/data/user_de/0" "$PKG" "$APP_DIR" "UserDe"
            [ -d "/data/media/0/Android/data/$PKG" ] && BUNDAPP "/data/media/0/Android/data" "$PKG" "$APP_DIR" "ExtData"
            OLD_DATA=$S_DATA
        fi
    fi

    if CHK 3; then
        GETPERM "$PKG" "$APP_DIR/Permissions.txt" &
    fi

    if CHK 4; then
        local m_med=0
        [ -d "/data/media/0/Android/media/$PKG" ] && [ ! -f "$APP_DIR/Media.shift" ] && m_med=1
        [ -d "/data/media/0/Android/obb/$PKG" ] && [ ! -f "$APP_DIR/Obb.shift" ] && m_med=1

        if [ "$S_MED" != "$OLD_MED" ] || [ "$m_med" -eq 1 ]; then
            [ -d "/data/media/0/Android/media/$PKG" ] && BUNDAPP "/data/media/0/Android/media" "$PKG" "$APP_DIR" "Media"
            [ -d "/data/media/0/Android/obb/$PKG" ] && BUNDAPP "/data/media/0/Android/obb" "$PKG" "$APP_DIR" "Obb"
            OLD_MED=$S_MED
        fi
    fi

    CHK 1 && TOTAL_KB_DONE=$((TOTAL_KB_DONE + S_APP))
    CHK 2 && TOTAL_KB_DONE=$((TOTAL_KB_DONE + S_DATA))
    CHK 4 && TOTAL_KB_DONE=$((TOTAL_KB_DONE + S_MED))

    if CHK 5; then
        CUR_SSAID=$(READID "$PKG")
        [ -n "$CUR_SSAID" ] && OLD_SSAID=$CUR_SSAID
    fi

    local final_pct=$(( TOTAL_KB_DONE * 100 / TOTAL_KB_JOB ))
    [ "$final_pct" -gt 100 ] && final_pct=100
    echo "ACTION:BACKUP_START|PKG:$PKG|LABEL:$LABEL|VER:$VER|CUR:$CUR_IDX|TOT:$TOT_IDX|PCT:$final_pct|SIZE:$SIZE|JOBS:$(jobs | wc -l)"

    SYS_PATH=""; [ "$TYPE" = "System" ] && SYS_PATH=$(dumpsys package "$PKG" 2>/dev/null | $AWK_BIN -F= '/codePath=\/(system|product|vendor|oem|odm)/{print $2; exit}')

    cat <<EOF > "$APP_DIR/Meta.txt"
Name=$LABEL
Package=$PKG
Version=$VER
VersionCode=$VCODE
AppSize=$OLD_APP
DataExtSize=$OLD_DATA
MediaOBBSize=$OLD_MED
EOF
    [ -n "$OLD_SSAID" ] && echo "SSAID=$OLD_SSAID" >> "$APP_DIR/Meta.txt"
    [ "$TYPE" = "System" ] && [ -n "$SYS_PATH" ] && echo "SysPath=$SYS_PATH" >> "$APP_DIR/Meta.txt"

    echo "ACTION:BACKUP_DONE|PKG:$PKG"
}

DO_RESTORE() {
    PKG="$1"; LABEL="$2"; VER="$3"; VCODE="$4"; TYPE="$5"; APK_PATH="$6"
    CUR_IDX="$7"; TOT_IDX="$8"; SIZE="$9"; S_APP="${10:-0}"; S_DATA="${11:-0}"; S_MED="${12:-0}"
    APP_DIR="$BACKUP_BASE/$TYPE/$LABEL"

    OVERHEAD=25
    CHK 3 && OVERHEAD=$((OVERHEAD + 5))
    CHK 5 && OVERHEAD=$((OVERHEAD + 1))
    TOTAL_KB_DONE=$((TOTAL_KB_DONE + OVERHEAD))

    [ "${TOTAL_KB_JOB:-0}" -eq 0 ] && TOTAL_KB_JOB=1
    START_PCT=$(( TOTAL_KB_DONE * 100 / TOTAL_KB_JOB ))
    echo "ACTION:RESTORE_START|PKG:$PKG|LABEL:$LABEL|VER:$VER|CUR:$CUR_IDX|TOT:$TOT_IDX|PCT:$START_PCT|SIZE:$SIZE|JOBS:$(jobs | wc -l)"

    [ -z "$PKG" ] && return
    UID=$($STAT_BIN -c '%u' "/data/data/$PKG" 2>/dev/null)
    rm -rf "/data/data/$PKG/cache" "/data/data/$PKG/code_cache" "/data/user_de/0/$PKG/cache" "/data/user_de/0/$PKG/code_cache"
    echo "$PKG|$UID" >> "$AM_TMP/restore_queue.list"

    if CHK 2; then
        [ -f "$APP_DIR/Data.shift" ] && UNBUNDAPP "$APP_DIR/Data.shift" "/data/data"
        [ -f "$APP_DIR/UserDe.shift" ] && UNBUNDAPP "$APP_DIR/UserDe.shift" "/data/user_de/0"
        [ -f "$APP_DIR/ExtData.shift" ] && UNBUNDAPP "$APP_DIR/ExtData.shift" "/data/media/0/Android/data"
    fi

    if CHK 4; then
        [ -f "$APP_DIR/Media.shift" ] && UNBUNDAPP "$APP_DIR/Media.shift" "/data/media/0/Android/media"
        [ -f "$APP_DIR/Obb.shift" ] && UNBUNDAPP "$APP_DIR/Obb.shift" "/data/media/0/Android/obb"
    fi

    if CHK 5 && [ -f "$APP_DIR/Meta.txt" ]; then
        OLD_SSAID=$($SED_BIN -n 's/^SSAID=//p' "$APP_DIR/Meta.txt" | tr -d '\r')
        [ -n "$OLD_SSAID" ] && CHANID "$PKG" "$OLD_SSAID"
    fi

    if CHK 3 && [ -f "$APP_DIR/Permissions.txt" ]; then
        SETPERM "$PKG" "$APP_DIR/Permissions.txt"
    fi

    CHK 1 && TOTAL_KB_DONE=$((TOTAL_KB_DONE + S_APP))
    CHK 2 && TOTAL_KB_DONE=$((TOTAL_KB_DONE + S_DATA))
    CHK 4 && TOTAL_KB_DONE=$((TOTAL_KB_DONE + S_MED))
    local final_pct=$(( TOTAL_KB_DONE * 100 / TOTAL_KB_JOB ))
    [ "$final_pct" -gt 100 ] && final_pct=100
    echo "ACTION:RESTORE_START|PKG:$PKG|LABEL:$LABEL|VER:$VER|CUR:$CUR_IDX|TOT:$TOT_IDX|PCT:$final_pct|SIZE:$SIZE|JOBS:$(jobs | wc -l)"

    rm -rf "$TMP_PKG" &
    echo "ACTION:RESTORE_DONE|PKG:$PKG"
}

INSTALL_APP_FILE() {
    local FILE="$1"
    local PKG="$2"
    local LABEL="$3"
    local EXT="${FILE##*.}"
    local T_PKG="$AM_TMP/install_$PKG"
    rm -rf "$T_PKG"; mkdir -p "$T_PKG"; chmod 777 "$T_PKG"

    echo "INFO:STEP|MSG:INSTALLING|PKG:$PKG|LABEL:$LABEL"

    case "$EXT" in
        apk|APK)
            cp "$FILE" "$T_PKG/base.apk"
            ;;
        *)
            unzip -q "$FILE" -d "$T_PKG" 2>/dev/null
            ;;
    esac

    chmod -R 777 "$T_PKG" 2>/dev/null
    local apks=$(find "$T_PKG" -type f -name "*.apk" | sort)

    if [ -n "$apks" ]; then
        local SID=$(su 1000 -c "cmd package install-create --user 0 -i com.android.vending --install-reason 4 -r -d -t --bypass-low-target-sdk-block 2>/dev/null" | tr -dc '0-9')
        if [ -n "$SID" ]; then
            local c=0
            for a in $apks; do
                c=$((c + 1))
                su 1000 -c "cmd package install-write $SID split_${c} '$a' >/dev/null 2>&1" || cmd package install-write $SID split_${c} '$a' >/dev/null 2>&1
            done
            local RES=$(su 1000 -c "cmd package install-commit $SID 2>&1" || cmd package install-commit $SID 2>&1)
            rm -rf "$T_PKG"
            if echo "$RES" | grep -iq "Success"; then
                echo "ACTION:INSTALL_DONE|PKG:$PKG"
            else
                echo "ACTION:INSTALL_ERROR|PKG:$PKG|MSG:$RES"
            fi
         else
            local err=0
            for a in $apks; do
                pm install -r -d "$a" >/dev/null 2>&1 || err=1
            done
            rm -rf "$T_PKG"
            [ "$err" -eq 0 ] && echo "ACTION:INSTALL_DONE|PKG:$PKG" || echo "ACTION:INSTALL_ERROR|PKG:$PKG"
        fi
    else
        rm -rf "$T_PKG"
        echo "ACTION:INSTALL_ERROR|PKG:$PKG"
    fi
}

do_install_apps() {
    local TARGET_FILE="$1"
    [ ! -f "$TARGET_FILE" ] && return

    cmd package disable com.android.vending >/dev/null 2>&1; settings put global verifier_verify_adb_installs 0; settings put global package_verifier_enable 0;  setprop pm.dexopt.install assume-verified; setprop pm.dexopt.install-bulk assume-verified; setprop pm.dexopt.install-bulk-downgraded skip

    while IFS='|' read -r file pkg label || [ -n "$file" ]; do
        [ -z "$file" ] && continue
        COOLDOWN 3
        INSTALL_APP_FILE "$file" "$pkg" "$label" &
    done < "$TARGET_FILE"
    wait

    cmd package enable com.android.vending >/dev/null 2>&1; settings put global verifier_verify_adb_installs 1;  settings put global package_verifier_enable 1;  setprop pm.dexopt.install speed-profile; setprop pm.dexopt.install-bulk speed-profile; setprop pm.dexopt.install-bulk-downgraded verify
    echo "ACTION:GLOBAL_DONE"
}

do_backup() {
    export APP_COMPS="$1"
    rm -rf "$AM_TMP/selected_apps_sizes.txt" "$AM_TMP/selected_apps_sorted.txt" 2>/dev/null

    echo "INFO:STEP|MSG:Preparing backup list..."

    if [ ! -f "$TARGETS" ]; then
        echo "ACTION:ERROR|MSG:Targets file not found at $TARGETS"
        return 1
    fi

    $AWK_BIN -F'|' -v global_comps="$APP_COMPS" '
    {
        pkg=$1; label=$2; ver=$3; vcode=$4; type=$5; apath=$6;
        s_app=$7; s_data=$8; s_med=$9; app_comps=$10;

        raw_comps = (app_comps != "" ? app_comps : global_comps)
        comps = " " raw_comps " "

        total = 25
        if(comps ~ / 3 /) total += 5
        if(comps ~ / 5 /) total += 1
        if(comps ~ / 1 /) total += s_app
        if(comps ~ / 2 /) total += s_data
        if(comps ~ / 4 /) total += s_med

        print total "|" label "|" pkg "|" ver "|" vcode "|" type "|" apath "|" s_app "|" s_data "|" s_med "|" raw_comps
    }' "$TARGETS" | tr -d '\r' > "$AM_TMP/selected_apps_sizes.txt"

    TOTAL_KB_JOB=$($AWK_BIN -F'|' '{s+=$1} END{print s+0}' "$AM_TMP/selected_apps_sizes.txt")
    TOTAL_KB_DONE=0
    sort -t'|' -k1 -n -r "$AM_TMP/selected_apps_sizes.txt" > "$AM_TMP/selected_apps_sorted.txt"

    START=$(date +%s); TOTAL_APPS=$(wc -l < "$AM_TMP/selected_apps_sorted.txt"); CURRENT_APP=0

    while IFS='|' read -r size label pkg ver vcode type apath s_app s_data s_med app_comps || [ -n "$size" ]; do
        CURRENT_APP=$((CURRENT_APP + 1))
        size=${size:-0}
        export APP_COMPS="$app_comps"
        DO_BACKUP "$pkg" "$label" "$ver" "$vcode" "$type" "$apath" "$CURRENT_APP" "$TOTAL_APPS" "$size" "$s_app" "$s_data" "$s_med"
    done < "$AM_TMP/selected_apps_sorted.txt"
    COOLDOWN 1
    echo "INFO:STEP|MSG:Almost Done, Please Wait..."
    wait
    echo "ACTION:GLOBAL_DONE|TOTAL:$TOTAL_KB_JOB|TIME:$((( $(date +%s) - START )))"
}

do_restore() {
    export APP_COMPS="$1"
    rm -rf "$AM_TMP/selected_restores.txt" "$AM_TMP/selected_restores_sorted.txt" "$AM_TMP/restore_queue.list" 2>/dev/null

    echo "INFO:STEP|MSG:Preparing restore list..."

    if [ ! -f "$TARGETS" ]; then
        echo "ACTION:ERROR|MSG:Targets file not found at $TARGETS"
        return 1
    fi

    $AWK_BIN -F'|' -v global_comps="$APP_COMPS" '
    {
        pkg=$1; label=$2; ver=$3; vcode=$4; type=$5; apath=$6;
        s_app=$7; s_data=$8; s_med=$9; app_comps=$10;

        raw_comps = (app_comps != "" ? app_comps : global_comps)
        comps = " " raw_comps " "

        total = 25
        if(comps ~ / 3 /) total += 5
        if(comps ~ / 5 /) total += 1
        if(comps ~ / 1 /) total += s_app
        if(comps ~ / 2 /) total += s_data
        if(comps ~ / 4 /) total += s_med

        print total "|" label "|" pkg "|" ver "|" vcode "|" type "|" apath "|" s_app "|" s_data "|" s_med "|" raw_comps
    }' "$TARGETS" | tr -d '\r' > "$AM_TMP/selected_restores.txt"

    TOTAL_KB_JOB=$($AWK_BIN -F'|' '{s+=$1} END{print s+0}' "$AM_TMP/selected_restores.txt")
    TOTAL_KB_DONE=0
    sort -t'|' -k1 -n -r "$AM_TMP/selected_restores.txt" > "$AM_TMP/selected_restores_sorted.txt"

    START=$(date +%s); TOTAL_APPS=$(wc -l < "$AM_TMP/selected_restores_sorted.txt"); CURRENT_APP=0

    cmd package disable com.android.vending >/dev/null 2>&1; settings put global verifier_verify_adb_installs 0;  settings put global package_verifier_enable 0;  setprop pm.dexopt.install assume-verified; setprop pm.dexopt.install-bulk assume-verified; setprop pm.dexopt.install-bulk-downgraded skip

    while IFS='|' read -r size label pkg ver vcode type apath s_app s_data s_med app_comps || [ -n "$size" ]; do
        [ -z "$pkg" ] && continue
        export APP_COMPS="${app_comps:-$1}"
        APP_DIR="$BACKUP_BASE/$type/$label"
        if CHK 1 && [ -f "$APP_DIR/App.shift" ] && ! PKG_INSTALLED "$pkg" "$vcode"; then
             COOLDOWN 3
             (
                 echo "INFO:STEP|MSG:Installing $label...|JOBS:$(jobs | wc -l)"
                 local T_PKG="$AM_TMP/$pkg"; mkdir -p "$T_PKG"; chmod 777 "$T_PKG"
                 "$ZAPDOS" -d -q -c "$APP_DIR/App.shift" | $TAR_BIN -xf - -C "$T_PKG" 2>/dev/null
                 chmod -R 777 "$T_PKG" 2>/dev/null
                 local apks=$(find "$T_PKG" -type f -name "*.apk" | sort)
                 if [ -n "$apks" ]; then
                     local SID=$(su 1000 -c "cmd package install-create --user 0 -i com.android.vending --install-reason 4 -r -d 2>/dev/null" | tr -dc '0-9')
                     if [ -n "$SID" ]; then
                         local c=0
                         for a in $apks; do
                             c=$((c + 1))
                             su 1000 -c "cmd package install-write $SID split_${c} '$a' >/dev/null 2>&1"
                         done
                         su 1000 -c "cmd package install-commit $SID >/dev/null 2>&1"
                     else
                        for a in $apks; do
                              pm install -r "$a" >/dev/null 2>&1
                        done
                     fi
                 fi
                 rm -rf "$T_PKG"; cmd package disable "$PKG" >/dev/null 2>&1
             ) &
        fi
    done < "$AM_TMP/selected_restores_sorted.txt"
    wait

    ADGID=$($STAT_BIN -c '%g' "/data/media/0/Android/data" 2>/dev/null); AMGID=$($STAT_BIN -c '%g' "/data/media/0/Android/media" 2>/dev/null); AOGID=$($STAT_BIN -c '%g' "/data/media/0/Android/obb" 2>/dev/null)
    while IFS='|' read -r size label pkg ver vcode type apath s_app s_data s_med app_comps || [ -n "$size" ]; do
        [ -z "$pkg" ] && continue
        CURRENT_APP=$((CURRENT_APP + 1))
        size=${size:-0}
        export APP_COMPS="${app_comps:-$1}"
        DO_RESTORE "$pkg" "$label" "$ver" "$vcode" "$type" "$apath" "$CURRENT_APP" "$TOTAL_APPS" "$size" "$s_app" "$s_data" "$s_med"
    done < "$AM_TMP/selected_restores_sorted.txt"
    COOLDOWN 1
    echo "INFO:STEP|MSG:Almost Done, Please Wait..."
    wait

    if [ -f "$AM_TMP/restore_queue.list" ]; then
        while IFS='|' read -r q_pkg q_uid; do
            COOLDOWN 10
            (
                chown -hR "$q_uid:$q_uid" "/data/data/$q_pkg" "/data/user_de/0/$q_pkg" 2>/dev/null
                [ -n "$ADGID" ] && chown -hR "$q_uid:$ADGID" "/data/media/0/Android/data/$q_pkg" 2>/dev/null
                [ -n "$AMGID" ] && chown -hR "$q_uid:$AMGID" "/data/media/0/Android/media/$q_pkg" 2>/dev/null
                [ -n "$AOGID" ] && chown -hR "$q_uid:$AOGID" "/data/media/0/Android/obb/$q_pkg" 2>/dev/null

                local q_ctx=$(ls -dZ "/data/data/$q_pkg" 2>/dev/null | $AWK_BIN '{print $1}')
                if [ -n "$q_ctx" ] && [ "$q_ctx" != "?" ]; then
                    chcon -hR "$q_ctx" "/data/data/$q_pkg" "/data/user_de/0/$q_pkg" 2>/dev/null
                else
                    restorecon -R "/data/data/$q_pkg" "/data/user_de/0/$q_pkg" 2>/dev/null
                fi
                DELGMS "$q_pkg"
                cmd package enable "$q_pkg" >/dev/null 2>&1
            ) &
        done < "$AM_TMP/restore_queue.list"
    fi
    wait

    cmd package enable com.android.vending >/dev/null 2>&1; settings put global verifier_verify_adb_installs 1;  settings put global package_verifier_enable 1;  setprop pm.dexopt.install speed-profile; setprop pm.dexopt.install-bulk speed-profile; setprop pm.dexopt.install-bulk-downgraded verify
    echo "ACTION:GLOBAL_DONE|TOTAL:$TOTAL_KB_JOB|TIME:$((( $(date +%s) - START )))"
}

do_remove() {
    local PKG="$1"
    local KEEP_DATA="$2"
    if [ "$KEEP_DATA" = "-k" ]; then
      cmd package archive "$PKG" >/dev/null 2>&1 || cmd package uninstall --user 0 -k "$PKG" >/dev/null 2>&1
    else
      cmd package uninstall "$PKG" >/dev/null 2>&1
      cmd package uninstall --user 0 "$PKG" >/dev/null 2>&1
    fi
}

do_restore_debloat() {
    local PKG="$1"
    cmd package install-existing "$PKG" >/dev/null 2>&1 || cmd package request-unarchive "$PKG" >/dev/null 2>&1
}

do_systemize() {
    local PKG="$1"
    local LABEL="$2"
    local APP_VER="${3:-1.0}"
    local APP_VER_CODE="${4:-1}"

    local MOD_DIR="/data/adb/modules/ROM-Shifter"
    local UP_DIR="/data/adb/modules_update/ROM-Shifter"
    local PROP="id=ROM-Shifter\nname=ROM Shifter Module\nversion=$APP_VER\nversionCode=$APP_VER_CODE\nauthor=ROM Shifter\ndescription=Used for some system dependent features such as Systemizer"

    mkdir -p "$MOD_DIR" && printf "$PROP\n" > "$MOD_DIR/module.prop" && chmod 644 "$MOD_DIR/module.prop"
    mkdir -p "$UP_DIR" && printf "$PROP\n" > "$UP_DIR/module.prop" && chmod 644 "$UP_DIR/module.prop"

    local APK_PATH=$(cmd package path "$PKG" | $SED_BIN -n 's/^package://p; q')

    if [ -n "$APK_PATH" ]; then
        local SAFE_LABEL=$(echo "$LABEL" | tr -cd 'a-zA-Z0-9_')
        TARGET_DIR="$UP_DIR/system/product/priv-app/$SAFE_LABEL"
        local SOURCE_DIR=$(dirname "$APK_PATH")

        mkdir -p "$TARGET_DIR"
        cp -f "$SOURCE_DIR"/*.apk "$TARGET_DIR/"
        chmod 755 "$TARGET_DIR"
        chmod 644 "$TARGET_DIR"/*.apk
    fi
}

do_backup_wifi() {
    local DEST="$1"
    mkdir -p "$DEST"
    local WIFI_DIR=""
    [ -d /data/misc/apexdata/com.android.wifi ] && WIFI_DIR="/data/misc/apexdata/com.android.wifi"
    [ -z "$WIFI_DIR" ] && [ -d /data/misc/wifi ] && WIFI_DIR="/data/misc/wifi"

    if [ -n "$WIFI_DIR" ]; then
        cp -a "$WIFI_DIR"/WifiConfigStore*.xml "$DEST/" 2>/dev/null
        cp -a "$WIFI_DIR"/ipconfig.txt "$DEST/" 2>/dev/null
    fi
}

do_restore_wifi() {
    local SRC="$1"
    [ -f "$SRC/WifiConfigStore.xml" ] || return

    echo "INFO:STEP|MSG:Enabling Wifi for Restore..."
    svc wifi enable 2>/dev/null
    cmd wifi set-wifi-enabled enabled >/dev/null 2>&1
    sleep 3

    echo "INFO:STEP|MSG:Restoring Networks via API..."

    $AWK_BIN '
    /<Network>/ { ssid=""; psk=""; sec="open"; in_net=1 }
    /<\/Network>/ {
        if (ssid != "") {
            gsub(/^&quot;|&quot;$/, "", ssid)
            gsub(/^&quot;|&quot;$/, "", psk)
            gsub(/^\"|\"$/, "", ssid)
            gsub(/^\"|\"$/, "", psk)

            gsub(/'\''/, "'\'\\\\\'\''", ssid)
            gsub(/'\''/, "'\'\\\\\'\''", psk)

            print ssid "|" sec "|" psk
        }
        in_net=0
        ssid=""; psk=""; sec="open"
    }
    in_net && /name="SSID"/ {
        if (match($0, />.*</)) {
            ssid = substr($0, RSTART+1, RLENGTH-2)
        }
    }
    in_net && /name="PreSharedKey"/ {
        if (match($0, />.*</)) {
            psk = substr($0, RSTART+1, RLENGTH-2)
        }
    }
    in_net && /name="AllowedKeyMgmt"/ {
        if ($0 ~ /02/) sec="wpa2"
        else if ($0 ~ /0100/ || $0 ~ /0001/) sec="wpa3"
    }
    ' "$SRC/WifiConfigStore.xml" | while IFS='|' read -r ssid sec psk; do
        if [ -n "$ssid" ]; then
            echo "INFO:STEP|MSG:Adding: $ssid"
            if [ "$sec" = "open" ]; then
                cmd wifi add-network "$ssid" open >/dev/null 2>&1
            else
                cmd wifi add-network "$ssid" "$sec" "$psk" >/dev/null 2>&1
            fi
            ( sleep 1; cmd wifi connect-network "$ssid" >/dev/null 2>&1 ) &
        fi
    done

    echo "INFO:STEP|MSG:Wifi Restore Done"
}

do_backup_wallpaper() {
    local DEST="$1"
    mkdir -p "$DEST"
    cp -a /data/system/users/0/wallpaper* "$DEST/" 2>/dev/null
}

do_restore_wallpaper() {
    local SRC="$1"
    cp -af "$SRC/"wallpaper* /data/system/users/0/ 2>/dev/null
    chown system:system /data/system/users/0/wallpaper* 2>/dev/null
    chmod 600 /data/system/users/0/wallpaper* 2>/dev/null
    restorecon /data/system/users/0/wallpaper* 2>/dev/null
}

do_backup_bt() {
    local DEST="$1"
    mkdir -p "$DEST"
    local BT_PATH=""
    [ -f /data/misc/bluedroid/bt_config.conf ] && BT_PATH="/data/misc/bluedroid/bt_config.conf"
    [ -z "$BT_PATH" ] && [ -f /data/misc/bluetooth/bt_config.conf ] && BT_PATH="/data/misc/bluetooth/bt_config.conf"

    if [ -n "$BT_PATH" ]; then
        cp -a "$BT_PATH" "$DEST/" 2>/dev/null
    fi
}

do_restore_bt() {
    local SRC="$1"
    [ -f "$SRC/bt_config.conf" ] || return
    local TARGET=""
    [ -d /data/misc/bluedroid ] && TARGET="/data/misc/bluedroid/bt_config.conf"
    [ -z "$TARGET" ] && [ -d /data/misc/bluetooth ] && TARGET="/data/misc/bluetooth/bt_config.conf"

    if [ -n "$TARGET" ]; then
        echo "INFO:STEP|MSG:Stopping Bluetooth..."
        svc bluetooth disable 2>/dev/null
        cmd bluetooth_manager disable >/dev/null 2>&1
        sleep 1
        cp -f "$SRC/bt_config.conf" "$TARGET"
        chown bluetooth:bluetooth "$TARGET" 2>/dev/null
        chmod 660 "$TARGET" 2>/dev/null
        restorecon "$TARGET" 2>/dev/null
        sleep 1
        echo "INFO:STEP|MSG:Starting Bluetooth..."
        svc bluetooth enable 2>/dev/null
        cmd bluetooth_manager enable >/dev/null 2>&1
    fi
}

do_ors() {
    local SCRIPT_CONTENT="$1"
    local REBOOT_OPT="$2"
    local LOCS="/cache/recovery /data/cache/recovery /metadata/recovery"
    mount -o rw,remount /cache 2>/dev/null
    mount -o rw,remount /metadata 2>/dev/null

    for loc in $LOCS; do
        mkdir -p "$loc" 2>/dev/null
        echo "$SCRIPT_CONTENT" > "$loc/openrecoveryscript"
        [ -n "$REBOOT_OPT" ] && [ "$REBOOT_OPT" != "none" ] && echo "reboot $REBOOT_OPT" >> "$loc/openrecoveryscript"
        chmod 666 "$loc/openrecoveryscript" 2>/dev/null
    done
}

get_partitions() {
    local paths="/dev/block/by-name /dev/block/bootdevice/by-name /dev/block/mapper"
    local blocked="system system_ext super vendor product odm userdata metadata persist control"
    for p in $paths; do
        [ -d "$p" ] || continue
        ls -1p "$p" 2>/dev/null | $AWK_BIN -v b="$blocked" '
            BEGIN { split(b, a, " "); for(x in a) bl[a[x]]=1 }
            !/\/$/ {
                skip=0
                for(p in bl) {
                    if ($0 == p || index($0, p "_") == 1) { skip=1; break }
                }
                if (!skip) print $0
            }
        '
    done | sort -u
}

get_images() {
    ls -1 "$1/Partitions/" 2>/dev/null | $SED_BIN -n '/\.img$/p'
}

delete_image() {
    rm -f "$1/Partitions/$2"
}

shifter_main() {
    case "$1" in
        --backup)
            MAIN_DIR="${3:-/sdcard/Shifter}"
            MAIN_DIR=$(echo "$MAIN_DIR" | tr -d '\r')
            BACKUP_BASE="$MAIN_DIR/Apps"
            init_shifter
            do_backup "$2"
            ;;
        --restore)
            MAIN_DIR="${3:-/sdcard/Shifter}"
            MAIN_DIR=$(echo "$MAIN_DIR" | tr -d '\r')
            BACKUP_BASE="$MAIN_DIR/Apps"
            init_shifter
            do_restore "$2"
            ;;
        --live-backup)
            MAIN_DIR="${3:-/sdcard/Shifter}"
            MAIN_DIR=$(echo "$MAIN_DIR" | tr -d '\r')
            LP_DIR="$MAIN_DIR/Partitions"
            init_shifter
            do_live_backup "$2"
            ;;
        --live-restore)
            init_shifter
            do_live_restore "$2" "$3"
            ;;
        --remove) init_shifter; do_remove "$2" "$3" ;;
        --restore-debloat) init_shifter; do_restore_debloat "$2" ;;
        --systemize) init_shifter; do_systemize "$2" "$3" "$4" "$5" ;;
        --backup-wifi) init_shifter; do_backup_wifi "$2" ;;
        --restore-wifi) init_shifter; do_restore_wifi "$2" ;;
        --backup-wallpaper) init_shifter; do_backup_wallpaper "$2" ;;
        --restore-wallpaper) init_shifter; do_restore_wallpaper "$2" ;;
        --backup-bt) init_shifter; do_backup_bt "$2" ;;
        --restore-bt) init_shifter; do_restore_bt "$2" ;;
        --install-apps) init_shifter; do_install_apps "$2" ;;
        --ors) do_ors "$2" "$3" ;;
        --get-partitions) get_partitions ;;
        --get-images) get_images "$2" ;;
        --delete-image) delete_image "$2" "$3" ;;
    esac
}

shifter_main "$@"
