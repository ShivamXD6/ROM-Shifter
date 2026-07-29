#!/system/bin/sh

# ==========================================
# ROM Shifter
# by @BuildBytes
# ==========================================

# --- Global Environment & Variables ---
BIN_DIR="/data/adb/#Shifter"
CACHE_DIR="$BIN_DIR/.cache"
CONFIG_FILE="$BIN_DIR/.config"
PORYGONZ="$BIN_DIR/porygonz"
ZAPDOS="$BIN_DIR/zapdos"
JOBS=$(nproc 2>/dev/null || echo 4)

AM_TMP="/data/local/tmp/appmgr_tmp"
AF_TMP="/data/local/tmp/autoflash_tmp"
PROMO_STATE="$BIN_DIR/.promo_state"

# --- Utility Functions ---
banner() {
    clear
    cat <<EOF
============================================
                ROM Shifter
               by @BuildBytes
============================================

EOF
}

ensure_root() {
    [ "$(id -u)" != "0" ] && { echo "[-] Please run as root."; exit 1; }
    mkdir -p "$BIN_DIR" "$CACHE_DIR" "$AM_TMP" "$AF_TMP"
    
    if [ ! -x "$BIN_DIR/porygonz" ]; then
        echo -e "[+] Extracting 'porygonz' to core directory..."
        echo "$PORYGONZ_B64" | base64 -d > "$BIN_DIR/porygonz" 2>/dev/null
        chmod +x "$BIN_DIR/porygonz"
    fi

    if [ ! -x "$BIN_DIR/zapdos" ]; then
        echo -e "[+] Extracting 'zapdos' to core directory..."
        echo "$ZAPDOS_B64" | base64 -d > "$BIN_DIR/zapdos" 2>/dev/null
        chmod +x "$BIN_DIR/zapdos"
    fi
    
    if [ ! -x "$BIN_DIR/porygonz" ] || [ ! -x "$BIN_DIR/zapdos" ]; then
        echo "[-] Error: Failed to extract binaries. Check Base64 strings."
        exit 1
    fi
}

select_directory() {
    local CURRENT_PATH="${1:-/sdcard}"
    local DIR_TMP="$AM_TMP/dir_list.txt"
    mkdir -p "$AM_TMP"
    local PAGE=1
    local PER_PAGE=12

    while true; do
        > "$DIR_TMP"
        ls -a "$CURRENT_PATH" 2>/dev/null | while IFS= read -r item; do
            [ "$item" = "." ] || [ "$item" = ".." ] && continue
            if [ -d "$CURRENT_PATH/$item" ]; then
                echo "$item" >> "$DIR_TMP"
            fi
        done
        
        local TOTAL_ITEMS=$(wc -l < "$DIR_TMP")
        local TOTAL_PAGES=$(( (TOTAL_ITEMS + PER_PAGE - 1) / PER_PAGE ))
        [ "$TOTAL_PAGES" -eq 0 ] && TOTAL_PAGES=1
        [ "$PAGE" -gt "$TOTAL_PAGES" ] && PAGE=$TOTAL_PAGES
        [ "$PAGE" -lt 1 ] && PAGE=1

        clear >&2
        echo "============================================" >&2
        echo "          Select Main Folder" >&2
        echo "============================================" >&2
        echo -e "\nCurrent Path: $CURRENT_PATH\n" >&2

        echo "[u] 📁 <Go Up (..)>" >&2
        
        local start_idx=$(( (PAGE - 1) * PER_PAGE + 1 ))
        local end_idx=$(( PAGE * PER_PAGE ))
        
        local count=1
        while IFS= read -r d; do
            if [ "$count" -ge "$start_idx" ] && [ "$count" -le "$end_idx" ]; then
                echo "[$count] 📂 $d" >&2
            fi
            count=$((count + 1))
        done < "$DIR_TMP"
        
        echo "--------------------------------------------------" >&2
        echo "Page $PAGE of $TOTAL_PAGES" >&2
        echo "[n] Next Page      | [p] Prev Page" >&2
        echo "[s] ✔️ SET FOLDER   | [m] ✍️ Enter Path Manually" >&2
        echo "[c] ➕ Create Folder | [0] ❌ Cancel" >&2
        printf "\nSelect an option:\n   -> " >&2
        read user_opt
        
        if [ "$user_opt" = "s" ] || [ "$user_opt" = "S" ]; then
            case "$CURRENT_PATH" in
                /storage*|/data/media*|/sdcard*) 
                    echo "$CURRENT_PATH"
                    rm -f "$DIR_TMP"
                    return 0
                    ;;
                *) 
                    echo "[-] Invalid Path! Must be inside /storage, /sdcard or /data/media." >&2
                    sleep 2
                    ;;
            esac
        elif [ "$user_opt" = "c" ] || [ "$user_opt" = "C" ]; then
            printf "\nEnter new folder name:\n   -> " >&2
            read new_folder_name
            if [ -n "$new_folder_name" ]; then
                safe_name=$(echo "$new_folder_name" | sed 's/ /_/g')
                mkdir -p "$CURRENT_PATH/$safe_name" 2>/dev/null
                if [ $? -eq 0 ]; then
                    echo "[+] Folder '$safe_name' created!" >&2
                else
                    echo "[-] Failed! Storage permission issue." >&2
                fi
                sleep 1
            fi
        elif [ "$user_opt" = "m" ] || [ "$user_opt" = "M" ]; then
            printf "\nEnter full path (e.g., /storage/emulated/0/MyFolder):\n   -> " >&2
            read manual_path
            manual_path=$(echo "$manual_path" | sed 's/\/$//')
            case "$manual_path" in
                /storage*|/data/media*|/sdcard*) 
                    echo "$manual_path"
                    rm -f "$DIR_TMP"
                    return 0
                    ;;
                *) 
                    echo "[-] Invalid Path! Must be inside /storage, /sdcard or /data/media." >&2
                    sleep 2
                    ;;
            esac
        elif [ "$user_opt" = "0" ]; then
            echo "CANCELLED"
            rm -f "$DIR_TMP"
            return 1
        elif [ "$user_opt" = "u" ] || [ "$user_opt" = "U" ]; then
            CURRENT_PATH=$(dirname "$CURRENT_PATH")
            PAGE=1
        elif [ "$user_opt" = "n" ] || [ "$user_opt" = "N" ]; then
            [ "$PAGE" -lt "$TOTAL_PAGES" ] && PAGE=$((PAGE + 1))
        elif [ "$user_opt" = "p" ] || [ "$user_opt" = "P" ]; then
            [ "$PAGE" -gt 1 ] && PAGE=$((PAGE - 1))
        else
            if echo "$user_opt" | grep -Eq '^[0-9]+$'; then
                if [ "$user_opt" -gt 0 ] && [ "$user_opt" -le "$TOTAL_ITEMS" ]; then
                    local selected_dir=$(sed -n "${user_opt}p" "$DIR_TMP")
                    CURRENT_PATH="$CURRENT_PATH/$selected_dir"
                    CURRENT_PATH=$(echo "$CURRENT_PATH" | sed 's/\/\//\//g')
                    PAGE=1
                else
                    echo "[-] Invalid option." >&2
                    sleep 1
                fi
            else
                echo "[-] Invalid option." >&2
                sleep 1
            fi
        fi
    done
}

init_shifter() {
    if [ ! -f "$CONFIG_FILE" ]; then
        banner
        echo -e "--- Initial Setup ---\n"
        echo "Please select your main working folder."
        sleep 1
        
        user_path=$(select_directory "/sdcard")
        [ "$user_path" = "CANCELLED" ] || [ -z "$user_path" ] && user_path="/sdcard"
        
        MAIN_DIR="${user_path}/#Shifter"
        MAIN_DIR=$(echo "$MAIN_DIR" | sed 's/\/\//\//g')
        echo "MAIN_DIR=\"$MAIN_DIR\"" > "$CONFIG_FILE"
    else
        . "$CONFIG_FILE"
    fi
    
    FLASH="$MAIN_DIR/Auto-Flash"
    BACKUP_BASE="$MAIN_DIR/Data-Migrated"
    LP_DIR="$MAIN_DIR/Live-Partition"
}

pause_prompt() {
    printf "\nPress any key to return..."
    read -n 1 -s -r
    echo
}

notify() {
    local TITLE="$1"
    local MSG="$2"
    if [ "$MSG" != "$LAST_MSG" ]; then
        local SAFE_MSG=$(printf '%b' "$MSG" | sed "s/'/'\\\\''/g")
        su -lp 2000 -c \
            "cmd notification post -S bigtext -t '$TITLE' 'Status' '$SAFE_MSG'" \
            >/dev/null 2>&1
        LAST_MSG="$MSG"
    fi
}

