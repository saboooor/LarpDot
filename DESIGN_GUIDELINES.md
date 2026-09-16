# LarpDot Android UI/UX Design Guidelines & Specifications

> **Handoff Document for UI Development**  
> This specification documents all visual patterns, design conventions, Material 3 Expressive rules, and architectural standards established for LarpDot. Any developer or AI agent extending this codebase must adhere strictly to these guidelines to preserve visual consistency and native Android design integrity.

---

## 1. Core Design Philosophy
- **Native Android & Pixel-First Aesthetics**: Designed with Google's Material 3 Expressive design language. Uses fluid spring physics, dynamic rounded shapes, and native navigation primitives.
- **Dynamic Color (Monet) Native**: Supports Android 12+ wallpaper-extracted dynamic theming (`dynamicDarkColorScheme` / `dynamicLightColorScheme`), falling back gracefully to customized high-contrast palettes.
- **Tonal Container Hierarchy**: Built around the 5 standard surface container levels:
  - `surfaceContainerLowest`
  - `surfaceContainerLow` (Default for neutral cards & lists)
  - `surfaceContainer` (Default for featured surfaces & app bars)
  - `surfaceContainerHigh` (Default for dialogs & elevated sheets)
  - `surfaceContainerHighest` (Default for tags, chips, track backgrounds)
- **Predictive Back & Edge-to-Edge**: Always enable `enableEdgeToEdge()` in the activity and handle `WindowInsets.safeDrawing` or explicit inset paddings without hardcoding status/navigation bar dimensions.

---

## 2. Card Styling & Outline Rules (CRITICAL)

### The Golden Rule for Card Outlines:
- **NEUTRAL / DARK GRAY / SURFACE CARDS MUST HAVE NO OUTLINES**:
  - Do NOT apply `border = BorderStroke(...)` or `outlineVariant` strokes to standard dark gray, surface-colored, or neutral cards.
  - Neutral cards rely strictly on elevation and background container differentiation (`surfaceContainerLow`, `surfaceContainer`).
  - Use `LarpCard` for all standard cards.
- **ONLY THEMED / HIGHLIGHTED CARDS KEEP OUTLINES**:
  - Outlines are reserved exclusively as color accents for themed feature cards, active alerts, or live state cards.
  - Outlines should always use subtle alpha levels: `accentColor.copy(alpha = 0.35f)` to avoid harsh contrast.
  - Use `AccentCard` for featured items.

---

## 3. Connected Button Groups & Segmented Controls

- **Connected Material 3 Button Groups**:
  - Wherever segment controls, category filters, or mutually exclusive toggles are used, implement native Material 3 Connected Button Groups (`ConnectedButtonGroup`).
  - Powered by `ButtonGroup` and `ToggleButton`.
  - Shapes are configured via:
    - Leading: `ButtonGroupDefaults.connectedLeadingButtonShapes()`
    - Middle: `ButtonGroupDefaults.connectedMiddleButtonShapes()`
    - Trailing: `ButtonGroupDefaults.connectedTrailingButtonShapes()`
  - Spacing uses `ButtonGroupDefaults.ConnectedSpaceBetween`.
  - Accessibility roles: `Modifier.semantics { role = Role.RadioButton }`.
- **Icons Are Mandatory**:
  - Every button inside a button group MUST include an appropriate icon paired with the text label (`Row { Icon(...); Text(...) }`).

---

## 4. Tactile Motion & Spring Physics

- **Spring-Based Press Feedback**:
  - Pixel and modern Android surfaces squash subtly when touched using a spring rather than a linear curve.
  - Use `Modifier.expressivePressEffect(pressed = pressed)`:
    - Damping Ratio: `Spring.DampingRatioMediumBouncy`
    - Stiffness: `Spring.StiffnessMediumLow`
    - Target scale: `0.97f` on touch down, spring back to `1f` on release.

---

## 5. Navigation Bar Architecture

- **Material 3 Expressive ShortNavigationBar**:
  - Utilize `ShortNavigationBar` and `ShortNavigationBarItem` for compact, expressive bottom navigation.
  - Screen transitions between root tabs must use `Crossfade` with expressive timing (250–300ms) or slide transitions.

---

## 6. Directory Structure & Modular Architecture

All application code is cleanly partitioned under `app/src/main/java/ca/saboor/larpdot/`:

```text
app/src/main/java/ca/saboor/larpdot/
├── MainActivity.kt               # Entry point & edge-to-edge configuration
├── LarpDotApp.kt                 # Root Scaffold & ShortNavigationBar
│
├── navigation/
│   └── Destination.kt            # Enum of navigation destinations
│
├── ui/
│   ├── theme/
│   │   ├── Theme.kt              # MaterialExpressiveTheme, Monet & ColorSchemes
│   │   └── Type.kt               # Expressive typography scale
│   │
│   ├── components/
│   │   └── Widgets.kt            # Reusable native M3 primitives (LarpCard, AccentCard, etc.)
│   │
│   └── screens/
│       └── HomeScreen.kt         # Clean, empty canvas ready for content
```

---

## 7. Guidelines for Extending the App
1. **Never re-add borders to neutral cards**: When adding a new card, leave `border = null` unless it is an explicitly highlighted or alert card.
2. **Keep functions modular**: Do not bloat `MainActivity.kt`. New features belong in their own screen files or `ui/components/`.
3. **Always verify compilation**: Run `./gradlew assembleDebug` after making changes to ensure all Compose compiler constraints and Kotlin imports pass.

