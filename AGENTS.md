# AI Agent Instructions

## Do Not Implement

**CRITICAL RULE FOR ALL AI ASSISTANTS:**
Do **NOT** implement, re-introduce, or propose any of the features listed below (including renamed equivalents or partial variants) unless the user explicitly asks to re-add a specific item and remove it from this list.

1. **Flashlight signal modes & safety auto-off timer**
   - Do not add strobe, SOS / Morse code pulsing patterns, strobe speed controls, signal mode switcher chips, or automatic flashlight shutoff / safety countdown timers.
2. **Volume slider activity (Volume HUD)**
   - Do not add a media volume live activity / pill, volume-change broadcast HUD, or expanded volume slider / mute controls inside the Dynamic Island.
3. **Clipboard & screenshot pocket ("Island Pocket" / "Hold in Island")**
   - Do not add screenshot auto-detection (`MediaStore` observers), photo-library permission requests for screenshots, a "Hold in Island" share-sheet target activity, clipboard pinning/holding in the island, or quick-note pocket cards.
4. **Presets (Quick Presets / Theme Presets)**
   - Do not add built-in visual presets, a "Quick Presets" gallery, or saved user theme presets.
   - Standard JSON configuration backup (export/import/copy/paste) and Reset to Defaults are separate from presets and may remain.
5. **Sleep timer (Music Sleep Timer)**
   - Do not add a music sleep timer, countdown-to-pause timer, sleep timer chips on the expanded music island, or sleep timer controls in Music settings.
6. **Quick timer & stopwatch**
   - Do not add a built-in quick timer, countdown timer launcher, or stopwatch live activity / card inside LarpDot (system/third-party clock notifications handled generically as Live Activities are separate).
7. **Flashlight compact pill tap-action setting**
   - Do not restore the flashlight-specific “Tap to Toggle” / “Tap to Expand” selector. A single tap on a secondary flashlight bubble toggles right-side info.
8. **Song history**
   - Do not add the Music History category, a Recently Played list, or local recording of played tracks for song history.

### Maintaining this list
When the user rejects or asks to remove a feature so future AI sessions do not rebuild it, add it to the **Do Not Implement** list above with a concise description of what is disallowed.