promo_check() {
    [ -f "$PROMO_STATE" ] && local STATE=$(cat "$PROMO_STATE")
    [ "$STATE" = "JOINED" ] && return
    
    if [ $((RANDOM % 10)) -eq 0 ]; then
        clear
        cat <<EOF
============================================
             Have some time :?
============================================

EOF
        local msg_type=$((RANDOM % 3))
        case $msg_type in
        0) echo -e "💡 Join BuildBytes Telegram\nFor updates, support, and announcements." ;;
        1) echo -e "🐞 Found a bug?\nReport it on the BuildBytes Telegram group." ;;
        2) echo -e "❔ Didn't understand something?\nAsk about it on BuildBytes Group." ;;
        esac
        
        echo -e "\n--------------------------------------------------"
        echo "Would you like to join our Telegram?"
        echo "[1] Yes, ofc   [2] Maybe later"
        printf "\nSelect an option:\n   -> "; read -n 1 promo_opt; echo
        
        case "$promo_opt" in
            1)
                am start -a android.intent.action.VIEW -d https://telegram.me/BuildBytes >/dev/null 2>&1
                (
                sleep 20
                notify "Just a Friendly Reminder 😄" \
                "Loved the script? and haven't starred the repo yet?\nGive a star then it costs no money ;)"
                am start -a android.intent.action.VIEW -d https://github.com/ShivamXD6/ROM-Shifter >/dev/null 2>&1
                ) &
                echo "JOINED" > "$PROMO_STATE"
                echo "\n[+] Opening links..."
                sleep 2
                ;;
            2) ;; 
            *) promo_check ;;
        esac
    fi
}

# Auto Flash
set_af_vars() {
    RECOVERY="/cache/recovery"
    ORS="$RECOVERY/openrecoveryscript"
    PIDFILE="/data/local/tmp/autoflash.pid"
    MASTER_LIST="$AF_TMP/autoflash_master_list.txt"
    STEP_FLASH="$FLASH/DELETE TO FLASH.txt"
}

keep_latest_zip() {
    local list_file="$1"
    local category="$2"
    [ -s "$list_file" ] || return
    local count=$(wc -l < "$list_file")
    
    if [ "$count" -gt 1 ]; then
        notify "Auto Flash" "Warning: Multiple $category ZIPs!\nMoving older out of Auto-Flash folder..."
        while [ "$count" -gt 1 ]; do
            local oldest=$(stat -c '%Y %n' $(cat "$list_file") 2>/dev/null | \
                sort -n | head -n 1 | cut -d' ' -f2-)
            if [ -n "$oldest" ]; then
                mv -f "$oldest" "$MAIN_DIR/"
                grep -v -F "$oldest" "$list_file" > "${list_file}.tmp"
                mv "${list_file}.tmp" "$list_file"
            fi
            count=$(wc -l < "$list_file")
        done
        sleep 3 
    fi
    cat "$list_file" >> "$MASTER_LIST"
}

