# LarpDot

A modern, production-ready Android template app powered by **Jetpack Compose**, targeting **Android 17 (API 37)**, built with the latest 2026 Android toolchain and Google's **Material 3 Expressive** design system.

---

## Tech Stack & Versions

- **Android Gradle Plugin (AGP)**: `9.3.2`
- **Kotlin**: `2.4.10`
- **Compose Compiler Plugin**: `2.4.10`
- **Gradle**: `9.7.1` (Wrapper included)
- **Compile SDK**: `37` (Android 17)
- **Target SDK**: `37`
- **Min SDK**: `26` (Android 8.0 Oreo)
- **Jetpack Compose BOM**: `2026.09.00`
- **Material 3 Expressive**: `1.5.0-alpha28`
- **AndroidX Core KTX**: `1.19.0`
- **AndroidX Activity Compose**: `1.13.0`
- **AndroidX Lifecycle Compose**: `2.11.0`

---

## Native Android Look & Feel Highlights

- **Dynamic Color (Monet)**: Real-time wallpaper extraction and color adaptation on Android 12+ (`dynamicDarkColorScheme` / `dynamicLightColorScheme`).
- **Material 3 Expressive Theme**: Built on `MaterialExpressiveTheme` and `MotionScheme.expressive()`.
- **Tonal Container Surface Hierarchy**: Explicit use of container tones (`surfaceContainerLowest`, `surfaceContainerLow`, `surfaceContainer`, `surfaceContainerHigh`, `surfaceContainerHighest`).
- **Strict Card Styling Rules**:
  - **Neutral / Surface cards**: Zero border outlines. Surfaces rely strictly on tonal elevation for clean system dark-mode aesthetics.
  - **Featured / Themed cards**: Subtle glowing borders with alpha accents (`accentColor.copy(alpha = 0.35f)`).
- **Tactile Spring Physics**: Responsive bounce squash on touch press (`expressivePressEffect`).
- **Material 3 Connected Button Groups**: Unified multi-segment controls with icons, proper edge radiuses (`connectedLeadingButtonShapes`, `connectedMiddleButtonShapes`, `connectedTrailingButtonShapes`), and accessible semantics.
- **Wavy Progress Indicators**: Modern `LinearWavyProgressIndicator` for telemetry and progress scrubbers.
- **ShortNavigationBar**: Compact, fluid bottom bar navigation with crossfade tab transitions.

---

## Project Structure

```text
LarpDot/
├── app/
│   ├── build.gradle.kts          # Module configuration with Compose & dependencies
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── java/ca/saboor/larpdot/
│           │   ├── MainActivity.kt        # Edge-to-edge entry point
│           │   ├── LarpDotApp.kt          # Scaffold & ShortNavigationBar
│           │   ├── navigation/
│           │   │   └── Destination.kt     # Enum of primary tabs & icons
│           │   ├── ui/
│           │   │   ├── theme/
│           │   │   │   ├── Theme.kt       # MaterialExpressiveTheme & ColorSchemes
│           │   │   │   └── Type.kt        # Expressive typography scale
│           │   │   ├── components/
│           │   │   │   └── Widgets.kt     # Reusable native M3 primitives (LarpCard, AccentCard, etc.)
│           │   │   └── screens/
│           │   │       └── HomeScreen.kt  # Clean empty canvas ready for app content
│           └── res/
│               └── values/
│                   ├── strings.xml
│                   └── themes.xml         # Edge-to-edge framework style
├── DESIGN_GUIDELINES.md          # Architectural rules & design specifications
├── build.gradle.kts              # Root build script
├── settings.gradle.kts           # Root settings & repository management
├── gradle.properties             # JVM arguments & AndroidX flags
├── gradlew                       # Gradle wrapper script
└── gradlew.bat                   # Windows wrapper script
```

---

## Building and Running

### Build Debug APK:
```bash
./gradlew assembleDebug
```
The resulting APK is generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### Install on Connected Device / Emulator:
```bash
./gradlew installDebug
```

### Run Tests:
```bash
./gradlew test
```

