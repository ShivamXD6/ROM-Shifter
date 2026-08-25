#!/system/bin/sh
# ==========================================
# ROM Shifter - APP BACKEND ENGINE
# ==========================================

BIN_DIR="/data/adb/Shifter"
ZAPDOS="$BIN_DIR/zapdos"
AM_TMP="/data/local/tmp/shifter_apps"
TARGETS="/data/local/tmp/shifter_targets.txt"

init_shifter() {
     mkdir -p "$BIN_DIR" "$AM_TMP"
     [ -n "$BACKUP_BASE" ] && mkdir -p "$BACKUP_BASE"
     [ -n "$LP_DIR" ] && mkdir -p "$LP_DIR"
     chmod +x "$ZAPDOS" 2>/dev/null
}

COOLDOWN() { while [ "$(jobs | grep -c 'Running')" -ge "$1" ] 2>/dev/null; do sleep 0.1; done; }
SANITIZE() { echo "$1" | sed 's/[^a-zA-Z0-9]/_/g'; }

CHK() { case " $APP_COMPS " in *" $1 "*) return 0 ;; *) return 1 ;; esac }

RAW_SIZE() {
    du -sk "$@" 2>/dev/null | awk '{sum+=$1} END{print sum+0}'
}

FORMAT_SIZE() {
    local raw=$(echo "$1" | tr -d '\r\n ')
    awk -v n="${raw:-0}" 'BEGIN{
        n = n + 0
        if(n >= 1048576) printf "%.2f GB", n/1048576
        else if(n >= 1024) printf "%.2f MB", n/1024
        else printf "%d KB", n
    }'
}
READID() { grep "package=\"$1\"" "/data/system/users/0/settings_ssaid.xml" 2>/dev/null | sed -n 's/.*value="\([^"]*\)".*/\1/p'; }
CHANID() { sed -i "/package=\"$1\"/s/\(value=\"\)[^\"]*\(.*defaultValue=\"\)[^\"]*/\1$2\2$2/" "/data/system/users/0/settings_ssaid.xml"; }
GETPERM() {
    local pkg="$1" out="$2"
    {
        dumpsys package "$pkg" 2>/dev/null | awk '
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
        cmd appops get "$pkg" 2>/dev/null | awk -F': ' '
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
    awk -v pkg="$1" -F'[:=]' '
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
    cmd package list packages | grep -q "^package:$1$" || return 1
    [ -z "$2" ] && return 0
    local apkpath="$(cmd package path "$1" 2>/dev/null | sed -n 's/^package://p' | head -n 1 | tr -d '\r')"
    [ -z "$apkpath" ] && return 1
    local inst_ver=$(dumpsys package "$1" | grep -m1 "versionCode=" | sed 's/.*versionCode=\([0-9]*\).*/\1/')
    [ "$inst_ver" = "$2" ] || return 1
    return 0
}
BUNDAPP() {
    COOLDOWN 4
    tar --exclude="$2/cache" --exclude="$2/code_cache" -cpf - -C "$1" "$2" 2>/dev/null | "$ZAPDOS" -1 -f -q -o "$3/$4.shift" &
}

UNBUNDAPP() {
    COOLDOWN 3
    "$ZAPDOS" -d -q -c "$1" | tar -pxf - -C "$2" 2>/dev/null &
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
    CUR_IDX="$7"; TOT_IDX="$8"; PCT="$9"; SIZE="${10}"
    CUR_APP="${11}"; CUR_DATA="${12}"; CUR_MED="${13}"

    touch "$BACKUP_BASE/.nomedia"
    APP_DIR="$BACKUP_BASE/$TYPE/$LABEL"; mkdir -p "$APP_DIR"
    echo "ACTION:BACKUP_START|PKG:$PKG|LABEL:$LABEL|VER:$VER|CUR:$CUR_IDX|TOT:$TOT_IDX|PCT:$PCT|SIZE:$SIZE"

    OLD_APP=0; OLD_DATA=0; OLD_MED=0; OLD_SSAID=""
    if [ -f "$APP_DIR/Meta.txt" ]; then
        while IFS='=' read -r key value || [ -n "$key" ]; do
            case "$key" in
                AppSize) OLD_APP=$value ;;
                DataSize|DataExtSize) OLD_DATA=$value ;;
                MediaSize|MediaOBBSize) OLD_MED=$value ;;
                SSAID) OLD_SSAID=$value ;;
            esac
        done < "$APP_DIR/Meta.txt"
    fi

    if CHK 1; then
        if [ "$CUR_APP" != "$OLD_APP" ] || [ ! -f "$APP_DIR/App.shift" ]; then
            if [ -n "$APK_PATH" ]; then
                local apk_dir=$(dirname "$APK_PATH")
                find "$apk_dir" -maxdepth 1 -name "*.apk" 2>/dev/null | sed 's|^/||' | tar -cf - -C / -T - 2>/dev/null | "$ZAPDOS" -1 -f -q -o "$APP_DIR/App.shift" &
            fi
            OLD_APP=$CUR_APP
        fi
    fi

    if CHK 2; then
        local m_data=0
        [ -d "/data/data/$PKG" ] && [ ! -f "$APP_DIR/Data.shift" ] && m_data=1
        [ -d "/data/user_de/0/$PKG" ] && [ ! -f "$APP_DIR/UserDe.shift" ] && m_data=1
        [ -d "/data/media/0/Android/data/$PKG" ] && [ ! -f "$APP_DIR/ExtData.shift" ] && m_data=1

        if [ "$CUR_DATA" != "$OLD_DATA" ] || [ "$m_data" -eq 1 ]; then
            [ -d "/data/data/$PKG" ] && BUNDAPP "/data/data" "$PKG" "$APP_DIR" "Data"
            [ -d "/data/user_de/0/$PKG" ] && BUNDAPP "/data/user_de/0" "$PKG" "$APP_DIR" "UserDe"
            [ -d "/data/media/0/Android/data/$PKG" ] && BUNDAPP "/data/media/0/Android/data" "$PKG" "$APP_DIR" "ExtData"
            OLD_DATA=$CUR_DATA
        fi
    fi

    if CHK 3; then
        GETPERM "$PKG" "$APP_DIR/Permissions.txt" &
    fi

    if CHK 4; then
        local m_med=0
        [ -d "/data/media/0/Android/media/$PKG" ] && [ ! -f "$APP_DIR/Media.shift" ] && m_med=1
        [ -d "/data/media/0/Android/obb/$PKG" ] && [ ! -f "$APP_DIR/Obb.shift" ] && m_med=1

        if [ "$CUR_MED" != "$OLD_MED" ] || [ "$m_med" -eq 1 ]; then
            [ -d "/data/media/0/Android/media/$PKG" ] && BUNDAPP "/data/media/0/Android/media" "$PKG" "$APP_DIR" "Media"
            [ -d "/data/media/0/Android/obb/$PKG" ] && BUNDAPP "/data/media/0/Android/obb" "$PKG" "$APP_DIR" "Obb"
            OLD_MED=$CUR_MED
        fi
    fi

    if CHK 5; then
        CUR_SSAID=$(READID "$PKG")
        [ -n "$CUR_SSAID" ] && OLD_SSAID=$CUR_SSAID
    fi

    local BASE_PCT=$(( (CUR_IDX - 1) * 100 / TOT_IDX ))
    while jobs | grep -q 'Running' 2>/dev/null; do
        local cur_app_size=$(du -sk "$APP_DIR" 2>/dev/null | awk '{print $1}')
        cur_app_size=${cur_app_size:-0}
        cur_app_size=$(( cur_app_size * 22 / 10 ))
        [ "$cur_app_size" -gt "$SIZE" ] && cur_app_size=$SIZE
        local app_pct=0; [ "$SIZE" -gt 0 ] && app_pct=$(( cur_app_size * 100 / SIZE ))
        local global_pct=$(( BASE_PCT + (app_pct / TOT_IDX) ))
        [ "$global_pct" -gt 100 ] && global_pct=100
        echo "ACTION:BACKUP_START|PKG:$PKG|LABEL:$LABEL|VER:$VER|CUR:$CUR_IDX|TOT:$TOT_IDX|PCT:$global_pct|SIZE:$SIZE"
        sleep 0.1
    done

    local final_pct=$(( CUR_IDX * 100 / TOT_IDX ))
    echo "ACTION:BACKUP_START|PKG:$PKG|LABEL:$LABEL|VER:$VER|CUR:$CUR_IDX|TOT:$TOT_IDX|PCT:$final_pct|SIZE:$SIZE"

    SYS_PATH=""; [ "$TYPE" = "System" ] && SYS_PATH=$(dumpsys package "$PKG" 2>/dev/null | awk -F= '/codePath=\/(system|product|vendor|oem|odm)/{print $2; exit}')

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
    LABEL="$1"; TYPE="$2"; CUR_IDX="$3"; TOT_IDX="$4"; PCT="$5"; SIZE="$6"
    APP_DIR="$BACKUP_BASE/$TYPE/$LABEL"
    [ -f "$APP_DIR/Meta.txt" ] || return

    PKG=""; VER=""; VCODE=""; OLD_SSAID=""

    while IFS='=' read -r key value || [ -n "$key" ]; do
        case "$key" in
            Package) PKG=$value ;;
            Version) VER=$value ;;
            VersionCode) VCODE=$value ;;
            SSAID) OLD_SSAID=$value ;;
        esac
    done < "$APP_DIR/Meta.txt"

    [ -z "$PKG" ] && return
    TMP_PKG="$AM_TMP/$PKG"; mkdir -p "$TMP_PKG"; chmod 777 "$TMP_PKG"

    echo "ACTION:RESTORE_START|PKG:$PKG|LABEL:$LABEL|VER:$VER|CUR:$CUR_IDX|TOT:$TOT_IDX|PCT:$PCT|SIZE:$SIZE"

    if CHK 1 && [ -f "$APP_DIR/App.shift" ]; then
        if ! PKG_INSTALLED "$PKG" "$VCODE"; then
            "$ZAPDOS" -d -q -c "$APP_DIR/App.shift" | tar -xf - -C "$TMP_PKG" 2>/dev/null
            chmod -R 777 "$TMP_PKG" 2>/dev/null
            local apks_to_install=$(find "$TMP_PKG" -type f -name "*.apk" | sort | tr '\n' ' ')
            if [ -n "$apks_to_install" ]; then
                local SESSION_ID=$(su 1000 -c "cmd package install-create --user 0 -i com.android.vending --install-reason 4 2>/dev/null" | tr -dc '0-9')
                if [ -n "$SESSION_ID" ]; then
                    local apk_count=0
                    for apk in $apks_to_install; do
                        apk_count=$((apk_count + 1))
                        su 1000 -c "cmd package install-write $SESSION_ID split_${apk_count} '$apk' >/dev/null 2>&1"
                    done
                    su 1000 -c "cmd package install-commit $SESSION_ID >/dev/null 2>&1"
                fi
            fi
        fi
    fi

    cmd package disable "$PKG" >/dev/null 2>&1
    NEW_UID=$(stat -c '%u' "/data/data/$PKG" 2>/dev/null)
    [ -z "$NEW_UID" ] && NEW_UID=$(dumpsys package "$PKG" | grep -m1 "userId=" | cut -d= -f2 | awk '{print $1}')

    if CHK 2; then
        [ -f "$APP_DIR/Data.shift" ] && UNBUNDAPP "$APP_DIR/Data.shift" "/data/data"
        [ -f "$APP_DIR/UserDe.shift" ] && UNBUNDAPP "$APP_DIR/UserDe.shift" "/data/user_de/0"
        [ -f "$APP_DIR/ExtData.shift" ] && UNBUNDAPP "$APP_DIR/ExtData.shift" "/data/media/0/Android/data"
    fi

    if CHK 4; then
        [ -f "$APP_DIR/Media.shift" ] && UNBUNDAPP "$APP_DIR/Media.shift" "/data/media/0/Android/media"
        [ -f "$APP_DIR/Obb.shift" ] && UNBUNDAPP "$APP_DIR/Obb.shift" "/data/media/0/Android/obb"
    fi

    local BASE_PCT=$(( (CUR_IDX - 1) * 100 / TOT_IDX ))

    while jobs | grep -q 'Running' 2>/dev/null; do
        local cur_ext_size=0
        local s1=0; local s2=0; local s3=0; local s4=0; local s5=0
        [ -d "/data/data/$PKG" ] && s1=$(du -sk "/data/data/$PKG" 2>/dev/null | awk '{print $1}')
        [ -d "/data/user_de/0/$PKG" ] && s2=$(du -sk "/data/user_de/0/$PKG" 2>/dev/null | awk '{print $1}')
        [ -d "/data/media/0/Android/data/$PKG" ] && s3=$(du -sk "/data/media/0/Android/data/$PKG" 2>/dev/null | awk '{print $1}')
        [ -d "/data/media/0/Android/media/$PKG" ] && s4=$(du -sk "/data/media/0/Android/media/$PKG" 2>/dev/null | awk '{print $1}')
        [ -d "/data/media/0/Android/obb/$PKG" ] && s5=$(du -sk "/data/media/0/Android/obb/$PKG" 2>/dev/null | awk '{print $1}')
        cur_ext_size=$(( ${s1:-0} + ${s2:-0} + ${s3:-0} + ${s4:-0} + ${s5:-0} ))

        [ "$cur_ext_size" -gt "$SIZE" ] && cur_ext_size=$SIZE

        local app_pct=0
        [ "$SIZE" -gt 0 ] && app_pct=$(( cur_ext_size * 100 / SIZE ))
        [ "$app_pct" -gt 100 ] && app_pct=100

        local global_pct=$(( BASE_PCT + (app_pct / TOT_IDX) ))
        [ "$global_pct" -gt 100 ] && global_pct=100
        echo "ACTION:RESTORE_START|PKG:$PKG|LABEL:$LABEL|VER:$VER|CUR:$CUR_IDX|TOT:$TOT_IDX|PCT:$global_pct|SIZE:$SIZE"
        sleep 0.1
    done

    local final_pct=$(( CUR_IDX * 100 / TOT_IDX ))
    echo "ACTION:RESTORE_START|PKG:$PKG|LABEL:$LABEL|VER:$VER|CUR:$CUR_IDX|TOT:$TOT_IDX|PCT:$final_pct|SIZE:$SIZE"

    if CHK 5; then
        CUR_SSAID=$(READID "$PKG")
        if [ -n "$OLD_SSAID" ] && [ "$CUR_SSAID" != "$OLD_SSAID" ]; then
            CHANID "$PKG" "$OLD_SSAID"
        fi
    fi

    if CHK 3 && [ -f "$APP_DIR/Permissions.txt" ]; then
        SETPERM "$PKG" "$APP_DIR/Permissions.txt" &
    fi

    if [ -n "$NEW_UID" ]; then
        chown -hR "$NEW_UID:$NEW_UID" "/data/data/$PKG" "/data/user_de/0/$PKG" 2>/dev/null
        [ -n "$ADGID" ] && chown -hR "$NEW_UID:$ADGID" "/data/media/0/Android/data/$PKG" 2>/dev/null
        [ -n "$AMGID" ] && chown -hR "$NEW_UID:$AMGID" "/data/media/0/Android/media/$PKG" 2>/dev/null
        [ -n "$AOGID" ] && chown -hR "$NEW_UID:$AOGID" "/data/media/0/Android/obb/$PKG" 2>/dev/null

        APP_CTX=$(ls -dZ "/data/data/$PKG" 2>/dev/null | awk '{print $1}')
        if [ -n "$APP_CTX" ] && [ "$APP_CTX" != "?" ]; then
            chcon -hR "$APP_CTX" "/data/data/$PKG" "/data/user_de/0/$PKG" 2>/dev/null
        else
            restorecon -R "/data/data/$PKG" "/data/user_de/0/$PKG" 2>/dev/null
        fi
        DELGMS "$PKG"
    fi
    cmd package enable "$PKG" >/dev/null 2>&1; rm -rf "$TMP_PKG"
    echo "ACTION:RESTORE_DONE|PKG:$PKG"
}

