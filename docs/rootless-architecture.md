# Rootless OneOS architecture

## Product boundary

OneOS runs as an ordinary Android application. It must not require root, invoke su, write system/vendor partitions, replace host system services, install Zygisk modules, or alter the host boot chain. Guest files and VM runtime state are stored in app-managed directories where possible.

## Runtime layers

1. Host app — UI, lifecycle, storage selection, logs and consent.
2. QEMU ARM64 backend — QEMU 11.1.3 system emulation from QubeVM, using TCG by default.
3. Guest disk — checksum-verified LineageOS 24 ARM64 QEMU guest package, extracted into app-private storage.
4. Virtual devices — QEMU's emulated ARM virt board, virtio storage/network/input and user-mode networking.
5. Host bridges — explicit, permission-gated APIs for display, audio, networking and selected sensors. Never assume camera, modem, GPU or vendor-driver passthrough.

## S22 Ultra target

Initial target: Snapdragon SCG14 Galaxy S22 Ultra on Android 16. The app uses the ARM64 host ABI and QEMU TCG, so it does not depend on /dev/kvm or privileged AVF APIs. Android's Virtualization Framework is not a public third-party app API on stock devices because its framework APIs require restricted permissions. See the Android platform API documentation.

TCG is compatible with ordinary app execution but is slower than hardware virtualization. Benchmark CPU load, thermals, memory pressure and sustained runtime on the exact SCG14 firmware before recommending long sessions.

## Guest operating system

OneOS can download and install the LineageOS 24 ARM64 QEMU VM archive published by jqssun/android-lineage-qemu. The guest image uses QEMU's ARM virt hardware and virtio devices. It is not Samsung firmware and does not use the host phone's board drivers.

The installer verifies the pinned archive SHA-256 before accepting the extracted virtual disks and UEFI variables. First boot, display, audio, networking and stability must still be tested on the exact SCG14 Android 16 firmware.

## Milestones

- [x] Remove the old root/system-switching implementation.
- [x] Create a rootless host app and app-managed VM workspace.
- [x] Integrate QEMU ARM64 system emulation with TCG as the default backend.
- [x] Add checksum-verified Android 16 guest archive installation and VM profile provisioning.
- [ ] Validate Android guest first boot on SCG14.
- [ ] Validate display, input, audio and user-mode networking.
- [ ] Implement pause/resume, lifecycle recovery and resource limits.
- [ ] Benchmark thermals and sustained performance.