update_zip_lists() {
    > "$MASTER_LIST"
    local F_FW="$AF_TMP/fw.$$"; local F_ROM="$AF_TMP/rom.$$"
    local F_GAPPS="$AF_TMP/gapps.$$"; local F_ADDON="$AF_TMP/addon.$$"
    local F_KERNEL="$AF_TMP/kernel.$$"; local F_OTHER="$AF_TMP/other.$$"
    touch "$F_FW" "$F_ROM" "$F_GAPPS" "$F_ADDON" "$F_KERNEL" "$F_OTHER"

    for zip in "$FLASH"/*.zip; do
        [ -f "$zip" ] || continue
        local name=$(basename "$zip" | tr '[:upper:]' '[:lower:]')
        local contents=$(unzip -l "$zip" 2>/dev/null)
        
        local IS_GAPPS=0; local IS_ADDON=0
        if echo "$name" | grep -qEi 'gapps|nikgapps|bitgapps|mindthegapps'; then
            IS_GAPPS=1; echo "$name" | grep -qEi 'addon' && IS_ADDON=1
        elif echo "$contents" | grep -qEi 'bitgapps|nikgapps|mindthegapps|busybox-arm|util_functions\.sh'; then
            IS_GAPPS=1; IS_ADDON=1
        fi

        if [ "$IS_GAPPS" -eq 1 ]; then
            [ "$IS_ADDON" -eq 1 ] && echo "$zip" >> "$F_ADDON" || echo "$zip" >> "$F_GAPPS"
            continue
        fi
        
        echo "$contents" | grep -qEi "module\.prop" && continue
        if echo "$contents" | grep -qEi 'firmware-update/|abl\.elf|xbl\.elf|tz\.mbn'; then
            echo "$zip" >> "$F_FW"; continue
        fi
        if echo "$contents" | grep -qEi 'payload\.bin|system\.new\.dat|system\.transfer\.list'; then
            echo "$zip" >> "$F_ROM"; continue
        fi
        if echo "$contents" | grep -qEi 'anykernel|zimage|image\.gz' || echo "$name" | grep -qEi 'kernel|perf|stormbreaker|eas'; then
            echo "$zip" >> "$F_KERNEL"; continue
        fi
        if echo "$contents" | grep -qEi 'META-INF/.*update-binary'; then
            echo "$zip" >> "$F_OTHER"; continue
        fi
    done

    keep_latest_zip "$F_FW" "Firmware"
    keep_latest_zip "$F_ROM" "ROM"
    sort "$F_GAPPS" >> "$MASTER_LIST"
    sort "$F_ADDON" >> "$MASTER_LIST"
    keep_latest_zip "$F_KERNEL" "Kernel"
    sort "$F_OTHER" >> "$MASTER_LIST"

    rm -f "$F_FW" "$F_ROM" "$F_GAPPS" "$F_ADDON" "$F_KERNEL" "$F_OTHER"
    
    VALID_ZIPS=$(wc -l < "$MASTER_LIST")
    if [ "$VALID_ZIPS" -gt 0 ]; then
        CURRENT_ZIP_NAME=""
        local counter=1
        while read -r FILE; do
            [ -z "$FILE" ] && continue
            if [ "$counter" -le 2 ]; then
                [ -z "$CURRENT_ZIP_NAME" ] && \
                    CURRENT_ZIP_NAME="[${counter}] $(basename "$FILE")" || \
                    CURRENT_ZIP_NAME="${CURRENT_ZIP_NAME}\n[${counter}] $(basename "$FILE")"
            elif [ "$counter" -eq 3 ]; then
                CURRENT_ZIP_NAME="${CURRENT_ZIP_NAME}\n...and $((VALID_ZIPS - 2)) more."
            fi
            counter=$((counter + 1))
        done < "$MASTER_LIST"
    else
        CURRENT_ZIP_NAME="No valid flashable ZIPs found."
    fi
}

screenlock_removed() { 
    locksettings verify >/dev/null 2>&1
    return $?
}

process_steps() {
    CURRENT_DIR_HASH=$(ls -l "$FLASH"/*.zip 2>/dev/null | md5sum | cut -d' ' -f1)
    if [ "$CURRENT_DIR_HASH" != "$LAST_DIR_HASH" ]; then
        update_zip_lists
        LAST_DIR_HASH="$CURRENT_DIR_HASH"
        SETTINGS_OPENED=0
    fi
    
    if screenlock_removed; then
        if [ "$SETTINGS_OPENED" -eq 1 ]; then
            sleep 1; SETTINGS_OPENED=0; input keyevent 4; input keyevent 4
        fi
        LOCK_OK=1
    else
        LOCK_OK=0
        if [ "$VALID_ZIPS" -gt 0 ] && [ "$SETTINGS_OPENED" -eq 0 ]; then
            sleep 1; am start --user 0 -a android.settings.SECURITY_SETTINGS >/dev/null 2>&1
            SETTINGS_OPENED=1
        fi
    fi

    if [ "$VALID_ZIPS" -gt 0 ] && [ "$LOCK_OK" -eq 1 ]; then
        if [ "$FLASH_READY_STATE" -eq 0 ]; then
            notify "Auto Flash" "Ready! Delete 'DELETE TO FLASH.txt' to reboot.\n$CURRENT_ZIP_NAME"
            touch "$STEP_FLASH"; FLASH_READY_STATE=1
        elif [ ! -f "$STEP_FLASH" ]; then
            if [ "$(ls -1 "$FLASH"/*.zip 2>/dev/null | wc -l)" -eq 0 ]; then
                notify "Auto Flash" "Flashing aborted! ZIP files were removed."
                FLASH_READY_STATE=0; return
            fi
            for i in 4 3 2 1; do
                notify "Auto Flash" "Flashing triggered! Starting in $i..."; sleep 1
            done
            generate_ors
            sync; sleep 2; rm -f "$STEP_FLASH" "$PIDFILE" "$MASTER_LIST"
            reboot recovery
        fi
    else
        FLASH_READY_STATE=0
        [ -f "$STEP_FLASH" ] && rm -f "$STEP_FLASH"
        if [ "$VALID_ZIPS" -eq 0 ]; then
            notify "Auto Flash" "Waiting for ZIPs...\nCopy files to: $FLASH"
        elif [ "$LOCK_OK" -eq 0 ]; then
            notify "Auto Flash" "Please remove screen lock before flashing."
        fi
    fi
}

generate_ors() {
    > "$ORS"
    [ "$FLASH_MODE" -ge 1 ] && { echo "wipe dalvik" >> "$ORS"; echo "wipe cache" >> "$ORS"; }
    [ "$FLASH_MODE" -ge 2 ] && { echo "wipe system" >> "$ORS"; echo "wipe data" >> "$ORS"; }
    [ "$FLASH_MODE" -eq 3 ] && echo "wipe metadata" >> "$ORS"
    while read -r FILE; do [ -f "$FILE" ] && echo "install $FILE" >> "$ORS"; done < "$MASTER_LIST"
    [ "$FLASH_MODE" -eq 3 ] && echo "format data" >> "$ORS"
    echo "reboot system" >> "$ORS"
}

af_monitor() {
    set_af_vars
    FLASH_MODE="$1"
    trap 'rm -f "$STEP_FLASH" "$PIDFILE" "$MASTER_LIST"; exit 0' INT TERM
    trap 'rm -f "$PIDFILE"' EXIT
    
    SETTINGS_OPENED=0; LAST_MSG=""; LAST_DIR_HASH=""
    VALID_ZIPS=0; CURRENT_ZIP_NAME=""; FLASH_READY_STATE=0
    
    mkdir -p "$FLASH" "$RECOVERY"
    while true; do process_steps; sleep 2; done
}

setup_daemon() {
    if [ -f "/data/local/tmp/autoflash.pid" ]; then
        OLD_PID=$(cat "/data/local/tmp/autoflash.pid" 2>/dev/null)
        if [ -n "$OLD_PID" ] && kill -0 "$OLD_PID" 2>/dev/null; then
            kill -9 "$OLD_PID" 2>/dev/null
        fi
        rm -f "/data/local/tmp/autoflash.pid"
    fi
    mkdir -p "$FLASH"
    
    banner
    echo "--- Auto Flash Setup ---"
    echo -e "Flashing Directory:\n$FLASH"
    echo; echo "--- Flashing Modes ---"
    echo "[1] Dirty : Wipe Dalvik, Cache"
    echo "[2] Clean : Dirty + Wipe System, Data"
    echo "[3] Format : Clean + Wipe Metadata & Format Data"
    printf "\nSelect mode (1/2/3) [Default: 2]:\n   -> "; read -n 1 user_mode; echo
    
    case "$user_mode" in 
        1) FLASH_MODE=1 ;; 
        3) FLASH_MODE=3 ;; 
        *) FLASH_MODE=2 ;; 
    esac
    
    echo -e "\n[+] Starting Auto Flash daemon (Mode: $FLASH_MODE)..."
    nohup sh "$0" __monitor "$FLASH_MODE" >/dev/null 2>&1 &
    MONITOR_PID=$!
    echo "$MONITOR_PID" > /data/local/tmp/autoflash.pid
    sleep 1
    
    if kill -0 "$MONITOR_PID" 2>/dev/null; then
        echo -e "[+] Daemon started successfully.\n    Open your file manager to:\n    $FLASH\n    And Follow the shell notifications."
    else
        echo "[-] Failed to start daemon."
    fi
    pause_prompt
}

# Live Partition Manager
resolve_partition() {
    local base_part="$1"
    local path_prefix="/dev/block/by-name"
    [ ! -d "$path_prefix" ] && path_prefix="/dev/block/bootdevice/by-name"
    
    if [ -e "$path_prefix/${base_part}_a" ] && [ -e "$path_prefix/${base_part}_b" ]; then
        local active_slot=$(getprop ro.boot.slot_suffix)
        echo -e "\n[+] Found multiple '$base_part' partitions:" >&2
        if [ "$active_slot" = "_a" ]; then
            echo "[1] ${base_part}_a (Active Slot)" >&2
            echo "[2] ${base_part}_b" >&2
        elif [ "$active_slot" = "_b" ]; then
            echo "[1] ${base_part}_a" >&2
            echo "[2] ${base_part}_b (Active Slot)" >&2
        else
            echo "[1] ${base_part}_a" >&2
            echo "[2] ${base_part}_b" >&2
        fi
        printf "Select partition to use (1 or 2):\n   -> " >&2
        read -n 1 slot_opt
        echo >&2
        case "$slot_opt" in
            1) echo "$path_prefix/${base_part}_a" ;;
            2) echo "$path_prefix/${base_part}_b" ;;
            *) echo "INVALID" ;;
        esac
    elif [ -e "$path_prefix/$base_part" ]; then
        echo "$path_prefix/$base_part"
    else
        echo "NOT_FOUND"
    fi
}

live_manager() {
    mkdir -p "$LP_DIR"
    while true; do
        banner
        echo -e "--- Live Partition Manager ---\n"
        echo "Safely backup/restore partitions without recovery."
        echo "Files are read/written at:"
        echo "$LP_DIR"
        
        echo -e "\n[1] Backup Boot (Kernel)"
        echo "[2] Backup Recovery"
        echo "[3] Backup Custom Partition"
        echo "[4] Restore Boot"
        echo "[5] Restore Recovery"
        echo "[6] Restore Custom Partition"
        echo "[0] Back\n"
        printf "Select an option:\n   -> "; read -n 1 lp_opt; echo

        case "$lp_opt" in
            1|2|3)
                case "$lp_opt" in
                    1) part="boot" ;; 
                    2) part="recovery" ;;
                    3) printf "Enter exact partition name:\n   -> "; read part; [ -z "$part" ] && continue ;;
                esac
                
                BLOCK_PATH=$(resolve_partition "$part")
                
                if [ "$BLOCK_PATH" = "INVALID" ]; then
                    echo "[-] Invalid selection."
                    pause_prompt; continue
                elif [ "$BLOCK_PATH" = "NOT_FOUND" ] || [ -z "$BLOCK_PATH" ]; then
                    echo "[-] Partition '$part' not found."
                    pause_prompt; continue
                fi
                
                REAL_PART_NAME=$(basename "$BLOCK_PATH")
                echo "[+] Backing up $REAL_PART_NAME..."
                dd if="$BLOCK_PATH" of="$LP_DIR/${REAL_PART_NAME}_backup.img" bs=4M
                echo -e "[+] Backup complete!\nSaved to: $LP_DIR/${REAL_PART_NAME}_backup.img"
                pause_prompt
                ;;
            4|5|6)
                case "$lp_opt" in
                    4) part="boot" ;; 
                    5) part="recovery" ;;
                    6) printf "Enter exact partition name to restore:\n   -> "; read part; [ -z "$part" ] && continue ;;
                esac
                
                BLOCK_PATH=$(resolve_partition "$part")
                
                if [ "$BLOCK_PATH" = "INVALID" ]; then
                    echo "[-] Invalid selection."
                    pause_prompt; continue
                elif [ "$BLOCK_PATH" = "NOT_FOUND" ] || [ -z "$BLOCK_PATH" ]; then
                    echo "[-] Target partition '$part' not found."
                    pause_prompt; continue
                fi
                
                REAL_PART_NAME=$(basename "$BLOCK_PATH")
                echo "[!] WARNING: Flashing '$REAL_PART_NAME' live."
                if [ ! -f "$LP_DIR/${REAL_PART_NAME}_backup.img" ]; then
                    echo -e "[-] Backup not found:\n$LP_DIR/${REAL_PART_NAME}_backup.img"
                    pause_prompt; continue
                fi
                
                printf "Restore $REAL_PART_NAME? (y/n): "; read -n 1 confirm; echo
                if [ "$confirm" = "y" ] || [ "$confirm" = "Y" ]; then
                    echo "[+] Restoring $REAL_PART_NAME..."
                    dd if="$LP_DIR/${REAL_PART_NAME}_backup.img" of="$BLOCK_PATH" bs=4M
                    echo "[+] Restore complete!"
                else
                    echo "[-] Aborted."
                fi
                pause_prompt
                ;;
            0) return ;;
            *) echo "[-] Invalid option." ; sleep 1 ;;
        esac
    done
}

# Data & Apps Migrator
COOLDOWN() { 
    while [ "$(jobs | grep -c 'Running')" -ge "$1" ] 2>/dev/null; do sleep 0.1; done 
}

SANITIZE() { 
    echo "$1" | sed 's/[^a-zA-Z0-9]/_/g' 
}

CHK() { 
    echo "$COMPS" | grep -qw "$1" 
}

RAW_SIZE() {
    [ -z "$1" ] && { echo 0; return; }
    echo "$1" | while IFS= read -r p; do
        if [ -e "$p" ]; then
            local base_size=$(du -sk "$p" 2>/dev/null | awk '{print $1}')
            local cache_size=$(du -sk "$p/cache" "$p/code_cache" 2>/dev/null | awk '{s+=$1} END{print s+0}')
            echo $(( ${base_size:-0} - ${cache_size:-0} ))
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

READID() { 
    grep "package=\"$1\"" "/data/system/users/0/settings_ssaid.xml" 2>/dev/null | \
    sed -n 's/.*value="\([^"]*\)".*/\1/p' 
}

CHANID() { 
    sed -i "/package=\"$1\"/s/\(value=\"\)[^\"]*\(.*defaultValue=\"\)[^\"]*/\1$2\2$2/" \
    "/data/system/users/0/settings_ssaid.xml"
}

GETPERM() {
    > "$2"
    local in=0
    dumpsys package "$1" 2>/dev/null | while IFS= read -r line; do
        case "$line" in 
            *runtime\ permissions:*) in=1; continue ;; 
            [![:space:]]*) in=0 ;; 
        esac
        [ "$in" -eq 1 ] && case "$line" in 
            *granted=true*) perm="${line%%:*}"; echo "${perm#"${perm%%[![:space:]]*}"}" >> "$2" ;; 
        esac
    done
    appops get "$1" 2>/dev/null | while IFS= read -r line; do
        case "$line" in 
            *:*) op=${line%%:*}; mode=${line#*:}; case "$mode" in *allow*) echo "appops:$op" >> "$2" ;; esac ;; 
        esac
    done
}

SETPERM() { 
    while IFS= read -r perm; do 
        case "$perm" in 
            appops:*) appops set "$1" "${perm#appops:}" allow 2>/dev/null & ;; 
            *) pm grant "$1" "$perm" 2>/dev/null & ;; 
        esac
    done < "$2"
}

