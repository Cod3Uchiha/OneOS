package com.cod3uchiha.oneos;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;


import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.FileInputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Locale;
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
        OneOSLog.init(getFilesDir());
        OneOSLog.info("APP_START", "UI created; API=" + Build.VERSION.SDK_INT + "; model=" + Build.MODEL);
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
        card(page, "QEMU 11.1.2 · ARM64",
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
        Button logs = button("View detailed installation logs");
        logs.setOnClickListener(v -> showInstallLogs());
        add(page, logs, 8);

        launchButton = button("Start OneOS");
        launchButton.setOnClickListener(v -> startActivity(new Intent(this, OneOSVmActivity.class)));
        add(page, launchButton, 16);
        launchButton.setEnabled(hasInstalledGuest());
        launchButton.setAlpha(launchButton.isEnabled() ? 1f : 0.45f);

        section(page, "HARDWARE AND PERFORMANCE");
        TextView note = text("The guest sees virtual ARM hardware, not the Snapdragon motherboard. Virgl can render guest 3D through Android EGL on the Adreno GPU, but this is mediated graphics, not direct GPU passthrough. Camera, modem, sensors and Samsung vendor drivers are not passed through. TCG CPU emulation can still be slower than native execution.", 13, 0xff8997a9);
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
        OneOSLog.info("PICKER", "Selected archive URI=" + data.getData());
        installGuestArchive(data.getData());
    }

    private void installGuestArchive(Uri uri) {
        new Thread(() -> {
            File root = new File(getFilesDir(), "guest");
            File staging = new File(root, "android16.tmp");
            File installed = new File(root, "android16");
            OneOSLog.info("INSTALL", "Start. uri=" + uri + "; staging=" + staging + "; freeBytes=" + root.getUsableSpace());
            deleteRecursively(staging);
            if (!staging.mkdirs() && !staging.isDirectory()) {
                IllegalStateException e = new IllegalStateException("Cannot create staging directory: " + staging);
                OneOSLog.error("STAGE_CREATE", "Unable to create guest staging directory.", e);
                showStatus("Cannot create guest storage. Open detailed logs.");
                return;
            }
            File vda = new File(staging, "vda.qcow2");
            File vdb = new File(staging, "vdb.qcow2");
            File vars = new File(staging, "efi_vars.fd");
            boolean foundA = false, foundB = false, foundVars = false;
            int seen = 0, matched = 0;
            long total = 0L;
            StringBuilder matchedPaths = new StringBuilder();
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                InputStream raw = getContentResolver().openInputStream(uri);
                if (raw == null) throw new IllegalStateException("Content provider returned null stream");
                ZipInputStream zip = new ZipInputStream(new DigestInputStream(raw, digest));
                byte[] buffer = new byte[262144];
                ZipEntry entry;
                OneOSLog.info("ZIP_SCAN", "ZIP stream opened; scanning entries.");
                while ((entry = zip.getNextEntry()) != null) {
                    seen++;
                    String name = entry.getName() == null ? "" : entry.getName().replace((char)92, '/');
                    String lower = name.toLowerCase(Locale.ROOT);
                    if (seen <= 250) OneOSLog.info("ZIP_ENTRY", "#" + seen + " name=" + name + " size=" + entry.getSize() + " compressed=" + entry.getCompressedSize() + " dir=" + entry.isDirectory());
                    File target = null;
                    String label = null;
                    if (lower.endsWith("/data/vda.qcow2") || lower.equals("data/vda.qcow2")) {
                        if (foundA) throw new IllegalStateException("Duplicate vda.qcow2 entry: " + name);
                        target = vda; foundA = true; label = "vda.qcow2";
                    } else if (lower.endsWith("/data/vdb.qcow2") || lower.equals("data/vdb.qcow2")) {
                        if (foundB) throw new IllegalStateException("Duplicate vdb.qcow2 entry: " + name);
                        target = vdb; foundB = true; label = "vdb.qcow2";
                    } else if (lower.endsWith("/data/efi_vars.fd") || lower.equals("data/efi_vars.fd")) {
                        if (foundVars) throw new IllegalStateException("Duplicate efi_vars.fd entry: " + name);
                        target = vars; foundVars = true; label = "efi_vars.fd";
                    }
                    if (target != null) {
                        matched++;
                        matchedPaths.append(label).append(" <- ").append(name).append("; ");
                        OneOSLog.info("EXTRACT", "Matched " + label + " at " + name + "; declaredSize=" + entry.getSize());
                        long fileSize = 0L;
                        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
                            int n;
                            while ((n = zip.read(buffer)) != -1) {
                                out.write(buffer, 0, n);
                                fileSize += n;
                                total += n;
                            }
                            out.flush();
                        }
                        OneOSLog.info("EXTRACT", "Completed " + label + "; bytesWritten=" + fileSize + "; fileLength=" + target.length());
                        String msg = "Extracted " + label + " (" + humanBytes(fileSize) + ")";
                        runOnUiThread(() -> status.setText(msg));
                    }
                    zip.closeEntry();
                }
                // getNextEntry() reaches EOF and consumes the ZIP central directory.
                // Do not read the underlying digest stream after closing ZipInputStream.
                zip.close();
                String actual = toHex(digest.digest());
                OneOSLog.info("ZIP_SCAN", "Complete. entries=" + seen + "; requiredMatches=" + matched + "; extractedBytes=" + total + "; paths=" + matchedPaths);
                OneOSLog.info("CHECKSUM", "expected=" + GUEST_SHA256 + "; actual=" + actual);
                if (!GUEST_SHA256.equals(actual)) throw new SecurityException("SHA-256 mismatch: expected " + GUEST_SHA256 + " but got " + actual);
                if (!foundA || !foundB || !foundVars) {
                    String missing = (!foundA ? "vda.qcow2 " : "") + (!foundB ? "vdb.qcow2 " : "") + (!foundVars ? "efi_vars.fd" : "");
                    throw new IllegalStateException("Missing " + missing + ". Scanned " + seen + " ZIP entries. Expected <bundle>.utm/Data/<file>. See ZIP_ENTRY log records.");
                }
                // The upstream bundle intentionally includes a small auxiliary vdb disk.
                // Do not impose an arbitrary 1 MiB minimum; validate the QCOW2 header.
                if (vda.length() < 104L || vdb.length() < 104L || vars.length() < 4096L) {
                    throw new IllegalStateException("Extracted file is empty or too small to contain its required header: vda=" + vda.length() + "; vdb=" + vdb.length() + "; efi_vars=" + vars.length());
                }
                boolean vdaQcow2 = hasQcow2Header(vda);
                boolean vdbQcow2 = hasQcow2Header(vdb);
                if (!vdaQcow2 || !vdbQcow2) {
                    throw new IllegalStateException("Disk format validation failed: expected QCOW2 magic QFI\\u001f\\u00fb. vdaHeaderValid=" + vdaQcow2 + "; vdbHeaderValid=" + vdbQcow2);
                }
                OneOSLog.info("VALIDATE", "QCOW2 headers and file sizes passed. vda=" + vda.length() + "; vdb=" + vdb.length() + " (small auxiliary disks allowed); efi_vars=" + vars.length());
                deleteRecursively(installed);
                if (!staging.renameTo(installed)) throw new IllegalStateException("Could not move staging directory to final guest location.");
                OneOSLog.info("SUCCESS", "Guest extraction, checksum and QCOW2 validation completed. QEMU will attach these files directly.");
                showStatus("Android 16 guest imported. Open OneOS and press Start OS.");
            } catch (Throwable error) {
                OneOSLog.error("INSTALL_FAILED", "seenEntries=" + seen + "; matchedEntries=" + matched + "; extractedBytes=" + total + "; foundVda=" + foundA + "; foundVdb=" + foundB + "; foundEfiVars=" + foundVars, error);
                deleteRecursively(staging);
                showStatus("Guest installation failed: " + error.getMessage() + ". Open detailed logs.");
            }
        }, "OneOS-Guest-Installer").start();
    }

    private void showInstallLogs() {
        File file = OneOSLog.getLogFile();
        if (file == null || !file.isFile()) {
            new AlertDialog.Builder(this).setTitle("OneOS logs").setMessage("No log file found.").setPositiveButton("OK", null).show();
            return;
        }
        try (FileInputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            String content = out.toString("UTF-8");
            ScrollView scroll = new ScrollView(this);
            TextView body = text(content, 11, 0xffd7e0eb);
            body.setTextIsSelectable(true);
            body.setPadding(dp(12), dp(12), dp(12), dp(12));
            scroll.addView(body);
            new AlertDialog.Builder(this).setTitle("Detailed OneOS logs").setView(scroll)
                    .setPositiveButton("Close", null)
                    .setNeutralButton("Share", (dialog, which) -> {
                        Intent send = new Intent(Intent.ACTION_SEND);
                        send.setType("text/plain");
                        send.putExtra(Intent.EXTRA_TEXT, content);
                        startActivity(Intent.createChooser(send, "Share OneOS logs"));
                    }).show();
        } catch (Exception e) {
            OneOSLog.error("LOG_VIEWER", "Unable to display log file.", e);
            new AlertDialog.Builder(this).setTitle("Log viewer").setMessage(e.toString()).setPositiveButton("OK", null).show();
        }
    }

    private static boolean hasQcow2Header(File file) {
        if (file == null || !file.isFile() || file.length() < 4L) return false;
        try (InputStream in = new FileInputStream(file)) {
            return in.read() == 0x51 && in.read() == 0x46 && in.read() == 0x49 && in.read() == 0xfb;
        } catch (Exception e) {
            OneOSLog.error("DISK_HEADER", "Unable to read QCOW2 header for " + file, e);
            return false;
        }
    }

    private static String humanBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        if (bytes < 1048576L) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        if (bytes < 1073741824L) return String.format(Locale.US, "%.1f MB", bytes / 1048576.0);
        return String.format(Locale.US, "%.2f GB", bytes / 1073741824.0);
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
