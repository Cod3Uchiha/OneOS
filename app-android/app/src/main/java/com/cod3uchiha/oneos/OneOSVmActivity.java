package com.cod3uchiha.oneos;

import android.os.Bundle;

import com.max2idea.android.qube.log.Logger;
import com.max2idea.android.qube.main.Config;
import com.max2idea.android.qube.main.QubeActivity;
import com.max2idea.android.qube.main.QubeApplication;

/** Rootless ARM64 QEMU frontend. TCG is forced because stock Android does not expose KVM to apps. */
public final class OneOSVmActivity extends QubeActivity {
    @Override
    public void onCreate(Bundle state) {
        QubeApplication.arch = Config.Arch.arm64;
        Config.clientClass = getClass();
        Config.enableKVM = false;
        Config.enableMTTCG = true;
        Config.enableEmulatedFloppy = false;
        Config.machineFolder = "machines/oneos/arm64/";
        super.onCreate(state);
        Logger.setupLogFile("/oneos/oneos-vm.log");
    }

    @Override
    protected void loadQEMULib() {
        System.loadLibrary("qemu-system-aarch64");
    }
}
