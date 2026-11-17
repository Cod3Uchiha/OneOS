# OneOS

OneOS is being rebuilt as a **rootless, in-app Android virtual machine**. The host Android installation (Samsung One UI on the initial target) remains untouched. A guest system is intended to run in its own VM and app-private storage.

## Current development status

The `rootless-vm-rebuild` branch removes the former root-based system switcher and provides a minimal Android app shell with private guest-image import. It is a foundation only: **a VM engine and bootable guest image are not yet integrated**, so this branch does not currently boot a second Android system.

Initial device target: Samsung Galaxy S22 Ultra SCG14 (Snapdragon 8 Gen 1, Android 16). Hardware acceleration and access to the phone's GPU, camera, modem, sensors and vendor drivers must be verified individually; they are not automatically available to a guest VM.

See [Android app build instructions](app-android/README.md).
