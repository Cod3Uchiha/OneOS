package com.cod3uchiha.oneos;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.OutputStreamWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Native Android guest display. QEMU renders its primary display to PNG through
 * QMP screendump; this View draws the frame and forwards touch/key events through
 * QMP input-send-event. No VNC, WebView, HTTP server, or network listener.
 */
public final class NativeGuestDisplay extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService qmpThread = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "OneOS-native-display");
        t.setDaemon(true);
        return t;
    });
    private final Object qmpLock = new Object();
    private volatile Bitmap frame;
    private volatile File socketPath;
    private volatile LocalSocket socket;
    private volatile BufferedReader reader;
    private volatile BufferedWriter writer;
    private volatile int requestId = 1;
    private float imageLeft, imageTop, imageWidth, imageHeight;

    public NativeGuestDisplay(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    public void start(File qmpSocket) {
        stop();
        socketPath = qmpSocket;
        running.set(true);
        qmpThread.execute(() -> displayLoop(qmpSocket));
    }

    public void stop() {
        running.set(false);
        synchronized (qmpLock) {
            closeQmp();
        }
        Bitmap old = frame;
        frame = null;
        if (old != null && !old.isRecycled()) old.recycle();
        postInvalidate();
    }

    private void displayLoop(File path) {
        File png = new File(getContext().getCacheDir(), "oneos-frame.png");
        try {
            connectQmp(path);
            command("qmp_capabilities", null);
            while (running.get()) {
                if (png.exists()) // QEMU screendump requires a new output file.
                    //noinspection ResultOfMethodCallIgnored
                    png.delete();
                JSONObject args = new JSONObject();
                args.put("filename", png.getAbsolutePath());
                args.put("format", "png");
                command("screendump", args);
                if (png.isFile() && png.length() > 64) {
                    Bitmap next = BitmapFactory.decodeFile(png.getAbsolutePath());
                    if (next != null) {
                        Bitmap previous = frame;
                        frame = next;
                        if (previous != null && !previous.isRecycled()) previous.recycle();
                        postInvalidate();
                    }
                }
                Thread.sleep(350); // ~2.8 FPS; conservative for mobile CPU and PNG capture.
            }
        } catch (Exception e) {
            OneOSLog.error("NATIVE_DISPLAY", "Native QMP display stopped: " + e.getMessage(), e);
        } finally {
            synchronized (qmpLock) { closeQmp(); }
            if (png.exists()) //noinspection ResultOfMethodCallIgnored
                png.delete();
        }
    }

    private void connectQmp(File path) throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt < 50 && running.get(); attempt++) {
            LocalSocket candidate = new LocalSocket();
            try {
                candidate.connect(new LocalSocketAddress(path.getAbsolutePath(),
                        LocalSocketAddress.Namespace.FILESYSTEM));
                candidate.setSoTimeout(8000);
                BufferedReader in = new BufferedReader(new java.io.InputStreamReader(candidate.getInputStream()));
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(candidate.getOutputStream()));
                String greeting = in.readLine();
                if (greeting == null || !greeting.contains("QMP"))
                    throw new IllegalStateException("QMP greeting missing");
                synchronized (qmpLock) {
                    socket = candidate;
                    reader = in;
                    writer = out;
                }
                return;
            } catch (Exception e) {
                last = e;
                try { candidate.close(); } catch (Exception ignored) { }
                Thread.sleep(250);
            }
        }
        throw new IllegalStateException("Could not connect to QEMU control socket", last);
    }

    private JSONObject command(String name, JSONObject arguments) throws Exception {
        synchronized (qmpLock) {
            if (writer == null || reader == null) throw new IllegalStateException("QMP is disconnected");
            JSONObject request = new JSONObject();
            request.put("execute", name);
            request.put("id", requestId++);
            if (arguments != null) request.put("arguments", arguments);
            writer.write(request.toString());
            writer.newLine();
            writer.flush();
            String line;
            while ((line = reader.readLine()) != null) {
                JSONObject response = new JSONObject(line);
                if (response.has("error"))
                    throw new IllegalStateException(response.getJSONObject("error").optString("desc", "QMP command failed"));
                if (response.has("return")) return response;
            }
            throw new IllegalStateException("QMP closed the connection");
        }
    }

    private void sendEvents(JSONArray events) {
        if (!running.get()) return;
        try {
            JSONObject args = new JSONObject();
            args.put("events", events);
            command("input-send-event", args);
        } catch (Exception e) {
            OneOSLog.error("NATIVE_INPUT", "Could not forward guest input.", e);
        }
    }

    private JSONObject event(String type, JSONObject data) throws Exception {
        JSONObject event = new JSONObject();
        event.put("type", type);
        event.put("data", data);
        return event;
    }

    private void sendPointer(float x, float y, boolean pressed) {
        Bitmap current = frame;
        if (current == null || imageWidth <= 0 || imageHeight <= 0) return;
        int px = Math.max(0, Math.min(32767, Math.round((x - imageLeft) / imageWidth * 32767f)));
        int py = Math.max(0, Math.min(32767, Math.round((y - imageTop) / imageHeight * 32767f)));
        try {
            JSONArray events = new JSONArray();
            JSONObject dx = new JSONObject(); dx.put("axis", "x"); dx.put("value", px);
            JSONObject dy = new JSONObject(); dy.put("axis", "y"); dy.put("value", py);
            events.put(event("abs", dx));
            events.put(event("abs", dy));
            if (pressed) {
                JSONObject button = new JSONObject(); button.put("down", true); button.put("button", "left");
                events.put(event("btn", button));
            }
            sendEvents(events);
        } catch (Exception e) {
            OneOSLog.error("NATIVE_INPUT", "Touch event encoding failed.", e);
        }
    }

    private void releasePointer() {
        try {
            JSONArray events = new JSONArray();
            JSONObject button = new JSONObject(); button.put("down", false); button.put("button", "left");
            events.put(event("btn", button));
            sendEvents(events);
        } catch (Exception e) {
            OneOSLog.error("NATIVE_INPUT", "Touch release failed.", e);
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.BLACK);
        Bitmap current = frame;
        if (current == null || current.isRecycled()) {
            paint.setColor(Color.LTGRAY);
            paint.setTextSize(36f * getResources().getDisplayMetrics().density);
            canvas.drawText("Starting OneOS display…", 24f, getHeight() / 2f, paint);
            return;
        }
        float scale = Math.min((float) getWidth() / current.getWidth(),
                (float) getHeight() / current.getHeight());
        imageWidth = current.getWidth() * scale;
        imageHeight = current.getHeight() * scale;
        imageLeft = (getWidth() - imageWidth) / 2f;
        imageTop = (getHeight() - imageHeight) / 2f;
        android.graphics.RectF dst = new android.graphics.RectF(imageLeft, imageTop,
                imageLeft + imageWidth, imageTop + imageHeight);
        canvas.drawBitmap(current, null, dst, paint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        requestFocus();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                sendPointer(event.getX(), event.getY(), true);
                return true;
            case MotionEvent.ACTION_MOVE:
                sendPointer(event.getX(), event.getY(), false);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                sendPointer(event.getX(), event.getY(), false);
                releasePointer();
                return true;
            default:
                return true;
        }
    }

    private String qcode(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER: return "ret";
            case KeyEvent.KEYCODE_DEL: return "backspace";
            case KeyEvent.KEYCODE_ESCAPE: return "esc";
            case KeyEvent.KEYCODE_SPACE: return "spc";
            case KeyEvent.KEYCODE_TAB: return "tab";
            case KeyEvent.KEYCODE_DPAD_UP: return "up";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "down";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "left";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "right";
            case KeyEvent.KEYCODE_FORWARD_DEL: return "delete";
            case KeyEvent.KEYCODE_BACK: return "esc";
            default:
                if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z)
                    return String.valueOf((char) ('a' + keyCode - KeyEvent.KEYCODE_A));
                if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9)
                    return String.valueOf((char) ('0' + keyCode - KeyEvent.KEYCODE_0));
                return null;
        }
    }

    private void sendKey(int keyCode, boolean down) {
        String code = qcode(keyCode);
        if (code == null) return;
        try {
            JSONObject key = new JSONObject(); key.put("type", "qcode"); key.put("data", code);
            JSONObject data = new JSONObject(); data.put("down", down); data.put("key", key);
            JSONArray events = new JSONArray(); events.put(event("key", data));
            sendEvents(events);
        } catch (Exception e) {
            OneOSLog.error("NATIVE_INPUT", "Keyboard event encoding failed.", e);
        }
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        sendKey(keyCode, true);
        return qcode(keyCode) != null || super.onKeyDown(keyCode, event);
    }

    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        sendKey(keyCode, false);
        return qcode(keyCode) != null || super.onKeyUp(keyCode, event);
    }

    private void closeQmp() {
        try { if (reader != null) reader.close(); } catch (Exception ignored) { }
        try { if (writer != null) writer.close(); } catch (Exception ignored) { }
        try { if (socket != null) socket.close(); } catch (Exception ignored) { }
        reader = null; writer = null; socket = null;
    }
}
