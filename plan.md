# ApexStudio editor stabilization plan

## Scope

Fix the editor's user-facing feature actions and improve the existing Compose timeline toward a CapCut-like editing flow without replacing the current Media3/export architecture. The repository has no literal `Future` screen; the implementation treats that wording as the requested incomplete feature behavior and removes silent no-op paths where a preset/action cannot currently execute.

## Implementation approach

1. Preserve the existing timeline authority and callbacks in `VideoTimeline`, `EditorTimelineUi`, and `EditorTimelineController`.
2. Make the timeline workspace visually layered using the existing dark navy/cyan/purple palette: a stable surface, subtle depth border, elevated resize handle, and clear focus affordance. Avoid expensive blur or fake 4K claims.
3. Improve interaction semantics around the timeline host and toolbar so selection, snapping/ripple state, focus, and resize affordances are explicit and accessible.
4. Audit edit-pack preset routing. Every displayed preset must either invoke a real callback already supported by the editor or show an honest unavailable state; no silent `else {}` path.
5. Add pure tests for the new preset routing policy/labels and preserve existing timeline geometry tests.

## Project structure

- `app/src/main/java/com/apexstudio/app/ui/components/`: timeline rendering, filmstrips, viewport gestures, and trim math.
- `app/src/main/java/com/apexstudio/app/ui/screens/editor/`: Compose editor workspace, toolbar, preview overlays, and feature panels.
- `app/src/main/java/com/apexstudio/app/presentation/viewmodel/`: editor state transitions, playback, timeline mutations, and persistence.
- `app/src/main/java/com/apexstudio/app/domain/model/`: shared clip/timeline/export models.
- `app/src/test/java/`: JVM/Compose regression coverage for math, layout, and routing.

## Design direction

Candidate styles: Neon editorial workstation (0.06), cinematic glass transport deck (0.07), or restrained pro NLE console (0.08). Choose **cinematic glass transport deck**: a deep navy canvas with cyan-to-violet edge light, restrained elevation, and compact controls that keep the playhead and clips visually dominant.

- Design movement: mobile NLE control surface with cinematic glass and instrument-panel depth.
- Core principles: playhead-first hierarchy, low-noise dark surfaces, one clear active accent, and responsive touch targets.
- Color philosophy: navy reduces preview fatigue; cyan marks transport/playhead truth; violet is reserved for secondary depth and future-facing feature affordances.
- Layout paradigm: stacked preview/timeline with a persistent, centered editing axis rather than a dense dashboard grid.
- Signature elements: a thin gradient timeline rim, elevated resize grip, and a compact cyan playhead/focus treatment.
- Interaction philosophy: every visible action either performs immediately or communicates its unavailable state; dragging preserves context and avoids unexpected resets.
- Animation: use short fade/slide transitions only for overlays; keep timeline gestures direct and avoid re-composition-heavy effects during playback.
- Typography: reuse the existing compact Compose typography; uppercase micro-labels only for timeline/status badges, normal case for actions.
- Brand essence: a precise pocket editing workstation for fast, tactile cuts; focused, cinematic, dependable.

## Constraints

- Do not claim unsupported 4K/3D rendering. “4K/3D feel” means visual polish and depth; export capability remains governed by the existing export capability checks.
- Do not merge or push until implementation and available verification are complete.
- The current sandbox Gradle run is blocked by the installed Java 21 runtime lacking a Java compiler toolchain capability; report this separately from code failures.
