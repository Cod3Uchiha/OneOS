# QEMU ARM64 backend

## Selected backend

OneOS uses the ARM64 guest build of [QubeVM](https://github.com/rhn-1k/QubeVM), pinned as a Git submodule at commit a45126697776e1efd4c8d72b94335e88590492ef (release 7.1.5). QubeVM packages QEMU 11.1.3 for Android ARM64 and supports aarch64-softmmu guests with TCG.

The OneOS VM activity sets Config.enableKVM = false so a stock Android app never assumes access to /dev/kvm. It enables multi-threaded TCG. The backend is an actual system emulator, not a chroot, PRoot container or launcher.

## Native runtime supply

The Android workflow downloads the upstream QubeVM ARM64 release APK from its 7.1.5 release, checks the published SHA-256:

ecb2e6ac3025a2a40572bd49a16c48df6d479eb4f092caec199df58328af7ae2

It extracts the ARM64 native libraries into the OneOS APK and builds the OneOS Java app against the pinned QubeVM source module. The upstream GPL-3.0 license and source are retained.

## Limits

- TCG is software emulation. It is rootless but can be much slower than KVM.
- Stock Android's AVF APIs require restricted system permissions and are not assumed to be available to an ordinary installed app.
- The QEMU ARM virt machine does not expose the phone's Adreno GPU or Samsung hardware devices.
- Integrating QEMU does not itself create an Android guest image. Android guest kernel, initramfs, fstab and system/vendor partitions must be built for the virtual hardware and tested independently.
