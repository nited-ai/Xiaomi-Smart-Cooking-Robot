package com.kitchenos.connect.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.widget.Toast;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/** Explicit Home-knob action only. No idle timer, receiver or remote endpoint. */
public final class StandbyController {
    private static StandbyController instance;
    public static synchronized StandbyController get(Context context) {
        if (instance == null) instance = new StandbyController(context.getApplicationContext());
        return instance;
    }
    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean busy = new AtomicBoolean();
    private volatile boolean entering;
    private volatile long ignoreKnobUntil;
    private volatile boolean discardKnobRelease;
    private StandbyController(Context app) { this.app = app; }
    public boolean blocksInput() { return entering; }
    public boolean consume(KeyEvent event) {
        int code = event.getKeyCode();
        if (code == 135) {
            if (entering || SystemClock.elapsedRealtime() < ignoreKnobUntil) {
                discardKnobRelease = true;
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN) discardKnobRelease = false;
            if (event.getAction() == KeyEvent.ACTION_UP && discardKnobRelease) {
                discardKnobRelease = false;
                return true;
            }
        }
        return entering;
    }
    public void start() {
        if (!busy.compareAndSet(false, true)) {
            message("Die Funkverbindung wird noch wiederhergestellt. Bitte kurz warten.");
            return;
        }
        entering = true;
        message("Standby wird vorbereitet …");
        new Thread(() -> {
            try {
                File helper = new File(app.getApplicationInfo().nativeLibraryDir, "libkitchenos_standby.so");
                if (!helper.isFile()) throw new IOException("Standby helper missing");
                String path = "'" + helper.getAbsolutePath().replace("'", "'\\''") + "'";
                Process process = new ProcessBuilder("/sbin/magisk", "su", "-c", "exec " + path + " --suspend")
                        .redirectErrorStream(true).start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        android.util.Log.i("KalomatikStandby", line);
                        if (line.equals("AWAKE")) {
                            discardKnobRelease = true;
                            ignoreKnobUntil = SystemClock.elapsedRealtime() + 1500;
                            entering = false;
                        }
                        if (line.equals("RADIO_RECOVERY_PENDING"))
                            message("WLAN verbindet sich noch. Die App ist wieder bereit.");
                    }
                }
                if (process.waitFor() != 0)
                    message("Standby nicht möglich. Maschine muss im Leerlauf und unter 50 °C sein; die Steuerung muss getrennt sein.");
            } catch (Exception error) {
                android.util.Log.w("KalomatikStandby", "Standby konnte nicht ausgeführt werden", error);
                message("Standby konnte nicht gestartet werden.");
            } finally {
                entering = false;
                busy.set(false);
            }
        }, "Kalomatik-standby").start();
    }
    private void message(String text) {
        main.post(() -> Toast.makeText(app, text, Toast.LENGTH_LONG).show());
    }
}
