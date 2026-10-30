# Installing Aurora Home

This is the full walkthrough. For a quick summary see the [README](../README.md#install).

## Before you start

- **Check your TV runs Android.** Aurora is an Android TV app. It will **not** install on
  Samsung (Tizen), LG (webOS) or Hisense VIDAA TVs — those are different operating systems.
  If your TV's settings mention an "Android version", you're good.
- **You need a USB stick** for the first install (updates after that can be over Wi-Fi).

## Step 1 — Put the APK on a USB stick

1. On a computer or phone, download
   [`AuroraHome.apk`](https://raw.githubusercontent.com/automations-mondcrest/aurora-tv/main/release/AuroraHome.apk).
2. Copy it to the **top level** of a USB stick (not inside a folder).
3. Eject the stick safely, then plug it into the TV.

## Step 2 — Install it

1. On the TV, open the file browser. Depending on the brand it's called **File Manager**,
   **Media Center**, **USB**, or similar.
2. Find your USB stick, then **AuroraHome.apk**. Select it and press **OK**.
3. If an **Install** screen appears, choose **Install**.
4. If you see **"blocked"** or **"unknown sources"**, the TV needs permission to install
   from the file browser. Look for **Security** or **Install unknown apps** in the TV's
   settings and allow it, then try again. (Aurora can't change this setting for you — it's
   a system switch.)

## Step 3 — Make Aurora the home screen

Press the **Home** button on the remote.

- **If the TV asks which home app to use:** pick **Aurora Home**, then **Always**. Done.
- **If nothing changes** (Home still opens the TV's own screen): your TV ignores the
  default-home setting. Use the helper below.

### The Home-button helper (for TVs that ignore the default-home setting)

1. Open Aurora from the TV's **app list** (apps grid / all apps).
2. Hold **OK** on the **Settings** button at the bottom until a menu appears.
3. Find and open the accessibility screen. It may be listed as **AccessibilitySettings**,
   or you can reach it through the TV's own settings app.
4. In the accessibility list, select **HomeKeyService** (also shown as *Aurora Home button*).
5. Switch it **On**. Android shows a warning that the service can observe actions — this is
   the standard warning for any accessibility tool. Choose **OK / Allow**.
6. Press **Home**. It now opens Aurora.

> The helper only reacts to the Home button and reads the *names* of screens that open
> (so it can close the ad screensaver). It never reads screen contents or your typing.

## Step 4 — Make it yours

- **Add apps to the dock:** select **+ Add** at the end of the dock, or **All apps &
  inputs**. Press OK on anything to add or remove it. A gold star marks your favourites.
- **Reorder or remove:** hold **OK** on any app in the dock → Move left / Move right /
  Remove / Uninstall.
- **Turn the ad-screensaver blocker on/off:** Settings → *Block the TV's ad screensaver*.

## Updating later

Once 1.7.5 or newer is installed you don't need the USB stick again:

1. **Settings → Check for updates.**
2. **Download and install.**
3. If the installer opens, press **Install**. If instead the file browser opens, go to the
   **Download** folder, select **AuroraHome-update.apk**, press OK, then Install.

## Removing Aurora

Uninstall **Aurora Home** from the TV's app manager, and turn off **HomeKeyService** in the
accessibility screen. The TV's original home screen comes back.