do_backup() {
    export APP_COMPS="$1"
    rm -rf "$AM_TMP/selected_apps_sizes.txt" "$AM_TMP/selected_apps_sorted.txt" 2>/dev/null

    echo "INFO:STEP|MSG:Preparing backup list..."

    awk -F'|' -v global_comps="$APP_COMPS" '
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
    }' "$TARGETS" > "$AM_TMP/selected_apps_sizes.txt"

    TOTAL_KB=$(awk -F'|' '{s+=$1} END{print s+0}' "$AM_TMP/selected_apps_sizes.txt")
    sort -t'|' -k1 -n -r "$AM_TMP/selected_apps_sizes.txt" > "$AM_TMP/selected_apps_sorted.txt"

    START=$(date +%s); TOTAL_APPS=$(wc -l < "$AM_TMP/selected_apps_sorted.txt"); CURRENT_APP=0

    while IFS='|' read -r size label pkg ver vcode type apath s_app s_data s_med app_comps || [ -n "$size" ]; do
        CURRENT_APP=$((CURRENT_APP + 1))
        size=${size:-0}
        local pct=0
        [ "$TOTAL_APPS" -gt 0 ] && pct=$(( (CURRENT_APP - 1) * 100 / TOTAL_APPS ))

        export APP_COMPS="$app_comps"
        DO_BACKUP "$pkg" "$label" "$ver" "$vcode" "$type" "$apath" "$CURRENT_APP" "$TOTAL_APPS" "$pct" "$size" "$s_app" "$s_data" "$s_med"
    done < "$AM_TMP/selected_apps_sorted.txt"
    wait

    echo "ACTION:GLOBAL_DONE|TOTAL:$TOTAL_KB|TIME:$((( $(date +%s) - START )))"
}

