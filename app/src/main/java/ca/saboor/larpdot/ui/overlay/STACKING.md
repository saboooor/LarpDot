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
