# Troubleshooting

Most problems below come from the TV locking something down, not from Aurora itself.
The **Hidden settings screens** tool (hold **OK** on the Settings button) is your main
diagnostic: it lists the TV's IP, how many ad screens were closed, the screens Aurora has
seen, and every settings-like screen it can open.

## The APK won't install ("blocked" / "unknown sources")

The TV blocks installs from the file browser by default. Find **Security**, **Install
unknown apps**, or **Unknown sources** in the TV's settings and allow it, then retry.
Aurora can't flip this switch for you — it's a system setting.

## Home button still opens the TV's own launcher

Turn on the Home-button helper: Aurora → hold **OK** on Settings → open the accessibility
screen → switch on **HomeKeyService**. See [INSTALL.md](INSTALL.md#the-home-button-helper-for-tvs-that-ignore-the-default-home-setting).

If there is **no accessibility screen** anywhere in the list, the TV has removed it. In
that case the Home button can't be redirected without root, but Aurora still opens at
startup and from the app list.

## The TV's ad screensaver still appears

1. Make sure **Block the TV's ad screensaver** is **On** (Settings).
2. Make sure the **HomeKeyService** helper is on — the blocker runs inside it.
3. When an ad screen appears, note it, then hold OK on Settings → **Recently seen screens**.
   The ad screen will be near the top. If it isn't already tagged `[ad, closed]`, open an
   issue with its package name (e.g. `com.cvte.tv.pictorial`) and it can be added to the
   block list.

Known CVTE ad screens already blocked: `com.cvte.tv.pictorial`,
`com.cvte.tv.launcher.space`, plus anything whose screen name ends in `AdActivity` or
contains `screensaver`.

## HDMI / input buttons don't switch directly

On TVs that don't expose Android TV "inputs" (many CVTE/budget sets), no app can switch
inputs directly — the OS doesn't offer it. Aurora's input buttons open the TV's own source
menu instead; pick the input there, or just use the remote's **Source** button.

On a standard Android TV the input buttons switch directly with no setup.

## Check for updates says "latest" when there's a newer version

GitHub's raw file cache can briefly serve an old `release/latest.json`. Wait a minute and
try again. Updates are published under a versioned filename to avoid a stale APK download.

## Update downloads but won't install

The TV blocks installs from Aurora (see the first section). Aurora falls back to saving the
update in the **Download** folder and opening the file browser — go there, select
**AuroraHome-update.apk** and install it by hand. If that also fails, install the APK from
a USB stick as in [INSTALL.md](INSTALL.md).

## The screen looks flat / no blur

The TV's built-in web engine is old and skips backdrop-blur. Everything still works; it
just looks flatter. There's no fix without a newer system WebView, which locked TVs don't
allow.

## Menus are cut off or won't scroll

Fixed in 1.7.2+. Update to the latest version.

## After a firmware update, the Home button or ad blocker stopped working

A TV firmware update can switch accessibility services off. Re-enable **HomeKeyService** as
in [INSTALL.md](INSTALL.md#the-home-button-helper-for-tvs-that-ignore-the-default-home-setting).

## It crashes / Home goes back to the TV launcher after an update

You may have caught a bad build. Reinstall the latest `AuroraHome.apk` from a USB stick.
If it keeps crashing, open an issue with your TV model and Android version.
