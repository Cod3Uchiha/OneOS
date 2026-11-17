# OneOS

OneOS is a **rootless ARM64 virtual-machine app** for running a second operating system inside Android. The host installation (One UI on the initial target) remains untouched.

## Current development status

- QEMU 11.1.3 ARM64 system-emulation backend integrated from [QubeVM](https://github.com/rhn-1k/QubeVM).
- TCG software acceleration is selected by default; no root or /dev/kvm access is required.
- OneOS includes a checksum-verified installer for the LineageOS 24 ARM64 QEMU guest image (Android 16).
- The guest image is downloaded separately from its upstream release and is not bundled in the APK. First boot and performance still need validation on the target SCG14.

Initial device target: Samsung Galaxy S22 Ultra SCG14 (Snapdragon 8 Gen 1, Android 16). The VM uses emulated virtual hardware. It does not automatically receive the host's Adreno GPU, camera, modem, sensors or Samsung vendor drivers.

## Build

The Android workflow downloads the pinned QubeVM ARM64 runtime, verifies its SHA-256, and builds the OneOS APK. See [app build instructions](app-android/README.md) and [the QEMU backend notes](docs/qemu-backend.md).

QubeVM is included as a GPL-3.0 submodule. Its source and license are retained under vendor/QubeVM; OneOS-specific files retain their existing license.
