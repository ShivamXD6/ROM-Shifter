# ⚡ ROM Shifter
**The Ultimate All-in-One Custom ROM Migration & Flashing Utility by [@BuildBytes](https://telegram.me/BuildBytes)**

[![Downloads](https://img.shields.io/github/downloads/ShivamXD6/ROM-Shifter/total?color=green&style=for-the-badge)](https://github.com/ShivamXD6/ROM-Shifter/releases/latest)
[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android)](#)
[![Shell](https://img.shields.io/badge/Language-Shell-4EAA25?style=for-the-badge&logo=gnu-bash)](#)
[![Root](https://img.shields.io/badge/Requires-Root-red?style=for-the-badge)](#)

**ROM Shifter** is a highly advanced, pure shell-based utility designed to make Android custom ROM migrations painless, fast, and completely automated. Whether you want to backup apps with their exact states, flash multiple ZIPs automatically in a broken recovery, or manage live partitions, ROM Shifter does it all without needing an internet connection.

### 📖 [Read the Detailed Documentation Here!](docs.md)
*Check out the documentation for a deep dive into every feature and video tutorials!*

---

## ✨ Key Features

### 📦 Faster & Lighter App Migrator (Beats Swift Backup)
Powered by [DataBackup](https://github.com/ShivamXD6/Data-Backup) Module, ROM Shifter achieves mind-blowing speeds and better compression than premium alternatives.
*   **Insane Speed (≈3× Faster):** Backups reach up to **~811.42 MB in just 10 seconds** (Internal Storage, 1 App) and **~509 MB in 34 seconds** (SD Card, 2 Apps).
*   **Superior Compression:** Unlike Swift Backup, it compresses APK + Splits alongside the data. This yields sizes **~3% smaller for a single app** and **~15%+ smaller for bulk backups**.
*   **Comprehensive Selection:** Gives you granular control to backup App (Base/Splits), Data, ExtData (`Android/data`), Media, OBB, and Android ID (SSAID) in one single go. Also supports **system apps**.
*   **Smart Restore Logic:** Automatically grants necessary runtime permissions, restores original UID/GID, and clears GMS tracking to prevent crash loops upon booting.
*   **Zero Installation:** Operates entirely as a script. It leaves no trace in your app drawer and auto-removes its background caches once operations are complete.

### 🤖 Smart Auto-Flasher (The Broken Recovery Savior)
Drop your ZIPs into the Auto-Flash folder, and the background daemon handles the rest. 
*   **The Ultimate Use-Case:** Got a custom recovery with broken touch and no PC/Laptop nearby? ROM Shifter's Auto-Flasher will sequentially flash your ROM, GApps, and Magisk automatically.
*   **Intelligent Auto-Sorting:** Automatically sorts your files in the safest flashing order: Firmware > ROM > GApps > Addons > Kernel > Magisk.
*   **Advanced Wipe Modes:** Allows you to seamlessly choose between Dirty Flash (Wipes Dalvik/Cache), Clean Flash (Dirty + Wipes System/Data), or Format Data (Wipe Everything).
*   **Duplicate Handling:** If multiple ZIPs of the same category exist, it safely ignores or moves the older ones out of the active flashing queue.
*   **Security Failsafes:** Strictly ensures your lock screen (PIN/Password) is removed before rebooting to prevent FRP in new rom or nasty decryption lockouts in recovery.

### 💾 Live Partition Manager (Unbrick on the Fly)
Backup and restore your device's core partitions while the system is running, completely bypassing the need for a PC.
*   **Save Your Device:** If your custom recovery dies or your bootloader (`abl`) gets corrupted, you can safely flash a working `.img` directly from the OS.
*   **Smart Slot Detection:** Automatically detects Active/Inactive slots for A/B dynamic devices (e.g., `boot_a`, `boot_b`) and highlights your current working slot.
*   **Standard & Custom Targets:** Features 1-click options for `boot` and `recovery`, while allowing you to manually type and flash any custom partition like `modem`, `splash`, or `dtbo`.
*   **Strict Verification:** Verifies the exact block path before attempting any read/write operations to prevent accidental soft-bricks.
*   **Raw Image Dumps:** Easily backup your currently working partitions to raw, safe `.img` files before testing a new kernel or recovery.

### 🛠️ Extras & System Optimization
A collection of powerful tools to optimize and modify your newly flashed Android system instantly.
*   **Debloat & Setup:** Flashed a new ROM filled with useless stock apps? Safely debloat unwanted System or User apps for User 0 in seconds.
*   **Safe Restoration:** Keeps track of debloated packages so you can easily restore them later with a single click.
*   **Systemize App (GCam Example):** If your current ROM's default camera is poor, easily convert your favorite GCam into a System App.
*   **AOT DexOptimization:** Purges Dalvik caches and forces a heavy Ahead-Of-Time (`speed-profile`) compilation in the background.
*   **Instant Fluidity:** Specifically designed to be run right after a dirty flash to eliminate lag and make your UI buttery smooth immediately.

### 📴 100% Offline, Portable & Open-Source
*   **Ultra-Lightweight:** Everything the script needs is built right into a single ~2.3 MB file.
*   **Self-Extracting:** Uses Base64-encoded strings to extract dependencies natively without pinging any external servers.
*   **No Internet Required:** Operates entirely locally. No unexpected `curl` downloads or "missing file" errors when you have no network.
*   **Clean Workspace:** Creates a highly organized, user-defined workspace folder (Default: `/sdcard/#Shifter/`) for easy manual file management.

---

## 📸 Terminal UI & Options Gallery

<div align="center">
  <p>
    <img width="280" alt="Main Folder Selection" src="https://github.com/user-attachments/assets/450b3b8b-e5d3-4e57-ac3b-ef530ae95295" />
    <img width="280" alt="Main Menu" src="https://github.com/user-attachments/assets/284b7901-ecf5-4b85-a3d6-731d3dbef550" />
    <img width="280" alt="Auto Flash Zip in Recovery" src="https://github.com/user-attachments/assets/63edd758-b33f-41cb-b462-9f653a886805" />
  </p>
  <p>
    <img width="280" alt="Apps and Data Migrator" src="https://github.com/user-attachments/assets/c07421ed-d960-4f5f-bdaa-3c48b50b4d26" />
    <img width="280" alt="Live Partition Manager" src="https://github.com/user-attachments/assets/2725cc6e-0346-4a93-9b63-9a07b7218b02" />
    <img width="280" alt="Extras and Optimization" src="https://github.com/user-attachments/assets/87e5f9da-b66b-41c6-b722-cf289c84f1b4" />
  </p>
</div>

---

## 📊 Real-World Benchmarks (Data and Apps Migrator)

<div align="center">
  <h4>Compression Size Comparison</h4>
  <p>
    <img width="280" alt="Data Backup" src="https://github.com/user-attachments/assets/0a45f371-0328-4111-86c3-c137ec92357d" />
    <img width="280" alt="Neo Backup" src="https://github.com/user-attachments/assets/11a72465-4592-424d-a7d1-df9ee9140aab" />
    <img width="280" alt="Swift Backup" src="https://github.com/user-attachments/assets/30e061ba-869d-4abe-9570-fd94668eb9ca" />
  </p>

  <h4>Backup Speed Benchmarks (Internal vs SD Card)</h4>
  <p>
    <img width="280" alt="Internal Speed" src="https://github.com/user-attachments/assets/44b4e3b1-a6d3-4c90-8b80-2345f757ef80" />
    <img width="280" alt="SD Card Speed" src="https://github.com/user-attachments/assets/85e99bf7-fd48-4061-b6d6-649cdd84b807" />
  </p>
</div>

---

## 🚀 How to Use

### Prerequisites
*   A Rooted Android Device (Magisk / KernelSU / APatch).
*   A Terminal Emulator like **MT Manager** (Recommended) or **Termux**.

### Installation & Execution
1. Download the latest `ROM-Shifter.sh` script from the [Releases](#) tab.
2. Open your Terminal, Termux or MT Manager and grant Superuser access:
   ```bash
   su
   ```
3. Execute the script:
   ```bash
   sh /sdcard/Download/ROM-Shifter.sh
   ```
   OR, Just use script executor of MT Manager.
4. On the first run, the Interactive Directory Browser will ask you to select your main working folder. Setup your workspace and enjoy!

---

## 🙏 Support & Donations

If you find ROM Shifter helpful and want to keep it alive, you can donate here:

💰 **PayPal:** [Donate via PayPal](https://paypal.me/ShivamXD6)

📲 **SuperMoney:** UPI ID - **shivam.dhage@superyes**

🔗 **GPay UPI QR Code:** [Donate via UPI QR](https://i.ibb.co/5g4J2RXR/1f38d6d7-a8a2-4696-88e6-9cf503e0592c.png)

Every contribution helps keep the project alive and improved! Thank you! 😊

## 💬 Community

Loved the script? Encountered a bug? Or just want to hang out with fellow Android enthusiasts?

*   💡 **Join our Telegram Channel:** [BuildBytes](https://telegram.me/BuildBytes)
*   🐞 **Report Bugs:** Open an issue on this repository or drop a message in our Telegram group.

<div align="center">
  <sub>Built with ❤️ by ShivamXD6 for the Android Custom ROM Community.</sub>
</div>
