# Island stacking

`resolveIslandStack` orders candidates by descending priority, then stable ID. The
first candidate owns the camera island. When there is 1 secondary candidate, it gets
a trailing dot on the right. When there are 2 secondary candidates, they are placed
on the left and right of the camera island instead of both on the right. Removing
a candidate automatically promotes the next one. IDs must be unique and must not
change when a provider updates its content.

`builtInIslandStack` is the shared adapter used by the Compose overlay and the
window controller. Priorities:
- Two-sided Live Activity (300): Call, timer, navigation with ETA, or progress with a percentage.
- Two-sided Media (200): Music playback with artwork and equalizer.
- Single-sided Live Activity (150): Notification activity without right-wing content (such as indeterminate progress without a percentage). It is demoted to a dot so richer two-sided providers like music take over the primary pill.
- Flashlight (100).

To add an integration, add its presentation type and renderer/actions, then add
its active candidate and priority to the adapter. Do not add pairwise split rules
to the renderer or window controller. Both consume the resulting primary and
secondary list; touch bounds reserve space for every dot (symmetrically on left
and right when 2 dots are present). `stackedPillWidth` reserves space on narrow screens
while preserving the camera anchor.

Expansion captures the primary type, selected dot index, and dot count so each
dot morphs from its own slot (left or right) rather than from a single secondary slot.

All built-in content uses the same `IslandModel` interaction contract. Providers
supply activate and hold actions; the shared model applies the tap-to-expand
preference for both primary pills and secondary bubbles. Content-specific
composables still render music, flashlight, or notification controls.

`rememberIslandPressFeedback` supplies one spring animation for all island slots
and the shared `IslandSurfaceOverlay`. It observes the initial pointer pass without
consuming events, so child controls keep their gestures while the island responds
to presses. Release and cancellation reset the feedback.

## Shared shell

- `IslandModel` combines provider identity, actions and `IslandPresentation`.
  The presentation adapter supplies status, accent, progress, outline preferences,
  and compact sizing. `IslandCompactContent` is the single dispatch point for
  primary compact content, including content displayed during expansion/collapse.
- `IslandGeometry` owns camera sizing, camera-side expanded placement and touch
  region calculation. Compose reports animated primary/secondary bounds; the
  controller unions those measured bounds with touch padding. It does not estimate
  provider sizes. Bounds changes schedule a touch-window update on the UI thread.
- `islandTapGestures` and `islandDragGestures` own gesture timing, thresholds,
  cancellation, and stretch. Provider actions implement playback changes or torch
  strength. Updated callbacks do not restart an in-progress gesture. A hold consumes
  the action so release cannot also activate a tap.
- `IslandLifecycle` owns visibility and cancellable delayed exits for providers and
  announcement state. The controller applies one collapse rule when the selected
  provider disappears. Expansion/collapse rendering stays in the common surface.

Artwork, playback controls, torch strength controls and activity-specific content
remain provider renderers. Their content sizes and actions may differ; shell
interaction, geometry and animation behavior are shared.
