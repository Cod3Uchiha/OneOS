package com.cod3uchiha.oneos;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class MainActivity extends Activity {
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

        TextView title = text("A second system.\nInside your phone.", 30, 0xfff4f7fb);
        title.setTypeface(null, 1);
        add(page, title, 14);

        TextView intro = text("A rootless ARM64 virtual machine. One UI remains your host system.", 15, 0xffb8c3d1);
        intro.setLineSpacing(dp(4), 1f);
        add(page, intro, 12);

        section(page, "HOST DEVICE");
        card(page, Build.MANUFACTURER + " " + Build.MODEL,
                "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\nHost ABI: " + Build.SUPPORTED_ABIS[0]);

        section(page, "VIRTUAL MACHINE");
        card(page, "QEMU 11.1.3 · ARM64 system emulation",
                "Runs as an ordinary app using QEMU TCG software emulation. No root, boot changes, partition writes or KVM permission are used. Multi-threaded TCG is enabled where supported.");

        Button launch = new Button(this);
        launch.setText("Open OneOS VM manager");
        launch.setAllCaps(false);
        launch.setTextSize(16);
        launch.setTextColor(0xff10141b);
        launch.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xffb9d7ff));
        launch.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(56)));
        launch.setOnClickListener(v -> startActivity(new Intent(this, OneOSVmActivity.class)));
        add(page, launch, 20);

        section(page, "ANDROID GUEST STATUS");
        card(page, "VM engine integrated; guest image is separate",
                "The VM manager can create and run ARM64 virtual machines. A bootable Android guest still needs a kernel, ramdisk and system/vendor images built for QEMU's ARM virt machine. A random phone firmware or generic system.img is not sufficient.");

        TextView note = text("Performance depends on the guest workload. TCG emulates guest CPU instructions in software; it does not directly expose the Snapdragon GPU, camera, modem or Samsung vendor drivers to the guest.", 13, 0xff8997a9);
        note.setLineSpacing(dp(3), 1f);
        add(page, note, 14);
        setContentView(scroll);
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
