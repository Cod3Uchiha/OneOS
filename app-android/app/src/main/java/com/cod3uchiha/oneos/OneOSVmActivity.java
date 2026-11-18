package com.cod3uchiha.oneos;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/** OneOS-owned standalone QEMU launcher. */
public final class OneOSVmActivity extends Activity {
    private Process qemu;
    private TextView status;
    private NativeGuestDisplay display;
    private int storageGb = 30;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        storageGb = Math.max(8, getIntent().getIntExtra("storage_gb", 30));
        getWindow().setStatusBarColor(0xff10141b);
        getWindow().setNavigationBarColor(0xff10141b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xff10141b);
        status = new TextView(this);
        status.setTextColor(0xffd8e2ef);
        status.setText("OneOS is stopped · virtual storage " + storageGb + " GB");
        status.setPadding(dp(16), dp(12), dp(16), dp(12));
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));
        display = new NativeGuestDisplay(this);
        root.addView(display, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(dp(12), dp(8), dp(12), dp(8));
        Button start = new Button(this);
        start.setText("Start OS");
        start.setAllCaps(false);
        start.setOnClickListener(v -> startGuest());
        Button stop = new Button(this);
        stop.setText("Stop OS");
        stop.setAllCaps(false);
        stop.setOnClickListener(v -> stopGuest());
        actions.addView(start, new LinearLayout.LayoutParams(0, dp(52), 1f));
        actions.addView(stop, new LinearLayout.LayoutParams(0, dp(52), 1f));
        root.addView(actions);
        setContentView(root);
        
    }

    private void startGuest() {
        if (qemu != null && qemu.isAlive()) { setStatus("OneOS is already running."); return; }
        try {
            File guest = new File(getFilesDir(), "guest");
            File iso = new File(guest, "installer.iso");
            File osQcow = new File(guest, "os.qcow2");
            File osImg = new File(guest, "os.img");
            File osRaw = new File(guest, "os.raw");
            File osDisk = iso.isFile() ? new File(guest, "system.qcow2")
                    : osQcow.isFile() ? osQcow : osImg.isFile() ? osImg : osRaw;
            if (!iso.isFile() && !osDisk.isFile()) throw new IllegalStateException("No supported OS image is installed.");
            File firmware = new File(getFilesDir(), "firmware/UEFI_CODE.fd");
            File vars = new File(guest, "efi_vars.fd");
            File qemuImg = new File(getFilesDir(), "bin/qemu-img");
            File executable = new File(getApplicationInfo().nativeLibraryDir, "libqemu-system-aarch64.so");
            File slirp = new File(getApplicationInfo().nativeLibraryDir, "libslirp.so");
            if (!executable.isFile() || !executable.canExecute()) throw new IllegalStateException("Standalone Android QEMU runtime is missing or not executable.");
            if (!slirp.isFile()) throw new IllegalStateException("QEMU networking library is missing.");
            if (!firmware.isFile()) copyAsset("firmware/UEFI_CODE.fd", firmware);
            if (!vars.isFile() || vars.length() != 64L * 1024L * 1024L) {
                if (vars.isFile()) vars.renameTo(new File(guest, "imported_efi_vars.fd"));
                copyAsset("firmware/UEFI_VARS.fd", vars);
            }
            if (!qemuImg.isFile()) copyAsset("bin/qemu-img", qemuImg);
            if (!qemuImg.setExecutable(true, true) && !qemuImg.canExecute()) throw new IllegalStateException("Could not prepare qemu-img.");

            ensureVirtualDisk(qemuImg, osDisk, storageGb);

            File qmpSocket = new File(guest, "qmp.sock");
            if (qmpSocket.exists() && !qmpSocket.delete()) throw new IllegalStateException("Cannot clear the previous QMP socket.");
            List<String> args = new ArrayList<>();
            args.add(executable.getAbsolutePath());
            add(args, "-name", "OneOS");
            add(args, "-machine", "virt,gic-version=3");
            add(args, "-cpu", "max");
            add(args, "-accel", "tcg,thread=multi");
            add(args, "-smp", "4");
            add(args, "-m", "4096");
            add(args, "-drive", "if=pflash,format=raw,unit=0,readonly=on,file=" + firmware.getAbsolutePath());
            add(args, "-drive", "if=pflash,format=raw,unit=1,file=" + vars.getAbsolutePath());
            add(args, "-drive", "if=none,id=osdisk,file=" + osDisk.getAbsolutePath() + ",format=" + (osDisk.getName().endsWith(".img") || osDisk.getName().endsWith(".raw") ? "raw" : "qcow2"));
            add(args, "-device", "virtio-blk-pci,drive=osdisk");
            if (iso.isFile()) {
                add(args, "-device", "virtio-scsi-pci,id=scsi0");
                add(args, "-drive", "if=none,id=installmedia,file=" + iso.getAbsolutePath() + ",format=raw,media=cdrom,readonly=on");
                add(args, "-device", "scsi-cd,drive=installmedia");
                add(args, "-boot", "order=d");
            }
            File aux = new File(guest, "aux.qcow2");
            if (aux.isFile()) {
                add(args, "-drive", "if=none,id=auxdisk,file=" + aux.getAbsolutePath() + ",format=qcow2");
                add(args, "-device", "virtio-blk-pci,drive=auxdisk");
            }
            add(args, "-device", "virtio-gpu-pci");
            add(args, "-device", "virtio-keyboard-pci");
            add(args, "-device", "virtio-tablet-pci");
            add(args, "-device", "virtio-rng-pci");
            add(args, "-netdev", "user,id=net0");
            add(args, "-device", "virtio-net-pci,netdev=net0");
            add(args, "-display", "none");
            add(args, "-qmp", "unix:" + qmpSocket.getAbsolutePath() + ",server=on,wait=off");
            add(args, "-serial", "file:" + new File(guest, "serial.log").getAbsolutePath());
            add(args, "-monitor", "none");
            args.add("-no-reboot");

            ProcessBuilder builder = new ProcessBuilder(args);
            builder.directory(guest);
            builder.environment().put("LD_LIBRARY_PATH", getApplicationInfo().nativeLibraryDir + ":" + getFilesDir().getAbsolutePath());
            builder.redirectErrorStream(true);
            qemu = builder.start();
            OneOSLog.info("QEMU_START", "Standalone QEMU launched; storageGb=" + storageGb + "; command=" + args);
            setStatus("Starting guest OS…");
            display.start(qmpSocket);
            final Process launched = qemu;
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(launched.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) OneOSLog.info("QEMU", line);
                } catch (Exception e) { OneOSLog.error("QEMU_LOG", "Could not read QEMU output.", e); }
                int code = -1;
                try { code = launched.waitFor(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                final int exit = code;
                runOnUiThread(() -> {
                    if (qemu == launched) qemu = null;
                    setStatus("Guest stopped (QEMU exit " + exit + "). Check OneOS logs for details.");
                });
                OneOSLog.info("QEMU_EXIT", "QEMU process exited with code " + exit);
            }, "OneOS-QEMU-Log").start();
        } catch (Throwable e) {
            OneOSLog.error("QEMU_START_FAILED", "Could not start standalone QEMU.", e);
            setStatus("Could not start guest: " + e.getMessage());
        }
    }

    private void ensureVirtualDisk(File qemuImg, File disk, int sizeGb) throws Exception {
        if (!disk.isFile()) {
            runTool(qemuImg, "create", "-f", "qcow2", disk.getAbsolutePath(), sizeGb + "G");
            return;
        }
        String info = runTool(qemuImg, "info", "--output=json", disk.getAbsolutePath());
        long virtualSize = new JSONObject(info).optLong("virtual-size", 0L);
        long requested = sizeGb * 1024L * 1024L * 1024L;
        if (virtualSize > 0 && virtualSize < requested) {
            runTool(qemuImg, "resize", disk.getAbsolutePath(), sizeGb + "G");
        }
    }

    private String runTool(File executable, String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(executable.getAbsolutePath());
        for (String argument : arguments) command.add(argument);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().put("LD_LIBRARY_PATH", getApplicationInfo().nativeLibraryDir + ":" + getFilesDir().getAbsolutePath());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) output.append(line).append('\n');
        }
        int code = process.waitFor();
        if (code != 0) throw new IllegalStateException("qemu-img failed (" + code + "): " + output);
        return output.toString();
    }

    private void stopGuest() {
        Process running = qemu;
        if (running == null || !running.isAlive()) { setStatus("OneOS is already stopped."); return; }
        running.destroy();
        setStatus("Stopping guest OS…");
    }

    private void copyAsset(String asset, File target) throws Exception {
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IllegalStateException("Cannot create " + parent);
        try (InputStream in = getAssets().open(asset); FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[65536];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            out.getFD().sync();
        }
    }

    private static void add(List<String> args, String key, String value) { args.add(key); args.add(value); }
    private void setStatus(String message) { runOnUiThread(() -> status.setText(message)); }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    @Override protected void onDestroy() {
        stopGuest();
        if (display != null) display.stop();
        super.onDestroy();
    }
}
