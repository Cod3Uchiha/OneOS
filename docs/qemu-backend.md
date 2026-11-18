# OneOS standalone QEMU backend

OneOS uses an app-owned Android ARM64 QEMU runtime. It does not use QubeVM, a VM-manager framework, root access, KVM, Termux, or host partition changes.

## Runtime build

The Android ARM64 QEMU runtime is built from upstream QEMU 11.1.2 using the Android NDK. The CI build recipe is pinned to ExTV/Podroid commit `e3fc1befa3c135d28bf8d40e3c2264d93fea87ef`, which contains an Android cross-compilation Dockerfile and its Bionic compatibility patches. The workflow extracts the QEMU system emulator, its SLIRP networking dependency, and qemu-img, then verifies that each executable is AArch64 before packaging.

The QEMU executable is named `libqemu-system-aarch64.so` for Android's native-library packaging layout; it is launched as a process, not loaded as a Java JNI API. `libslirp.so` is a runtime dependency. The app uses QEMU TCG software emulation.

The Android build packages AAVMF ARM64 UEFI code and a writable variable-store template, plus noVNC for local VNC display over a loopback-only WebSocket.

## Import and disk setup

The app accepts ARM64 `.qcow2`, `.img`, `.raw` and `.iso` files, either directly or inside a ZIP archive. For UTM ZIPs it recognizes `Data/vda.qcow2`, `Data/vdb.qcow2` and `Data/efi_vars.fd`. It writes imported files into app-private storage and never flashes or repartitions the phone.

The user selects a virtual disk capacity from 8 to 128 GB (30 GB by default). QEMU's `qemu-img` creates a sparse QCOW2 disk for an ISO installer or grows an imported disk when its virtual capacity is smaller than the selected value. Existing images larger than the selected size are not shrunk. QCOW2 storage grows physically as the guest writes data rather than reserving its full virtual capacity immediately.

For UTM archives, OneOS preserves the auxiliary disk. It uses the bundled AAVMF variable template when the archive's variable file does not match the 64 MiB AAVMF store size.

## Boot and display

Guests run on QEMU's ARM `virt` machine with UEFI, virtio storage, graphics, keyboard, mouse and networking. The local noVNC client connects to QEMU's VNC WebSocket on loopback only.

## Limits and validation

- QEMU TCG is software CPU emulation. It may be slow on a phone compared with KVM.
- The guest sees virtual ARM hardware, not the Snapdragon motherboard, Samsung vendor drivers, camera, modem or sensors.
- Images must be ARM64 and support the virtual devices/firmware configured by OneOS. Arbitrary images are not guaranteed to boot.
- The standalone runtime build and Android app build must pass CI before an APK is distributed. First boot and display compatibility on the SCG14 still require a real-device test.

## Licensing and source

QEMU is licensed under GPL-2.0. The Android cross-build recipe is sourced from [ExTV/Podroid](https://github.com/ExTV/Podroid), also GPL-2.0; its pinned Dockerfile and Android compatibility patches are used as a build input. The AAVMF firmware and noVNC retain their upstream licenses and notices. Any distributed OneOS APK containing these components must be accompanied by the corresponding source and notices required by their licenses.
