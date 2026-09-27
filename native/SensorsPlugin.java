package com.escapator.app;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.nfc.NfcAdapter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.view.WindowManager;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@CapacitorPlugin(name = "NativeSensors")
public class SensorsPlugin extends Plugin implements SensorEventListener {
    private SensorManager sm;
    private final Map<String, Integer> types = new HashMap<>();
    private final Map<Integer, String> names = new HashMap<>();
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private String torchId = null;

    @Override
    public void load() {
        sm = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
        reg("magnetic", Sensor.TYPE_MAGNETIC_FIELD);
        reg("light", Sensor.TYPE_LIGHT);
        reg("proximity", Sensor.TYPE_PROXIMITY);
        reg("pressure", Sensor.TYPE_PRESSURE);
        tts = new TextToSpeech(getContext(), status -> ttsReady = status == TextToSpeech.SUCCESS);
        try {
            CameraManager cm = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
            for (String id : cm.getCameraIdList()) {
                Boolean f = cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (f != null && f) { torchId = id; break; }
            }
        } catch (Exception ignored) {}
    }

    private void reg(String n, int t) { types.put(n, t); names.put(t, n); }

    @PluginMethod
    public void available(PluginCall call) {
        JSObject r = new JSObject();
        for (Map.Entry<String, Integer> e : types.entrySet()) r.put(e.getKey(), sm.getDefaultSensor(e.getValue()) != null);
        r.put("torch", torchId != null);
        r.put("nfc", NfcAdapter.getDefaultAdapter(getContext()) != null);
        r.put("tts", ttsReady);
        call.resolve(r);
    }

    @PluginMethod
    public void start(PluginCall call) {
        Integer t = types.get(call.getString("type", ""));
        Sensor s = t == null ? null : sm.getDefaultSensor(t);
        if (s == null) { call.reject("unavailable"); return; }
        sm.registerListener(this, s, t == Sensor.TYPE_MAGNETIC_FIELD ? SensorManager.SENSOR_DELAY_GAME : SensorManager.SENSOR_DELAY_UI);
        JSObject r = new JSObject();
        r.put("max", (double) s.getMaximumRange());
        call.resolve(r);
    }

    @PluginMethod
    public void stop(PluginCall call) { sm.unregisterListener(this); call.resolve(); }

    @Override
    public void onSensorChanged(SensorEvent e) {
        JSObject d = new JSObject();
        d.put("type", names.get(e.sensor.getType()));
        JSArray v = new JSArray();
        try { for (float f : e.values) v.put((double) f); } catch (Exception ignored) {}
        d.put("v", v);
        notifyListeners("sensor", d);
    }

    @Override
    public void onAccuracyChanged(Sensor s, int a) {}

    @PluginMethod
    public void torch(PluginCall call) {
        try {
            CameraManager cm = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
            cm.setTorchMode(torchId, call.getBoolean("on", false));
            call.resolve();
        } catch (Exception e) { call.reject(String.valueOf(e.getMessage())); }
    }

    @PluginMethod
    public void vibrate(PluginCall call) {
        try {
            JSArray p = call.getArray("pattern", new JSArray());
            Vibrator vb = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (p.length() == 0) { vb.cancel(); call.resolve(); return; }
            long[] t = new long[p.length() + 1];
            for (int i = 0; i < p.length(); i++) t[i + 1] = p.getLong(i);
            if (Build.VERSION.SDK_INT >= 26) vb.vibrate(VibrationEffect.createWaveform(t, -1));
            else vb.vibrate(t, -1);
            call.resolve();
        } catch (Exception e) { call.reject(String.valueOf(e.getMessage())); }
    }

    @PluginMethod
    public void speak(PluginCall call) {
        if (!ttsReady) { call.reject("tts"); return; }
        tts.setLanguage(Locale.forLanguageTag(call.getString("lang", "fr-FR")));
        tts.setPitch(call.getFloat("pitch", 1f));
        tts.speak(call.getString("text", ""), TextToSpeech.QUEUE_FLUSH, null, "escapator");
        call.resolve();
    }

    @PluginMethod
    public void share(PluginCall call) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, call.getString("text", ""));
        Intent c = Intent.createChooser(i, null);
        c.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(c);
        call.resolve();
    }

    @PluginMethod
    public void nfcStart(PluginCall call) {
        NfcAdapter n = NfcAdapter.getDefaultAdapter(getContext());
        if (n == null || !n.isEnabled()) { call.reject("nfc"); return; }
        n.enableReaderMode(getActivity(), tag -> {
            JSObject d = new JSObject();
            d.put("id", hex(tag.getId()));
            notifyListeners("nfc", d);
        }, NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B | NfcAdapter.FLAG_READER_NFC_F
            | NfcAdapter.FLAG_READER_NFC_V | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null);
        call.resolve();
    }

    @PluginMethod
    public void nfcStop(PluginCall call) {
        NfcAdapter n = NfcAdapter.getDefaultAdapter(getContext());
        if (n != null) n.disableReaderMode(getActivity());
        call.resolve();
    }

    private static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format("%02X", x));
        return s.toString();
    }

    @PluginMethod
    public void keepAwake(PluginCall call) {
        final boolean on = call.getBoolean("on", false);
        getActivity().runOnUiThread(() -> {
            if (on) getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        });
        call.resolve();
    }

    @PluginMethod
    public void battery(PluginCall call) {
        BatteryManager bm = (BatteryManager) getContext().getSystemService(Context.BATTERY_SERVICE);
        JSObject r = new JSObject();
        r.put("level", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) / 100.0);
        r.put("charging", Build.VERSION.SDK_INT >= 23 && bm.isCharging());
        call.resolve(r);
    }

    @Override
    protected void handleOnPause() {
        sm.unregisterListener(this);
        try {
            if (torchId != null) ((CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE)).setTorchMode(torchId, false);
            NfcAdapter n = NfcAdapter.getDefaultAdapter(getContext());
            if (n != null) n.disableReaderMode(getActivity());
        } catch (Exception ignored) {}
    }

    @Override
    protected void handleOnDestroy() {
        sm.unregisterListener(this);
        if (tts != null) tts.shutdown();
    }
}
