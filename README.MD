# SMBsync+

SMBsync+ is a public, independently maintained fork of [Sentaroh's SMBSync2](https://github.com/Sentaroh/SMBSync2). It retains the upstream MIT license and synchronization engine while improving task setup, file browsing, and security. This project is maintained at [Sergi095/SMBsync-plus](https://github.com/Sergi095/SMBsync-plus); it is not an official Sentaroh release.

## Changes in this fork

- A dark, location-first workspace inspired by Cx File Explorer, with Files, Sync, Activity, Security, and More navigation. The file explorer browses phone folders and entire SMB servers: open a connection saved in a sync task or one saved in **Settings → SMB Settings → Explorer SMB connections**, list its shares, and navigate every folder. The explorer's **Connections ⚙** button opens the same settings screen; adding a server is no longer a location row. Saved connection details, including passwords, are encrypted on the device. Sync task setup can reuse a saved SMB connection. The explorer can search, sort, create folders, preview, rename, delete empty folders, and copy files between locations. Existing destination files are skipped when pasted.
- The launcher icon adapts Lucide's open source **Folder Sync** icon in SMBsync+'s navy and cyan colors. Its ISC license notice is packaged in `SMBSync2/src/main/assets/third_party_notices.txt`.
- In-place APK updates keep saved SMB connection credentials and sync task credentials. Connection credentials remain encrypted in app preferences; sync tasks remain in the app's private files. Keep the same package name and signing key for each update. Uninstalling the app or clearing its data removes these credentials.
- SMB folder setup uses a plain directory path and no longer presents date keyword buttons such as `%DAY%`. Existing tasks with date placeholders keep their paths until edited, so updating the app does not silently redirect a sync destination.
- Phone → NAS and NAS → Phone setup cards, clearer task cards, and a prominent New sync task button. NAS setup groups connection, SMB signing, credentials, and share details. Appearance can be Standard, Light, Black, or Follow system. The app UI is English only.
- New task setup includes scheduling in the same form: choose daily with a time, every 15 minutes, every 30 minutes, or every hour. The schedule saves with the task; Manage schedules remains available for advanced timing and later edits.
- **Add missing files (rsync style)** copies files absent from the destination and always keeps every existing destination file, including files that differ from the source. It never deletes destination-only files. New Copy and Move tasks also leave existing destination files untouched by default; their **Replace existing destination files** option permits replacement. Mirror offers a visible **Delete destination files missing from source** option in task setup.
- Phone ↔ NAS setup cards preselect **Any connected network (Wi-Fi, mobile data, or VPN)**. Use a VPN to reach a NAS over mobile data; do not expose SMB directly to the public internet.
- Security is a main navigation item. When startup protection is enabled, the workspace and explorer relock after leaving and reopening the app. App password protection can optionally use Android's biometric prompt on Android 9 and newer. The password remains available when biometric authentication is unavailable.
- Sync progress reports the number of source files checked as the scan runs. Activity history keeps the checked-file count for each new run, and Sync messages record a checked/copied summary. It does not add an artificial delay.
- **Settings → Miscellaneous → App updates** checks [SMBsync+ GitHub Releases](https://github.com/Sergi095/SMBsync-plus/releases). **Update source** shows and opens the exact release path. Downloaded APKs must match the release SHA-256, package name, newer version code, and installed signing key before Android's installer opens. More → App updates remains a shortcut.
- Plain-text SMB passwords are disabled by default for new installs. SMB1 remains available for compatibility and should be avoided when a server supports SMB2/3.
- Build uses Java 17, Gradle 8.10.2, Android Gradle Plugin 8.8.2, and Android SDK 35. The target SDK remains 29 while legacy storage behavior is migrated.
- App-specific temporary files use the SMBsync+ package directory, so copying new files works after changing the application ID.
- Debug and release builds use R8 code shrinking and obfuscation plus resource shrinking. The test APK copied to `Escritorio/MacOS` is release signed, so future releases can update it in place.
- R8 keeps the Bouncy Castle MD4 and AES-CMAC classes that SMB authentication and SMB3 signing load by name.

Build a test APK with `JAVA_HOME=/path/to/jdk17 ANDROID_HOME=/path/to/android-sdk bash gradlew assembleDebug`. The output is `SMBSync2/build/outputs/apk/debug/SMBSync2-debug.apk`.

## Updates and signing

The app's package ID is `io.github.sergi095.smbsyncplus`. Android retains saved tasks and encrypted SMB credentials during an in-place update only when the package ID and signing key stay the same. Public releases use one permanent release signing key. Its certificate SHA-256 fingerprint is `78BE3D232A3BBAA7B8D87DA437C61ED67714C2E7780685ABA18BAC66EF5C940A`. Keep the private keystore outside the repository and back it up securely; changing keys requires uninstalling the app and loses its on-device data. A debug APK cannot update a release APK or vice versa.

The updater expects a GitHub Release tagged `v2.55-plus.N` with an APK named `SMBsync-plus-v2.55-plus.N.apk`. It verifies GitHub's SHA-256 asset digest and the APK's installed signing certificate. See [GitHub Releases](https://github.com/Sergi095/SMBsync-plus/releases) for public builds. The source and upstream MIT license are in this repository; the Lucide icon's ISC notice is packaged in `SMBSync2/src/main/assets/third_party_notices.txt`.

For a local release build, put `release-signing.properties` outside this repository and point `SMBSYNC_PLUS_SIGNING_PROPERTIES` to it, or use `~/.config/smbsync-plus/release-signing.properties`. It needs `storeFile`, `storePassword`, `keyAlias`, `keyPassword`, and `storeType=PKCS12`. Run `ANDROID_HOME=/path/to/android-sdk bash ./gradlew :SMBSync2:assembleRelease`. The release APK is `SMBSync2/build/outputs/apk/release/SMBSync2-release.apk`. Never commit the signing properties or keystore.

## Original functions

SMBsync+ synchronizes files using SMB1, SMB2 or SMB3 between Android storage and a PC/NAS. Synchronization is one-way from source to destination. Mirror, Add missing files, Move, Copy and Archive modes are supported. Many storage combinations are supported (internal storage, SD card, USB OTG, SMB, ZIP).
Sync can be automatically started by external applications (Tasker, AutoMagic etc) or SMBSync2 schedule. 
Sync occurs between two folder pairs called the Master (source folder) and the Target (destination folder). It is a one direction Sync, from the Master to the Target.

The supported Sync modes are:  
- Mirror  
  Adds missing source files to the destination. Its **Delete destination files missing from source** option removes destination-only files when enabled. **Remove folders and files excluded by name filters** is an additional Mirror option. Replacing existing destination files is controlled separately by **Replace existing destination files**.

- Add missing files (rsync style)
  Adds source files only when the destination path does not already exist. It never replaces or deletes destination files, even when the source version changes. This is equivalent to a one-way `rsync --ignore-existing` style transfer, without rsync's full feature set.
  
- Move  
  New files are copied to the destination and then removed from the source. Existing destination files are kept by default. Destination-only files are preserved.
  
- Copy  
  New files are copied to the destination and kept at the source. Existing destination files are always skipped by default, even when the source changes. Destination-only files are preserved.
  
- Archive  
  Move photos and videos in the master's directory to the target if they were taken before 7 days or 30 days before the archive execution date. (However, you cannot use zip to target.)  
The following file types are eligible for archiving.  
"gif", "jpg", "jpeg", "jpe", "png", "mp4", "mov".  

**Compare criteria:** 
Files are considered different based on these criteria:  

1. File name exists only on master or target, not on both sides  
2. Files have different sizes  
3. Files have a different time stamp (last modification date and time)  

In Advanced Options, many compare settings can be adjusted.(Here is an example)  

- Time tolerance interval can be set to ignore difference if less than 1, 3, 5 or 10 sec for compatibility with FAT/exFAT medias. 
- Ignore Daylight Saving time is supported. 
- Option to not overwrite target file if it is newer than the master or if it is larger in size.   
# FAQs

Please refer to the link below.

[English](https://sentaroh.github.io/Documents/SMBSync2/SMBSync2_FAQ_EN.htm)
<br>

# Documents

Please refer to the link below.

[English](https://sentaroh.github.io/Documents/SMBSync2/SMBSync2_Desc_EN.htm)
<br>

# Build

Use the Java 17 build command above. The generated debug APK is separate from upstream SMBSync2 releases.

# License information

- This software includes the work that is distributed in the Apache License 2.0.  
  - [Zip4J 1.3.2](https://mvnrepository.com/artifact/net.lingala.zip4j/zip4j/1.3.2)  
  - [Metadata-extractor](https://github.com/drewnoakes/metadata-extractor)  
- This software includes [Adobe XMP Core 5.1.3](https://repo.maven.apache.org/maven2/com/adobe/xmp/xmpcore/5.1.3/) under its BSD license.
- This software includes the work that is distributed in the GNU Lesser General Public License v2.1.  
  - [jcifs-ng](https://github.com/AgNO3/jcifs-ng)  
  - [jcifs-1.3.17](https://jcifs.samba.org/)  
- This software includes the work that is distributed in the MOZILLA PUBLIC LICENSE 1.1.  
  - [juniversalchardet-1.0.3](https://code.google.com/archive/p/juniversalchardet/)  
- This software includes the work that is distributed in the Bouncy Castle LICENSE.
  - [Bouncy Castle Provider](https://mvnrepository.com/artifact/org.bouncycastle/bcprov-jdk15on/1.58)
- This software includes the work that is distributed in the BSD 3-Clause "New" or "Revised" License
  - [markdownj](https://github.com/myabc/markdownj)
