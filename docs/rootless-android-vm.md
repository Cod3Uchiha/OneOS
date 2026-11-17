# Rootless Android VM track

## Goal

Add an isolated guest operating system that runs inside the OneOS Android app without root, bootloader unlocking, system-partition changes, or replacement of the Samsung host OS. The host remains One UI. This track is separate from the existing root-based system-switching features.

Target validation device: Samsung Galaxy S22 Ultra SCG14, Snapdragon 8 Gen 1 (SM8450), Adreno 730, Android 16.

## Important platform constraints

- A normal Play-installed application cannot assume access to KVM, /dev/kvm, host kernel modules, or privileged virtualization services.
- Android Virtualization Framework (AVF) is not a drop-in public SDK for arbitrary third-party apps, and Microdroid is not a full Android desktop/mobile guest. Do not report AVF as available until the exact device firmware and app permissions are verified.
- The initial compatibility baseline must therefore be a userspace emulator backend (QEMU system emulation / TCG) or another backend that works within an ordinary app sandbox. Expect a significant performance penalty without KVM.
- The guest must use app-private storage. No su, Shizuku-based privilege escalation, boot image patching, overlay replacement, or writes to host system partitions are part of this feature.
- Host hardware access is mediated by Android APIs. Do not promise direct guest access to Adreno, camera, modem, fingerprint, or other Samsung drivers. GPU acceleration and device passthrough are separate experiments after a guest boots.

## Architecture

1. **Host UI and lifecycle** — install/import a compatible guest image, allocate RAM/storage, start, stop, pause where supported, and show console/log output.
2. **Backend interface** — isolate emulator-specific code behind a small Kotlin interface. Backend capability detection must report unavailable features rather than silently falling back.
3. **VM process/runtime** — package a legally redistributable ARM64 emulator runtime and required native libraries. Launch it with explicit arguments and an app-private working directory. Never execute downloaded guest scripts with host privileges.
4. **Guest disk management** — keep immutable base images separate from writable per-instance overlays; validate checksums and free storage before import.
5. **Display/input bridge** — render the guest framebuffer in an Android surface and forward touch, keyboard, and pointer input.
6. **Host integrations** — optional clipboard, network, audio, and file-sharing bridges, each permission-gated and disabled by default until implemented and tested.

## Milestones and acceptance criteria

### M0 — repository separation and design
- Keep the existing root-based OneOS implementation unchanged.
- Add a separate rootless VM feature track and explicit compatibility constraints.

### M1 — runtime feasibility
- Build or obtain a reproducible ARM64 userspace emulator runtime with license notices and source provenance.
- Verify that the runtime can execute from app-private storage on Android 16 without root.
- Measure CPU/RAM/storage overhead on the SCG14.

### M2 — minimal guest boot
- Select a guest image format that the chosen backend actually supports.
- Boot a known-good minimal guest to a visible console/framebuffer.
- Persist guest state in an app-private writable disk overlay.
- Stop cleanly and recover from force-stop/reboot without corrupting the base image.

### M3 — usable guest UI
- Touch/keyboard input, display scaling/rotation, pause/resume only if supported by backend.
- Clear lifecycle states and actionable error messages.

### M4 — acceleration and integrations
- Test graphics acceleration independently; keep software rendering as a fallback.
- Add network/audio/clipboard/file bridges only after security review and device tests.
- Treat camera, modem, biometric, and direct GPU passthrough as unsupported unless proven on this exact firmware.

## Device validation checklist

Record Android build fingerprint, available RAM/storage, ABI, runtime version, guest image checksum, backend mode (TCG/KVM/other), boot duration, CPU/RAM use, thermal behavior, display status, and crash logs. A successful build is not a successful device test. A VM is not considered working until it boots and survives a clean stop/restart on the SCG14.

## Current status

Design only. No VM runtime, guest image, APK, boot test, or hardware-acceleration test is included by this document.