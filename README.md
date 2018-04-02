[![Build Status](https://travis-ci.org/righettod/access-brute-forcer.svg)](https://travis-ci.org/righettod/access-brute-forcer)

# Access Brute Forcer

Android v7+ application to perform a dictionary brute force attack against a host exposing:
* SMB Windows shares.
* FTP server.
* SSH access.

The application is developed using Android Studio so you can import the project into it in order to compile a APK bundle.

# Motivation

This tool was developed in order to provide help in this case:

During a reconnaissance phase of an authorized penetration test at network level, when a open WIFI network was identified in which hosts are connected and exposes SMB Windows shares (see port 445 opened) / FTP server / SSH access, the goal is to perform a quick evaluation from a smartphone (more easy to launch and hide than a laptop) of the attack surface represented by theses points.

The application allow to download and keep password dictionaries from predefined list of dictionaries or from the device itself (for tailored password dictionaries).

# Download

HockeyApp system is used to publish releases and track the applications crashes.

Last release:
* [APK file](https://rink.hockeyapp.net/apps/64dd8a3981644cfd9923617dc0d05989)
* [VirusTotal APK file analysis](https://www.virustotal.com/#/file/6ff09f75a4642ebcb50d0b16bba3964168345009718cb8a53f8573173de72a65/detection)

# Working version enhancement

Version: **1.2**

* Fix the bug on the protocol selected by default that do not match the selected one at code level (SSH selected in code but FTP selected in UI).
* Add notification about attack progress during brute force operation and at the end whatever the brute force result.
* Update notification to support notification on wear (smartwatch in my test case).

# Build command line

## Debug version

Use the following command line `gradlew clean cleanBuildCache assembleDebug`

## Release version

Follow these steps:

1. Create a [JKS keystore](https://stackoverflow.com/a/37488577) with a RSA keypair.
2. Create a file named **keystore.properties** at the root folder level (same location than the file `gradlew`) with the following content:
```
storePassword=[StorePassword]
keyPassword=[KeyPassword]
keyAlias=[KeyAlias]
storeFile=[Store file full location or relative location from app sub folder]
```
Example:
```
# Configuration of the keystore used to sign the released APK
storePassword=fB5YDpcvTvQH7Sg399xG49YFK
keyPassword=gHTaEq93Xe93c3rWJu8v33WVB
keyAlias=keys
storeFile=../release-keystore.jks
```
3. Use the following command line `gradlew clean cleanBuildCache assembleRelease`
4. APK is available in folder `[ROOT_FOLDER]/app/build/outputs/apk`

# Usage efficiency

The application should be combined with the following applications to enhance efficiency:
* [FING](https://play.google.com/store/apps/details?id=com.overlook.android.fing&hl=en): For WIFI network discovery and target identification,
* [FILE MANAGER](https://play.google.com/store/apps/details?id=com.alphainventor.filemanager&hl=en): To access to Windows SMB Shares, FTP, SSH (via SFTP) content after the credentials identification.
* [JUICE SSH](https://play.google.com/store/apps/details?id=com.sonelli.juicessh&hl=en): To access via SSH shell if SFTP is not enabled.

# Action flow

1. Use **Fing** to identify a target host (copy the host IP or name in the clipboard via **Fing** copy/paste feature).

2. Use the app to identify the credentials (paste the host IP or name from the clipboard into the **Target** field). Port is optional, if not specified then default one is used.

![Main screen](example.png)

3. Use **File Manager** or **Juice SSH** to access to the contents.
