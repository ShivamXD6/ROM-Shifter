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

* **📦 Apps Migration**
  Backs up and restores User/System apps at blazing fast speed **(~52.77 GB in just 7m 28s)** using a custom ZSTD `zapdos` binary to create highly compressed `.shift` archives.
* **💬 Native Data Backup**
  Backs up your Messages, Call Logs, and Contacts into single, `.shift` files for easy restoration.
* **⚡ Auto Flash Wizard**
  Queue up your Firmware, ROMs, GApps, and Kernels, select your wipe partitions, and the app will auto-flash everything in Your Custom Recovery like TWRP. (Perfect when your recovery touchscreen doesn't work).
* **💾 Backup Flash Partitions**
  Directly clone or flash raw `.img` files to block devices (like `boot` or `recovery`) while Android is fully booted.
* **🧹 App Management**
  Debloat useless system apps, or systemize your own user apps seamlessly using Meta-OverlayFS (For KernelSU or it's forks).
* <h3> Rest Features explore it by yourself ;') </h3>

## 📱 Screenshots & Previews

<details>
  <summary><b>Tap to view Screenshots</b></summary>
  <br>
  <div align="center">
    <img src="https://github.com/user-attachments/assets/d0ec7ca7-7ecf-420a-8be6-f4249f8d5fef" width="30%" alt="Flash Tab"/>
    <img src="https://github.com/user-attachments/assets/e3cd2c2e-be25-4ef4-a6e9-43c69b22a007" width="30%" alt="Migrate Tab"/>
    <img src="https://github.com/user-attachments/assets/20424390-2264-4cc2-b6c3-528da5f4a256" width="30%" alt="Settings Tab"/>
  </div>
</details>

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
