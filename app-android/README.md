# OneOS Android app — rootless rebuild

This branch removes the former root-based system-switching implementation and replaces it with a clean Android app shell for an in-app guest VM.

## Current state

- No root permission, `su` execution, partition writes, system overlays, Zygisk modules, or One UI replacement.
- Guest disk import uses Android's Storage Access Framework and copies data into app-private storage.
- The VM start action is deliberately disabled because no compatible ARM64 virtualization backend is bundled yet.

**This is not yet a bootable second Android system.** Do not install this branch expecting Android to boot inside the app. The next milestone is integrating and validating a VM backend on the target device, then supplying a compatible guest image.

## Build

```bash
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

Initial target: Samsung Galaxy S22 Ultra SCG14, Snapdragon 8 Gen 1, Android 16. Hardware acceleration and access to Samsung GPU, camera, modem and vendor drivers must be tested individually; they are not assumed.