do_restore() {
    export APP_COMPS="$1"
    rm -rf "$AM_TMP/selected_restores.txt" "$AM_TMP/selected_restores_sorted.txt" 2>/dev/null
    > "$AM_TMP/selected_restores.txt"

    while IFS='|' read -r pkg label ver vcode type apath s_app s_data s_med app_comps || [ -n "$pkg" ]; do
        [ -z "$pkg" ] && continue
        pkg=$(echo "$pkg" | tr -d '\r'); label=$(echo "$label" | tr -d '\r'); type=$(echo "$type" | tr -d '\r')

        APP_PATH="$BACKUP_BASE/$type/$label/Meta.txt"
        if [ -f "$APP_PATH" ]; then
            local s=25
            local a_size=0; local d_size=0; local m_size=0
            while IFS='=' read -r key value || [ -n "$key" ]; do
                case "$key" in
                    AppSize) a_size=$value ;;
                    DataExtSize) d_size=$value ;;
                    MediaOBBSize) m_size=$value ;;
                esac
            done < "$APP_PATH"

            CHK 3 && s=$((s + 5))
            CHK 5 && s=$((s + 1))
            CHK 1 && s=$((s + a_size))
            CHK 2 && s=$((s + d_size))
            CHK 4 && s=$((s + m_size))

            echo "${s:-0}|${label}|${type}" >> "$AM_TMP/selected_restores.txt"
        fi
    done < "$TARGETS"

    sort -t'|' -k1 -n -r "$AM_TMP/selected_restores.txt" > "$AM_TMP/selected_restores_sorted.txt"
    TOTAL_KB=$(awk -F'|' '{s+=$1} END{print s+0}' "$AM_TMP/selected_restores_sorted.txt")
    START=$(date +%s); TOTAL_APPS=$(wc -l < "$AM_TMP/selected_restores_sorted.txt"); CURRENT_APP=0

    cmd package disable com.android.vending >/dev/null 2>&1
    settings put global verifier_verify_adb_installs 0
    setprop pm.dexopt.install assume-verified
    setprop pm.dexopt.install-bulk assume-verified
    setprop pm.dexopt.install-bulk-downgraded skip

    while IFS='|' read -r size label type || [ -n "$size" ]; do
        CURRENT_APP=$((CURRENT_APP + 1))
        size=${size:-0}
        local pct=0
        [ "$TOTAL_APPS" -gt 0 ] && pct=$(( (CURRENT_APP - 1) * 100 / TOTAL_APPS ))

        DO_RESTORE "$label" "$type" "$CURRENT_APP" "$TOTAL_APPS" "$pct" "$size"
    done < "$AM_TMP/selected_restores_sorted.txt"

    cmd package enable com.android.vending >/dev/null 2>&1
    settings put global verifier_verify_adb_installs 1
    setprop pm.dexopt.install speed-profile
    setprop pm.dexopt.install-bulk speed-profile
    setprop pm.dexopt.install-bulk-downgraded verify

    wait
    echo "ACTION:GLOBAL_DONE|TOTAL:$TOTAL_KB|TIME:$((( $(date +%s) - START )))"
}

