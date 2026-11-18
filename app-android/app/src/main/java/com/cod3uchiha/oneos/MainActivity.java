package com.cod3uchiha.oneos;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class MainActivity extends Activity {
    private static final int PICK_OS_IMAGE = 71;
    private TextView status;
    private TextView storageValue;
    private Button startButton;
    private SeekBar storageSeek;
    private int storageGb = 30;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        OneOSLog.init(getFilesDir());
        OneOSLog.info("APP_START", "OneOS standalone QEMU UI started; API=" + android.os.Build.VERSION.SDK_INT + "; model=" + android.os.Build.MODEL);
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
        TextView title = text("Your OS. Inside your phone.", 29, 0xfff4f7fb);
        title.setTypeface(null, 1);
        add(page, title, 14);
        TextView intro = text("Import an ARM64 OS image, choose its virtual disk capacity, then start it. OneOS uses standalone QEMU without root.", 15, 0xffb8c3d1);
        intro.setLineSpacing(dp(4), 1f);
        add(page, intro, 12);

        section(page, "1 · OS IMAGE");
        Button importButton = button("Import OS image");
        importButton.setOnClickListener(v -> chooseImage());
        add(page, importButton, 8);
        status = text(installedStatus(), 13, 0xffaebaca);
        status.setLineSpacing(dp(3), 1f);
        add(page, status, 10);

        section(page, "2 · VIRTUAL STORAGE");
        storageValue = text("30 GB", 22, 0xfff4f7fb);
        storageValue.setTypeface(null, 1);
        add(page, storageValue, 4);
        storageSeek = new SeekBar(this);
        storageSeek.setMax(120);
        storageSeek.setProgress(22);
        storageSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                storageGb = 8 + progress;
                storageValue.setText(storageGb + " GB");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        add(page, storageSeek, 4);
        TextView hint = text("8–128 GB. The virtual disk grows as the guest writes data; it does not reserve the full capacity immediately.", 12, 0xff8997a9);
        hint.setLineSpacing(dp(3), 1f);
        add(page, hint, 4);

        section(page, "3 · RUN");
        startButton = button("Start OneOS");
        startButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, OneOSVmActivity.class);
            intent.putExtra("storage_gb", storageGb);
            startActivity(intent);
        });
        add(page, startButton, 8);
        startButton.setEnabled(hasInstalledImage());
        startButton.setAlpha(startButton.isEnabled() ? 1f : 0.45f);

        Button logs = button("View OneOS logs");
        logs.setOnClickListener(v -> showLogs());
        add(page, logs, 8);

        section(page, "COMPATIBILITY");
        TextView note = text("OneOS runs ARM64 guests on QEMU's virtual ARM machine using software CPU emulation. The guest does not use the phone's Samsung drivers, camera, modem or sensors. Images must support ARM64 and the virtual hardware. Some OS images may need different firmware or boot settings.", 13, 0xff8997a9);
        note.setLineSpacing(dp(3), 1f);
        add(page, note, 8);
        setContentView(scroll);
    }

    private void chooseImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_OS_IMAGE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_OS_IMAGE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        status.setText("Importing and validating OS image…");
        startButton.setEnabled(false);
        new Thread(() -> importImage(uri), "OneOS-Image-Import").start();
    }

    private void importImage(Uri uri) {
        File root = new File(getFilesDir(), "guest");
        File staging = new File(getFilesDir(), "guest.tmp");
        deleteRecursively(staging);
        if (!staging.mkdirs() && !staging.isDirectory()) {
            showStatus("Cannot create private guest storage.");
            return;
        }
        try {
            String name = displayName(uri);
            String lower = name.toLowerCase(Locale.ROOT);
            OneOSLog.info("IMPORT", "Selected image " + name + "; uri=" + uri);
            if (lower.endsWith(".zip")) {
                extractArchive(uri, staging);
            } else {
                String extension = extension(lower);
                File target = new File(staging, targetName(extension));
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    if (in == null) throw new IllegalStateException("Could not open selected image.");
                    copy(in, target);
                }
            }
            File os = findOsImage(staging);
            if (os == null || os.length() == 0) throw new IllegalStateException("No supported .qcow2, .img, .raw or .iso image was found.");
            if (os.getName().endsWith(".qcow2") && !hasQcow2Header(os)) throw new IllegalStateException("The selected QCOW2 image has an invalid header.");
            File vars = new File(staging, "efi_vars.fd");
            if (!vars.isFile()) copyAsset("firmware/UEFI_VARS.fd", vars);
            deleteRecursively(root);
            if (!staging.renameTo(root)) throw new IllegalStateException("Could not finalize imported OS image.");
            OneOSLog.info("IMPORT_SUCCESS", "OS image imported: " + os.getName() + "; bytes=" + os.length());
            showStatus("Imported " + os.getName() + " successfully.");
        } catch (Throwable e) {
            OneOSLog.error("IMPORT_FAILED", "OS image import failed.", e);
            deleteRecursively(staging);
            showStatus("Import failed: " + e.getMessage());
        }
    }

    private void extractArchive(Uri uri, File staging) throws Exception {
        File generic = new File(staging, "candidate.tmp");
        String genericName = null;
        long genericSize = -1;
        boolean foundVda = false;
        try (InputStream raw = getContentResolver().openInputStream(uri);
             ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw))) {
            if (raw == null) throw new IllegalStateException("Could not open ZIP archive.");
            byte[] buffer = new byte[131072];
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String path = entry.getName() == null ? "" : entry.getName().replace((char)92, '/');
                String lower = path.toLowerCase(Locale.ROOT);
                File target = null;
                if (lower.endsWith("/data/vda.qcow2") || lower.equals("data/vda.qcow2") || lower.equals("vda.qcow2")) {
                    if (foundVda) throw new IllegalStateException("Archive contains more than one vda.qcow2.");
                    target = new File(staging, "os.qcow2");
                    foundVda = true;
                } else if (lower.endsWith("/data/vdb.qcow2") || lower.equals("data/vdb.qcow2") || lower.equals("vdb.qcow2")) {
                    target = new File(staging, "aux.qcow2");
                } else if (lower.endsWith("/data/efi_vars.fd") || lower.equals("data/efi_vars.fd") || lower.endsWith("/efi_vars.fd")) {
                    target = new File(staging, "efi_vars.fd");
                } else {
                    String ext = extension(lower);
                    if (".qcow2".equals(ext) || ".img".equals(ext) || ".raw".equals(ext) || ".iso".equals(ext)) {
                        File temp = new File(staging, "candidate-" + ext.substring(1));
                        long written = copy(zip, temp);
                        if (written > genericSize) {
                            generic.delete();
                            if (!temp.renameTo(generic)) throw new IllegalStateException("Could not stage image from archive.");
                            genericName = targetName(ext);
                            genericSize = written;
                        } else temp.delete();
                    }
                }
                if (target != null) copy(zip, target);
                zip.closeEntry();
            }
        }
        if (!foundVda && genericSize > 0 && genericName != null) {
            File target = new File(staging, genericName);
            if (!generic.renameTo(target)) throw new IllegalStateException("Could not finalize image from archive.");
        } else generic.delete();
    }

    private File findOsImage(File dir) {
        for (String name : new String[]{"os.qcow2", "os.img", "os.raw", "installer.iso"}) {
            File file = new File(dir, name);
            if (file.isFile()) return file;
        }
        return null;
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) { }
        return "imported-image";
    }

    private static String extension(String name) {
        if (name.endsWith(".qcow2")) return ".qcow2";
        if (name.endsWith(".iso")) return ".iso";
        if (name.endsWith(".raw")) return ".raw";
        if (name.endsWith(".img")) return ".img";
        return "";
    }

    private static String targetName(String extension) {
        if (".iso".equals(extension)) return "installer.iso";
        if (".img".equals(extension)) return "os.img";
        if (".raw".equals(extension)) return "os.raw";
        return "os.qcow2";
    }

    private long copy(InputStream in, File target) throws Exception {
        long total = 0;
        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
            byte[] buffer = new byte[131072];
            int n;
            while ((n = in.read(buffer)) != -1) { out.write(buffer, 0, n); total += n; }
            out.flush();
        }
        return total;
    }

    private void copyAsset(String asset, File target) throws Exception {
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IllegalStateException("Cannot create firmware directory.");
        try (InputStream in = getAssets().open(asset); FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[65536];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            out.getFD().sync();
        }
    }

    private static boolean hasQcow2Header(File file) {
        try (FileInputStream in = new FileInputStream(file)) {
            return in.read() == 0x51 && in.read() == 0x46 && in.read() == 0x49 && in.read() == 0xfb;
        } catch (Exception e) { return false; }
    }

    private boolean hasInstalledImage() { return findOsImage(new File(getFilesDir(), "guest")) != null; }
    private String installedStatus() {
        File os = findOsImage(new File(getFilesDir(), "guest"));
        return os == null ? "No OS image imported yet." : "Installed image: " + os.getName();
    }

    private void showStatus(String message) {
        runOnUiThread(() -> {
            status.setText(message);
            boolean ready = hasInstalledImage();
            startButton.setEnabled(ready);
            startButton.setAlpha(ready ? 1f : 0.45f);
        });
    }

    private void showLogs() {
        File file = OneOSLog.getLogFile();
        if (file == null || !file.isFile()) {
            new AlertDialog.Builder(this).setTitle("OneOS logs").setMessage("No log file found.").setPositiveButton("OK", null).show();
            return;
        }
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[(int) Math.min(file.length(), 2_000_000L)];
            int n = in.read(buffer);
            String content = new String(buffer, 0, Math.max(0, n), java.nio.charset.StandardCharsets.UTF_8);
            TextView body = text(content, 11, 0xffd7e0eb);
            body.setTextIsSelectable(true);
            body.setPadding(dp(12), dp(12), dp(12), dp(12));
            ScrollView scroll = new ScrollView(this);
            scroll.addView(body);
            new AlertDialog.Builder(this).setTitle("OneOS logs").setView(scroll)
                    .setPositiveButton("Close", null)
                    .setNeutralButton("Share", (dialog, which) -> {
                        Intent send = new Intent(Intent.ACTION_SEND);
                        send.setType("text/plain");
                        send.putExtra(Intent.EXTRA_TEXT, content);
                        startActivity(Intent.createChooser(send, "Share OneOS logs"));
                    }).show();
        } catch (Exception e) {
            OneOSLog.error("LOG_VIEWER", "Could not display log file.", e);
        }
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
    private void add(LinearLayout page, View view, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(top);
        page.addView(view, p);
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
    private TextView text(String value, int size, int color) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        return v;
    }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
}
