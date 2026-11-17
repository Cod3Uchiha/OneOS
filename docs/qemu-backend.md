# QEMU ARM64 backend

## Selected backend

OneOS uses the ARM64 guest build of [QubeVM](https://github.com/rhn-1k/QubeVM), pinned as a Git submodule at commit a45126697776e1efd4c8d72b94335e88590492ef (release 7.1.5). QubeVM packages QEMU 11.1.3 for Android ARM64 and supports aarch64-softmmu guests with TCG.

The OneOS VM activity sets Config.enableKVM = false so a stock Android app never assumes access to /dev/kvm. It enables multi-threaded TCG. The backend is an actual system emulator, not a chroot, PRoot container or launcher.

## Android 16 guest image

OneOS targets the ARM64-only LineageOS 24 virtual-device build from jqssun/android-lineage-qemu, release v2026.11.17. Its UTM archive contains two QCOW2 virtual disks and UEFI variable storage for a QEMU ARM virt machine.

The app's installer verifies the release archive SHA-256:

812c498d80130e4ae79d15311a9a16d0e0cc66fa11ff4dbd91c2f35df83dbdc9

The guest archive is approximately 1.1 GB and is downloaded separately; it is not embedded in the APK. The installer extracts only vda.qcow2, vdb.qcow2 and efi_vars.fd, then provisions a two-vCPU, 2 GB ARM64 VM profile using TCG and virtio devices.

## Native runtime supply

The Android workflow downloads the upstream QubeVM ARM64 release APK from its 7.1.5 release, checks the published SHA-256:

ecb2e6ac3025a2a40572bd49a16c48df6d479eb4f092caec199df58328af7ae2

It extracts the ARM64 native libraries into the OneOS APK and builds the OneOS Java app against the pinned QubeVM source module. The upstream GPL-3.0 license and source are retained.

## Limits

- TCG is software emulation. It is rootless but can be much slower than KVM.
- Stock Android's AVF APIs require restricted system permissions and are not assumed to be available to an ordinary installed app.
- The QEMU ARM virt machine does not expose the phone's Adreno GPU or Samsung hardware devices.
- Android guest first boot and SCG14 runtime compatibility are not yet validated. The app must not be represented as device-tested until that is done.
