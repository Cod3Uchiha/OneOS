# OneOS Android app

## VM engine

OneOS integrates the ARM64 system-emulation runtime from [QubeVM](https://github.com/rhn-1k/QubeVM), pinned as a Git submodule at vendor/QubeVM.

- QEMU 11.1.3, aarch64-softmmu
- TCG software emulation; KVM is explicitly disabled
- ARM64 host ABI (arm64-v8a)
- QEMU user-mode networking (SLIRP)
- No root, su, boot-chain modification, partition writes or host system replacement

The build workflow downloads the upstream ARM64 QubeVM release APK, verifies the published SHA-256, and extracts its native libraries into the OneOS APK. The QubeVM Java frontend and library source are built from the pinned submodule.

## Android 16 guest

OneOS offers an installer for the upstream [LineageOS 24 ARM64 QEMU guest](https://github.com/jqssun/android-lineage-qemu/releases/tag/v2026.11.17). The UTM archive is approximately 1.1 GB. Download it from the in-app link, select the ZIP with the install action, and OneOS will verify its SHA-256, extract the two virtual disks and UEFI variables, and create a QEMU ARM64 VM profile.

The image is built for QEMU's ARM virt hardware with virtio devices; it is not Samsung firmware. **The guest's first boot and compatibility have not yet been validated on the SCG14.** Keep the host One UI installation untouched.

## Build

    git clone --recurse-submodules https://github.com/Cod3Uchiha/OneOS.git
    cd OneOS
    sed -i -e 's/compileSdk = 37/compileSdk = 36/' -e 's/targetSdk = 37/targetSdk = 36/' vendor/QubeVM/qube-android-lib/build.gradle
    cd app-android
    ./gradlew assembleDebug

The GitHub Actions workflow performs the QubeVM SDK compatibility patch and native library extraction automatically.

Initial target: Samsung Galaxy S22 Ultra SCG14, Snapdragon 8 Gen 1, Android 16. TCG is software emulation and may be slow under sustained guest CPU load. It does not pass through the phone's Adreno GPU, camera, modem, sensors or Samsung vendor drivers.

## Licensing

QubeVM is GPL-3.0 and is included as a pinned submodule. Its source and COPYING license are retained in vendor/QubeVM. The build also bundles the QubeVM license in the APK assets.
