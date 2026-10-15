# MergeSMS Standalone Android App

This is a clean native Android project foundation for **MergeSMS**, intended to build as a standalone APK installed directly on an Android phone. It is not a web app.

## Current foundation
- Native Android Java app (`com.mergesms.app`)
- Green-and-white Material-inspired interface
- Main navigation: Messages, Contacts, Bulk SMS, Settings
- Bulk SMS screen with CSV import, recipient selection, compact merge-field list with small `X` controls
- Draft autosave for the composer
- Duplicate phone-number detection during CSV import
- Scheduled SMS/campaign data model and schedule screen
- Per-recipient send status/history UI
- Native SMS permissions and SIM-aware send path scaffold
- GitHub Actions workflow to build a debug APK

## Important implementation notes
This project is a **starter implementation**, not a claim that every carrier/device behavior is already production-tested. Android SMS access and sending depend on runtime permissions, default-SMS-app role requirements and Play distribution policy. Dual-SIM selection and delivery reports vary by Android version, manufacturer and carrier. Scheduled sending can be delayed by Doze/battery restrictions and OEM background limits.

The current CSV importer supports CSV files. Excel `.xlsx` parsing is not yet included; the UI labels this as a future extension. Caller-ID identification equivalent to Truecaller requires a suitable external caller-ID dataset/service and is not included here.

## Build in GitHub Codespaces
1. Upload/extract the contents of this ZIP into a new GitHub repository, with the files at repository root.
2. In Codespaces terminal, run:
   ```bash
   chmod +x gradlew
   ./gradlew assembleDebug
   ```
3. Or push the project and let GitHub Actions build it.
4. Download the artifact named `MergeSMS-debug-APK`; the APK is normally at `app/build/outputs/apk/debug/app-debug.apk`.

## Package
- Application ID: `com.mergesms.app`
- Minimum SDK: 23 (Android 6.0)
- Compile/target SDK: 35
