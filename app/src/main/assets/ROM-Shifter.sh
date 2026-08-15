#!/system/bin/sh
# ==========================================
# ROM Shifter - APP BACKEND ENGINE
# ==========================================

BIN_DIR="/data/adb/Shifter"
ZAPDOS="$BIN_DIR/zapdos"
JOBS=$(nproc 2>/dev/null || echo 4)
AM_TMP="/data/local/tmp/appmgr_tmp"
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
    > "$2"
    { dumpsys package "$1" 2>/dev/null; echo "---APPOPS---"; cmd appops get "$1" 2>/dev/null; } | awk '
        BEGIN { mode = "pm" }
        /^---APPOPS---$/ { mode = "appops"; next }

        mode == "pm" {
            if ($0 ~ /runtime permissions:/) { in_perms=1; next }
            if ($0 ~ /^[ \t]*[A-Za-z0-9_]+:/ && !($0 ~ /runtime permissions:/)) { in_perms=0 }

            if (in_perms && $1 ~ /^[A-Za-z0-9_]+\.[A-Za-z0-9_.]+:/) {
                split($1, a, ":")
                perm = a[1]
                granted = "false"
                if ($0 ~ /granted=true/) granted = "true"

                print "PERM:" perm "=" granted

                n = split(perm, b, ".")
                seen_pm[b[n]] = 1
            }
        }

        mode == "appops" {
            if ($1 ~ /^[ \t]*[A-Za-z0-9_]+:/) {
                split($0, parts, ":")
                op = parts[1]; sub(/^[ \t]+/, "", op)

                if (op == "Uid mode") next

                val = parts[2]; sub(/^[ \t]+/, "", val); sub(/;.*$/, "", val); sub(/[ \t]+$/, "", val)

                if (op != "" && !seen_op[op]++ && !seen_pm[op]) {
                    print "APPOP:" op "=" val
                }
            }
        }
    ' > "$2"
}

SETPERM() {
    awk -v pkg="$1" -F':' '
        /^PERM:/ {
            split($2, p, "=")
            if (p[2] == "true") print "pm grant " pkg " " p[1] " >/dev/null 2>&1"
            else print "pm revoke " pkg " " p[1] " >/dev/null 2>&1"
        }
        /^APPOP:/ {
            split($2, p, "=")
            print "cmd appops set " pkg " " p[1] " " p[2] " >/dev/null 2>&1"
        }
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
    tar --exclude="$2/cache" --exclude="$2/code_cache" -cpf - -C "$1" "$2" 2>/dev/null | "$ZAPDOS" -1 -f -q -o "$3/$4.shift" &
}