DELGMS() { 
    rm -f "/data/data/$1/databases/com.google.android.datatransport.events" \
          "/data/data/$1/databases/com.google.android.datatransport.events-journal" \
          "/data/data/$1/no_backup/com.google.android.gms.appid-no-backup" \
          "/data/data/$1/shared_prefs/com.google.android.gms.appid.xml" \
          "/data/data/$1/shared_prefs/com.google.android.gms.measurement.prefs.xml" 2>/dev/null
}

PKG_INSTALLED() { 
    pm list packages | grep -q "^package:$1$" || return 1
    [ -z "$2" ] && return 0
    local apkpath="$(pm path "$1" 2>/dev/null | sed -n 's/^package://p' | head -n 1)"
    [ -z "$apkpath" ] && return 1
    local inst_ver="$("$PORYGONZ" dump badging "$apkpath" 2>/dev/null | awk -F"'" '/package: name=/{print $6; exit}')"
    [ "$inst_ver" = "$2" ] || return 1
    return 0
}

BUNDAPP() { 
    COOLDOWN "$((JOBS / 2))"
    tar --exclude="$2/cache" --exclude="$2/code_cache" -cf - -C "$1" "$2" 2>/dev/null | \
    "$ZAPDOS" -1 -f -q -o "$3/$4.bundle.pack" & 
}

UNBUNDAPP() { 
    COOLDOWN "$((JOBS - 2))"
    "$ZAPDOS" -d -q -c "$1" | tar -xf - -C "$2" 2>/dev/null & 
}

DO_BACKUP() {
    PKG="$1"; LABEL="$2"; VER="$3"; COMPS="$4"; TYPE="$5"
    APP_DIR="$BACKUP_BASE/$TYPE/$LABEL"; mkdir -p "$APP_DIR"
    
    local formatted_size=$(FORMAT_SIZE "$9")
    echo -e "\n📦 App      : $LABEL (v$VER)\n📊 Progress : [$6/$7] - $8%\n💾 Size     : ~ $formatted_size\n⚙️  Details  :"
    notify "Data Migrator" "Backing Up: $LABEL (v$VER) ~$formatted_size\nProgress: [$6/$7] - $8%"
    
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
    rm -rf "$TMP_SIZES"
    ACT=0
    
    if CHK 1; then
        if [ "$CUR_APP" != "$OLD_APP" ] || { [ "$CUR_APP" -gt 0 ] && [ ! -f "$APP_DIR/App.bundle.pack" ]; }; then
            if [ "$CUR_APP" -gt 0 ]; then
                echo "   [✓] App (Base & Splits)"
                echo "$apks" | sed 's|^/||' | tar -cf - -C / -T - 2>/dev/null | "$ZAPDOS" -1 -f -q -o "$APP_DIR/App.bundle.pack" & ACT=1
            else rm -f "$APP_DIR/App.bundle.pack"; fi
            OLD_APP=$CUR_APP
        fi
    fi
    if CHK 2; then
        if [ "$CUR_DATA" != "$OLD_DATA" ] || { [ "$CUR_DATA" -gt 0 ] && [ ! -f "$APP_DIR/Data.bundle.pack" ] && [ ! -f "$APP_DIR/UserDe.bundle.pack" ]; }; then
            if [ "$CUR_DATA" -gt 0 ]; then
                echo "   [✓] Data (/data & user_de)"
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
                echo "   [✓] ExtData (Android/data)"
                BUNDAPP "/data/media/0/Android/data" "$PKG" "$APP_DIR" "ExtData"; ACT=1
            else rm -f "$APP_DIR/ExtData.bundle.pack"; fi
            OLD_EXT=$CUR_EXT
        fi
    fi
    if CHK 4; then
        if [ "$CUR_MED" != "$OLD_MED" ] || { [ "$CUR_MED" -gt 0 ] && [ ! -f "$APP_DIR/Media.bundle.pack" ]; }; then
            if [ "$CUR_MED" -gt 0 ]; then
                echo "   [✓] Media (Android/media)"
                BUNDAPP "/data/media/0/Android/media" "$PKG" "$APP_DIR" "Media"; ACT=1
            else rm -f "$APP_DIR/Media.bundle.pack"; fi
            OLD_MED=$CUR_MED
        fi
    fi
    if CHK 5; then
        if [ "$CUR_OBB" != "$OLD_OBB" ] || { [ "$CUR_OBB" -gt 0 ] && [ ! -f "$APP_DIR/Obb.bundle.pack" ]; }; then
            if [ "$CUR_OBB" -gt 0 ]; then
                echo "   [✓] OBB (Android/obb)"
                BUNDAPP "/data/media/0/Android/obb" "$PKG" "$APP_DIR" "Obb"; ACT=1
            else rm -f "$APP_DIR/Obb.bundle.pack"; fi
            OLD_OBB=$CUR_OBB
        fi
    fi
    if CHK 6; then
        CUR_SSAID=$(READID "$PKG")
        if [ -n "$CUR_SSAID" ] && [ "$CUR_SSAID" != "$OLD_SSAID" ]; then
            echo "   [✓] Android ID"; OLD_SSAID=$CUR_SSAID; ACT=1
        fi
    fi

    [ "$ACT" -eq 0 ] && echo "   [⏭️] Up to date (Skipped)"
    local APP_TOTAL_KB=$(( OLD_APP + OLD_DATA + OLD_EXT + OLD_MED + OLD_OBB ))
    
    SYS_PATH=""
    [ "$TYPE" = "System" ] && SYS_PATH=$(dumpsys package "$PKG" 2>/dev/null | awk -F= '/codePath=\/(system|product|vendor|oem|odm)/{print $2; exit}')
    
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
}

