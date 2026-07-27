# 📚 ROM Shifter - Ultimate Documentation & User Guide

Welcome to the comprehensive manual for **ROM Shifter**. This document breaks down the internal workings of every module, explains each option in detail, and provides pro tips to help you maximize efficiency and safety.

---

## 📑 Table of Contents
1. [Initial Setup & Workspace](#1-initial-setup--workspace)
2. [📦 Apps & Data Migrator](#2--apps--data-migrator)
3. [🤖 Smart Auto-Flasher](#3--smart-auto-flasher)
4. [💾 Live Partition Manager](#4--live-partition-manager)
5. [🛠️ System Utilities & Extras](#5-️-system-utilities--extras)
6. [⚙️ Settings & Maintenance](#6-️-settings--maintenance)
7. [💡 Pro Tips & Best Practices](#7--pro-tips--best-practices)

---

## 1. Initial Setup & Workspace

When you run ROM Shifter for the first time, it initializes an isolated workspace. It uses a custom **POSIX-compliant Mini File Explorer** to navigate directories directly within the terminal.

*   **How it Works:** The script reads your storage tree and pages it (12 items per screen) to prevent terminal lag. It restricts the workspace creation to safe paths like `/sdcard`, `/storage`, or `/data/media` to prevent accidental OS corruption.
*   **Workspace Structure:** Once a path is selected, it creates a `#Shifter` folder containing three sub-directories: `Auto-Flash/`, `Data-Migrated/`, and `Live-Partition/`.

> **💡 Tip:** Instead of navigating through `/storage/emulated/0`, simply press `[u]` to go up and select `/sdcard` for immediate access to your internal storage, or use the `[m] Enter Path Manually` option to paste a direct path.

---

## 2. 📦 Apps & Data Migrator

The crown jewel of ROM Shifter. This module achieves mind-blowing speeds (~811 MB in 10s) using custom compiled `porygonz` and `zapdos` binaries for aggressive, multi-threaded compression and extraction.

### Options Available
When you select an app, you can choose exactly which parts to back up/restore:
*   **#App:** Backs up the Base APK and all associated Split APKs.
*   **#Data:** Backs up standard app data (`/data/data/` and `/data/user_de/`).
*   **#ExtData:** Backs up external data (`/sdcard/Android/data/`).
*   **#Media:** Backs up app-specific media (`/sdcard/Android/media/`).
*   **#OBB:** Backs up game assets (`/sdcard/Android/obb/`).
*   **#Android ID:** Backs up the unique SSAID (Secure Settings Android ID) of the app. 

### How the Restore Engine Works
When restoring, ROM Shifter doesn't just extract files; it "heals" the app:
1.  **Installs APKs:** Installs the base and splits using the native Android package manager.
2.  **Restores Data:** Extracts data and immediately restores the correct UID/GID ownership so Android recognizes the files.
3.  **Permission Granting:** Automatically grants all required runtime permissions and AppOps (e.g., location, storage, camera).
4.  **GMS Fix:** Deletes Google Play Services tracking data for that specific app to prevent instant crashes upon launch.

> **💡 Pro Tips:**
> *   **Speed up Backups:** Only select `#OBB` and `#ExtData` for large games (like BGMI/Genshin). Normal apps like WhatsApp or Instagram only need `#App` and `#Data`.
> *   **Fixing Login Issues:** Always check `#Android ID` when backing up banking apps or social media accounts. This tricks the app into thinking it was never uninstalled, often bypassing 2FA requirements.

---

## 3. 🤖 Smart Auto-Flasher

A lifesaver for devices with broken touch recoveries or for users who hate manually queuing ZIPs in TWRP/OrangeFox.

### How it Works
It utilizes a lightweight background daemon (occupying <4MB RAM and ~0.1% CPU). The daemon monitors the `Auto-Flash/` directory. When triggered, it analyzes the ZIP names, automatically writes an `openrecoveryscript` (ORS) command list in the cache, and reboots the device into recovery. The recovery reads the ORS and flashes everything automatically.

### Flashing Modes
*   **[1] Dirty Flash:** Wipes Dalvik and Cache only. Ideal for updating the same ROM to a newer build.
*   **[2] Clean Flash:** Wipes Dalvik, Cache, System, and Data. Ideal for switching from one Custom ROM to another.
*   **[3] Format Data:** Erases everything including Internal Storage (Metadata/Data). Ideal for switching from MIUI/HyperOS to AOSP, or dealing with encryption issues.

> **💡 Pro Tips:**
> *   **The Trigger File:** After setting up your flash, the script creates a `DELETE TO FLASH.txt` file in your root folder. Simply delete this file using your regular File Manager to trigger the automated reboot and flash!
> *   **Auto-Sorting Override:** While the script is smart enough to sort files automatically, you can guarantee a specific order by prefixing numbers to your files (e.g., `1-Firmware.zip`, `2-ROM.zip`, `3-GApps.zip`).
> *   **CRITICAL:** ALWAYS remove your PIN/Password/Pattern before triggering a flash to prevent FRP lock or decryption failures in recovery.

### Video Demonstration

https://github.com/user-attachments/assets/2c2907af-a701-4a03-b035-3566c4f68938

---

## 4. 💾 Live Partition Manager

This module manipulates raw block devices (`/dev/block/by-name/`) directly from the booted OS.

### Options & Workings
*   **A/B Slot Auto-Detection:** Reads the `ro.boot.slot_suffix` property. If you are on an A/B device, it explicitly asks which slot to target (`_a` or `_b`), highlighting the active one.
*   **Backup (Dump):** Uses the `dd` command to create a raw, bit-for-bit `.img` copy of the selected partition into your workspace.
*   **Restore (Flash):** Flashes a selected `.img` file directly to the partition block. 

> **💡 Pro Tips:**
> *   **The Unbrick Method:** If your recovery is dead or touch is broken, you can boot the OS, open ROM Shifter, and manually flash a working `recovery.img` to the recovery partition.
> *   **Kernel Testing:** Before flashing a new custom Kernel (boot image), ALWAYS dump your current working `boot` partition. If the new kernel causes a bootloop, you can flash the dump back via fastboot.
> *   **Custom Targets:** You aren't limited to Boot and Recovery. By selecting `Custom`, you can type `vendor`, `dtbo`, `vbmeta`, or `system` to modify advanced partitions.

---

## 5. 🛠️ System Utilities & Extras

Tools designed to be run immediately after a clean flash to finalize your setup.

*   **App Debloater:** Uses `pm uninstall -k --user 0 <package>` to hide bloatware from the current user without actually deleting system files. This prevents system partition corruption and allows easy restoration.
*   **Systemizer:** Moves a user-installed APK from `/data/app/` to `/system/product/app/`. *Note: Requires a ROM with R/W (Read/Write) access to the system partition.*
*   **AOT DexOptimization:** Android usually compiles apps in the background while the phone is idle and charging. ROM Shifter forces this compilation (`pm compile -a -m speed-profile`) immediately, clearing out jitter, lag, and UI stutters right after a fresh boot.

> **💡 Pro Tips:**
> *   **The GCam Trick:** If your ROM's default camera is terrible, install your favorite GCam APK, Systemize it using ROM Shifter, and then debloat the stock camera.
> *   **Battery Drain Fix:** Run the **AOT DexOptimization** once after every Dirty Flash or App restoration spree. It will consume heavy CPU for about 5-10 minutes, but it drastically improves battery life and fluidity afterward.

---

## 6. ⚙️ Settings & Maintenance

Manage the script's behavior and footprint.

*   **Change Main Folder Path:** Safely moves all your backups and ZIPs to a new location (e.g., from Internal Storage to an SD Card) and updates the script's configuration file.
*   **Rename Main Folder:** Don't like the name `#Shifter`? Change it to `#MyRomData` or anything else without breaking the internal pathing logic.
*   **Clear Caches:** Deletes the `dir_list.txt` and temporary package lists. Use this if the app list is showing uninstalled apps or if you want to free up space.
*   **Stop Auto-Flash Daemon:** Finds the exact PID (Process ID) of the background monitoring daemon and kills it safely without affecting your system.

---

## 7. 💡 Pro Tips & Best Practices

1.  **Keep It Clean:** Never edit the `ROM-Shifter.sh` file with a standard text editor if you don't understand Base64 payloads. The core binaries are embedded at the very top of the script for maximum offline portability.
2.  **Terminal Choice:** While Termux works great, **MT Manager's Terminal** is highly recommended for running this script as it handles root namespaces (`su -mm`) and character encoding perfectly.
3.  **Storage Access:** If ROM Shifter fails to create folders or shows "Permission Denied," ensure your terminal app has "All Files Access" granted in Android Settings.
