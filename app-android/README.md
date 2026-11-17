# OneOS Android app

## VM engine

OneOS integrates the ARM64 system-emulation runtime from [QubeVM](https://github.com/rhn-1k/QubeVM), pinned as a Git submodule at vendor/QubeVM.

- QEMU 11.1.3, aarch64-softmmu
- TCG software emulation; KVM is explicitly disabled
- ARM64 host ABI (arm64-v8a)
- QEMU user-mode networking (SLIRP) is available through the VM manager
- No root, su, boot-chain modification, partition writes or host system replacement

The build workflow downloads the upstream ARM64 QubeVM release APK, verifies the published SHA-256, and extracts its native libraries into the OneOS APK. The QubeVM Java frontend and library source are built from the pinned submodule.

## Android guest status

The VM engine can create and run ARM64 virtual machines. **OneOS does not yet ship a ready-to-boot Android guest.** A suitable Android guest needs a kernel, ramdisk, and system/vendor partitions configured for QEMU's ARM virt hardware. Stock Samsung firmware and generic Android system images are not automatically compatible. The app must not claim Android boot support until that guest image is built and tested.

## Build

    git clone --recurse-submodules https://github.com/Cod3Uchiha/OneOS.git
    cd OneOS/app-android
    ./gradlew assembleDebug

For a local build, place the verified QubeVM ARM64 runtime libraries in app/src/main/jniLibs/arm64-v8a/. The GitHub Actions workflow performs this step automatically.

Initial target: Samsung Galaxy S22 Ultra SCG14, Snapdragon 8 Gen 1, Android 16. TCG is software emulation and may be slow under sustained guest CPU load. It does not pass through the phone's Adreno GPU, camera, modem, sensors or Samsung vendor drivers.

## Licensing

QubeVM is GPL-3.0 and is included as a pinned submodule. Its source and COPYING license are retained in vendor/QubeVM. The build also bundles the QubeVM license in the APK assets.
