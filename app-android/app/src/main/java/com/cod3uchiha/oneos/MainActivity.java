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
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public final class MainActivity extends Activity {
    private static final int PICK_GUEST_IMAGE = 41;
    private TextView imageStatus;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xff10141b);
        getWindow().setNavigationBarColor(0xff10141b);
        buildScreen();
    }

    private void buildScreen() {
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
        TextView intro = text("Rootless guest-system workspace. One UI stays installed and remains the host.", 15, 0xffb8c3d1);
        intro.setLineSpacing(dp(4), 1f);
        add(page, intro, 12);

        section(page, "HOST DEVICE");
        card(page, "Samsung / Android", Build.MANUFACTURER + " " + Build.MODEL
                + "\nAndroid " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT
                + ")\nABI: " + Build.SUPPORTED_ABIS[0]);

        section(page, "GUEST SYSTEM");
        card(page, "VM engine not installed",
                "The previous root-based system switcher has been removed. This build does not invoke root commands, modify partitions, or replace One UI components.");
        imageStatus = text(imageLabel(), 14, 0xffb8c3d1);
        cardView(page, imageStatus, "Guest images are copied into OneOS private app storage.");
        Button importButton = button("Import guest disk image");
        importButton.setOnClickListener(v -> chooseImage());
        add(page, importButton, 10);

        Button startButton = button("Start guest Android");
        startButton.setEnabled(false);
        startButton.setAlpha(0.45f);
        add(page, startButton, 10);
        TextView note = text("Guest boot is not available yet. A real ARM64 VM backend and a compatible Android guest image must be integrated before this action can be enabled.", 13, 0xff8997a9);
        note.setLineSpacing(dp(3), 1f);
        add(page, note, 12);

        section(page, "HARDWARE ACCESS");
        card(page, "Host hardware stays with One UI",
                "CPU, memory and storage are shared by Android. GPU, camera, modem, sensors and Samsung vendor drivers are not automatically passed into a guest VM. Supported host features need explicit Android API bridges.");
        setContentView(scroll);
    }

    private void chooseImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_GUEST_IMAGE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_GUEST_IMAGE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            File dir = new File(getFilesDir(), "guest");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Cannot create guest directory");
            File target = new File(dir, "guest-disk.img");
            File temp = new File(dir, "guest-disk.img.part");
            try (InputStream in = getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(temp)) {
                if (in == null) throw new IllegalStateException("Cannot open selected file");
                byte[] buffer = new byte[1024 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                out.getFD().sync();
            }
            if (!temp.renameTo(target)) {
                if (target.exists()) target.delete();
                if (!temp.renameTo(target)) throw new IllegalStateException("Cannot finalize image copy");
            }
            imageStatus.setText(imageLabel());
        } catch (Exception e) {
            imageStatus.setText("Import failed: " + e.getMessage());
        }
    }

    private String imageLabel() {
        File image = new File(new File(getFilesDir(), "guest"), "guest-disk.img");
        return image.exists() ? "Guest image stored · " + android.text.format.Formatter.formatFileSize(this, image.length()) : "No guest image imported";
    }

    private void section(LinearLayout page, String label) {
        TextView v = text(label, 12, 0xff8da0b7);
        v.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams p = lp(-1, -2);
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
        LinearLayout.LayoutParams bp = lp(-1, -2);
        bp.topMargin = dp(8);
        box.addView(b, bp);
        add(page, box, 10);
    }

    private void cardView(LinearLayout page, TextView heading, String body) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(15), dp(16), dp(15));
        box.setBackgroundColor(0xff1b2430);
        box.addView(heading);
        TextView b = text(body, 13, 0xffaebaca);
        b.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams bp = lp(-1, -2);
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
        b.setLayoutParams(lp(-1, dp(54)));
        return b;
    }

    private void add(LinearLayout page, View view, int top) {
        LinearLayout.LayoutParams p = lp(-1, -2);
        p.topMargin = dp(top);
        page.addView(view, p);
    }
    private TextView text(String value, int size, int color) {
        TextView v = new TextView(this);
        v.setText(value); v.setTextSize(size); v.setTextColor(color);
        return v;
    }
    private LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }
    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }
}
