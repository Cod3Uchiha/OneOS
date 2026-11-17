package com.max2idea.android.qube.machine;

import com.max2idea.android.qube.main.Config;
import com.max2idea.android.qube.main.QubeApplication;

import java.io.File;
import java.util.ArrayList;

/** Creates the OneOS Android 16 ARM64 VM using the QubeVM database model. */
public final class OneOSGuestProvisioner {
    private OneOSGuestProvisioner() { }

    public static void configure(String vdaPath, String vdbPath, String efiVarsPath) {
        QubeApplication.arch = Config.Arch.arm64;
        MachineController controller = MachineController.getInstance();
        String name = "OneOS Android 16";
        ArrayList<String> existing = controller.getStoredMachines();
        if (existing.contains(name)) {
            controller.setStoredMachine(name);
        } else if (!controller.createVM(name)) {
            throw new IllegalStateException("Could not create the OneOS Android 16 VM");
        }

        Machine machine = controller.getMachine();
        machine.setMemory(2048);
        machine.setCpuNum(2);
        machine.setCpu("max");
        machine.setEnableKVM(0);
        machine.setEnableMTTCG(1);
        machine.setTcgBuffer(1024);

        machine.setHdaImagePath(vdaPath);
        machine.setHdaInterface("virtio");
        machine.setHdbImagePath(vdbPath);
        machine.setHdbInterface("virtio");

        File firmwareCode = new File(QubeApplication.getBasefileDir(), "roms/QUBE_ARM_EFI.fd");
        if (!firmwareCode.isFile()) {
            throw new IllegalStateException("QEMU ARM64 UEFI firmware is missing");
        }
        machine.setBiosType("UEFI");
        machine.setBiosCode(firmwareCode.getAbsolutePath());
        machine.setBiosVars(efiVarsPath);

        machine.setVga("virtio-gpu-pci");
        machine.setRenderer(0);
        machine.setNetwork("User");
        machine.setNetworkCard("virtio-net-pci");
        machine.setMouse("usb-tablet");
        machine.setExtraParams("-device virtio-serial -device virtio-rng-pci");
    }
}
