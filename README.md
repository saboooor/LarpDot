# LarpDot

LarpDot is an Android app that adds a customizable Dynamic Island style overlay around the front camera cutout. It shows music playback, ongoing notification activities, and flashlight status in a floating pill or compact dots.

## Features

- **Camera cutout alignment:** Detect the cutout and adjust the overlay's size and position manually.
- **Music island:** Show playback information and controls, customize album art and player layout, and choose a visualizer mode (BPM, device audio, or audio preview).
- **Live activities:** Turn supported ongoing notifications, including progress, navigation, calls, and timers, into island activities.
- **Flashlight island:** Control the torch and show its status in the island. Swipe right or left on the compact pill or dot, then hold to step through supported brightness levels. Moving farther makes the steps repeat faster and change more levels at once. Optional strength outlines show brightness around the compact island and expanded card. [PixelLight](https://github.com/chenxiaolong/PixelLight) integration is available when that app is installed.
- **Display controls:** Configure tap behavior, lock screen and screen off visibility, and an app blacklist.

The app is built with Kotlin, Jetpack Compose, and Material 3 Expressive. It supports Android 8.0 (API 26) and newer.

## Getting started

1. Install Android Studio, JDK 17, and the Android SDK platform for API 37.
2. Open the repository root in Android Studio, or build from the command line:

   ```bash
   ./gradlew assembleDebug
   ```

3. Install the debug build on a connected device or emulator:

   ```bash
   ./gradlew installDebug
   ```

   The APK is also available at `app/build/outputs/apk/debug/app-debug.apk`.

4. Open LarpDot, tap **Setup**, grant the access needed for the features you want, and switch on the overlay in the top bar.

The overlay works best on a device with a front camera cutout. Use **Home → Cutout Alignment** if the automatic position needs adjustment.

## Permissions and feature access

| Access | Used for |
| --- | --- |
| Display over other apps | Shows the island above other apps using the standard overlay service. |
| Notification access | Reads media and ongoing notification state for music and live activities. |
| Accessibility service | Allows the island to appear over the notification shade and lock screen. It can also manage the overlay in place of the standard overlay service. |
| Audio capture consent | Requested when starting the device audio visualizer; Android's MediaProjection prompt grants a live capture session. This mode requires Android 10 or newer. |
| Internet | Fetches optional 30 second music previews from Deezer or iTunes for the audio preview visualizer. |

The setup screen links to the Android settings for notification, overlay, and accessibility access. The flashlight feature requires a device with a camera flash; PixelLight is optional.

## Development

Run local unit tests with:

```bash
./gradlew testDebugUnitTest
```

The main code is in `app/src/main/java/ca/saboor/larpdot/`: `ui/screens/` contains the settings screens, `ui/overlay/` renders the island, and `service/` handles overlay, notification, accessibility, and audio capture services. Build configuration is in `app/build.gradle.kts`.

Release signing uses `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` environment variables configured in the app's Gradle build file.