UNBUNDAPP() {
    COOLDOWN "$((JOBS - 2))"
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
    PKG="$1"; LABEL="$2"; VER="$3"; TYPE="$4"; CUR_IDX="$5"; TOT_IDX="$6"; PCT="$7"; SIZE="$8"
    CUR_APP="${9}"; CUR_DATA="${10}"; CUR_EXT="${11}"; CUR_MED="${12}"; CUR_OBB="${13}"
    touch "$BACKUP_BASE/.nomedia"
    APP_DIR="$BACKUP_BASE/$TYPE/$LABEL"; mkdir -p "$APP_DIR"
    echo "ACTION:BACKUP_START|PKG:$PKG|LABEL:$LABEL|VER:$VER|CUR:$CUR_IDX|TOT:$TOT_IDX|PCT:$PCT|SIZE:$SIZE"
    OLD_APP=0; OLD_DATA=0; OLD_EXT=0; OLD_MED=0; OLD_OBB=0; OLD_SSAID=""
    if [ -f "$APP_DIR/Meta.txt" ]; then
        OLD_APP=$(grep "^AppSize=" "$APP_DIR/Meta.txt" | cut -d= -f2); OLD_APP=${OLD_APP:-0}
        OLD_DATA=$(grep "^DataSize=" "$APP_DIR/Meta.txt" | cut -d= -f2); OLD_DATA=${OLD_DATA:-0}
        OLD_EXT=$(grep "^ExtDataSize=" "$APP_DIR/Meta.txt" | cut -d= -f2); OLD_EXT=${OLD_EXT:-0}
        OLD_MED=$(grep "^MediaSize=" "$APP_DIR/Meta.txt" | cut -d= -f2); OLD_MED=${OLD_MED:-0}
        OLD_OBB=$(grep "^ObbSize=" "$APP_DIR/Meta.txt" | cut -d= -f2); OLD_OBB=${OLD_OBB:-0}
        OLD_SSAID=$(grep "^SSAID=" "$APP_DIR/Meta.txt" | cut -d= -f2)
    fi

    if CHK 1; then
        if [ "$CUR_APP" != "$OLD_APP" ] || { [ "$CUR_APP" -gt 0 ] && [ ! -f "$APP_DIR/App.shift" ]; }; then
            if [ "$CUR_APP" -gt 0 ]; then
                apks="$(pm path "$PKG" 2>/dev/null | sed 's/^package://' | tr -d '\r')"
                [ -n "$apks" ] && echo "$apks" | sed 's|^/||' | tar -cf - -C / -T - 2>/dev/null | "$ZAPDOS" -1 -f -q -o "$APP_DIR/App.shift" &
            else rm -f "$APP_DIR/App.shift"; fi
            OLD_APP=$CUR_APP
        fi
    fi
    if CHK 2; then
        if [ "$CUR_DATA" != "$OLD_DATA" ] || { [ "$CUR_DATA" -gt 0 ] && [ ! -f "$APP_DIR/Data.shift" ] && [ ! -f "$APP_DIR/UserDe.shift" ]; }; then
            if [ "$CUR_DATA" -gt 0 ]; then
                [ -d "/data/data/$PKG" ] && BUNDAPP "/data/data" "$PKG" "$APP_DIR" "Data"
                [ -d "/data/user_de/0/$PKG" ] && BUNDAPP "/data/user_de/0" "$PKG" "$APP_DIR" "UserDe"
            else rm -f "$APP_DIR/Data.shift" "$APP_DIR/UserDe.shift"; fi
                OLD_DATA=$CUR_DATA
        fi
    fi
    if CHK 3; then
        if [ "$CUR_EXT" != "$OLD_EXT" ] || { [ "$CUR_EXT" -gt 0 ] && [ ! -f "$APP_DIR/ExtData.shift" ]; }; then
            if [ "$CUR_EXT" -gt 0 ]; then
                BUNDAPP "/data/media/0/Android/data" "$PKG" "$APP_DIR" "ExtData"
            else rm -f "$APP_DIR/ExtData.shift"; fi
            OLD_EXT=$CUR_EXT
        fi
    fi
    if CHK 4; then
        if [ "$CUR_MED" != "$OLD_MED" ] || { [ "$CUR_MED" -gt 0 ] && [ ! -f "$APP_DIR/Media.shift" ]; }; then
            if [ "$CUR_MED" -gt 0 ]; then
                BUNDAPP "/data/media/0/Android/media" "$PKG" "$APP_DIR" "Media"
            else rm -f "$APP_DIR/Media.shift"; fi
            OLD_MED=$CUR_MED
        fi
    fi
    if CHK 5; then
        if [ "$CUR_OBB" != "$OLD_OBB" ] || { [ "$CUR_OBB" -gt 0 ] && [ ! -f "$APP_DIR/Obb.shift" ]; }; then
            if [ "$CUR_OBB" -gt 0 ]; then
                BUNDAPP "/data/media/0/Android/obb" "$PKG" "$APP_DIR" "Obb"
            else rm -f "$APP_DIR/Obb.shift"; fi
            OLD_OBB=$CUR_OBB
        fi
    fi
    if CHK 6; then
        CUR_SSAID=$(READID "$PKG")
        if [ -n "$CUR_SSAID" ] && [ "$CUR_SSAID" != "$OLD_SSAID" ]; then
            OLD_SSAID=$CUR_SSAID
        fi
    fi

    local BASE_PCT=$(( (CUR_IDX - 1) * 100 / TOT_IDX ))

    while jobs | grep -q 'Running' 2>/dev/null; do
        local cur_app_size=$(du -sk "$APP_DIR" 2>/dev/null | awk '{print $1}')
        cur_app_size=${cur_app_size:-0}
        cur_app_size=$(( cur_app_size * 22 / 10 ))
        [ "$cur_app_size" -gt "$SIZE" ] && cur_app_size=$SIZE

        local app_pct=0
        [ "$SIZE" -gt 0 ] && app_pct=$(( cur_app_size * 100 / SIZE ))
        [ "$app_pct" -gt 100 ] && app_pct=100

        local global_pct=$(( BASE_PCT + (app_pct / TOT_IDX) ))
        [ "$global_pct" -gt 100 ] && global_pct=100

        echo "ACTION:BACKUP_START|PKG:$PKG|LABEL:$LABEL|VER:$VER|CUR:$CUR_IDX|TOT:$TOT_IDX|PCT:$global_pct|SIZE:$SIZE"
        sleep 0.8
    done

    wait

    local final_pct=$(( CUR_IDX * 100 / TOT_IDX ))
    echo "ACTION:BACKUP_START|PKG:$PKG|LABEL:$LABEL|VER:$VER|CUR:$CUR_IDX|TOT:$TOT_IDX|PCT:$final_pct|SIZE:$SIZE"
    local APP_TOTAL_KB=$(( OLD_APP + OLD_DATA + OLD_EXT + OLD_MED + OLD_OBB ))
    SYS_PATH=""; [ "$TYPE" = "System" ] && SYS_PATH=$(dumpsys package "$PKG" 2>/dev/null | awk -F= '/codePath=\/(system|product|vendor|oem|odm)/{print $2; exit}')

    cat <<EOF > "$APP_DIR/Meta.txt"
Name=$LABEL
Version=$VER
Package=$PKG
TotalSize=$APP_TOTAL_KB
AppSize=$OLD_APP
DataSize=$OLD_DATA
EOF
    [ "$OLD_EXT" -gt 0 ] && echo "ExtDataSize=$OLD_EXT" >> "$APP_DIR/Meta.txt"
    [ "$OLD_MED" -gt 0 ] && echo "MediaSize=$OLD_MED" >> "$APP_DIR/Meta.txt"
    [ "$OLD_OBB" -gt 0 ] && echo "ObbSize=$OLD_OBB" >> "$APP_DIR/Meta.txt"
    [ -n "$OLD_SSAID" ] && echo "SSAID=$OLD_SSAID" >> "$APP_DIR/Meta.txt"
    [ "$TYPE" = "System" ] && [ -n "$SYS_PATH" ] && echo "SysPath=$SYS_PATH" >> "$APP_DIR/Meta.txt"

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

    echo "ACTION:RESTORE_START|PKG:$PKG|LABEL:$LABEL|VER:$VER|CUR:$CUR_IDX|TOT:$TOT_IDX|PCT:$PCT|SIZE:$SIZE"

    OLD_APP=$(grep "^AppSize=" "$APP_DIR/Meta.txt" | cut -d= -f2); OLD_APP=${OLD_APP:-0}
    OLD_DATA=$(grep "^DataSize=" "$APP_DIR/Meta.txt" | cut -d= -f2); OLD_DATA=${OLD_DATA:-0}
    OLD_EXT=$(grep "^ExtDataSize=" "$APP_DIR/Meta.txt" | cut -d= -f2); OLD_EXT=${OLD_EXT:-0}
    OLD_MED=$(grep "^MediaSize=" "$APP_DIR/Meta.txt" | cut -d= -f2); OLD_MED=${OLD_MED:-0}
    OLD_OBB=$(grep "^ObbSize=" "$APP_DIR/Meta.txt" | cut -d= -f2); OLD_OBB=${OLD_OBB:-0}
    OLD_SSAID=$(grep "^SSAID=" "$APP_DIR/Meta.txt" | cut -d= -f2)
    FORCE_DATA=0
    if CHK 1 && [ -f "$APP_DIR/App.shift" ]; then
        if ! PKG_INSTALLED "$PKG" "$VER"; then
            "$ZAPDOS" -d -q -c "$APP_DIR/App.shift" | tar -xf - -C "$TMP_PKG" 2>/dev/null
            chmod 777 "$TMP_PKG"/*.apk 2>/dev/null
            local apks_to_install=$(find "$TMP_PKG" -type f -name "*.apk" | sort)
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
            FORCE_DATA=1
        fi
    fi

    pm disable "$PKG" >/dev/null 2>&1
    NEW_UID=$(stat -c '%u' "/data/data/$PKG" 2>/dev/null)
    [ -z "$NEW_UID" ] && NEW_UID=$(dumpsys package "$PKG" | grep -m1 "userId=" | cut -d= -f2 | awk '{print $1}')

    CUR_DATA=0; CUR_EXT=0; CUR_MED=0; CUR_OBB=0

    if [ "$FORCE_DATA" -eq 0 ]; then
        TMP_SIZES="$AM_TMP/${PKG}_sizes"; mkdir -p "$TMP_SIZES"
        CHK 2 && { [ -f "$APP_DIR/Data.shift" ] || [ -f "$APP_DIR/UserDe.shift" ]; } && ( echo $(( $(RAW_SIZE "/data/data/$PKG") + $(RAW_SIZE "/data/user_de/0/$PKG") )) > "$TMP_SIZES/data" ) &
        CHK 3 && [ -f "$APP_DIR/ExtData.shift" ] && ( echo $(RAW_SIZE "/data/media/0/Android/data/$PKG") > "$TMP_SIZES/ext" ) &
        CHK 4 && [ -f "$APP_DIR/Media.shift" ] && ( echo $(RAW_SIZE "/data/media/0/Android/media/$PKG") > "$TMP_SIZES/med" ) &
        CHK 5 && [ -f "$APP_DIR/Obb.shift" ] && ( echo $(RAW_SIZE "/data/media/0/Android/obb/$PKG") > "$TMP_SIZES/obb" ) &
        wait
        CUR_DATA=$(cat "$TMP_SIZES/data" 2>/dev/null); CUR_DATA=${CUR_DATA:-0}; CUR_EXT=$(cat "$TMP_SIZES/ext" 2>/dev/null); CUR_EXT=${CUR_EXT:-0}; CUR_MED=$(cat "$TMP_SIZES/med" 2>/dev/null); CUR_MED=${CUR_MED:-0}; CUR_OBB=$(cat "$TMP_SIZES/obb" 2>/dev/null); CUR_OBB=${CUR_OBB:-0}
        rm -rf "$TMP_SIZES"
    fi

    if CHK 2 && { [ -f "$APP_DIR/Data.shift" ] || [ -f "$APP_DIR/UserDe.shift" ]; }; then
        if [ "$FORCE_DATA" -eq 1 ] || [ "$CUR_DATA" != "$OLD_DATA" ]; then
            [ -f "$APP_DIR/Data.shift" ] && UNBUNDAPP "$APP_DIR/Data.shift" "/data/data"
            [ -f "$APP_DIR/UserDe.shift" ] && UNBUNDAPP "$APP_DIR/UserDe.shift" "/data/user_de/0"
        fi
    fi
    if CHK 3 && [ -f "$APP_DIR/ExtData.shift" ]; then
        if [ "$FORCE_DATA" -eq 1 ] || [ "$CUR_EXT" != "$OLD_EXT" ]; then
            UNBUNDAPP "$APP_DIR/ExtData.shift" "/data/media/0/Android/data"
        fi
    fi
    if CHK 4 && [ -f "$APP_DIR/Media.shift" ]; then
        if [ "$FORCE_DATA" -eq 1 ] || [ "$CUR_MED" != "$OLD_MED" ]; then
            UNBUNDAPP "$APP_DIR/Media.shift" "/data/media/0/Android/media"
        fi
    fi
    if CHK 5 && [ -f "$APP_DIR/Obb.shift" ]; then
        if [ "$FORCE_DATA" -eq 1 ] || [ "$CUR_OBB" != "$OLD_OBB" ]; then
            UNBUNDAPP "$APP_DIR/Obb.shift" "/data/media/0/Android/obb"
        fi
    fi

    local BASE_PCT=$(( (CUR_IDX - 1) * 100 / TOT_IDX ))

    while jobs | grep -q 'Running' 2>/dev/null; do
        local cur_ext_size=0
        local s1=$(du -sk "/data/data/$PKG" 2>/dev/null | awk '{print $1}')
        local s2=$(du -sk "/data/user_de/0/$PKG" 2>/dev/null | awk '{print $1}')
        local s3=$(du -sk "/data/media/0/Android/data/$PKG" 2>/dev/null | awk '{print $1}')
        local s4=$(du -sk "/data/media/0/Android/media/$PKG" 2>/dev/null | awk '{print $1}')
        local s5=$(du -sk "/data/media/0/Android/obb/$PKG" 2>/dev/null | awk '{print $1}')
        cur_ext_size=$(( ${s1:-0} + ${s2:-0} + ${s3:-0} + ${s4:-0} + ${s5:-0} ))

        [ "$cur_ext_size" -gt "$SIZE" ] && cur_ext_size=$SIZE

        local app_pct=0
        [ "$SIZE" -gt 0 ] && app_pct=$(( cur_ext_size * 100 / SIZE ))
        [ "$app_pct" -gt 100 ] && app_pct=100

        local global_pct=$(( BASE_PCT + (app_pct / TOT_IDX) ))
        [ "$global_pct" -gt 100 ] && global_pct=100
        echo "ACTION:RESTORE_START|PKG:$PKG|LABEL:$LABEL|VER:$VER|CUR:$CUR_IDX|TOT:$TOT_IDX|PCT:$global_pct|SIZE:$SIZE"
        sleep 0.8
    done

    wait

    local final_pct=$(( CUR_IDX * 100 / TOT_IDX ))
    echo "ACTION:RESTORE_START|PKG:$PKG|LABEL:$LABEL|VER:$VER|CUR:$CUR_IDX|TOT:$TOT_IDX|PCT:$final_pct|SIZE:$SIZE"

    if CHK 6; then
        CUR_SSAID=$(READID "$PKG")
        if [ -n "$OLD_SSAID" ] && [ "$CUR_SSAID" != "$OLD_SSAID" ]; then
            CHANID "$PKG" "$OLD_SSAID"
        fi
    fi

    [ -f "$APP_DIR/Permissions.txt" ] && SETPERM "$PKG" "$APP_DIR/Permissions.txt"

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
    pm enable "$PKG" >/dev/null 2>&1; rm -rf "$TMP_PKG"
    echo "ACTION:RESTORE_DONE|PKG:$PKG"
}

do_backup() {
    export APP_COMPS="$1"
    rm -rf "$AM_TMP/precalc" "$AM_TMP/selected_apps_sizes.txt" "$AM_TMP/selected_apps_sorted.txt" "$AM_TMP/paths.list" "$AM_TMP/du.out" "$AM_TMP/sizes.map" 2>/dev/null
    mkdir -p "$AM_TMP/precalc"

    if CHK 1; then
        pm list packages -f 2>/dev/null > "$AM_TMP/pm_list.txt"
    else
        > "$AM_TMP/pm_list.txt"
    fi

    awk -v comps=" $APP_COMPS " '
    NR==FNR {
        if ($0 == "") next
        pkg = $0; sub(/.*=/, "", pkg)
        path = $0; sub(/^package:/, "", path); sub(/=[^=]*$/, "", path)
        sub(/\/base\.apk$/, "", path)
        apk_dirs[pkg] = path
        next
    }
    {
        split($0, a, "|"); pkg = a[1];
        sub(/\r/, "", pkg)
        if(pkg == "") next

        if(comps ~ / 1 / && pkg in apk_dirs) print apk_dirs[pkg] "|" pkg "_app"
        if(comps ~ / 2 /) { print "/data/data/" pkg "|" pkg "_data"; print "/data/user_de/0/" pkg "|" pkg "_data" }
        if(comps ~ / 3 /) print "/data/media/0/Android/data/" pkg "|" pkg "_ext"
        if(comps ~ / 4 /) print "/data/media/0/Android/media/" pkg "|" pkg "_med"
        if(comps ~ / 5 /) print "/data/media/0/Android/obb/" pkg "|" pkg "_obb"
    }' "$AM_TMP/pm_list.txt" "$TARGETS" > "$AM_TMP/paths.list"

    awk -F'|' '{print $1}' "$AM_TMP/paths.list" | tr '\n' '\0' | xargs -0 du -sk 2>/dev/null > "$AM_TMP/du.out"

    awk '
    NR==FNR {
        s=$1; sub(/^[0-9]+[ \t]+/, "", $0); size[$0]=s; next
    }
    {
        split($0, a, "|");
        path = a[1]; id = a[2];
        if (path in size) total[id] += size[path];
    }
    END {
        for (i in total) print i "=" total[i]
    }' "$AM_TMP/du.out" "$AM_TMP/paths.list" > "$AM_TMP/sizes.map"

    awk -F'|' '
    NR==FNR {
        split($0, a, "="); map[a[1]]=a[2]; next
    }
    {
        pkg=$1; label=$2; ver=$3; type=$4
        gsub(/\r/, "", pkg); gsub(/\r/, "", label); gsub(/\r/, "", ver); gsub(/\r/, "", type)
        if(length(pkg) == 0) next;

        s_app = map[pkg "_app"] + 0
        s_data = map[pkg "_data"] + 0
        s_ext = map[pkg "_ext"] + 0
        s_med = map[pkg "_med"] + 0
        s_obb = map[pkg "_obb"] + 0

        size = s_app + s_data + s_ext + s_med + s_obb
        print size "|" label "|" pkg "|" ver "|" type "|" s_app "|" s_data "|" s_ext "|" s_med "|" s_obb
    }' "$AM_TMP/sizes.map" "$TARGETS" > "$AM_TMP/selected_apps_sizes.txt"

    TOTAL_KB=$(awk -F'|' '{s+=$1} END{print s+0}' "$AM_TMP/selected_apps_sizes.txt")
    sort -t'|' -k1 -n -r "$AM_TMP/selected_apps_sizes.txt" > "$AM_TMP/selected_apps_sorted.txt"

    START=$(date +%s); TOTAL_APPS=$(wc -l < "$AM_TMP/selected_apps_sorted.txt"); CURRENT_APP=0

    while IFS='|' read -r size label pkg ver type s_app s_data s_ext s_med s_obb || [ -n "$size" ]; do
        CURRENT_APP=$((CURRENT_APP + 1))
        size=${size:-0}
        local pct=$(( (CURRENT_APP - 1) * 100 / TOTAL_APPS ))

        DO_BACKUP "$pkg" "$label" "$ver" "$type" "$CURRENT_APP" "$TOTAL_APPS" "$pct" "$size" "$s_app" "$s_data" "$s_ext" "$s_med" "$s_obb"
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
            local s=0
            CHK 1 && { val=$(grep "^AppSize=" "$APP_PATH" | cut -d= -f2); s=$((s + ${val:-0})); }
            CHK 2 && { val=$(grep "^DataSize=" "$APP_PATH" | cut -d= -f2); s=$((s + ${val:-0})); }
            CHK 3 && { val=$(grep "^ExtDataSize=" "$APP_PATH" | cut -d= -f2); s=$((s + ${val:-0})); }
            CHK 4 && { val=$(grep "^MediaSize=" "$APP_PATH" | cut -d= -f2); s=$((s + ${val:-0})); }
            CHK 5 && { val=$(grep "^ObbSize=" "$APP_PATH" | cut -d= -f2); s=$((s + ${val:-0})); }
            echo "${s:-0}|${label}|${type}" >> "$AM_TMP/selected_restores.txt"
        fi
    done < "$TARGETS"

    sort -t'|' -k1 -n -r "$AM_TMP/selected_restores.txt" > "$AM_TMP/selected_restores_sorted.txt"
    TOTAL_KB=$(awk -F'|' '{s+=$1} END{print s+0}' "$AM_TMP/selected_restores_sorted.txt")
    START=$(date +%s); TOTAL_APPS=$(wc -l < "$AM_TMP/selected_restores_sorted.txt"); CURRENT_APP=0

    pm disable com.android.vending >/dev/null 2>&1
    settings put global verifier_verify_adb_installs 0
    setprop pm.dexopt.install assume-verified
    setprop pm.dexopt.install-bulk assume-verified
    setprop pm.dexopt.install-bulk-downgraded skip

    while IFS='|' read -r size label type || [ -n "$size" ]; do
        CURRENT_APP=$((CURRENT_APP + 1))
        size=${size:-0}
        local pct=$(( (CURRENT_APP - 1) * 100 / TOTAL_APPS ))

        DO_RESTORE "$label" "$type" "$CURRENT_APP" "$TOTAL_APPS" "$pct" "$size"
    done < "$AM_TMP/selected_restores_sorted.txt"

    pm enable com.android.vending >/dev/null 2>&1
    settings put global verifier_verify_adb_installs 1
    setprop pm.dexopt.install speed-profile
    setprop pm.dexopt.install-bulk speed-profile
    setprop pm.dexopt.install-bulk-downgraded verify

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

    local MOD_DIR="/data/adb/modules/ROM-Shifter"
    local UP_DIR="/data/adb/modules_update/ROM-Shifter"
    local PROP="id=ROM-Shifter\nname=ROM Shifter Module\nversion=1.0\nversionCode=1\nauthor=ROM Shifter\ndescription=Used for some system dependent features such as Systemizer"

    mkdir -p "$MOD_DIR" && printf "$PROP\n" > "$MOD_DIR/module.prop" && chmod 644 "$MOD_DIR/module.prop"
    mkdir -p "$UP_DIR" && printf "$PROP\n" > "$UP_DIR/module.prop" && chmod 644 "$UP_DIR/module.prop"

    local APK_PATH=$(pm path "$PKG" | sed 's/^package://' | head -n 1 | tr -d '\r')

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

case "$1" in
    --backup)
        MAIN_DIR="${3:-/sdcard/Shifter}"
        BACKUP_BASE="$MAIN_DIR/Apps"
        init_shifter
        do_backup "$2"
        ;;
    --restore)
        MAIN_DIR="${3:-/sdcard/Shifter}"
        BACKUP_BASE="$MAIN_DIR/Apps"
        init_shifter
        do_restore "$2"
        ;;
    --live-backup)
        MAIN_DIR="${3:-/sdcard/Shifter}"
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
    --systemize) init_shifter; do_systemize "$2" "$3" ;;
    --backup-msgs) init_shifter; do_backup_msgs "$2" ;;
    --restore-msgs) init_shifter; do_restore_msgs "$2" ;;
esac