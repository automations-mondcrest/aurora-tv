# Changelog

## 1.7.5
- Fix a broken 1.7.4 build that shipped without its app code.

## 1.7.4
- Settings shows only screens that do something; the CVTE "TV Setting" entry (which only
  opened live TV) is removed. The main settings app is labelled "Android settings".
- Diagnostics shows whether anything is listening for network debugging.
- *(Published without app code — superseded by 1.7.5.)*

## 1.7.3
- Add a "Test direct HDMI switching" tool and a "Copy TV system apps to USB" tool for
  diagnosing input switching on locked TVs. *(Both later removed once it was confirmed the
  hardware can't switch inputs directly.)*

## 1.7.2
- Fix menus that overlapped and wouldn't scroll on older Android WebViews.
- Clearer Settings names.

## 1.7.1
- Install updates on TVs that block app installs, by saving to the Download folder and
  opening the TV's own file browser.

## 1.7
- Self-update: check this repo for a newer version, download and install it.

## 1.6
- Settings lists the TV's real settings apps instead of guessing.
- Smarter HDMI buttons: Android TV inputs when available, else the TV's own input menu,
  learned from the Source button.

## 1.5
- 20 rotating photos; drop the two where the clock was hard to read.

## 1.4
- Ad-screensaver blocker.
- Calm screen: the dock fades after 3 minutes idle.
- Darker menus; reorganised Settings.

## 1.3
- "Find hidden settings screens" tool for TVs that hide developer/accessibility screens.

## 1.2
- Shortcuts to Android's hidden settings screens.

## 1.1
- Home-button helper (accessibility service) for TVs that hard-wire Home to their launcher.
- Open at startup.
- Uninstall apps from the app library.

## 1.0
- First release: lockscreen-style home with a rotating photo, depth-effect clock, and a
  favourites dock.
