# Rootless OneOS architecture

## Product boundary

OneOS runs as an ordinary Android application. It must not require root, invoke su, write system/vendor partitions, replace host system services, install Zygisk modules, or alter the host boot chain. Guest files and VM runtime state are stored in app-managed directories where possible.

## Runtime layers

1. Host app — UI, lifecycle, storage selection, logs and consent.
2. QEMU ARM64 backend — QEMU 11.1.3 system emulation from QubeVM, using TCG by default.
3. Guest disk — user-supplied disk image. Validate format, architecture, checksum and size before boot.
4. Virtual devices — QEMU's emulated ARM virt board, virtio storage/network/input and user-mode networking.
5. Host bridges — explicit, permission-gated APIs for display, audio, networking and selected sensors. Never assume camera, modem, GPU or vendor-driver passthrough.

## S22 Ultra target

Initial target: Snapdragon SCG14 Galaxy S22 Ultra on Android 16. The app uses the ARM64 host ABI and QEMU TCG, so it does not depend on /dev/kvm or privileged AVF APIs. Android's Virtualization Framework is not a public third-party app API on stock devices because its framework APIs require restricted permissions. See the Android platform API documentation.

TCG is compatible with ordinary app execution but is slower than hardware virtualization. Benchmark CPU load, thermals, memory pressure and sustained runtime on the exact SCG14 firmware before recommending long sessions.

## Guest operating system requirements

QEMU ARM virt presents emulated hardware, not the Snapdragon board. A bootable Android guest must be built for that virtual board with a matching ARM64 kernel, initramfs, fstab, virtio drivers and Android system/vendor partitions. A stock Samsung firmware image, generic Android system.img, or emulator image built for Goldfish/Ranchu is not assumed to boot.

The current milestone integrates the VM engine and manager. A validated Android guest image is a separate milestone.

## Milestones

- [x] Remove the old root/system-switching implementation.
- [x] Create a rootless host app and app-managed VM workspace.
- [x] Integrate QEMU ARM64 system emulation with TCG as the default backend.
- [ ] Build and validate a bootable ARM64 Linux test guest on SCG14.
- [ ] Build a compatible Android guest kernel, ramdisk and system/vendor image.
- [ ] Validate Android guest boot and basic input/display/audio.
- [ ] Implement pause/resume, lifecycle recovery and resource limits.
- [ ] Add tested host bridges and benchmark performance.
