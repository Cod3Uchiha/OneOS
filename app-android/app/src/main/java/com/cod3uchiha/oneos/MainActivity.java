package com.cod3uchiha.oneos;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.max2idea.android.qube.files.FileInstaller;
import com.max2idea.android.qube.machine.OneOSGuestProvisioner;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class MainActivity extends Activity {
    private static final int PICK_ANDROID_GUEST = 71;
    private static final String GUEST_URL = "https://github.com/jqssun/android-lineage-qemu/releases/download/v2026.11.17/UTM-VM-lineage-24.0-20261117-jqssun-virtio_arm64only.zip";
    private static final String GUEST_SHA256 = "812c498d80130e4ae79d15311a9a16d0e0cc66fa11ff4dbd91c2f35df83dbdc9";
    private TextView status;
    private Button launchButton;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xff10141b);
        getWindow().setNavigationBarColor(0xff10141b);

        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(24), dp(34), dp(24), dp(28));
        page.setBackgroundColor(0xff10141b);
        scroll.addView(page);

        TextView brand = text("ONEOS", 13, 0xff9aa9bd);
        brand.setLetterSpacing(0.18f);
        page.addView(brand);

        TextView title = text("A second Android.\nInside your phone.", 30, 0xfff4f7fb);
        title.setTypeface(null, 1);
        add(page, title, 14);

        TextView intro = text("A rootless ARM64 virtual machine. One UI remains your host system.", 15, 0xffb8c3d1);
        intro.setLineSpacing(dp(4), 1f);
        add(page, intro, 12);

        section(page, "HOST DEVICE");
        card(page, Build.MANUFACTURER + " " + Build.MODEL,
                "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\nHost ABI: " + Build.SUPPORTED_ABIS[0]);

        section(page, "VIRTUAL MACHINE");
        card(page, "QEMU 11.1.3 · ARM64",
                "Rootless QEMU system emulation using multi-threaded TCG. KVM is disabled; no root access or host partition changes are used.");

        section(page, "ANDROID 16 GUEST");
        card(page, "LineageOS 24 · ARM64 virtio",
                "Guest image built specifically for QEMU's ARM virt machine. Download is approximately 1.1 GB. The archive is SHA-256 verified before installation.");

        Button download = button("Download Android 16 guest · 1.1 GB");
        download.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(GUEST_URL))));
        add(page, download, 12);

        Button install = button("Install downloaded guest ZIP");
        install.setOnClickListener(v -> chooseGuestArchive());
        add(page, install, 8);

        status = text(guestStatus(), 13, 0xffaebaca);
        status.setLineSpacing(dp(3), 1f);
        add(page, status, 10);

        launchButton = button("Open OneOS VM manager");
        launchButton.setOnClickListener(v -> startActivity(new Intent(this, OneOSVmActivity.class)));
        add(page, launchButton, 16);
        launchButton.setEnabled(hasInstalledGuest());
        launchButton.setAlpha(launchButton.isEnabled() ? 1f : 0.45f);

        section(page, "HARDWARE AND PERFORMANCE");
        TextView note = text("The guest sees virtual ARM hardware, not the Snapdragon motherboard. TCG is software emulation and can be slower than native execution. The Adreno GPU, camera, modem, sensors and Samsung vendor drivers are not automatically passed through.", 13, 0xff8997a9);
        note.setLineSpacing(dp(3), 1f);
        add(page, note, 10);

        setContentView(scroll);
    }

    private void chooseGuestArchive() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/zip");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_ANDROID_GUEST);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_ANDROID_GUEST || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        status.setText("Preparing firmware and checking the Android guest archive...");
        FileInstaller.installFiles(this, false);
        installGuestArchive(data.getData());
    }

    private void installGuestArchive(Uri uri) {
        new Thread(() -> {
            File guestRoot = new File(getFilesDir(), "guest");
            File staging = new File(guestRoot, "android16.tmp");
            File installed = new File(guestRoot, "android16");
            deleteRecursively(staging);
            if (!staging.mkdirs() && !staging.isDirectory()) {
                showStatus("Installation failed: could not create guest storage.");
                return;
            }

            File vda = new File(staging, "vda.qcow2");
            File vdb = new File(staging, "vdb.qcow2");
            File vars = new File(staging, "efi_vars.fd");
            boolean foundVda = false;
            boolean foundVdb = false;
            boolean foundVars = false;
            long extracted = 0;

            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                InputStream raw = getContentResolver().openInputStream(uri);
                if (raw == null) throw new IllegalStateException("Could not open selected archive");
                DigestInputStream digested = new DigestInputStream(raw, digest);
                ZipInputStream zip = new ZipInputStream(digested);
                byte[] buffer = new byte[1024 * 1024];

                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName().replace('\\', '/');
                    File output = null;
                    if (name.endsWith("/Data/vda.qcow2") || name.equals("Data/vda.qcow2")) {
                        if (foundVda) throw new IllegalStateException("Archive contains duplicate vda.qcow2");
                        output = vda;
                        foundVda = true;
                    } else if (name.endsWith("/Data/vdb.qcow2") || name.equals("Data/vdb.qcow2")) {
                        if (foundVdb) throw new IllegalStateException("Archive contains duplicate vdb.qcow2");
                        output = vdb;
                        foundVdb = true;
                    } else if (name.endsWith("/Data/efi_vars.fd") || name.equals("Data/efi_vars.fd")) {
                        if (foundVars) throw new IllegalStateException("Archive contains duplicate efi_vars.fd");
                        output = vars;
                        foundVars = true;
                    }

                    if (output != null) {
                        runOnUiThread(() -> status.setText("Extracting Android 16 virtual disk files..."));
                        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(output))) {
                            int count;
                            while ((count = zip.read(buffer)) != -1) {
                                out.write(buffer, 0, count);
                                extracted += count;
                            }
                            out.flush();
                        }
                    }
                    zip.closeEntry();
                }

                while (digested.read(buffer) != -1) { }
                zip.close();
                String actualHash = toHex(digest.digest());
                if (!GUEST_SHA256.equals(actualHash)) {
                    throw new SecurityException("Guest archive checksum does not match the pinned Android 16 release");
                }
                if (!foundVda || !foundVdb || !foundVars || vda.length() < 1024 * 1024 || vdb.length() < 1024 * 1024 || vars.length() < 4096) {
                    throw new IllegalStateException("The archive is missing required ARM64 VM disk files");
                }

                deleteRecursively(installed);
                if (!staging.renameTo(installed)) throw new IllegalStateException("Could not finalize the guest image");
                FileInstaller.installFiles(this, false);
                OneOSGuestProvisioner.configure(
                        new File(installed, "vda.qcow2").getAbsolutePath(),
                        new File(installed, "vdb.qcow2").getAbsolutePath(),
                        new File(installed, "efi_vars.fd").getAbsolutePath());
                showStatus("Android 16 guest installed and VM configured. Open the VM manager and press Start.");
            } catch (Exception error) {
                deleteRecursively(staging);
                showStatus("Guest installation failed: " + error.getMessage());
            }
        }, "OneOS-Guest-Installer").start();
    }

    private boolean hasInstalledGuest() {
        File dir = new File(getFilesDir(), "guest/android16");
        return new File(dir, "vda.qcow2").isFile()
                && new File(dir, "vdb.qcow2").isFile()
                && new File(dir, "efi_vars.fd").isFile();
    }

    private String guestStatus() {
        return hasInstalledGuest()
                ? "Android 16 guest files found in private OneOS storage."
                : "No Android 16 guest installed yet.";
    }

    private void showStatus(String message) {
        runOnUiThread(() -> {
            status.setText(message);
            boolean ready = hasInstalledGuest();
            launchButton.setEnabled(ready);
            launchButton.setAlpha(ready ? 1f : 0.45f);
        });
    }

    private static String toHex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) value.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
        return value.toString();
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        file.delete();
    }

    private void section(LinearLayout page, String label) {
        TextView v = text(label, 12, 0xff8da0b7);
        v.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(26);
        p.bottomMargin = dp(10);
        page.addView(v, p);
    }

    private void card(LinearLayout page, String heading, String body) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(15), dp(16), dp(15));
        box.setBackgroundColor(0xff1b2430);
        TextView h = text(heading, 15, 0xfff4f7fb);
        h.setTypeface(null, 1);
        box.addView(h);
        TextView b = text(body, 13, 0xffaebaca);
        b.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.topMargin = dp(8);
        box.addView(b, bp);
        add(page, box, 10);
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(0xff10141b);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xffb9d7ff));
        b.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(54)));
        return b;
    }

    private void add(LinearLayout page, View view, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(top);
        page.addView(view, p);
    }

    private TextView text(String value, int size, int color) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        return v;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