DO_RESTORE() {
    LABEL="$1"; TYPE="$2"; COMPS="$7"; APP_DIR="$BACKUP_BASE/$TYPE/$LABEL"
    [ -f "$APP_DIR/Meta.txt" ] || return
    PKG=$(grep "Package=" "$APP_DIR/Meta.txt" | cut -d= -f2); [ -z "$PKG" ] && return
    VER=$(grep "Version=" "$APP_DIR/Meta.txt" | cut -d= -f2)
    SYS_PATH=$(grep "^SysPath=" "$APP_DIR/Meta.txt" | cut -d= -f2)
    TMP_PKG="$AM_TMP/$PKG"; mkdir -p "$TMP_PKG"
    
    local formatted_size=$(FORMAT_SIZE "$6")
    echo -e "\n📦 App      : $LABEL (v$VER)\n📊 Progress : [$3/$4] - $5%\n💾 Size     : ~ $formatted_size\n⚙️  Details  :"
    notify "Data Migrator" "Restoring: $LABEL (v$VER) ~$formatted_size\nProgress: [$3/$4] - $5%"
    
    OLD_APP=$(grep "^AppSize=" "$APP_DIR/Meta.txt" | cut -d= -f2); OLD_APP=${OLD_APP:-0}
    OLD_DATA=$(grep "^DataSize=" "$APP_DIR/Meta.txt" | cut -d= -f2); OLD_DATA=${OLD_DATA:-0}
    OLD_EXT=$(grep "^ExtDataSize=" "$APP_DIR/Meta.txt" | cut -d= -f2); OLD_EXT=${OLD_EXT:-0}
    OLD_MED=$(grep "^MediaSize=" "$APP_DIR/Meta.txt" | cut -d= -f2); OLD_MED=${OLD_MED:-0}
    OLD_OBB=$(grep "^ObbSize=" "$APP_DIR/Meta.txt" | cut -d= -f2); OLD_OBB=${OLD_OBB:-0}
    OLD_SSAID=$(grep "^SSAID=" "$APP_DIR/Meta.txt" | cut -d= -f2)
    ACT=0; FORCE_DATA=0

    if CHK 1 && [ -f "$APP_DIR/App.bundle.pack" ]; then
        if ! PKG_INSTALLED "$PKG" "$VER"; then
            echo "   [✓] App (Base & Splits)"
            "$ZAPDOS" -d -q -c "$APP_DIR/App.bundle.pack" | tar -xf - -C "$TMP_PKG" 2>/dev/null
            apks_to_install=$(find "$TMP_PKG" -type f -name "*.apk" | sort)
            [ -n "$apks_to_install" ] && pm install -g --dexopt-compiler-filter skip $apks_to_install >/dev/null 2>&1
            ACT=1; FORCE_DATA=1
            
            if [ "$TYPE" = "System" ] && [ -n "$SYS_PATH" ]; then
                echo "   [✓] Systemizing to $SYS_PATH..."
                mount -o rw,remount / >/dev/null 2>&1
                mount -o rw,remount /system >/dev/null 2>&1
                mount -o rw,remount /product >/dev/null 2>&1
                
                APK_PATHS=$(pm path "$PKG" | sed 's/^package://')
                if [ -n "$APK_PATHS" ]; then
                    APP_DIR_SRC=$(dirname "$(echo "$APK_PATHS" | head -n 1)")
                    TARGET_DIR="$SYS_PATH"
                    
                    mkdir -p "$TARGET_DIR" 2>/dev/null
                    cp -rf "$APP_DIR_SRC/"* "$TARGET_DIR/" 2>/dev/null
                    
                    find "$TARGET_DIR" -type d -exec chmod 755 {} \;
                    find "$TARGET_DIR" -type f -exec chmod 644 {} \;
                    chown -R root:root "$TARGET_DIR"
                fi
            fi
        fi
    fi
    pm disable "$PKG" >/dev/null 2>&1
    NEW_UID=$(stat -c '%u' "/data/data/$PKG" 2>/dev/null)    
    CUR_DATA=0; CUR_EXT=0; CUR_MED=0; CUR_OBB=0
    if [ "$FORCE_DATA" -eq 0 ]; then
        TMP_SIZES="$AM_TMP/${PKG}_sizes"; mkdir -p "$TMP_SIZES"
        CHK 2 && { [ -f "$APP_DIR/Data.bundle.pack" ] || [ -f "$APP_DIR/UserDe.bundle.pack" ]; } && \
            ( echo $(( $(RAW_SIZE "/data/data/$PKG") + $(RAW_SIZE "/data/user_de/0/$PKG") )) > "$TMP_SIZES/data" ) &
        CHK 3 && [ -f "$APP_DIR/ExtData.bundle.pack" ] && \
            ( echo $(RAW_SIZE "/data/media/0/Android/data/$PKG") > "$TMP_SIZES/ext" ) &
        CHK 4 && [ -f "$APP_DIR/Media.bundle.pack" ] && \
            ( echo $(RAW_SIZE "/data/media/0/Android/media/$PKG") > "$TMP_SIZES/med" ) &
        CHK 5 && [ -f "$APP_DIR/Obb.bundle.pack" ] && \
            ( echo $(RAW_SIZE "/data/media/0/Android/obb/$PKG") > "$TMP_SIZES/obb" ) &
        wait
        CUR_DATA=$(cat "$TMP_SIZES/data" 2>/dev/null); CUR_DATA=${CUR_DATA:-0}
        CUR_EXT=$(cat "$TMP_SIZES/ext" 2>/dev/null); CUR_EXT=${CUR_EXT:-0}
        CUR_MED=$(cat "$TMP_SIZES/med" 2>/dev/null); CUR_MED=${CUR_MED:-0}
        CUR_OBB=$(cat "$TMP_SIZES/obb" 2>/dev/null); CUR_OBB=${CUR_OBB:-0}
        rm -rf "$TMP_SIZES"
    fi

    if CHK 2 && { [ -f "$APP_DIR/Data.bundle.pack" ] || [ -f "$APP_DIR/UserDe.bundle.pack" ]; }; then
        if [ "$FORCE_DATA" -eq 1 ] || [ "$CUR_DATA" != "$OLD_DATA" ]; then
            echo "   [✓] Data (/data & user_de)"
            [ -f "$APP_DIR/Data.bundle.pack" ] && UNBUNDAPP "$APP_DIR/Data.bundle.pack" "/data/data"
            [ -f "$APP_DIR/UserDe.bundle.pack" ] && UNBUNDAPP "$APP_DIR/UserDe.bundle.pack" "/data/user_de/0"
            ACT=1
        fi
    fi
    if CHK 3 && [ -f "$APP_DIR/ExtData.bundle.pack" ]; then
        if [ "$FORCE_DATA" -eq 1 ] || [ "$CUR_EXT" != "$OLD_EXT" ]; then
            echo "   [✓] ExtData (Android/data)"; UNBUNDAPP "$APP_DIR/ExtData.bundle.pack" "/data/media/0/Android/data"; ACT=1
        fi
    fi
    if CHK 4 && [ -f "$APP_DIR/Media.bundle.pack" ]; then
        if [ "$FORCE_DATA" -eq 1 ] || [ "$CUR_MED" != "$OLD_MED" ]; then
            echo "   [✓] Media (Android/media)"; UNBUNDAPP "$APP_DIR/Media.bundle.pack" "/data/media/0/Android/media"; ACT=1
        fi
    fi
    if CHK 5 && [ -f "$APP_DIR/Obb.bundle.pack" ]; then
        if [ "$FORCE_DATA" -eq 1 ] || [ "$CUR_OBB" != "$OLD_OBB" ]; then
            echo "   [✓] OBB (Android/obb)"; UNBUNDAPP "$APP_DIR/Obb.bundle.pack" "/data/media/0/Android/obb"; ACT=1
        fi
    fi
    wait

    if CHK 6; then
        CUR_SSAID=$(READID "$PKG")
        if [ -n "$OLD_SSAID" ] && [ "$CUR_SSAID" != "$OLD_SSAID" ]; then
            echo "   [✓] Android ID & Permissions"; CHANID "$PKG" "$OLD_SSAID"; ACT=1
        fi
    fi
    
    [ "$ACT" -eq 1 ] && [ -f "$APP_DIR/Permissions.txt" ] && SETPERM "$PKG" "$APP_DIR/Permissions.txt"
    [ "$ACT" -eq 0 ] && echo "   [⏭️] Up to date (Skipped)"
    
    if [ -n "$NEW_UID" ] && [ "$ACT" -eq 1 ]; then
        chown -R "$NEW_UID:$NEW_UID" "/data/data/$PKG" "/data/user_de/0/$PKG" 2>/dev/null
        [ -n "$ADGID" ] && chown -R "$NEW_UID:$ADGID" "/data/media/0/Android/data/$PKG" 2>/dev/null
        [ -n "$AMGID" ] && chown -R "$NEW_UID:$AMGID" "/data/media/0/Android/media/$PKG" 2>/dev/null
        [ -n "$AOGID" ] && chown -R "$NEW_UID:$AOGID" "/data/media/0/Android/obb/$PKG" 2>/dev/null
        DELGMS "$PKG"
    fi
    pm enable "$PKG" >/dev/null 2>&1; rm -rf "$TMP_PKG"
}

select_components() {
    cat <<EOF >&2

--- Select Parts to $2 (for $1 apps) ---
[1] App (Base & Splits)    [4] Media (Android/media)
[2] Data (/data & user_de) [5] OBB (Android/obb)
[3] ExtData (Android/data) [6] Android ID

EOF
    printf "Enter numbers separated by space \n(Leave blank to $2 EVERYTHING):\n   -> " >&2
    read comp_input; echo "${comp_input:-1 2 3 4 5 6}"
}

display_paginated_list() {
    LIST_FILE_ORIGINAL="$1"
    LIST_FILE="$AM_TMP/current_view_list.txt"
    CART_FILE="$AM_TMP/selected_cart.txt"
    cp "$LIST_FILE_ORIGINAL" "$LIST_FILE"
    > "$CART_FILE"
    PAGE=1; PER_PAGE=12
    
    while true; do
        TOTAL_ITEMS=$(wc -l < "$LIST_FILE")
        TOTAL_PAGES=$(( (TOTAL_ITEMS + PER_PAGE - 1) / PER_PAGE ))
        [ "$TOTAL_PAGES" -eq 0 ] && TOTAL_PAGES=1
        CART_COUNT=$(wc -l < "$CART_FILE" 2>/dev/null || echo 0)
        
        banner
        echo "Select Apps (Page $PAGE of $TOTAL_PAGES | Selected: $CART_COUNT)"
        echo "--------------------------------------------------"
        
        sed -n "$(( (PAGE - 1) * PER_PAGE + 1 )),$(( PAGE * PER_PAGE ))p" "$LIST_FILE" | \
        while IFS='|' read -r num label pkg ver; do
            if grep -qx "$num" "$CART_FILE" 2>/dev/null; then
                printf "[✓] %s (%s)\n" "$label" "$pkg"
            else
                printf "[%s] %s (%s)\n" "$num" "$label" "$pkg"
            fi
        done
        
        cat <<EOF
--------------------------------------------------
[n] Next Page      | [p] Previous Page
[s] Search / Clear | [all] Select ALL
[c] Clear Queue    | [d] Done & Proceed
[0] Cancel
EOF
        printf "Enter app numbers or command:\n   -> "
        read user_input
        case "$user_input" in
            n|N) [ "$PAGE" -lt "$TOTAL_PAGES" ] && PAGE=$((PAGE + 1)) ;;
            p|P) [ "$PAGE" -gt 1 ] && PAGE=$((PAGE - 1)) ;;
            c|C) > "$CART_FILE" ;;
            d|D)
                if [ ! -s "$CART_FILE" ]; then
                    echo "[-] Queue is empty! Please select at least one app."
                    sleep 1
                else
                    cp "$CART_FILE" "$AM_TMP/selected_nums.txt"
                    return 0
                fi
                ;;
            all|ALL)
                awk -F'|' '{print $1}' "$LIST_FILE" >> "$CART_FILE"
                sort -u "$CART_FILE" -o "$CART_FILE"
                ;;

                s|S)
                local HISTORY_FILE="$CACHE_DIR/search_history.txt"
                touch "$HISTORY_FILE"
                
                clear
                echo "--------------------------------------------------"
                echo "                  Search Apps"
                echo "--------------------------------------------------"
                if [ -s "$HISTORY_FILE" ]; then
                    echo "Recent Searches:"
                    awk '{print "["NR"] "$0}' "$HISTORY_FILE"
                    echo "--------------------------------------------------"
                fi
                
                printf "Enter search query (or type History Number):\n  -> "
                read sq
                
                if [ -z "$sq" ]; then
                    cp "$LIST_FILE_ORIGINAL" "$LIST_FILE"
                else
                    if echo "$sq" | grep -Eq '^[0-9]+$'; then
                        local hist_term=$(sed -n "${sq}p" "$HISTORY_FILE")
                        [ -n "$hist_term" ] && sq="$hist_term"
                    fi
                    
                    if ! echo "$sq" | grep -Eq '^[0-9]+$' && [ -n "$sq" ]; then
                        grep -v -ixF "$sq" "$HISTORY_FILE" > "${HISTORY_FILE}.tmp" 2>/dev/null
                        echo "$sq" | cat - "${HISTORY_FILE}.tmp" | head -n 5 > "$HISTORY_FILE"
                        rm -f "${HISTORY_FILE}.tmp"
                    fi
                    
                    > "$AM_TMP/search_tmp.txt"
                    for term in $sq; do
                        clean_term=$(echo "$term" | tr '_' ' ')
                        grep -iE "($term|$clean_term)" "$LIST_FILE_ORIGINAL" >> "$AM_TMP/search_tmp.txt"
                    done
                    
                    if [ -s "$AM_TMP/search_tmp.txt" ]; then
                        sort -u "$AM_TMP/search_tmp.txt" > "$LIST_FILE"
                    else
                        echo "[-] No apps found matching '$sq'."
                        sleep 1
                    fi
                    rm -f "$AM_TMP/search_tmp.txt"
                fi
                PAGE=1
                ;;
            0) return 1 ;;
            *)
                valid_entry=0
                for num in $user_input; do
                    case "$num" in
                        ''|*[!0-9]*) continue ;;
                        *)
                            if grep -qx "$num" "$CART_FILE" 2>/dev/null; then
                                grep -vx "$num" "$CART_FILE" > "${CART_FILE}.tmp"
                                mv "${CART_FILE}.tmp" "$CART_FILE"
                            else
                                if grep -q "^${num}|" "$LIST_FILE_ORIGINAL"; then
                                    echo "$num" >> "$CART_FILE"
                                fi
                            fi
                            valid_entry=1
                            ;;
                    esac
                done
                [ "$valid_entry" -eq 0 ] && { echo "[-] Invalid input."; sleep 1; }
                ;;
        esac
    done
}

