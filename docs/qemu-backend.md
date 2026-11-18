# OneOS standalone QEMU backend

OneOS is moving to an app-owned QEMU backend. It does not use QubeVM, a VM-manager framework, root access, KVM, Termux, or host partition changes.

## Runtime build

The Android ARM64 QEMU runtime is built from upstream QEMU 11.1.2 using the Android NDK. The current CI build recipe is pinned to ExTV/Podroid commit `e3fc1befa3c135d28bf8d40e3c2264d93fea87ef`, which contains an Android cross-compilation Dockerfile and its Bionic compatibility patches. The workflow extracts the QEMU system emulator, its SLIRP networking dependency, and qemu-img, then verifies that each executable is AArch64 before packaging.

The QEMU executable is named `libqemu-system-aarch64.so` for Android's native-library packaging layout; it is launched as a process, not loaded as a Java JNI API. `libslirp.so` is a runtime dependency. The app uses QEMU TCG software emulation.

The OneOS build also packages ARM64 EDK2 UEFI firmware and noVNC for local VNC display over a loopback-only WebSocket.

## Current guest format

The existing importer accepts the LineageOS 24 ARM64 UTM ZIP published by jqssun's Android QEMU project. It verifies the archive SHA-256 and extracts:

- `vda.qcow2`: primary guest disk
- `vdb.qcow2`: auxiliary guest disk
- `efi_vars.fd`: guest UEFI variable store

The guest is launched on QEMU's ARM `virt` machine with virtio storage, graphics, input and networking devices. The UEFI code image is packaged separately; the guest's writable variable store remains in app-private storage.

## Limits and validation

- QEMU TCG is software CPU emulation. It may be slow on a phone compared with KVM.
- The guest sees virtual ARM hardware, not the Snapdragon motherboard, Samsung vendor drivers, camera, modem or sensors.
- The current UTM guest's first boot and display compatibility on the SCG14 are not yet verified.
- Imported images must match the ARM64 architecture and the virtual devices/firmware configured by OneOS. Arbitrary images are not guaranteed to boot.
- The app's current importer is still specialized for the Android 16 UTM bundle; generic ISO/raw/QCOW2 import and user-selected virtual disk capacity are follow-up work.

## Licensing and source

QEMU is licensed under GPL-2.0. The Android cross-build recipe is sourced from [ExTV/Podroid](https://github.com/ExTV/Podroid), also GPL-2.0; its pinned Dockerfile and Android compatibility patches are used as a build input. The firmware and noVNC retain their upstream licenses and notices. Any distributed OneOS APK containing these components must be accompanied by the corresponding source and notices required by their licenses.
