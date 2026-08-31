<div align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp" width="128" height="128" alt="ROM Shifter Icon"/>
  <h1>⚡ROM Shifter App</h1>
  <p><b>A unified, lightweight toolkit for all your ROM shifting and root-related tasks.</b></p>

[![Downloads](https://img.shields.io/github/downloads/ShivamXD6/ROM-Shifter/total?color=green&style=for-the-badge)](../../releases)
[![Release](https://img.shields.io/github/v/release/ShivamXD6/ROM-Shifter?style=for-the-badge)](../../releases)
[![Join Build Bytes](https://img.shields.io/badge/Join-Build%20Bytes-2CA5E0?style=for-the-badge&logo=telegram)](https://telegram.me/BuildBytes)
[![Join Chat](https://img.shields.io/badge/Join%20Chat-Build%20Bytes%20Discussion-2CA5E0?style=for-the-badge&logo=telegram)](https://telegram.me/BuildBytesDiscussion)
![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Root](https://img.shields.io/badge/Root-ff0000?style=for-the-badge&logo=superuser&logoColor=white)
![Magisk](https://img.shields.io/badge/Magisk-8A2BE2?style=for-the-badge&logo=magisk&logoColor=white)
![KernelSU](https://img.shields.io/badge/KernelSU-000000?style=for-the-badge&logo=linux&logoColor=white)
![APatch](https://img.shields.io/badge/APatch-FF6B00?style=for-the-badge&logo=android&logoColor=white)

</div>

<br>

* **ROM Shifter** is a an Android app built to make flashing, backing up, and migrating between custom ROMs as painless as possible. As well provides tools for some common things we do after switching to another ROM.

* *Note: This app is the native frontend successor to my original [ROM-Shifter Shell Script](https://github.com/ShivamXD6/ROM-Shifter-Script), which is archived now*

## 💡 Why ROM Shifter?

* There are plenty of great backup apps out there, and they all do their best, still i felt they are slow in backup restore. But honestly, I just wanted a single, unified, lightweight app that handles *everything* I actually need when hopping between ROMs without the unnecessary bloat.

* I didn't want three separate apps to back up my data, manage my partitions, and debloat apps etc. ROM Shifter combines a hyper-fast custom Shell backend engine with a clean Kotlin UI to do it all locally and quickly.

## ✨ Features

<div align="center">
  <img src="https://github.com/user-attachments/assets/01847ad1-8c4f-4697-958f-27739db79b86" width="30%" alt="Flash Tab"/>
  <img src="https://github.com/user-attachments/assets/4419f6ba-5d70-4ce3-a218-54c9705510ee" width="30%" alt="Migrate Tab"/>
  <img src="https://github.com/user-attachments/assets/61dc61dc-ff84-4979-bceb-d34ae5d6734b" width="30%" alt="Tools Tab"/>
</div>

### ⚡ Flash

*Everything you need to flash, install, and restore device partitions.*

<details>
<summary><b>Auto Flash Wizard (Click to Expand)</b></summary>

*Automate the complete ROM flashing process directly from Android.*

* Configure the required wipes before flashing, including Dalvik, Cache, Data, Metadata, System, and
  Format Data.
* Select and arrange Firmware, ROM, G-Apps, Kernel, Recovery, and other flashable ZIPs in the order
  you want.
* Automatically creates the recovery flashing sequence using commonly recommended flashing
  practices.
* Warns you if a screen lock is enabled to help prevent FRP issues when booting into the new ROM.
* Choose what to boot into after flashing: System, Recovery, or Bootloader.
* Useful when recovery touch is not working or when you want to flash multiple ZIPs without manually
  interacting with recovery.

<div align="center">
  <img src="https://github.com/user-attachments/assets/55b197c3-bde8-461c-ab34-0252088944f7" width="30%" alt="Wipe Options"/>
  <img src="https://github.com/user-attachments/assets/37e05cc5-6d79-4536-b0fe-96a4fe982269" width="30%" alt="Wipe/Flash zips Order"/>
  <img src="https://github.com/user-attachments/assets/a7c773a6-1c17-4ce4-8b8f-45c0d85e38d2" width="30%" alt="Reboot to Recovery and Start Flashing"/>
</div>

</details>

<details>
<summary><b>Install Batch Apps (Click to Expand)</b></summary>

*Install apps with multiple extensions simultaneously*

* Supports Apk, Apks, XApk and Apkm for Installation (Single or Batch)
* Much Faster than traditional apps installers.
  * Tested on Instagram 30 seconds on normal installer V/S 9 seconds with ROM Shifter Installer
  * Tested with 7 Apps Bulk Installed in 5 Seconds
* No Irritating Play Protect Detection While Installation.
* Spoofs Installer source as Play Store (no more unknown apps installed detection on root)
* Batch Installation (parallel up to 3 Apps to avoid throttling and get best Installation speed)
* Good UI to show whether you're installing first time, Reinstalling the same, upgrading or
  downgrading.
* With support for displaying Target Android, Size, Selected and Current versions.
* Icons indicator for the batch installations to Determine the Re-install, Downgrade or Upgrade.

<div align="center">
  <img src="https://github.com/user-attachments/assets/78dffc23-22f3-4e3c-8947-b476d00c7955" width="30%" alt="Reinstall the Same App"/>
  <img src="https://github.com/user-attachments/assets/55f1e302-d909-44a0-8623-b6c37294dcc1" width="30%" alt="Update the App"/>
  <img src="https://github.com/user-attachments/assets/9640e228-e030-4ead-9b02-3dfde24a928e" width="30%" alt="Downgrade the App"/>
  <img src="https://github.com/user-attachments/assets/c50b42d1-6a2d-4288-b683-8062bae013eb" width="30%" alt="Batch Installer"/>
  <img src="https://github.com/user-attachments/assets/f4ded782-e40c-4daf-96a7-e5ab3486eab3" width="30%" alt="Batch Installing Apps"/>
  <img src="https://github.com/user-attachments/assets/4646a055-c345-44bd-a410-ccc28ec3cb63" width="30%" alt="Installation Finished"/>
</div>

</details>

<details>
<summary><b>Backup / Restore / Flash Partitions (Click to Expand)</b></summary>

*Create raw `.img` backups of selected device partitions.*

* Search for and select the partitions you want to back up.
* Back up multiple partitions at the same time.
* Useful before flashing ROMs, kernels, recoveries, or making other low-level system changes.

*Flash a raw `.img` file directly to a selected partition.*

* Restore partition images created with ROM Shifter.
* Flash custom `.img` files to supported partitions.
* Useful for recovering or replacing individual partitions.

<div align="center">
  <img src="https://github.com/user-attachments/assets/f2be843c-e948-4992-bbe5-a80fe7fd7c56" width="30%" alt="Backup Partitions"/>
  <img src="https://github.com/user-attachments/assets/973016c5-06d4-44ca-8d27-294cfd980f01" width="30%" alt="Restore Paritions"/>
  <img src="https://github.com/user-attachments/assets/84061293-95d6-4fdb-a558-8e1e3581d0cc" width="30%" alt="Flash Custom Partitions"/>
</div>

</details>

---

### 🔄 Migrate

*Everything you need to move your apps and personal or ROM data between ROMs.*

<details>
<summary><b>Backup Apps (Click to Expand)</b></summary>

*Backup user and system apps so they can be restored later.*

* Select individual apps or back up multiple apps at once.
* Choose exactly which components to back up, including:

  * APK / Split APKs
  * App Data
  * External Data
  * Media
  * OBB
  * Android ID
  * Granted Permissions
* All component, action, and filter controls are organized for quick and easy selection.
* Backup Benchmarks (Performed by users):

  * ~52.71 GB in 4m 14s (Internal Storage, UFS 4.0, 35 Apps) - Poco F6 (Old)
  * ~15.60 GB in 1m 1s (Internal Storage, UFS 4.0, 5 Apps) - Poco X6 Pro (Old)
  * ~15.35 GB in 31s (Internal Storage, UFS 4.0, 5 Apps) - Poco X6 Pro (Updated)
  * ~811.42 MB in 10s (Internal Storage, eMMC 5.1, 1 App) - realme 3 Pro (Old)
  * ~509 MB in 34s (SD Card, 2 Apps) - Redmi 10C (Old)
* Updated Benchmarks are 15% much better than mentioned one, because of improved parallel
  processing.
* Fully compresses base APKs and split APKs along with other backup components.

  * This results in backup sizes around 3% smaller for a single app and 15%+ smaller for bulk
    backups.
* Use Auto Select to automatically select apps that have already backup.
* Toggle System Apps to show or hide them.
* Useful before a clean ROM installation or when moving to another ROM.

<div align="center">
  <img src="https://github.com/user-attachments/assets/5516dfb4-c73c-4fcf-a106-5227b098cc39" width="30%" alt="App Size with Icon, Meta, Permissions and AndroidID"/>
  <img src="https://github.com/user-attachments/assets/7e147476-e5a0-48a1-b927-833cf93db5fa" width="30%" alt="App Size with Selected all components"/>
  <img src="https://github.com/user-attachments/assets/29568332-42a4-45f6-8bc5-4239acfdf1c3" width="30%" alt="System Apps"/>
  <img src="https://github.com/user-attachments/assets/234d2a5b-2dfc-454c-bbcf-784c4f7d19a2" width="30%" alt="Auto Select Apps"/>
  <img src="https://github.com/user-attachments/assets/5acf57a8-ec2a-4ba0-81e6-6ac92b8b8b85" width="30%" alt="Backup Completed (Updated Benchmarks)"/>
</div>

</details>

> [!NOTE]
> App caches are not backed up because they are not required. Apps automatically recreate their
> caches when needed.

<details>
<summary><b>Restore Apps (Click to Expand)</b></summary>

*Restore apps and their backed-up data after installing a new ROM.*

* Select individual apps or restore multiple apps at once.
* Choose exactly which components to restore, including:

  * APK / Split APKs
  * App Data
  * External Data
  * Media
  * OBB
  * Android ID
  * Granted Permissions
* All component, action, and filter controls are organized for quick and easy selection.
* Restore Benchmarks (Performed by users):

  * ~52.77 GB in 7m 28s (Internal Storage, UFS 4.0, 312 Apps) - Poco X6 Pro (Old)
  * ~15.35 GB in 31s (Internal Storage, UFS 4.0, 5 Apps) - Poco X6 Pro (Updated)
  * ~16.33 GB in 1m 41s (Internal Storage, UFS 3.1, 1 App) - realme GT NEO 3T (Old)
  * ~1.31 GB in 32s (Internal Storage, eMMC 5.1, 5 Apps) - realme 3 Pro (Old)
* Updated Benchmarks are 15% much better than mentioned one, due to parallel Apps Installation.
* Removes GMS transport files to prevent notification delays after restoration.
* Restores SELinux contexts to ensure apps such as Termux work correctly after restoration.
* Installs restored apps with Google Play Store as the installer source to prevent crashes in apps
  such as Airtel.
* Use Auto Select to automatically select apps that have not been restored yet.
* Toggle System Apps to show or hide them.

<div align="center">
  <img src="https://github.com/user-attachments/assets/488c24cb-683a-4d7d-a697-ff728003add0" width="30%" alt="Restoring Data and other Components"/>
  <img src="https://github.com/user-attachments/assets/e9273019-eeaa-4175-bc67-0d797e6cad15" width="30%" alt="Auto Select Apps"/>
  <img src="https://github.com/user-attachments/assets/d60da7a3-699b-45a9-be36-176f3d7c1b8e" width="30%" alt="Restore Completed (Updated Benchmark)"/>
</div>

</details>

> [!NOTE]
> Backup and restore speeds automatically adapt to your device's hardware to deliver the best
> possible I/O performance across UFS, eMMC, and SD cards.

<details>
<summary><b>Native Data Backup & Restore (Click to Expand)</b></summary>

*Back up and restore important Android data that is not tied to individual apps.*

* Supports native backup and restore of:

  * SMS (including RCS)
  * Call Logs
  * Contacts (vCard)
  * WiFi (Saved Devices)
  * Wallpaper (Lock and Home Screen)
  * Bluetooth (Paired Devices and Name)
* Unlike other solutions, you do not need to set ROM Shifter as the default app to restore SMS, Call
  Logs, or Contacts.

<div align="center">
  <img src="https://github.com/user-attachments/assets/ba6a2032-6874-4b2d-8445-521d5b31c6a4" width="30%" alt="Backup Native Data"/>
  <img src="https://github.com/user-attachments/assets/541b6d45-363a-436a-a6ba-7244bbd767bc" width="30%" alt="Notifications showing the Native Backup"/>
  <img src="https://github.com/user-attachments/assets/385386ec-22a4-4533-8520-987207235cbd" width="30%" alt="Restore Native Data"/>
</div>

</details>

<details>
<summary><b>Manage Backups (Click to Expand)</b></summary>

*Browse, manage, and delete existing app and native backups to keep your storage organized.*

* Use Auto Select to automatically select apps that have already been restored.
* Toggle System Apps to show or hide them.

<div align="center">
  <img src="https://github.com/user-attachments/assets/5ad3c8a8-97ca-4add-acf7-959577c851c0" width="30%" alt="Manage Backups No apps Selected"/>
  <img src="https://github.com/user-attachments/assets/6734d49d-9290-4537-89c5-e901079c8542" width="30%" alt="Manage Native Data"/>
  <img src="https://github.com/user-attachments/assets/dbbe3616-8647-4a98-83c8-e80b35043f9f" width="30%" alt="Auto Select Apps"/>
</div>

</details>

---

### 🛠️ Tools

*Utilities for managing, customizing, and maintaining your rooted Android system.*

<details>
<summary><b>Debloat / Restore Apps (Click to Expand)</b></summary>

*Remove unwanted user or system apps from your device.*

* Select individual apps or multiple apps at once.
* Choose between User Apps and System Apps.
* Use Auto Select to automatically select apps that have already been backed up.
* Toggle System Apps to show or hide them.
* Enable Restore to bring back previously debloated system apps.
* Useful for removing vendor bloatware and unnecessary system packages after installing a ROM.

<div align="center">
  <img src="https://github.com/user-attachments/assets/c5cc6a90-b1d7-4b6d-a914-ffdaf98b7eb9" width="30%" alt="Debloat Apps"/>
  <img src="https://github.com/user-attachments/assets/c4800dfb-efab-4973-87fe-b2765e6960cb" width="30%" alt="Restore Apps"/>
  <img src="https://github.com/user-attachments/assets/4007ad26-da9d-448d-8b5d-bb16db109868" width="30%" alt="Auto Select Apps"/>
</div>

</details>

<details>
<summary><b>Systemize / De-Systemize (Click to Expand)</b></summary>

*Move supported apps into or out of the Android system environment.*

* Requires the Mountify Module because directly moving apps into the system can be risky and is not
  supported on Dynamic Partitions.
* Shows only User Apps by default, since systemizing an existing System App is unnecessary.
* Supports De-Systemization when you no longer want a user app to remain installed as a system app.
* Useful when you want an app to become part of the system and no longer be removable like a normal
  user app.

<div align="center">
  <img src="https://github.com/user-attachments/assets/bb58b786-2cb6-410b-a7e8-4988e0458d42" width="30%" alt="Mountify Required"/>
  <img src="https://github.com/user-attachments/assets/7eba401b-c6cd-479a-94ef-94302ba8abc6" width="30%" alt="Systemize Apps"/>
  <img src="https://github.com/user-attachments/assets/b36534f6-887c-4da8-b79b-b1b667ccc605" width="30%" alt="De-Systemize Apps"/>
</div>

</details>

---

### ⚙️ Settings

*Settings to customize your experience with the app*

<details>
<summary><b>All the Settings (Click to Expand)</b></summary>

* Select the Folder by file picker or write a custom one.
  * Supports SDcard as well.
  * Automatically moves the existing Files to another Directory you selected.
* Change Appearance or Theme to whatever you like,
  * Supports Dynamic Wallpapers based accent colors to Android 9 - 11
  * Supports Dynamic Wallpapers based Monet colors to Android 12+
  * Supports Light, Dark, AMOLED (Black) theme as well to save some battery.
  * Disable Dynamic colors to use the default accents colors.
* Get latest updates directly from the app.
  * Choose between Pre-Releases or Stable Release.
  * View all the changelogs from starting to this version.
* About and Support, not a setting ofc but it's there incase if you like my work you can donate,
  contribute or help :)

<div align="center">
  <img src="https://github.com/user-attachments/assets/04d05fe6-ef52-4d7a-9d09-6f0d834231fc" width="30%" alt="Main Settings"/>
  <img src="https://github.com/user-attachments/assets/7266c302-258a-4e98-85d1-7a48a9d7309a" width="30%" alt="Select Theme Dynamic Colors"/>
  <img src="https://github.com/user-attachments/assets/43fed36e-765a-47e1-863b-82d33b68c23e" width="30%" alt="Select Theme Accent Colors"/>
  <img src="https://github.com/user-attachments/assets/4341a8d8-f3ef-4ccf-abd1-37c5c81261ea" width="30%" alt="App Updates"/>
  <img src="https://github.com/user-attachments/assets/214e05dd-0f75-4d79-b5c1-2a342115e524" width="30%" alt="App Changelogs"/>
  <img src="https://github.com/user-attachments/assets/d1e2d055-a728-4d5f-baa3-cbe8188c6593" width="30%" alt="About and Support"/>
</div>

</details>

  <h3> Rest Features explore it by yourself ;') </h3>

## 📊 Real-World Benchmarks Comparison

<div align="center">
  <h3>Compression Size (Lower is Better)</h3>
  <p>
    <img src="https://github.com/user-attachments/assets/0a45f371-0328-4111-86c3-c137ec92357d" width="30%" alt="Data Backup" />
    <img src="https://github.com/user-attachments/assets/11a72465-4592-424d-a7d1-df9ee9140aab" width="30%" alt="Neo Backup" />
    <img src="https://github.com/user-attachments/assets/30e061ba-869d-4abe-9570-fd94668eb9ca" width="30%" alt="Swift Backup" />
  </p>

<h3>Backup/Restore Speed Benchmarks (Lower is Better)</h3>
  <p>
    <img src="https://github.com/user-attachments/assets/dcb57aa4-43b6-407e-af6b-9a59e91b1a24" width="30%" alt="Backup Comparison" />
    <img src="https://github.com/user-attachments/assets/2a98f5ab-0c10-4f09-87d1-037e859724ed" width="30%" alt="Restore Swift Backup" />
    <img src="https://github.com/user-attachments/assets/efa445c0-c934-4115-8c4a-51cb73e489e4" width="30%" alt="Restore ROM Shifter" />
  </p>
</div>

## 📥 Installation & Requirements

1. Your device must be rooted. Supported managers include **Magisk**, **KernelSU**, **APatch** and their forks.
2. Download the latest APK from the [Releases Tab](../../releases).
3. Open the app, grant Root permissions, and complete the setup wizard to pick your `Shifter`
   storage directory.

## 💖 Support the Project

ROM Shifter is open-source and entirely free. If this app saves your time, headaches, or saves your phone from a bootloop (jk XD), consider supporting the development!

* [Sponsor via GitHub](https://github.com/sponsors/ShivamXD6)
* [Donate via PayPal](https://paypal.me/ShivamXD6)
* [Donate via UPI QR](https://i.ibb.co/5g4J2RXR/1f38d6d7-a8a2-4696-88e6-9cf503e0592c.png)
* **UPI (India):** `shivamashokdhage6@oksbi` or `shivam.dhage@superyes`

Every contribution helps keep the project alive and improved! Thank you! 😊

---
<div align="center">
  Developed by <b>@ShivamXD6</b> and the <b>Build Bytes</b> Team.<br>
  <a href="https://t.me/buildbytes">Join Telegram Community</a> • <a href="https://www.youtube.com/@BuildBytesX">Subscribe on YouTube</a>
</div>
