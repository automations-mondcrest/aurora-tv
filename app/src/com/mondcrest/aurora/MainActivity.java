package com.mondcrest.aurora;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Aurora home screen: a full-screen WebView running the launcher UI from assets,
 * with a small bridge ("Aurora") that lists and opens installed apps and TV inputs.
 */
public class MainActivity extends Activity {

    private WebView web;
    private boolean pageReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.BLACK));

        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setTextZoom(100);

        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
            }
        });
        web.addJavascriptInterface(new Bridge(this), "Aurora");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
        web.requestFocus();
        hideSystemUi();
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    /* ------------- update hand-off to the TV's file browser ------------- */

    private String pendingUrl = null;
    static final int REQ_STORAGE = 71;

    void handoffUpdate(String url) {
        pendingUrl = url;
        if (android.os.Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            return;
        }
        startHandoff();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_STORAGE && pendingUrl != null) startHandoff();
    }

    private void startHandoff() {
        final String url = pendingUrl;
        pendingUrl = null;
        if (url == null) return;
        new Thread(new Runnable() {
            public void run() {
                java.io.File dest;
                boolean shared = android.os.Build.VERSION.SDK_INT < 23
                        || checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
                if (shared) {
                    java.io.File dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
                    dir.mkdirs();
                    dest = new java.io.File(dir, "AuroraHome-update.apk");
                } else {
                    dest = new java.io.File(getExternalFilesDir(null), "AuroraHome-update.apk");
                }
                try {
                    Bridge.download(url, dest);
                } catch (Exception e) {
                    js2("{\"error\":\"Download failed\"}");
                    return;
                }
                final String where = shared ? "Download" : "Android/data/" + getPackageName() + "/files";
                final String opened = openFileBrowser(dest);
                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(MainActivity.this, "Update saved. Open " + where + " > AuroraHome-update.apk and press OK to install", Toast.LENGTH_LONG).show();
                    }
                });
                JSONObject o = new JSONObject();
                try {
                    o.put("handoff", true);
                    o.put("where", where);
                    o.put("file", dest.getName());
                    o.put("browser", opened == null ? "" : opened);
                } catch (Exception ignored) { }
                js2(o.toString());
            }
        }).start();
    }

    private void js2(final String json) {
        runOnUiThread(new Runnable() {
            public void run() { js("window.auroraUpdateStatus && window.auroraUpdateStatus(" + JSONObject.quote(json) + ")"); }
        });
    }

    /**
     * Opens the TV's file browser: the app that opened the installer last time (from the
     * screen log), else any app that looks like a file or media browser.
     */
    private String openFileBrowser(java.io.File f) {
        PackageManager pm = getPackageManager();
        java.util.List<String> cands = new java.util.ArrayList<String>();
        try {
            JSONArray seen = new JSONArray(getSharedPreferences(HomeKeyService.PREFS, MODE_PRIVATE).getString("seen", "[]"));
            for (int k = 1; k < seen.length(); k++) {
                if (seen.getJSONObject(k).optString("pkg").contains("packageinstaller")) {
                    String prev = seen.getJSONObject(k - 1).optString("pkg");
                    if (!prev.contains("packageinstaller") && !prev.equals(getPackageName()) && !cands.contains(prev)) cands.add(0, prev);
                }
            }
        } catch (Exception ignored) { }
        java.util.regex.Pattern looks = java.util.regex.Pattern.compile("(?i)(filemanager|fileexplorer|filebrowser|explorer|mediacenter|media\\.center|\\.files|documentsui|usb|localmedia|mediaplayer)");
        for (ApplicationInfo a : pm.getInstalledApplications(0)) {
            if (looks.matcher(a.packageName).find() && !cands.contains(a.packageName)) cands.add(a.packageName);
        }
        for (String pkg : cands) {
            Intent i = null;
            try { i = pm.getLeanbackLaunchIntentForPackage(pkg); } catch (Throwable ignored) { }
            if (i == null) i = pm.getLaunchIntentForPackage(pkg);
            if (i == null) continue;
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(i);
                return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
            } catch (Exception ignored) { }
        }
        return null;
    }

    private void js(String code) {
        if (web != null && pageReady) web.evaluateJavascript(code, null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        if (web != null) {
            web.onResume();
            web.requestFocus();
        }
        js("window.auroraResume && window.auroraResume()");
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) web.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while already home: close any open panel.
        js("window.auroraHome && window.auroraHome()");
        // Came back because the TV's ad screensaver tried to start: show Aurora's calm screen.
        if (intent != null && intent.getBooleanExtra("fromAd", false)) js("window.auroraIdle && window.auroraIdle()");
    }

    @Override
    public void onBackPressed() {
        // A home screen never exits; Back closes panels inside the page.
        js("window.auroraBack && window.auroraBack()");
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            if (k == KeyEvent.KEYCODE_MENU || k == KeyEvent.KEYCODE_INFO) {
                js("window.auroraMenu && window.auroraMenu()");
                return true;
            }
            if (k == KeyEvent.KEYCODE_CHANNEL_UP || k == KeyEvent.KEYCODE_MEDIA_NEXT) {
                js("window.auroraNext && window.auroraNext()");
                return true;
            }
        }
        if (k == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_UP) {
            onBackPressed();
            return true;
        }
        if (k == KeyEvent.KEYCODE_BACK) return true;
        return super.dispatchKeyEvent(e);
    }

    /* ------------------------------------------------------------------ */

    public static class Bridge {
        private final MainActivity act;

        Bridge(MainActivity a) { act = a; }

        @JavascriptInterface
        public String apps() {
            JSONArray out = new JSONArray();
            try {
                PackageManager pm = act.getPackageManager();
                Set<String> seen = new HashSet<String>();
                String self = act.getPackageName();
                String[] cats = {"android.intent.category.LEANBACK_LAUNCHER", Intent.CATEGORY_LAUNCHER};
                for (String cat : cats) {
                    Intent i = new Intent(Intent.ACTION_MAIN);
                    i.addCategory(cat);
                    List<ResolveInfo> list = pm.queryIntentActivities(i, 0);
                    for (ResolveInfo ri : list) {
                        String pkg = ri.activityInfo.packageName;
                        if (pkg.equals(self) || seen.contains(pkg)) continue;
                        seen.add(pkg);
                        JSONObject o = new JSONObject();
                        o.put("pkg", pkg);
                        o.put("name", String.valueOf(ri.loadLabel(pm)));
                        o.put("icon", iconB64(ri.loadIcon(pm)));
                        ApplicationInfo ai = ri.activityInfo.applicationInfo;
                        o.put("system", (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
                        out.put(o);
                    }
                }
            } catch (Exception ignored) { }
            return out.toString();
        }

        @JavascriptInterface
        public String inputs() {
            JSONArray out = new JSONArray();
            try {
                TvInputManager tim = (TvInputManager) act.getSystemService(Context.TV_INPUT_SERVICE);
                if (tim != null) {
                    for (TvInputInfo info : tim.getTvInputList()) {
                        if (Boolean.TRUE.equals(call(info, "isHidden"))) continue;
                        JSONObject o = new JSONObject();
                        o.put("id", info.getId());
                        o.put("type", info.getType());
                        Object customObj = call(info, "loadCustomLabel");
                        CharSequence custom = customObj instanceof CharSequence ? (CharSequence) customObj : null;
                        CharSequence label = (custom != null && custom.length() > 0) ? custom : info.loadLabel(act);
                        o.put("label", String.valueOf(label));
                        o.put("passthrough", info.isPassthroughInput());
                        out.put(o);
                    }
                }
            } catch (Exception ignored) { }
            return out.toString();
        }

        @JavascriptInterface
        public boolean launch(final String pkg) {
            act.runOnUiThread(new Runnable() {
                public void run() {
                    PackageManager pm = act.getPackageManager();
                    Intent i = null;
                    try { i = pm.getLeanbackLaunchIntentForPackage(pkg); } catch (Throwable ignored) { }
                    if (i == null) i = pm.getLaunchIntentForPackage(pkg);
                    if (i == null) { toast("Can't open this app"); return; }
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try { act.startActivity(i); } catch (ActivityNotFoundException e) { toast("Can't open this app"); }
                }
            });
            return true;
        }

        /**
         * Switches to a TV input. Order:
         * 1. Android TV's standard way (works on TVs with Live Channels: Sony, TCL, Philips...).
         * 2. The TV's own input menu that Aurora learned when you pressed Source.
         * 3. An input/source screen found on the TV by name.
         */
        @JavascriptInterface
        public boolean openInput(final String id, final boolean passthrough) {
            act.runOnUiThread(new Runnable() {
                public void run() {
                    PackageManager pm = act.getPackageManager();
                    Uri uri = passthrough
                            ? TvContract.buildChannelUriForPassthroughInput(id)
                            : TvContract.buildChannelsUriForInput(id);
                    Intent std = new Intent(Intent.ACTION_VIEW, uri);
                    std.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    if (pm.resolveActivity(std, 0) != null) {
                        try { act.startActivity(std); return; } catch (Exception ignored) { }
                    }
                    android.content.SharedPreferences sp = prefs();
                    String dp = sp.getString("directPkg", null), dc = sp.getString("directCls", null);
                    if (dp != null && dc != null && passthrough) {
                        Intent d = new Intent(Intent.ACTION_VIEW, uri);
                        d.setClassName(dp, dc);
                        d.putExtra("android.media.tv.extra.INPUT_ID", id);
                        d.putExtra("inputId", id);
                        d.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        try { act.startActivity(d); return; } catch (Exception ignored) { }
                    }
                    String sp1 = sp.getString("srcPkg", null), sc1 = sp.getString("srcCls", null);
                    if (sp1 != null && sc1 != null) {
                        try {
                            Intent m = cmp(sp1, sc1);
                            m.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            act.startActivity(m);
                            toast("Pick the input in your TV's menu");
                            return;
                        } catch (Exception ignored) { }
                    }
                    for (String[] c : findScreens("(?i)(inputsource|sourceselect|sourcelist|signalsource|sourceactivity|inputactivity|hdmiactivity|sourcemenu)")) {
                        try {
                            Intent m = cmp(c[0], c[1]);
                            m.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            act.startActivity(m);
                            toast("Pick the input in your TV's menu");
                            return;
                        } catch (Exception ignored) { }
                    }
                    toast("Press the Source button on your remote once. Aurora will learn your TV's input menu and open it from here next time.");
                }
            });
            return true;
        }

        /** Exported, permission-free activities anywhere on the TV whose class name matches. */
        private java.util.List<String[]> findScreens(String regex) {
            java.util.List<String[]> out = new java.util.ArrayList<String[]>();
            java.util.regex.Pattern want = java.util.regex.Pattern.compile(regex);
            PackageManager pm = act.getPackageManager();
            for (ApplicationInfo app : pm.getInstalledApplications(0)) {
                android.content.pm.PackageInfo pi;
                try { pi = pm.getPackageInfo(app.packageName, PackageManager.GET_ACTIVITIES); } catch (Throwable t) { continue; }
                if (pi.activities == null) continue;
                for (android.content.pm.ActivityInfo ai : pi.activities) {
                    if (!ai.exported || !ai.enabled) continue;
                    if (ai.permission != null && ai.permission.length() > 0) continue;
                    if (want.matcher(ai.name).find()) out.add(new String[]{ai.packageName, ai.name, String.valueOf(ai.loadLabel(pm))});
                }
            }
            return out;
        }

        @JavascriptInterface
        public String inputScreens() {
            JSONArray a = new JSONArray();
            try {
                for (String[] c : findScreens("(?i)(source|input|hdmi|signal|livetv|tvplayer|tvview|channel)")) {
                    if (c[0].startsWith("com.android.providers")) continue;
                    JSONObject o = new JSONObject();
                    o.put("pkg", c[0]); o.put("cls", c[1]); o.put("label", c[2]);
                    a.put(o);
                }
            } catch (Exception ignored) { }
            return a.toString();
        }

        @JavascriptInterface
        public void setSource(String pkg, String cls) {
            prefs().edit().putString("srcPkg", pkg).putString("srcCls", cls).apply();
        }

        /** Live-TV screens that might accept "show this input": JVC/CVTE player activities etc. */
        @JavascriptInterface
        public String tvPlayers() {
            JSONArray a = new JSONArray();
            try {
                java.util.regex.Pattern pk = java.util.regex.Pattern.compile("(?i)(tv\\.api|tvplayer|livetv|cvte\\.tv|\\.tv\\.|mstar|mediatek|realtek|tvos)");
                java.util.regex.Pattern nm = java.util.regex.Pattern.compile("(?i)(tvsupport|tvplayer|livetv|tvactivity|tvmain|tvview|playeractivity|rootactivity|tvsource|sourceactivity)");
                for (String[] c : findScreens(".")) {
                    boolean apiPkg = c[0].toLowerCase().contains("tv.api");
                    if (!apiPkg && !(pk.matcher(c[0]).find() && nm.matcher(c[1]).find())) continue;
                    JSONObject o = new JSONObject();
                    o.put("pkg", c[0]); o.put("cls", c[1]); o.put("label", c[2]);
                    a.put(o);
                }
            } catch (Exception ignored) { }
            return a.toString();
        }

        /** Test: ask one live-TV screen to show one input, the standard Android TV way. */
        @JavascriptInterface
        public void tryDirectInput(final String inputId, final String pkg, final String cls) {
            act.runOnUiThread(new Runnable() {
                public void run() {
                    Intent i = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(inputId));
                    i.setClassName(pkg, cls);
                    i.putExtra("android.media.tv.extra.INPUT_ID", inputId);
                    i.putExtra("inputId", inputId);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    try { act.startActivity(i); }
                    catch (Exception e) { toast("That screen refused: " + e.getClass().getSimpleName()); }
                }
            });
        }

        @JavascriptInterface
        public void setDirect(String pkg, String cls) {
            prefs().edit().putString("directPkg", pkg).putString("directCls", cls).apply();
        }

        /**
         * Copies the TV's own CVTE/TV apps onto the USB stick (Android/data/com.mondcrest.aurora/files/export)
         * so their manifests can be read on a PC.
         */
        @JavascriptInterface
        public void exportSystemApps() {
            new Thread(new Runnable() {
                public void run() {
                    String msg;
                    try {
                        java.io.File usb = null;
                        java.io.File[] dirs = act.getExternalFilesDirs(null);
                        for (java.io.File d : dirs) {
                            if (d != null && android.os.Environment.isExternalStorageRemovable(d)) { usb = d; break; }
                        }
                        if (usb == null) { msg = "Plug the USB stick into the TV first"; }
                        else {
                            java.io.File out = new java.io.File(usb, "export");
                            out.mkdirs();
                            int n = 0;
                            PackageManager pm = act.getPackageManager();
                            java.util.regex.Pattern want = java.util.regex.Pattern.compile("(?i)(cvte|\\.tv\\.|tvsettings|tv\\.settings|mstar|mediatek|livetv|source|hdmi)");
                            for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
                                if (!want.matcher(ai.packageName).find()) continue;
                                if (ai.sourceDir == null) continue;
                                java.io.File src = new java.io.File(ai.sourceDir);
                                if (!src.canRead() || src.length() > 80L * 1024 * 1024) continue;
                                java.io.FileInputStream in = new java.io.FileInputStream(src);
                                java.io.FileOutputStream o = new java.io.FileOutputStream(new java.io.File(out, ai.packageName + ".apk"));
                                byte[] buf = new byte[65536]; int r;
                                while ((r = in.read(buf)) > 0) o.write(buf, 0, r);
                                o.close(); in.close();
                                n++;
                            }
                            msg = "Copied " + n + " TV apps to the USB stick (Android/data/com.mondcrest.aurora/files/export)";
                        }
                    } catch (Exception e) { msg = "Copy failed: " + e.getClass().getSimpleName(); }
                    final String m = msg;
                    act.runOnUiThread(new Runnable() { public void run() { toast(m); } });
                }
            }).start();
        }

        @JavascriptInterface
        public String learnedSource() {
            android.content.SharedPreferences sp = prefs();
            String p = sp.getString("srcPkg", null);
            return p == null ? "" : p + "/" + sp.getString("srcCls", "");
        }

        /**
         * Every settings app on the TV, each with the screen it opens. Lets the menu show
         * the real ones instead of two buttons that land in the same place.
         */
        @JavascriptInterface
        public String settingsApps() {
            JSONArray out = new JSONArray();
            try {
                PackageManager pm = act.getPackageManager();
                Set<String> seenTargets = new HashSet<String>();
                // What Android itself opens for "Settings" goes first.
                ResolveInfo def = pm.resolveActivity(new Intent(Settings.ACTION_SETTINGS), 0);
                if (def != null && def.activityInfo != null) {
                    addSetting(out, seenTargets, pm, def.activityInfo.packageName, def.activityInfo.name, true);
                }
                String[][] known = {
                        {"com.android.tv.settings", "com.android.tv.settings.MainSettings"},
                        {"com.android.tv.settings", "com.android.tv.settings.TvSettingsActivity"},
                        {"com.android.settings", "com.android.settings.Settings"}};
                for (String[] k : known) addSetting(out, seenTargets, pm, k[0], k[1], false);
                for (ApplicationInfo app : pm.getInstalledApplications(0)) {
                    String p = app.packageName.toLowerCase();
                    if (!(p.contains("setting") || p.contains("tvmenu") || p.endsWith(".menu"))) continue;
                    if (p.startsWith("com.android.providers") || p.equals(act.getPackageName())) continue;
                    Intent li = null;
                    try { li = pm.getLeanbackLaunchIntentForPackage(app.packageName); } catch (Throwable ignored) { }
                    if (li == null) li = pm.getLaunchIntentForPackage(app.packageName);
                    if (li != null && li.getComponent() != null) {
                        addSetting(out, seenTargets, pm, li.getComponent().getPackageName(), li.getComponent().getClassName(), false);
                    }
                }
            } catch (Exception ignored) { }
            return out.toString();
        }

        private void addSetting(JSONArray out, Set<String> seen, PackageManager pm, String pkg, String cls, boolean isDefault) {
            try {
                android.content.pm.ActivityInfo ai = pm.getActivityInfo(new android.content.ComponentName(pkg, cls), 0);
                if (!ai.exported && !isDefault) return;
                String key = pkg + "/" + cls;
                if (seen.contains(key)) return;
                // Same app twice (e.g. two entry points into one menu) counts once.
                for (int k = 0; k < out.length(); k++) if (out.getJSONObject(k).optString("pkg").equals(pkg)) return;
                seen.add(key);
                JSONObject o = new JSONObject();
                o.put("pkg", pkg); o.put("cls", cls);
                o.put("app", String.valueOf(ai.applicationInfo.loadLabel(pm)));
                o.put("label", String.valueOf(ai.loadLabel(pm)));
                o.put("default", isDefault);
                out.put(o);
            } catch (Exception ignored) { }
        }

        @JavascriptInterface
        public void openSettings() {
            act.runOnUiThread(new Runnable() {
                public void run() {
                    Intent i = new Intent(Settings.ACTION_SETTINGS);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try { act.startActivity(i); } catch (ActivityNotFoundException e) { toast("Settings not available"); }
                }
            });
        }

        @JavascriptInterface
        public boolean isNative() { return true; }

        /**
         * Lists every openable screen on the TV whose name suggests developer options,
         * accessibility, device info or home-app settings — including ones JVC left in the
         * firmware but removed from its menus.
         */
        @JavascriptInterface
        public String hiddenScreens() {
            JSONArray out = new JSONArray();
            try {
                PackageManager pm = act.getPackageManager();
                java.util.regex.Pattern want = java.util.regex.Pattern.compile(
                        "(?i)(develop|accessib|deviceinfo|device_info|aboutactivity|about\\b|\\.about|homesettings|home_settings|defaultapp|default_app|homeapp|usbdebug|adb|debug|unknownsource|externalsources|security)");
                for (ApplicationInfo app : pm.getInstalledApplications(0)) {
                    android.content.pm.PackageInfo pi;
                    try {
                        pi = pm.getPackageInfo(app.packageName, PackageManager.GET_ACTIVITIES);
                    } catch (Throwable t) { continue; }
                    if (pi.activities == null) continue;
                    for (android.content.pm.ActivityInfo ai : pi.activities) {
                        if (!ai.exported || !ai.enabled) continue;
                        if (ai.permission != null && ai.permission.length() > 0) continue;
                        if (!want.matcher(ai.name).find()) continue;
                        JSONObject o = new JSONObject();
                        o.put("pkg", ai.packageName);
                        o.put("cls", ai.name);
                        CharSequence label = ai.loadLabel(pm);
                        o.put("label", label == null ? "" : String.valueOf(label));
                        out.put(o);
                    }
                }
            } catch (Exception ignored) { }
            return out.toString();
        }

        @JavascriptInterface
        public void openActivity(final String pkg, final String cls) {
            act.runOnUiThread(new Runnable() {
                public void run() {
                    Intent i = cmp(pkg, cls);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try { act.startActivity(i); } catch (Exception e) { toast("That screen won't open: " + e.getClass().getSimpleName()); }
                }
            });
        }

        /* ---------------- preferences, version, diagnostics ---------------- */

        private android.content.SharedPreferences prefs() {
            return act.getSharedPreferences(HomeKeyService.PREFS, Context.MODE_PRIVATE);
        }

        @JavascriptInterface
        public boolean getBool(String key, boolean def) { return prefs().getBoolean(key, def); }

        @JavascriptInterface
        public void setBool(String key, boolean v) { prefs().edit().putBoolean(key, v).apply(); }

        @JavascriptInterface
        public int adsBlocked() { return prefs().getInt("adsBlocked", 0); }

        @JavascriptInterface
        public String seenScreens() { return prefs().getString("seen", "[]"); }

        @JavascriptInterface
        public String version() {
            try {
                return act.getPackageManager().getPackageInfo(act.getPackageName(), 0).versionName;
            } catch (Exception e) { return "?"; }
        }

        @JavascriptInterface
        public String netInfo() {
            JSONObject o = new JSONObject();
            try {
                StringBuilder ips = new StringBuilder();
                for (java.net.NetworkInterface ni : java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())) {
                    if (!ni.isUp() || ni.isLoopback()) continue;
                    for (java.net.InetAddress a : java.util.Collections.list(ni.getInetAddresses())) {
                        if (a instanceof java.net.Inet4Address) {
                            if (ips.length() > 0) ips.append(", ");
                            ips.append(a.getHostAddress());
                        }
                    }
                }
                o.put("ip", ips.toString());
                int adb = Settings.Global.getInt(act.getContentResolver(), Settings.Global.ADB_ENABLED, 0);
                o.put("adb", adb == 1);
                o.put("tcpPort", sysProp("service.adb.tcp.port") + "/" + sysProp("persist.adb.tcp.port"));
                o.put("devMode", Settings.Global.getInt(act.getContentResolver(), "development_settings_enabled", 0) == 1);
            } catch (Exception ignored) { }
            return o.toString();
        }

        private static String sysProp(String key) {
            try {
                Class<?> c = Class.forName("android.os.SystemProperties");
                return (String) c.getMethod("get", String.class).invoke(null, key);
            } catch (Throwable t) { return ""; }
        }

        /* ---------------- self-update from GitHub releases ---------------- */

        static final String RELEASES = "https://api.github.com/repos/automations-mondcrest/aurora-tv/releases/latest";
        /** Version file kept in the repo itself: {"version":"1.5","apk":"https://...","notes":"..."} */
        static final String LATEST_JSON = "https://raw.githubusercontent.com/automations-mondcrest/aurora-tv/main/release/latest.json";

        @JavascriptInterface
        public void checkUpdate() {
            new Thread(new Runnable() {
                public void run() {
                    JSONObject r = new JSONObject();
                    try {
                        // 1) version file in the repo
                        try {
                            JSONObject lj = new JSONObject(httpGet(LATEST_JSON + "?t=" + System.currentTimeMillis()));
                            String tag = lj.optString("version", "").replaceFirst("^[vV]", "");
                            String url = lj.optString("apk", "");
                            r.put("latest", tag);
                            r.put("current", version());
                            r.put("url", url);
                            r.put("newer", url.length() > 0 && newer(tag, version()));
                            r.put("notes", lj.optString("notes", ""));
                            sendJs("window.auroraUpdateStatus && window.auroraUpdateStatus(" + JSONObject.quote(r.toString()) + ")");
                            return;
                        } catch (Exception ignored) { }
                        // 2) GitHub releases, if the repo uses them
                        String body = httpGet(RELEASES);
                        JSONObject rel = new JSONObject(body);
                        String tag = rel.optString("tag_name", "").replaceFirst("^[vV]", "");
                        String url = null;
                        JSONArray assets = rel.optJSONArray("assets");
                        if (assets != null) {
                            for (int k = 0; k < assets.length(); k++) {
                                JSONObject a = assets.getJSONObject(k);
                                if (a.optString("name").toLowerCase().endsWith(".apk")) { url = a.optString("browser_download_url"); break; }
                            }
                        }
                        r.put("latest", tag);
                        r.put("current", version());
                        r.put("url", url == null ? "" : url);
                        r.put("newer", url != null && newer(tag, version()));
                        r.put("notes", rel.optString("body", ""));
                    } catch (Exception e) {
                        try { r.put("error", "Couldn't reach GitHub (" + e.getClass().getSimpleName() + ")"); } catch (Exception ignored) { }
                    }
                    sendJs("window.auroraUpdateStatus && window.auroraUpdateStatus(" + JSONObject.quote(r.toString()) + ")");
                }
            }).start();
        }

        @JavascriptInterface
        public void installUpdate(final String url) {
            if (!canInstall()) {
                // Try the TV's own "install unknown apps" screen for Aurora first.
                if (openUnknownSources()) {
                    sendJs("window.auroraUpdateStatus && window.auroraUpdateStatus(" + JSONObject.quote("{\"needsPermission\":true}") + ")");
                    return;
                }
                // Locked: hand the file to the TV's own file browser, which is allowed to install.
                act.handoffUpdate(url);
                return;
            }
            new Thread(new Runnable() {
                public void run() {
                    try {
                        java.io.File f = ApkProvider.updateFile(act);
                        download(url, f);
                        act.runOnUiThread(new Runnable() {
                            public void run() {
                                Uri u = Uri.parse("content://" + ApkProvider.AUTHORITY + "/update.apk");
                                Intent i = new Intent(Intent.ACTION_INSTALL_PACKAGE, u);
                                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                                try { act.startActivity(i); }
                                catch (Exception e) {
                                    Intent v = new Intent(Intent.ACTION_VIEW);
                                    v.setDataAndType(u, "application/vnd.android.package-archive");
                                    v.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                                    try { act.startActivity(v); } catch (Exception e2) { toast("This TV won't open the installer. Use the USB stick instead."); }
                                }
                            }
                        });
                        sendJs("window.auroraUpdateStatus && window.auroraUpdateStatus(" + JSONObject.quote("{\"downloaded\":true}") + ")");
                    } catch (Exception e) {
                        sendJs("window.auroraUpdateStatus && window.auroraUpdateStatus(" + JSONObject.quote("{\"error\":\"Download failed\"}") + ")");
                    }
                }
            }).start();
        }

        private boolean canInstall() {
            try {
                Object ok = PackageManager.class.getMethod("canRequestPackageInstalls").invoke(act.getPackageManager());
                return Boolean.TRUE.equals(ok);
            } catch (Throwable t) { return true; }
        }

        /** Opens the screen that lets Aurora install apps. Returns false if the TV has none. */
        private boolean openUnknownSources() {
            Uri me = Uri.parse("package:" + act.getPackageName());
            Intent[] tries = {
                    new Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES", me),
                    withData(cmp("com.cvte.settings", "com.cvte.settings.Settings$ManageAppExternalSourcesActivity"), me),
                    cmp("com.cvte.settings", "com.cvte.settings.Settings$ManageExternalSourcesActivity"),
                    withData(cmp("com.android.settings", "com.android.settings.Settings$ManageAppExternalSourcesActivity"), me)};
            PackageManager pm = act.getPackageManager();
            for (Intent i : tries) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    if (pm.resolveActivity(i, 0) == null) continue;
                    act.startActivity(i);
                    toast("Switch on Aurora Home here, press Back, then choose Check for updates again");
                    return true;
                } catch (Exception ignored) { }
            }
            return false;
        }

        private static Intent withData(Intent i, Uri u) { i.setData(u); return i; }

        private void sendJs(final String code) {
            act.runOnUiThread(new Runnable() { public void run() { act.js(code); } });
        }

        static boolean newer(String a, String b) {
            String[] x = a.split("\\."), y = b.split("\\.");
            for (int k = 0; k < Math.max(x.length, y.length); k++) {
                int p = k < x.length ? parse(x[k]) : 0, q = k < y.length ? parse(y[k]) : 0;
                if (p != q) return p > q;
            }
            return false;
        }

        private static int parse(String s) {
            try { return Integer.parseInt(s.replaceAll("[^0-9]", "")); } catch (Exception e) { return 0; }
        }

        private static String httpGet(String u) throws Exception {
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(u).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(15000);
            c.setRequestProperty("Accept", "application/vnd.github+json");
            c.setRequestProperty("User-Agent", "AuroraHome");
            java.io.InputStream in = c.getInputStream();
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            return bos.toString("UTF-8");
        }

        static void download(String u, java.io.File dest) throws Exception {
            for (int hop = 0; hop < 5; hop++) {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(u).openConnection();
                c.setInstanceFollowRedirects(false);
                c.setConnectTimeout(20000);
                c.setReadTimeout(30000);
                c.setRequestProperty("User-Agent", "AuroraHome");
                int code = c.getResponseCode();
                if (code >= 300 && code < 400) { u = c.getHeaderField("Location"); continue; }
                if (code != 200) throw new java.io.IOException("HTTP " + code);
                java.io.InputStream in = c.getInputStream();
                java.io.FileOutputStream out = new java.io.FileOutputStream(dest);
                byte[] buf = new byte[16384]; int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.close(); in.close();
                dest.setReadable(true, false);
                return;
            }
            throw new java.io.IOException("Too many redirects");
        }

        /** Asks Android to uninstall an app; Android shows its own confirmation. */
        @JavascriptInterface
        public void uninstall(final String pkg) {
            act.runOnUiThread(new Runnable() {
                public void run() {
                    Uri u = Uri.parse("package:" + pkg);
                    Intent[] tries = {new Intent(Intent.ACTION_UNINSTALL_PACKAGE, u), new Intent(Intent.ACTION_DELETE, u)};
                    for (Intent i : tries) {
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try { act.startActivity(i); return; } catch (Exception ignored) { }
                    }
                    toast("This TV doesn't allow uninstalling from here");
                }
            });
        }

        /** True when the "Aurora Home button" accessibility service is switched on. */
        @JavascriptInterface
        public boolean homeKeyOn() {
            try {
                String on = Settings.Secure.getString(act.getContentResolver(),
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                return on != null && on.contains(act.getPackageName() + "/");
            } catch (Exception e) {
                return false;
            }
        }

        /** Opens a system screen. Tries each candidate until one exists on this TV. */
        @JavascriptInterface
        public void openSystem(final String which) {
            act.runOnUiThread(new Runnable() {
                public void run() {
                    Intent[] tries;
                    if ("accessibility".equals(which)) {
                        tries = new Intent[]{
                                cmp("com.cvte.settings", "com.cvte.settings.Settings$AccessibilitySettings"),
                                new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                                cmp("com.android.tv.settings", "com.android.tv.settings.system.AccessibilityActivity"),
                                cmp("com.android.settings", "com.android.settings.Settings$AccessibilitySettingsActivity")};
                    } else if ("home".equals(which)) {
                        tries = new Intent[]{
                                new Intent("android.settings.HOME_SETTINGS"),
                                new Intent("android.settings.MANAGE_DEFAULT_APPS_SETTINGS"),
                                cmp("com.android.settings", "com.android.settings.Settings$HomeSettingsActivity")};
                    } else if ("android".equals(which)) {
                        tries = new Intent[]{
                                cmp("com.cvte.settings", "com.cvte.settings.Settings"),
                                cmp("com.android.tv.settings", "com.android.tv.settings.MainSettings"),
                                cmp("com.android.settings", "com.android.settings.Settings"),
                                new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)};
                    } else if ("dev".equals(which)) {
                        tries = new Intent[]{
                                new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
                                cmp("com.android.tv.settings", "com.android.tv.settings.system.development.DevelopmentActivity"),
                                cmp("com.android.settings", "com.android.settings.Settings$DevelopmentSettingsActivity")};
                    } else if ("about".equals(which)) {
                        tries = new Intent[]{
                                cmp("com.cvte.settings", "com.cvte.settings.Settings$DeviceInfoSettings"),
                                new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS),
                                cmp("com.android.tv.settings", "com.android.tv.settings.about.AboutActivity"),
                                cmp("com.android.settings", "com.android.settings.Settings$DeviceInfoSettingsActivity")};
                    } else {
                        tries = tvSettingsIntents();
                    }
                    for (Intent i : tries) {
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try {
                            act.startActivity(i);
                            return;
                        } catch (Exception ignored) { }
                    }
                    toast("This screen is locked on this TV");
                }
            });
        }

        /** JVC's own "System Setting" app: whichever installed settings app isn't Android's. */
        private Intent[] tvSettingsIntents() {
            java.util.List<Intent> l = new java.util.ArrayList<Intent>();
            l.add(new Intent(Settings.ACTION_SETTINGS));
            PackageManager pm = act.getPackageManager();
            java.util.List<String> pkgs = new java.util.ArrayList<String>();
            for (ApplicationInfo a : pm.getInstalledApplications(0)) {
                String p = a.packageName.toLowerCase();
                if (!p.contains("setting") || p.equals("com.cvte.settings") || p.startsWith("com.android.providers")) continue;
                if (p.contains("tv") || p.contains("cvte") || p.contains("mstar") || p.contains("jvc")) pkgs.add(0, a.packageName);
                else pkgs.add(a.packageName);
            }
            for (String p : pkgs) {
                Intent i = null;
                try { i = pm.getLeanbackLaunchIntentForPackage(p); } catch (Throwable ignored) { }
                if (i == null) i = pm.getLaunchIntentForPackage(p);
                if (i != null) l.add(i);
            }
            return l.toArray(new Intent[0]);
        }

        private static Intent cmp(String pkg, String cls) {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.setClassName(pkg, cls);
            return i;
        }

        /** Calls an (API 24+) TvInputInfo method taking a Context, if this Android has it. */
        private Object call(TvInputInfo info, String method) {
            try {
                return TvInputInfo.class.getMethod(method, Context.class).invoke(info, act);
            } catch (Throwable t) {
                return null;
            }
        }

        private void toast(final String msg) {
            Toast.makeText(act, msg, Toast.LENGTH_LONG).show();
        }

        private static String iconB64(Drawable d) {
            int size = 128;
            Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            if (d instanceof BitmapDrawable && ((BitmapDrawable) d).getBitmap() != null) {
                Bitmap src = ((BitmapDrawable) d).getBitmap();
                c.drawBitmap(Bitmap.createScaledBitmap(src, size, size, true), 0, 0, null);
            } else {
                d.setBounds(0, 0, size, size);
                d.draw(c);
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.PNG, 100, bos);
            return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
        }
    }
}