fetch_apps_list() {
    local TYPE="$1"
    local flag="-s"
    [ "$TYPE" = "User" ] && flag="-3"
    
    local CACHE_FILE="$CACHE_DIR/${TYPE}_apps.txt"
    local CACHE_HASH="$CACHE_DIR/${TYPE}_hash.txt"
    local PKGS_RAW=$(pm list packages -f $flag | sed 's/package://g')
    local CURRENT_HASH=$(echo "$PKGS_RAW" | md5sum | awk '{print $1}')
    
    if [ ! -f "$CACHE_FILE" ] || [ ! -f "$CACHE_HASH" ] || [ "$(cat "$CACHE_HASH" 2>/dev/null)" != "$CURRENT_HASH" ]; then
        echo -e "\n[+] Fetching $TYPE Apps...\nPlease wait."
        mkdir -p "$AM_TMP/fetch"
        
        local FETCH_JOBS=$((JOBS * 3))
        [ "$FETCH_JOBS" -lt 8 ] && FETCH_JOBS=8
        
        while IFS= read -r line; do
            (
                pkg="${line##*=}"; app="${line%=$pkg}"; app="${app%=}"; app="${app#package:}"
                [ -f "$app" ] || exit 0
                
                info=$("$PORYGONZ" dump badging "$app" 2>/dev/null | awk -F"'" '
                    /^application-label:|application: label=/ && !lbl { lbl=$2 }
                    /launchable-activity:/ { lnch=1 }
                    /package: name=/ && !ver { ver=$6 }
                    END { print lbl "|" ver "|" lnch }
                ')
                IFS='|' read -r label ver launchable <<< "$info"
                
                if [ -z "$label" ]; then
                    sys_path=$(dumpsys package "$pkg" 2>/dev/null | awk -F= '/codePath=\/(system|product|vendor|oem|odm)/{print $2; exit}')
                    [ -n "$sys_path" ] && label="${sys_path##*/}" || { dir="${app%/*}"; label="${dir##*/}"; }
                    case "$label" in
                        app|priv-app|system|product|vendor|base|*==*|*-*)
                            label="$(echo "$pkg" | awk -F. '{print toupper(substr($NF,1,1)) substr($NF,2)}')"
                            ;;
                    esac
                fi
                
                label=$(SANITIZE "$label"); [ -z "$label" ] && label="$pkg"
                echo "${label}|${pkg}|${ver}" > "$AM_TMP/fetch/$pkg.txt"
            ) & COOLDOWN "$FETCH_JOBS"
        done <<< "$PKGS_RAW"
        wait
        
        cat "$AM_TMP/fetch/"*.txt | sort -f | awk -F'|' '{print NR"|"$1"|"$2"|"$3}' > "$CACHE_FILE"
        echo "$CURRENT_HASH" > "$CACHE_HASH"
        rm -rf "$AM_TMP/fetch"
    fi
}

am_manage() {
    while true; do
        banner; echo -e "--- Manage Backups ---\n[1] Delete User App Backups\n[2] Delete System App Backups\n[0] Back"
        printf "\nSelect:\n   -> "; read -n 1 m_opt; echo
        case "$m_opt" in
            1|2)
                TYPE="User"; [ "$m_opt" = "2" ] && TYPE="System"
                DIR="$BACKUP_BASE/$TYPE"
                [ ! -d "$DIR" ] || [ -z "$(ls -A "$DIR")" ] && { echo "[-] No $TYPE backups found."; pause_prompt; continue; }
                echo -e "\nBackups in $TYPE:"
                ls -1 "$DIR" > "$AM_TMP/manage_list.txt"
                awk '{print "  ["NR"] "$0}' "$AM_TMP/manage_list.txt"
                printf "\nEnter numbers separated by space\n(or 0 to cancel): "; read d_opts
                [ "$d_opts" = "0" ] || [ -z "$d_opts" ] && continue
                deleted_any=0
                for num in $d_opts; do
                    case "$num" in
                        ''|*[!0-9]*) continue ;; 
                        *) TARGET=$(sed -n "${num}p" "$AM_TMP/manage_list.txt")
                           if [ -n "$TARGET" ] && [ -d "$DIR/$TARGET" ]; then
                               
                               rm -rf "$DIR/$TARGET"
                               
                               if [ -d "$DIR/$TARGET" ]; then
                                   nsenter -t 1 -m rm -rf "$DIR/$TARGET" 2>/dev/null
                               fi
                               if [ -d "$DIR/$TARGET" ]; then
                                   su -mm -c "rm -rf '$DIR/$TARGET'" 2>/dev/null
                               fi
                               
                               # Final Check
                               if [ -d "$DIR/$TARGET" ]; then
                                   echo "   [!] Failed to delete: $TARGET (Storage Permission Issue)"
                               else
                                   echo "   [✓] Deleted: $TARGET"
                                   deleted_any=1
                               fi
                           fi ;;
                    esac
                done
                [ "$deleted_any" -eq 1 ] && sleep 1.5 ;;
            0) return ;;
        esac
    done
}

am_backup() {
    local TYPE="$1"
    mkdir -p "$BACKUP_BASE"
    fetch_apps_list "$TYPE"
    
    if display_paginated_list "$CACHE_DIR/${TYPE}_apps.txt"; then
        SELECTED_NUMS=$(cat "$AM_TMP/selected_nums.txt")
        COMPS=$(select_components "$(echo "$SELECTED_NUMS" | wc -w)" "Backup")
        echo -e "\n[+] Calculating Sizes & Free Space...\nPlease wait."
        mkdir -p "$AM_TMP/precalc"
        
        for num in $SELECTED_NUMS; do
            (
                APP_DATA=$(awk -F'|' -v n="$num" '$1==n' "$CACHE_DIR/${TYPE}_apps.txt"); [ -z "$APP_DATA" ] && exit 0
                IFS='|' read -r _ label pkg ver <<< "$APP_DATA"
                size=0
                CHK 1 && apks=$(pm path "$pkg" 2>/dev/null | sed 's/^package://') && [ -n "$apks" ] && size=$((size + $(RAW_SIZE "$apks") ))
                CHK 2 && size=$((size + $(RAW_SIZE "/data/data/$pkg") + $(RAW_SIZE "/data/user_de/0/$pkg") ))
                CHK 3 && size=$((size + $(RAW_SIZE "/data/media/0/Android/data/$pkg") ))
                CHK 4 && size=$((size + $(RAW_SIZE "/data/media/0/Android/media/$pkg") ))
                CHK 5 && size=$((size + $(RAW_SIZE "/data/media/0/Android/obb/$pkg") ))
                echo "${size}|${label}|${pkg}|${ver}" > "$AM_TMP/precalc/$pkg.txt"
            ) & COOLDOWN "$JOBS"
        done
        wait
        
        cat "$AM_TMP/precalc/"*.txt > "$AM_TMP/selected_apps_sizes.txt"; rm -rf "$AM_TMP/precalc"
        TOTAL_KB=$(awk -F'|' '{s+=$1} END{print s+0}' "$AM_TMP/selected_apps_sizes.txt")
        FREE_KB=$(df -k "$BACKUP_BASE" | awk 'NR==2 {print $4}')
        
        if [ "$TOTAL_KB" -gt "$FREE_KB" ]; then
            echo -e "[-] Error: Insufficient storage!\nRequired: $(FORMAT_SIZE "$TOTAL_KB") | Free: $(FORMAT_SIZE "$FREE_KB")"
            pause_prompt; return
        fi
        
        sort -t'|' -k1 -n -r "$AM_TMP/selected_apps_sizes.txt" > "$AM_TMP/selected_apps_sorted.txt"
        START=$(date +%s); TOTAL_APPS=$(wc -l < "$AM_TMP/selected_apps_sorted.txt"); CURRENT_APP=0
        
        while IFS='|' read -r size label pkg ver; do
            CURRENT_APP=$((CURRENT_APP + 1))
            DO_BACKUP "$pkg" "$label" "$ver" "$COMPS" "$TYPE" "$CURRENT_APP" "$TOTAL_APPS" "$((CURRENT_APP * 100 / TOTAL_APPS))" "$size"
        done < "$AM_TMP/selected_apps_sorted.txt"
        wait
        
        MIN=$((( $(date +%s) - START ) / 60)); SEC=$((( $(date +%s) - START ) % 60))
        local time_str=""
        [ "$MIN" -gt 0 ] && time_str="${MIN}m ${SEC}s" || time_str="${SEC}s"
        
        echo -e "\n[+] Backup Process Completed!"
        echo "📦 Total Data Backed Up: $(FORMAT_SIZE "$TOTAL_KB")"
        echo "⏱️ Took: $time_str"
        notify "Data Migrator" "Backup Completed!\nTotal: $(FORMAT_SIZE "$TOTAL_KB") | Took: $time_str"
        pause_prompt
    fi
}