do_remove() {
    local PKG="$1"
    cmd package uninstall "$PKG" >/dev/null 2>&1
    cmd package uninstall --user 0 "$PKG" >/dev/null 2>&1
}

do_restore_debloat() {
    cmd package install-existing "$1" >/dev/null 2>&1
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

    local APK_PATH=$(cmd package path "$PKG" | sed 's/^package://' | head -n 1 | tr -d '\r')

    if [ -n "$APK_PATH" ]; then
        local SAFE_LABEL=$(echo "$LABEL" | tr -cd 'a-zA-Z0-9_')
        TARGET_DIR="$UP_DIR/system/product/app/$SAFE_LABEL"
        local SOURCE_DIR=$(dirname "$APK_PATH")

        mkdir -p "$TARGET_DIR"
        cp -f "$SOURCE_DIR"/*.apk "$TARGET_DIR/"
        chmod 755 "$TARGET_DIR"
        chmod 644 "$TARGET_DIR"/*.apk
    fi
}

do_backup_msgs() {
    local DEST="$1/Advanced_Msgs"
    mkdir -p "$DEST/Telephony" "$DEST/Messages"
    cp -a /data/user_de/0/com.android.providers.telephony/databases/mmssms* "$DEST/Telephony/" 2>/dev/null
    cp -a /data/data/com.google.android.apps.messaging/databases/* "$DEST/Messages/" 2>/dev/null
}

do_restore_msgs() {
    local SRC="$1/Advanced_Msgs"
    if [ -d "$SRC/Telephony" ]; then
        am force-stop com.android.providers.telephony 2>/dev/null
        am force-stop com.google.android.apps.messaging 2>/dev/null
        rm -f /data/user_de/0/com.android.providers.telephony/databases/mmssms* 2>/dev/null
        rm -f /data/data/com.google.android.apps.messaging/databases/* 2>/dev/null

        cp -a "$SRC/Telephony/"mmssms* /data/user_de/0/com.android.providers.telephony/databases/ 2>/dev/null
        chown -R radio:radio /data/user_de/0/com.android.providers.telephony/databases/ 2>/dev/null
        restorecon -R /data/user_de/0/com.android.providers.telephony/ 2>/dev/null

        local MSG_UID=$(stat -c "%u" /data/data/com.google.android.apps.messaging 2>/dev/null)
        if [ -n "$MSG_UID" ]; then
            cp -a "$SRC/Messages/"* /data/data/com.google.android.apps.messaging/databases/ 2>/dev/null
            chown -R "$MSG_UID:$MSG_UID" /data/data/com.google.android.apps.messaging/databases/ 2>/dev/null
            restorecon -R /data/data/com.google.android.apps.messaging/ 2>/dev/null
        fi
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
    local all_found=""
    for p in $paths; do
        if [ -d "$p" ]; then
            local list=$(ls -1p "$p" 2>/dev/null | grep -v /)
            for item in $list; do
                local skip=0
                for b in $blocked; do
                    if [ "$item" = "$b" ] || [ "${item#${b}_}" != "$item" ]; then
                        skip=1; break
                    fi
                done
                [ $skip -eq 0 ] && all_found="$all_found $item"
            done
        fi
    done
    echo "$all_found" | tr ' ' '\n' | sort -u
}

get_images() {
    ls -1p "$1/Partitions/" 2>/dev/null | grep -v / | grep '\.img$'
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
        --backup-msgs) init_shifter; do_backup_msgs "$2" ;;
        --restore-msgs) init_shifter; do_restore_msgs "$2" ;;
        --ors) do_ors "$2" "$3" ;;
        --get-partitions) get_partitions ;;
        --get-images) get_images "$2" ;;
        --delete-image) delete_image "$2" "$3" ;;
    esac
}

shifter_main "$@"
