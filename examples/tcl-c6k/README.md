# TCL C6K — personal setup

ADB identifies this TV as TCL G08 / Smart TV Pro, running Android 14 (SDK 34).
Original launcher: `com.google.android.apps.tv.launcherx/.home.HomeActivity`.
Live TV: `com.tcl.tv`.

Dock, from left to right: YouTube, Stremio, YouCine, SportzX, Netflix, Prime Video, and HBO Max.
Apps to preserve: YouTube, Stremio, Netflix, Globoplay, Prime Video, HBO Max, SportzX, and YouCine.
Configuration: `launcher/app/src/main/res/values/config.xml`.
Launcher labels and dates use English, including when the system language is Portuguese.
The compact layout uses seven columns, a selectable art background, and a dock in the lower half,
leaving the upper area open for a wallpaper. Large headings and dynamic glow are disabled.
Live TV, PS5 (when its CEC input is present and not disconnected), Globoplay, and Play Store appear in the second row.
The PS5 shortcut opens HDMI 1; its observed input ID is configured in `ps5_input_id` and should be updated if the console changes ports.
Date and time are on the left; Inputs, network, and Settings are on the right.
Other shortcuts are only hidden through `hidden_apps`; their apps and services remain installed and enabled.

## Installation

With the Python environment described in the main README, JDK 17, Android SDK 34, and
build-tools 34.0.0, run these commands from the repository root:

```bash
export TV_HOST=192.168.5.162
export JAVA_HOME=/path/to/jdk17/Contents/Home
export ANDROID_HOME=/path/to/android-sdk
bash examples/tcl-c6k/install.sh
```

The script builds the launcher, verifies the transfer and installation, opens the launcher,
and checks which activity handles Home. If the firmware still prioritizes Google TV,
it disables `com.google.android.apps.tv.launcherx` and
`com.google.android.tungsten.setupwraith` for user 0. On this firmware,
the recovery activity in the second package interferes with the Home button when
Google TV is disabled. This package also provides setup and account-linking features;
re-enable it before using those features.
The script waits for the firmware transition and checks the Home handler again.
The original launcher remains installed and can be re-enabled with the restore script.
Other apps and services remain enabled.
On the first installation, check the screen and remote-control navigation.

## Restore Google TV

```bash
TV_HOST=192.168.5.162 bash examples/tcl-c6k/restore.sh
```

The script re-enables the setup assistant and original launcher, sets its activity as the
Home handler, and opens Home. The custom launcher remains installed for future use.

## On-device verification

Build and installation were verified, including transfer integrity.
All seven apps in the first row opened from the dock and returned to Home with the Home button.
The grid and input menu were checked through screenshots. All eight specified apps remain enabled.
Restoring Google TV was also tested before reapplying the custom launcher with the installation script.
The PS5 shortcut was verified to open HDMI 1. Physical cable disconnection has not been tested.

## Local files

The inventory captured before changes is stored in `backup/tcl-c6k/baseline.txt`.
Screenshots and the build environment are under `backup/`, which Git ignores.
The inventory may contain device identifiers and network details; keep it local.

## Change the appearance

To change the dock order, edit `dock_apps` in the configuration file and rebuild.
Use **Wallpaper** in the top bar to switch between the bundled paintings and a custom image
included during installation. To include your own JPG, PNG, or WebP from the computer, set
`TV_WALLPAPER=/absolute/path/to/image.jpg` before running the install script. It is bundled into
the APK, transferred to the TV over ADB, and selected on first launch. To replace it later, rerun
the installer with a different image path.
The choice is saved; old animated or blue background selections migrate to Hokusai.
Artwork credits and source links are in `launcher/WALLPAPER-CREDITS.md`.

Press **Up** from the first app row to view the painting: the dock slides to the bottom
edge and the top bar and shading fade away. **Down**, **OK**, or **Back** restores the
apps and the previous selection without launching an app. Press **Up** again to reach
the top controls (Wallpaper, Inputs, Network, Settings). Only the brief transition is
animated; there is no continuous wallpaper animation.

This interaction is inspired by Apple's full-screen Top Shelf preview:
https://support.apple.com/guide/tv/navigate-apple-tv-4k-atvbe55a4b4e/tvos