am_restore() {
    DIR="$BACKUP_BASE/$1"
    [ ! -d "$DIR" ] || [ -z "$(ls -A "$DIR")" ] && { echo -e "\n[-] No $1 backups found in $DIR"; pause_prompt; return; }
    
    ADGID=$(stat -c '%g' "/data/media/0/Android/data" 2>/dev/null); ADGID=${ADGID:-1077}
    AMGID=$(stat -c '%g' "/data/media/0/Android/media" 2>/dev/null); AMGID=${AMGID:-1023}
    AOGID=$(stat -c '%g' "/data/media/0/Android/obb" 2>/dev/null); AOGID=${AOGID:-1079}

    > "$AM_TMP/backup_list.txt"; count=1
    for app in "$DIR"/*; do
        [ -d "$app" ] && {
            pkg=$(grep "^Package=" "$app/Meta.txt" | cut -d= -f2)
            size=$(grep "^TotalSize=" "$app/Meta.txt" | cut -d= -f2)
            echo "$count|$(basename "$app")|${pkg}|${size:-0}" >> "$AM_TMP/backup_list.txt"
            count=$((count + 1))
        }
    done
    
    if display_paginated_list "$AM_TMP/backup_list.txt"; then
        SELECTED_NUMS=$(cat "$AM_TMP/selected_nums.txt")
        COMPS=$(select_components "$(echo "$SELECTED_NUMS" | wc -w)" "Restore")
        echo -e "\n[+] Sorting by Size...\nPlease wait."
        > "$AM_TMP/selected_restores.txt"
        
        for num in $SELECTED_NUMS; do
            APP_DATA=$(awk -F'|' -v n="$num" '$1==n' "$AM_TMP/backup_list.txt"); [ -z "$APP_DATA" ] && continue
            IFS='|' read -r _ label _ size <<< "$APP_DATA"; echo "${size}|${label}" >> "$AM_TMP/selected_restores.txt"
        done

        sort -t'|' -k1 -n -r "$AM_TMP/selected_restores.txt" > "$AM_TMP/selected_restores_sorted.txt"
        TOTAL_KB=$(awk -F'|' '{s+=$1} END{print s+0}' "$AM_TMP/selected_restores_sorted.txt")
        START=$(date +%s); TOTAL_APPS=$(wc -l < "$AM_TMP/selected_restores_sorted.txt"); CURRENT_APP=0
        settings put global verifier_verify_adb_installs 0
        
        while IFS='|' read -r size label; do
            CURRENT_APP=$((CURRENT_APP + 1))
            DO_RESTORE "$label" "$1" "$CURRENT_APP" "$TOTAL_APPS" "$((CURRENT_APP * 100 / TOTAL_APPS))" "$size" "$COMPS"
        done < "$AM_TMP/selected_restores_sorted.txt"
        
        settings put global verifier_verify_adb_installs 1
        MIN=$((( $(date +%s) - START ) / 60)); SEC=$((( $(date +%s) - START ) % 60))
        local time_str=""
        [ "$MIN" -gt 0 ] && time_str="${MIN}m ${SEC}s" || time_str="${SEC}s"
        
        echo -e "\n[+] Restore Process Completed!"
        echo "📦 Total Data Restored: $(FORMAT_SIZE "$TOTAL_KB")"
        echo "⏱️ Took: $time_str"
        notify "Data Migrator" "Restore Completed!\nTotal: $(FORMAT_SIZE "$TOTAL_KB") | Took: $time_str"
        pause_prompt
    fi
}

apps_manager_menu() {
    mkdir -p "$AM_TMP"
    while true; do
        banner
        echo "--- Data & Apps Migrator ---"
        echo "[1] Backup User Apps"
        echo "[2] Restore User Apps"
        echo "[3] Backup System Apps"
        echo "[4] Restore System Apps"
        echo "[5] Manage Backups"
        echo "[0] Back to Main Menu"
        printf "\nSelect an option:\n   -> "; read -n 1 am_opt; echo
        
        case "$am_opt" in
            1) echo "[+] Loading User Apps..."; am_backup "User" ;;
            2) echo "[+] Loading User Backups..."; am_restore "User" ;;
            3) echo "[+] Loading System Apps..."; am_backup "System" ;;
            4) echo "[+] Loading System Backups..."; am_restore "System" ;;
            5) am_manage ;;
            0) return ;;
            *) echo "[-] Invalid option."; sleep 1 ;;
        esac
    done
}

# Extras & Utilities
do_debloat_apps() {
    echo -e "\n[+] Loading Debloatable Apps\n(User + System)..."
    fetch_apps_list "User"
    fetch_apps_list "System"
    
    DEBLOAT_LIST="$AM_TMP/debloat_combined.txt"
    cat "$CACHE_DIR/User_apps.txt" "$CACHE_DIR/System_apps.txt" 2>/dev/null > "$DEBLOAT_LIST"
    
    sort -u -t'|' -k3 "$DEBLOAT_LIST" | awk -F'|' '{print NR"|"$2"|"$3"|"$4}' > "$AM_TMP/debloat_final.txt"
    
    if display_paginated_list "$AM_TMP/debloat_final.txt"; then
        SELECTED_NUMS=$(cat "$AM_TMP/selected_nums.txt")
        echo -e "\n[+] Debloating selected apps for User 0..."
        for num in $SELECTED_NUMS; do
            APP_DATA=$(awk -F'|' -v n="$num" '$1==n' "$AM_TMP/debloat_final.txt")
            IFS='|' read -r _ label pkg ver <<< "$APP_DATA"
            if [ -n "$pkg" ]; then
                pm uninstall --user 0 "$pkg" >/dev/null 2>&1
                echo "   [✓] Debloated: $label ($pkg)"
            fi
        done
        echo -e "\n[+] Debloat Process Completed!"
        pause_prompt
    fi
}

do_restore_debloated() {
    echo -e "\n[+] Fetching Debloated Packages..."
    RESTORE_LIST="$AM_TMP/debloated_packages.txt"
    > "$RESTORE_LIST"
    
    local ALL_PKG="$AM_TMP/all_pkg.txt"
    pm list packages | sed 's/package://g' > "$ALL_PKG"
    
    count=1
    for pkg in $(pm list packages -u | sed 's/package://g'); do
        if ! grep -qx "$pkg" "$ALL_PKG"; then
            label=$(awk -F'|' -v p="$pkg" '$3==p {print $2; exit}' "$CACHE_DIR"/*_apps.txt 2>/dev/null)
            if [ -z "$label" ]; then
                label="$(echo "$pkg" | awk -F. '{print toupper(substr($NF,1,1)) substr($NF,2)}')"
            fi
            echo "$count|$label|$pkg|Debloated" >> "$RESTORE_LIST"
            count=$((count + 1))
        fi
    done
    
    if [ ! -s "$RESTORE_LIST" ]; then
        echo "[-] No debloated apps found!"
        pause_prompt; return
    fi
    
    if display_paginated_list "$RESTORE_LIST"; then
        SELECTED_NUMS=$(cat "$AM_TMP/selected_nums.txt")
        echo -e "\n[+] Restoring selected debloated apps..."
        for num in $SELECTED_NUMS; do
            APP_DATA=$(awk -F'|' -v n="$num" '$1==n' "$RESTORE_LIST")
            IFS='|' read -r _ label pkg ver <<< "$APP_DATA"
            if [ -n "$pkg" ]; then
                cmd package install-existing "$pkg" >/dev/null 2>&1
                echo "   [✓] Restored: $label ($pkg)"
            fi
        done
        echo -e "\n[+] Restore Process Completed!"
        pause_prompt
    fi
}

do_systemize_app() {
    echo -e "\n[+] Loading User Apps..."
    fetch_apps_list "User"
    
    if display_paginated_list "$CACHE_DIR/User_apps.txt"; then
        SELECTED_NUMS=$(cat "$AM_TMP/selected_nums.txt")
        echo -e "\n[+] Preparing to Systemize selected apps..."
        
        mount -o rw,remount / >/dev/null 2>&1
        mount -o rw,remount /system >/dev/null 2>&1
        mount -o rw,remount /product >/dev/null 2>&1
        
        local SYS_DIR="/system/product/app"
        [ ! -d "$SYS_DIR" ] && SYS_DIR="/system/app"
        
        local FREE_KB=$(df -k "$SYS_DIR" | awk 'NR==2 {print $4}')
        local apps_processed=0
        
        for num in $SELECTED_NUMS; do
            APP_DATA=$(awk -F'|' -v n="$num" '$1==n' "$CACHE_DIR/User_apps.txt")
            IFS='|' read -r _ label pkg ver <<< "$APP_DATA"
            [ -z "$pkg" ] && continue
            
            APK_PATHS=$(pm path "$pkg" | sed 's/^package://')
            if [ -z "$APK_PATHS" ]; then
                continue
            fi
            
            APP_DIR_SRC=$(dirname "$(echo "$APK_PATHS" | head -n 1)")
            REQ_KB=$(RAW_SIZE "$APP_DIR_SRC")
            
            if [ "$REQ_KB" -gt "$FREE_KB" ]; then
                echo -e "\n[+] Not enough space in system for $label!\nRequired: $(FORMAT_SIZE "$REQ_KB") | Free: $(FORMAT_SIZE "$FREE_KB")"
                continue
            fi
            
            APP_FOLDER_NAME=$(echo "$label" | sed 's/[^a-zA-Z0-9]/_/g')
            TARGET_DIR="$SYS_DIR/$APP_FOLDER_NAME"
            
            mkdir -p "$TARGET_DIR" 2>/dev/null
            if [ ! -d "$TARGET_DIR" ]; then
                echo "[-] Failed to mount /system as read-write!"
                break
            fi
            
            cp -rf "$APP_DIR_SRC/"* "$TARGET_DIR/" 2>/dev/null
            
            find "$TARGET_DIR" -type d -exec chmod 755 {} \;
            find "$TARGET_DIR" -type f -exec chmod 644 {} \;
            chown -R root:root "$TARGET_DIR"
            
            FREE_KB=$((FREE_KB - REQ_KB))
            apps_processed=$((apps_processed + 1))
        done
        
        if [ "$apps_processed" -gt 0 ]; then
            echo -e "\n[+] App systemized successfully.\nReboot to system to apply changes."
        fi
        pause_prompt
    fi
}

do_cache_dexopt() {
    banner
    echo "--- Runtime Optimization Engine ---"
    echo -e "This wipes runtime cache and\nruns AOT DexOptimization (speed-profile)\nto improve UI fluidity and app launch speeds."
    printf "\nProceed? (y/n): "; read -n 1 confirm; echo
    
    if [ "$confirm" = "y" ] || [ "$confirm" = "Y" ]; then
        echo -e "\n[+] Clearing Package Cache & Dalvik Caches..."
        rm -rf /data/system/package_cache/* /data/dalvik-cache/* 2>/dev/null
        echo "[+] Cache purged successfully!"
        
        echo -e "\n[+] Running Ahead-Of-Time (AOT) DexOptimization..."
        notify "ROM Shifter" "Running AOT DexOptimization in background..."
        
        pm compile -a -m speed-profile >/dev/null 2>&1 &
        echo "[+] Optimization job scheduled in background!"
        notify "ROM Shifter" "DexOptimization Scheduled Successfully!"
    else
        echo "[-] Aborted."
    fi
    pause_prompt
}

extras_menu() {
    mkdir -p "$AM_TMP"
    while true; do
        banner
        echo "--- Extras & Optimization ---"
        echo "[1] Debloat Apps (User & System)"
        echo "[2] Restore Debloated Apps"
        echo "[3] Make User App a System App"
        echo "[4] Wipe Cache & Run DexOptimization"
        echo "[0] Back to Main Menu"
        printf "\nSelect an option:\n   -> "; read -n 1 ext_opt; echo
        
        case "$ext_opt" in
            1) do_debloat_apps ;;
            2) do_restore_debloated ;;
            3) do_systemize_app ;;
            4) do_cache_dexopt ;;
            0) return ;;
            *) echo "[-] Invalid option."; sleep 1 ;;
        esac
    done
}

maintenance_menu() {
    while true; do
        banner
        echo "--- Settings & Utilities ---"
        echo "[1] Clear Caches"
        echo "[2] Change Main Folder Path"
        echo "[3] Stop Auto-Flash Daemon"
        local current_folder_name=$(basename "$MAIN_DIR")
        echo "[4] Rename Main Folder (Current: $current_folder_name)"
        echo "[0] Back to Main Menu"
        printf "\nSelect an option:\n   -> "; read -n 1 m_opt; echo
        
        case "$m_opt" in
            1) 
               rm -rf "$CACHE_DIR" && mkdir -p "$CACHE_DIR"
               echo -e "\n[+] Caches cleared!\nApps may take slightly longer to display on next load."
               pause_prompt
               ;;
            2)
               echo -e "\nCurrent Folder: $MAIN_DIR"
               echo "Opening Directory Browser..."
               sleep 1
               
               new_path=$(select_directory "/sdcard")
               if [ "$new_path" = "CANCELLED" ]; then
                   echo -e "\n[-] Action cancelled."
                   pause_prompt
                   continue
               fi
               
               local current_name=$(basename "$MAIN_DIR")
               new_path="${new_path}/${current_name}"
               new_path=$(echo "$new_path" | sed 's/\/\//\//g')
               
               if [ "$new_path" != "$MAIN_DIR" ]; then
                   echo -e "\n[+] Moving data to $new_path...\nPlease wait."
                   mkdir -p "$new_path"
                   mv "$MAIN_DIR/"* "$new_path/" 2>/dev/null
                   rm -rf "$MAIN_DIR"
                   MAIN_DIR="$new_path"
                   
                   FLASH="$MAIN_DIR/Auto-Flash"
                   BACKUP_BASE="$MAIN_DIR/Data-Migrated"
                   LP_DIR="$MAIN_DIR/Live-Partition"
                   
                   echo "MAIN_DIR=\"$MAIN_DIR\"" > "$CONFIG_FILE"
                   echo -e "[+] Folder successfully moved to:\n$MAIN_DIR"
               else
                   echo -e "\n[-] The folder is already set to $MAIN_DIR."
               fi
               pause_prompt
               ;;
            3)
               if [ -f "/data/local/tmp/autoflash.pid" ]; then
                   OLD_PID=$(cat "/data/local/tmp/autoflash.pid" 2>/dev/null)
                   if [ -n "$OLD_PID" ] && kill -0 "$OLD_PID" 2>/dev/null; then
                       kill -9 "$OLD_PID" 2>/dev/null
                       echo -e "[+] Stopped daemon\n(Process halted for recovery flashing)."
                   else
                       echo "[-] Auto-Flash Daemon isn't running."
                   fi
                   rm -f "/data/local/tmp/autoflash.pid"
               else
                   echo "[-] Auto-Flash Daemon isn't running."
               fi
               pause_prompt
               ;;
            4)
               local old_name=$(basename "$MAIN_DIR")
               local parent_dir=$(dirname "$MAIN_DIR")
               echo -e "\nCurrent Folder Name: $old_name"
               printf "Enter NEW folder name (No spaces allowed):\n   -> "
               read new_name
               
               if [ -z "$new_name" ] || [ "$new_name" = "$old_name" ]; then
                   echo "[-] Name unchanged or empty."
                   pause_prompt
                   continue
               fi
               
               new_name=$(echo "$new_name" | sed 's/[^a-zA-Z0-9#_-]/_/g')
               local new_full_path="$parent_dir/$new_name"
               
               if [ -e "$new_full_path" ]; then
                   echo "[-] A folder with this name already exists in $parent_dir!"
               else
                   echo -e "\n[+] Renaming to $new_name...\nPlease wait."
                   mv "$MAIN_DIR" "$new_full_path" 2>/dev/null
                   if [ $? -eq 0 ]; then
                       MAIN_DIR="$new_full_path"
                       FLASH="$MAIN_DIR/Auto-Flash"
                       BACKUP_BASE="$MAIN_DIR/Data-Migrated"
                       LP_DIR="$MAIN_DIR/Live-Partition"
                       
                       echo "MAIN_DIR=\"$MAIN_DIR\"" > "$CONFIG_FILE"
                       echo "[+] Successfully renamed!"
                   else
                       echo "[-] Failed to rename! Check storage permissions."
                   fi
               fi
               pause_prompt
               ;;
            0) return ;;
            *) echo "[-] Invalid option." ; sleep 1 ;;
        esac
    done
}

# Main Menu
ensure_root
init_shifter

if [ "$1" = "__monitor" ]; then
    af_monitor "$2"
    exit 0
fi

while true; do
    promo_check
    banner
    echo "--- Main Menu ---"
    echo "[1] Auto-Flash in Recovery"
    echo "[2] Apps & Data Migrator"
    echo "[3] Live Partition Manager"
    echo "[4] Extras & Utilities"
    echo "[5] Settings & Configs"
    echo "[0] Exit"
    printf "\nSelect an option:\n   -> "; read -n 1 main_opt; echo
    
    case "$main_opt" in
        1) setup_daemon ;;
        2) apps_manager_menu ;;
        3) live_manager ;;
        4) extras_menu ;;
        5) maintenance_menu ;;
        0) exit 0 ;;
        *) echo "[-] Invalid option." ; sleep 1 ;;
    esac
done
