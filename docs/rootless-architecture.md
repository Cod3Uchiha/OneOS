# Rootless OneOS architecture

## Product boundary

OneOS runs as an ordinary Android application. It must not require root, invoke `su`, write system/vendor partitions, replace host system services, install Zygisk modules, or alter the host boot chain. Guest files live under the app's private files directory.

## Runtime layers

1. Host app — UI, lifecycle, storage selection, logs and consent.
2. VM backend — isolated ARM64 virtual machine. The backend must report whether hardware acceleration is genuinely available.
3. Guest disk — imported or downloaded image in app-private storage. Validate format, architecture, checksum and size before boot.
4. Host bridges — explicit, permission-gated APIs for display, audio, networking and selected sensors. Never assume camera, modem, GPU or vendor-driver passthrough.

## S22 Ultra target

Initial target: Snapdragon SCG14 Galaxy S22 Ultra on Android 16. Verify runtime availability, app-level access, kernel support, SELinux policy and ABI on this exact firmware before selecting a backend. If hardware virtualization is unavailable to ordinary apps, software emulation may be possible but substantially slower.

## Milestones

- [x] Remove the old root/system-switching implementation from this branch.
- [x] Create a rootless host-app shell and app-private guest-image import.
- [ ] Integrate an ARM64 VM backend and prove launch on SCG14.
- [ ] Boot a compatible Android guest image.
- [ ] Implement pause/resume, lifecycle recovery and resource limits.
- [ ] Add tested host bridges and benchmark performance.

Importing a disk image does not prove it can boot. The app must keep guest start disabled until a real backend and validated guest image are present.
