# OneOS

OneOS is a **rootless ARM64 virtual-machine app** for running a second operating system inside Android. The host installation (One UI on the initial target) remains untouched.

## Current development status

- QEMU 11.1.3 ARM64 system-emulation backend integrated from [QubeVM](https://github.com/rhn-1k/QubeVM).
- TCG software acceleration is selected by default; no root or /dev/kvm access is required.
- The app opens an ARM64 VM manager for creating and configuring virtual machines.
- A bootable Android guest image is **not yet included or validated**. Android needs a kernel, ramdisk and system/vendor images built for QEMU's ARM virt machine; a random phone firmware or generic system.img will not work.

Initial device target: Samsung Galaxy S22 Ultra SCG14 (Snapdragon 8 Gen 1, Android 16). The VM uses emulated virtual hardware. It does not automatically receive the host's Adreno GPU, camera, modem, sensors or Samsung vendor drivers.

## Build

The Android workflow downloads the pinned QubeVM ARM64 runtime, verifies its SHA-256, and builds the OneOS APK. See [app build instructions](app-android/README.md) and [the QEMU backend notes](docs/qemu-backend.md).

QubeVM is included as a GPL-3.0 submodule. Its source and license are retained under vendor/QubeVM; OneOS-specific files retain their existing license.
