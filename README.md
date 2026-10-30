# Aurora Home

An iPhone-lockscreen-style home screen for Android TVs. Real photos rotate in the
background, the clock tucks behind mountains and buildings, and your favourite apps
sit in a glass dock along the bottom.

Built for a JVC LT-32N750A (Android 9, CVTE board) whose own home screen is full of
ads. It should work on most Android TV sets running Android 5 or newer.

## What it does

- **Lockscreen look.** A random photo every time the TV turns on, then a new random
  photo every 5 minutes with a slow crossfade. On photos with a clear skyline the
  clock sits behind the scenery.
- **Favourites dock.** Pick which apps sit in the dock. Hold OK on any app to move it,
  remove it or uninstall it.
- **All apps.** Every installed app in one place. Press OK to add or remove a favourite.
- **Home button takeover.** On TVs that hard-wire the Home button to their own
  launcher, an optional accessibility helper sends Home to Aurora instead.
- **Ad screensaver blocker.** The same helper closes the TV's own ad screensaver and
  shows Aurora's calm clock screen instead. You can switch this off in Settings.
- **Calm screen.** After 3 minutes without a button press the dock fades away and
  only the photo and clock stay. Any button brings it back.
- **Self-update.** Settings → Check for updates reads `release/latest.json` in this repo,
  downloads the newer APK and opens the TV's installer.

## Install

1. Download [`AuroraHome.apk`](https://raw.githubusercontent.com/automations-mondcrest/aurora-tv/main/release/AuroraHome.apk) (also in the `release` folder).
2. Copy it to the top level of a USB stick.
3. Plug the stick into the TV, open the TV's file browser, select the APK and press OK.
4. Press Home. If the TV asks which home screen to use, pick **Aurora Home** → **Always**.

### If Home still opens the TV's own launcher

Many budget TVs ignore the "default home app" setting. Aurora works around it:

1. Open Aurora (from the TV's app list), then hold OK on **Settings**.
2. In the list, open the **AccessibilitySettings** screen.
3. Switch on **HomeKeyService** (shown as *Aurora Home button* on some TVs).

The helper only sees the Home button and the names of screens that open. It never
reads what is on screen or anything you type.

## Remote controls

| Button | What it does |
| --- | --- |
| Arrows | Move |
| OK | Open |
| Hold OK, or Menu | Edit (move, remove, uninstall) |
| Back | Close a panel |
| Channel Up | Next photo |
| Hold OK on Settings | Diagnostics and hidden settings screens |

## Known limits

- Input buttons (HDMI, AV) switch directly on TVs that support Android TV inputs. On TVs that
  don't (many CVTE/budget sets), press the remote's Source button once: Aurora learns the TV's
  input menu and its HDMI buttons open that menu from then on. You can also pick it by hand:
  hold OK on Settings → Choose the TV's input menu.
- Apps built into the TV can't be uninstalled without root.
- The look depends on the TV's built-in web engine. Older engines skip the blur effects.

## Building it yourself

The app is a single WebView activity plus a small Java bridge. It builds without
Android Studio or Gradle, using Ubuntu's Android packages:

```sh
sudo apt install aapt apksigner android-sdk-platform-23 dalvik-exchange zipalign default-jdk
./build.sh path/to/your.keystore your-alias
```

`web/aurora.tpl.html` is the browser preview of the UI. `tools/make_tv_page.py` turns it
into `app/assets/index.html` for the TV (bundled fonts, photos as files, CSS that old
Android WebViews understand, and the native bridge).

To publish an update, bump `versionName`/`versionCode` in `app/AndroidManifest.xml`,
replace `release/AuroraHome.apk` and update `release/latest.json`.

Updates only install over an existing copy when they are signed with the same key.
Keep your keystore private and never commit it.

## Credits

Photos and fonts are by others and keep their own licences. See [CREDITS.md](CREDITS.md).

Made by Mondcrest Automations.
