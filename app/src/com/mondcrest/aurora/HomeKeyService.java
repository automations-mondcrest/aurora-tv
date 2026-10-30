package com.mondcrest.aurora;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 1. Catches the remote's Home button (JVC sends it straight to its own launcher)
 *    and opens Aurora instead.
 * 2. Watches which screen comes to the front so it can close the TV's own
 *    ad screensaver and show Aurora instead. It reads screen names only, never content.
 */
public class HomeKeyService extends AccessibilityService {

    static final String PREFS = "aurora";
    private long lastBounce = 0;

    /** Screens known to be (or that look like) the TV's ad screensaver. */
    static boolean isAdScreen(String pkg, String cls) {
        if (pkg == null) return false;
        if (pkg.startsWith("com.mondcrest.")) return false;
        String p = pkg.toLowerCase();
        String c = cls == null ? "" : cls.toLowerCase();
        if (p.equals("com.cvte.tv.pictorial") || p.equals("com.cvte.tv.launcher.space")) return true;
        if (p.contains("screensaver") || c.contains("screensaver") || c.contains("screen_saver")) return true;
        if (c.endsWith(".adactivity") || c.endsWith("$adactivity")) return true;
        return c.contains("dreamactivity") || p.equals("com.android.dreams.basic");
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;
        if (event.getAction() == KeyEvent.ACTION_UP) openAurora("fromHomeKey");
        return true; // swallow both down and up so the built-in launcher never opens
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        String pkg = event.getPackageName() == null ? null : event.getPackageName().toString();
        String cls = event.getClassName() == null ? "" : event.getClassName().toString();
        if (pkg == null) return;
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        log(sp, pkg, cls);
        if (sp.getBoolean("blockAds", true) && isAdScreen(pkg, cls)) {
            long now = System.currentTimeMillis();
            if (now - lastBounce < 1500) return;
            lastBounce = now;
            sp.edit().putInt("adsBlocked", sp.getInt("adsBlocked", 0) + 1).apply();
            openAurora("fromAd");
        }
    }

    private void openAurora(String why) {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        i.putExtra(why, true);
        try { startActivity(i); } catch (Exception ignored) { }
    }

    /** Keeps the last 25 screen names so we can see what the ad screen is called. */
    private static void log(SharedPreferences sp, String pkg, String cls) {
        try {
            JSONArray old = new JSONArray(sp.getString("seen", "[]"));
            if (old.length() > 0) {
                JSONObject last = old.getJSONObject(old.length() - 1);
                if (pkg.equals(last.optString("pkg")) && cls.equals(last.optString("cls"))) return;
            }
            JSONArray out = new JSONArray();
            int start = Math.max(0, old.length() - 24);
            for (int k = start; k < old.length(); k++) out.put(old.get(k));
            JSONObject o = new JSONObject();
            o.put("pkg", pkg);
            o.put("cls", cls);
            o.put("t", System.currentTimeMillis());
            o.put("ad", isAdScreen(pkg, cls));
            out.put(o);
            sp.edit().putString("seen", out.toString()).apply();
        } catch (Exception ignored) { }
    }

    @Override
    public void onInterrupt() { }
}
