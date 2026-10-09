# ApexStudio → Professional-Grade Editor: Research Report

**Date:** 2026-10-09
**Goal:** Make ApexStudio an A1-class mobile editor that makes users forget CapCut, KineMaster, VN, and LightCut.
**Scope:** Research only. No code changes. All recommended resources have clear permissive licenses (MIT / CC0 / Apache-2.0 / OFL / ISC).

---

## 1. Executive Summary

ApexStudio already has a stronger technical core than most competitors admit: Jetpack Compose + Media3 Transformer, a GPUImage 3D-LUT engine, 10 video layers at 60fps, keyframes, chroma key, speed ramps, AR face filters, Jamendo CC music integration, and 720p/1080p/4K export. The template infrastructure (`TimelineTemplateManager`, `TimelineTemplateModels`, `TransmissionTemplate`) already exists in the repo.

The gap to "professional" is **not engine power — it is content volume and packaging**: competitors win on (a) hundreds of one-tap filters/LUTs, (b) categorized sticker/text/effect galleries (the exact UI in Prince's 7 screenshots: filter galleries, template browsers, Hot/Emoji/Movie-Quote sticker pickers, timeline clip thumbnails, 16:9 + 1080p export), (c) CapCut-style one-tap templates with placeholder swapping, and (d) beat-synced/auto-caption conveniences.

Recommended strategy: **keep the engine, flood the content library with own-generated or permissively-licensed assets, and ship templates as the headline feature.** All assets below are either generated in-house (zero license risk — already the established ApexStudio convention) or carry MIT/CC0/OFL/ISC licenses.

---

## 2. Feature Comparison: ApexStudio vs Competitors vs Target

Legend: ✅ has it · ⚠️ partial · ❌ missing · 🎯 target for this program

| Category / Feature | ApexStudio (current) | CapCut | KineMaster | VN | LightCut | Target |
|---|---|---|---|---|---|---|
| **Timeline & core editing** |
| Multi-track timeline | ✅ 10 video layers, 60fps | ⚠️ limited layers | ✅ true multi-layer | ✅ multi-track | ⚠️ basic | ✅ keep, polish |
| Trim / split / crop | ✅ TrimPanel | ✅ | ✅ frame-precise | ✅ 0.05s precision | ✅ | ✅ |
| Speed control + speed ramp / curve | ✅ SpeedRampPanel | ✅ velocity curves | ✅ | ✅ | ✅ | ✅ |
| Reverse clip | ❌ | ✅ | ✅ | ✅ | ❌ | 🎯 Phase 4 |
| Freeze frame | ❌ | ✅ | ✅ | ✅ | ❌ | 🎯 Phase 4 |
| Keyframe animation (any param) | ✅ KeyframePanel + presets | ✅ | ✅ almost any param | ✅ 19 built-in | ⚠️ | ✅ extend presets |
| **Filters / LUTs** |
| Built-in filter count | ✅ 20 own parametric LUTs | ✅ 100s | ✅ many (pro paywalled) | ✅ many | ✅ many | 🎯 100+ via generation |
| 3D-LUT (.cube) import | ✅ GPUImage engine | ✅ | ✅ | ✅ | ✅ | ✅ |
| Filter intensity slider | ⚠️ verify | ✅ | ✅ | ✅ | ✅ | 🎯 ensure |
| Category browsing (Cinematic/Vintage/etc.) | ❌ | ✅ | ✅ | ✅ | ✅ | 🎯 Phase 1 |
| **Color grading** |
| Brightness/contrast/saturation/hue | ✅ AdjustPanel | ✅ | ✅ + curves | ✅ | ✅ | ✅ |
| Curves / HSL / selective | ❌ | ⚠️ | ✅ curves | ✅ | ❌ | 🎯 Phase 4 |
| **Templates** |
| One-tap templates w/ placeholder swap | ⚠️ infra exists, needs content+UX | ✅ flagship | ✅ downloadable | ✅ | ✅ flagship | 🎯 Phase 3 headline |
| Template categories (travel/vlog/etc.) | ❌ | ✅ | ✅ | ✅ | ✅ | 🎯 Phase 3 |
| Beat-synced templates | ❌ | ✅ Beat Sync | ❌ | ⚠️ markers | ✅ tempo | 🎯 Phase 3 |
| **Text & titles** |
| Text overlays + custom fonts | ✅ TextPanel + TextFonts | ✅ trending fonts | ✅ | ✅ | ✅ | ✅ |
| Animated text presets | ✅ AnimatedTextOverlayView | ✅ rich | ✅ | ✅ | ✅ | 🎯 expand presets |
| Auto-captions (speech→text) | ❌ | ✅ 23 languages | ❌ | ❌ | ❌ | 🎯 Phase 4 (on-device) |
| Text templates (title/lower-third packs) | ❌ | ✅ | ✅ | ✅ | ✅ | 🎯 Phase 2 |
| **Stickers** |
| Sticker overlays | ✅ StickerPanel + canvas | ✅ huge library | ✅ asset store | ✅ | ✅ | 🎯 categorized packs |
| Animated stickers | ❌ | ✅ | ✅ | ✅ | ✅ | 🎯 Phase 2 (WebP/APNG) |
| Emoji picker | ❌ | ✅ | ✅ | ✅ | ✅ | 🎯 Phase 2 (Fluent Emoji) |
| **Transitions** |
| Transition picker | ✅ TransitionPickerSheet | ✅ many | ✅ customizable | ✅ advanced | ✅ | 🎯 GLSL pack |
| GPU shader transitions (glitch/3D/etc.) | ❌ | ✅ | ✅ | ✅ | ✅ | 🎯 Phase 1 (gl-transitions) |
| **Effects** |
| Video effects (glitch/VHS/particles) | ✅ FxPanel (verify depth) | ✅ magical effects | ✅ asset store | ✅ | ✅ | 🎯 shader pack |
| Overlays (dust/light leaks/film grain) | ❌ | ✅ | ✅ | ✅ | ✅ | 🎯 Phase 2 |
| Masking | ❌ | ✅ | ✅ video masks | ✅ | ❌ | 🎯 Phase 4 |
| Background remover (AI) | ❌ | ✅ AI | ❌ | ❌ | ❌ | ⏭️ skip (needs ML) |
| **Audio** |
| Multi-track audio / mixer | ✅ AudioMixerPanel | ✅ | ✅ advanced | ✅ | ✅ | ✅ |
| Royalty-free music library | ✅ Jamendo CC (500k+) | ✅ exclusive library | ✅ copyright-free | ⚠️ | ✅ licensed | ✅ keep |
| SFX library | ❌ | ✅ | ✅ | ✅ | ✅ auto-match | 🎯 Phase 2 (Pixabay) |
| Voice recorder / voiceover | ✅ VoiceRecorderPanel | ✅ | ✅ | ✅ | ✅ | ✅ |
| Beat markers on timeline | ❌ | ✅ | ❌ | ✅ markers | ❌ | 🎯 Phase 3 |
| Audio effects (EQ/voice changer) | ❌ | ✅ | ✅ EQ + changer | ❌ | ❌ | 🎯 Phase 4 |
| **PIP / compositing** |
| Picture-in-picture / overlays | ✅ PipOverlayCanvas | ✅ | ✅ blending modes | ✅ | ✅ | ✅ |
| Chroma key (green screen) | ✅ ChromaKeyPanel | ✅ | ✅ adjustable | ✅ | ❌ | ✅ |
| Blending modes | ❌ | ✅ | ✅ multiply/overlay/etc. | ✅ | ❌ | 🎯 Phase 4 |
| **Camera / capture** |
| In-app camera capture | ✅ CameraCapturePanel | ✅ | ✅ | ✅ | ✅ Inspire Cam | ✅ |
| AR face filters | ✅ 13 native cards | ✅ beauty | ❌ | ❌ | ❌ | ✅ differentiator |
| **Export** |
| Resolutions 720p/1080p/4K | ✅ | ✅ 4K+Smart HDR | ✅ 4K | ✅ 4K | ✅ 4K | ✅ |
| Frame rate / bitrate control | ⚠️ verify UI | ✅ | ✅ | ✅ custom | ✅ | 🎯 expose in UI |
| Aspect ratios (16:9/9:16/1:1) | ⚠️ verify | ✅ | ✅ | ✅ | ✅ | 🎯 ensure all 3 |
| Watermark-free export | ✅ | ✅ | ❌ (paywall) | ✅ | ✅ | ✅ differentiator |
| No cloud round-trip / on-device | ✅ | ❌ cloud features | ❌ | ✅ | ❌ | ✅ differentiator |

### Table-stakes vs differentiators

**Table-stakes (must have to be taken seriously):** multi-track trim/split, speed ramp, keyframes, 100+ categorized filters with intensity slider, text presets + fonts, categorized stickers incl. emoji, 30+ transitions, basic effects pack, PIP + chroma key, music + SFX library, voiceover, 720p/1080p/4K export with aspect-ratio choice, watermark-free.

**Differentiators (where ApexStudio can win):**
1. **100% on-device, no watermark, no account** — CapCut/KineMaster push cloud + paywalls; VN is the only close rival here.
2. **13 native AR face cards** — none of the four competitors do this in-editor.
3. **Honest pro color tools** — own parametric LUT engine (no license baggage), curves/HSL later.
4. **Template quality over quantity** — a small set of genuinely good, beat-synced templates beats CapCut's shovelware.
5. **Privacy posture** — no upload, no tracking; matches the TEAM PK AI brand.

---

## 3. Free Resources (permissive licenses only)

> Rule applied: MIT, CC0, Apache-2.0, OFL, ISC only. "Free for commercial use" packs *without* a named redistribution license are listed as **generate-your-own instead** — do NOT bundle them.

### 3.1 LUTs / color looks

**Recommendation: generate, don't download.** Nearly all "free LUT packs" (FreeVisuals, IWLTBAP, etc.) grant *use* rights but are silent on *redistribution inside an app* — bundling them is a legal gray zone. ApexStudio already proved the better path: its 20 shipped LUTs are own parametric transforms (SIZE 17), zero license risk, tiny size.

| # | Resource | License | URL | How to use |
|---|---|---|---|---|
| 1 | **Own parametric LUT generator** (existing engine) | own (no license) | in-repo | Extend the SIZE-17 transform set to 100+ looks; bake to `.cube` at build time or ship as params |
| 2 | `mikheilkuzmidi/lut-generator` — generate industry-standard 33³ `.cube` LUTs from reference images/presets | **MIT** | https://github.com/mikheilkuzmidi/lut-generator | Run offline to mass-produce original `.cube` files; bundle outputs (your files, your rights) |
| 3 | `isaacrowntree/color-grade-ai` — pre-baked correction LUTs in `correction_luts/` | check repo LICENSE | https://github.com/isaacrowntree/color-grade-ai | Utility LUTs (white-balance/skin fixes) — verify license file before bundling |
| 4 | `pierre-thurau/lut-vizualizer` — bundles **CC0 sample LUTs** | **CC0** (samples) | https://github.com/pierre-thurau/lut-vizualizer | The CC0 sample LUTs are public domain — safe to bundle with attribution note |
| 5 | DaVinci Resolve built-in LUTs | — | — | **Do NOT bundle** (Blackmagic license). Listed only as *reference looks* to recreate parametrically |

**Practical plan:** use `lut-generator` (MIT) + the existing parametric engine to produce ~100 original `.cube` files in categories: Cinematic, Vintage, Vibrant, B&W, Warm, Cool, Portrait, Night, Vlog, Drone/Aerial. Ship as downloadable packs (5–10 MB per pack) to keep base APK lean.

### 3.2 Stickers & emoji

| # | Resource | License | URL | Notes |
|---|---|---|---|---|
| 1 | **Microsoft Fluent Emoji** — full emoji set as SVG, 3D / flat / high-contrast styles | **MIT** | https://github.com/microsoft/fluentui-emoji | Best-in-class; the exact "Emoji" picker category in Prince's screenshot. Bundle flat style (~small SVGs), offer 3D as download pack |
| 2 | **Lucide icons** — 1500+ clean vector icons | **ISC** | https://lucide.dev | UI icons + minimal sticker shapes (arrows, stars, badges) |
| 3 | Own shape pack (circles, ribbons, badges, lower-third bars) | own | generate in-house | Simple vector shapes drawn by the team — matches "own polished originals" convention |

Sticker categories to mirror the screenshot UI: **Hot** (trending, curated weekly) · **Emoji** (Fluent) · **Movie Quote** (text-style stickers — own designs) · **DJI-style** (skip brand; use "Travel/Aerial") · **Crayon** (hand-drawn doodles — commission or draw own).

Animated stickers: ship as WebP or APNG (both decode on Android via `ImageDecoder`); keep each under ~300 KB.

### 3.3 Fonts for text overlays

| # | Resource | License | URL |
|---|---|---|---|
| 1 | **Google Fonts** — entire catalogue | **OFL** (mostly) | https://fonts.google.com |
| 2 | Recommended display picks (all OFL): Anton, Bebas Neue, Montserrat ExtraBold, Poppins, Playfair Display, Lobster, Pacifico, Dancing Script, Orbitron, Russo One | OFL | via Google Fonts |

Bundle 8–12 fonts in-app; offer the rest as downloadable packs. OFL requires keeping the license text with the font — include a `fonts/OFL.txt`.

### 3.4 Transitions & effects (GLSL shaders)

| # | Resource | License | URL | Notes |
|---|---|---|---|---|
| 1 | **gl-transitions** — 100+ community GLSL transitions (fade, wipe, glitch, 3D cube, kaleido, etc.), each a single fragment shader with `progress` uniform | **MIT** | https://github.com/gl-transitions/gl-transitions | The canonical library. Port the fragment shaders to OpenGL ES (they are already ES-compatible GLSL); drive `progress` from the transition duration. Curate 30–40 best for mobile GPUs |
| 2 | `ashwinchhimpaxd/glsl-shader-image-on-scroll-transition-effect` — liquid displacement + RGB shift, water ripple | **MIT** | https://github.com/ashwinchhimpaxd/glsl-shader-image-on-scroll-transition-effect | Ready-made "magical" effects matching CapCut's style |
| 3 | Own shader pack (vignette, film grain, light leak, chromatic aberration, shake) | own | write in-house | ~15 small fragment shaders; trivial to author, zero license risk |

**Android notes:** keep shaders to GLSL ES 1.00 (`precision mediump float`), no dynamic loop bounds, prefer 720p preview resolution for complex transitions, benchmark on mid-range devices (Adreno 610 class).

### 3.5 Music & SFX

| # | Resource | License | URL | Notes |
|---|---|---|---|---|
| 1 | **Jamendo** (already integrated — Discover tab) | CC (per track) | https://developer.jamendo.com/ | Keep. 500k+ CC tracks; attribution required per track |
| 2 | **Pixabay Music & SFX** — free, no attribution required | Pixabay Content License | https://pixabay.com/music/ · https://pixabay.com/sound-effects/ | No *documented* public audio API (README already verified this) — provide curated deep-links or a small bundled SFX starter pack downloaded manually |
| 3 | **Freesound.org** | CC0 / CC-BY / CC-BY-NC (filter!) | https://freesound.org | API available; only use CC0 tracks to avoid attribution chains, or CC-BY with in-app credit screen |
| 4 | Built-in synthesized tracks (already exist — Offline tab) | own | in-repo | Keep as zero-dependency fallback |

**SFX starter pack (bundle ~20, all CC0 or own):** whoosh, pop, click, riser, impact, record-scratch, camera shutter, applause, sad trombone, vinyl stop, glitch zap, crowd cheer. Source from Pixabay SFX (manual download, license allows) or synthesize.

### 3.6 What NOT to use

- ❌ "Free LUT packs" with no explicit redistribution license (FreeVisuals, IWLTBAP freebies, etc.) — use is fine, bundling is not clearly allowed.
- ❌ Twemoji (CC-BY 4.0) — attribution in every video is impractical; Fluent Emoji (MIT) is strictly better.
- ❌ OpenMoji (CC-BY-SA 4.0) — share-alike would infect the app.
- ❌ DJI-branded LUT names ("DJI Lito X1") — trademark; recreate the *look*, not the name.
- ❌ Any CapCut/KineMaster asset rips — obvious, but stated for the record.

---

## 4. Technical Approach (Android, high-level)

The coding agent owns the details. The standard, proven stack:

| Concern | Approach | Notes |
|---|---|---|
| Real-time preview filters | **GPUImage for Android** (already in repo) or Media3 `GlEffect` chain | GPUImage is already the 3D-LUT engine; keep it for preview |
| Export-time filters/effects | **Media3 Transformer** with custom `GlEffect` / `VideoFrameProcessor` | Already the export path; add shader-based effects as composable `GlEffect`s |
| Transitions | Port **gl-transitions** GLSL to GLES; render as a `GlEffect` blending two input frames by `progress` | Curate 30–40; preview at reduced res |
| Preview playback | **ExoPlayer** (Media3) | Already in use |
| LUT application | 3D-LUT as `TEXTURE_3D` / 2D strip texture, hardware trilinear | Existing engine; extend with intensity uniform (mix original ↔ graded) |
| Keyframe animation | Existing `KeyframePanel`; add per-property curves (linear/bezier) | Extend presets, not architecture |
| Templates | JSON (schema §5) → inflate into existing `Project`/`MediaClip` domain models via `TimelineTemplateManager` | Infra already exists — this is content + UX work |
| Stickers/emoji | SVG → VectorDrawable (Fluent flat) or rasterized WebP at build time | VectorDrawable keeps APK small |
| Fonts | Downloadable TTF/OTF packs in app-private storage; OFL text bundled | — |
| Music/SFX | Existing Jamendo + bundled CC0 SFX pack | — |
| Performance guardrails | Shader complexity tiers; 720p effect preview; hardware decode/encode delegation (already in `ExportEngine`) | Keep the existing OOM work |

**Do not:** add FFmpeg mobile (huge, LGPL complications), Snap SDK (decision pending separately — keep this program independent of it), or any cloud rendering.

---

## 5. Template System Design (CapCut-style)

### How CapCut templates work (structural summary)

A template is a **serialized project with placeholder slots**. Conceptually: a normal multi-layer timeline (video/image layers, text layers, sticker layers, audio layers, transitions, filters, effects, keyframes) where specific media references are marked **replaceable**. When the user taps "Use template," the app:

1. Loads the template JSON and inflates it into a live project.
2. Shows N **slots** (e.g. "Photo 1", "Clip 2") — each slot has a target duration, aspect handling (cover/fit), and allowed media type.
3. The user picks their own media per slot (or "auto-fill" in order).
4. **Text variables** (`{name}`, `{date}`) are editable inline; styling/animations are locked or optionally unlocked.
5. Everything else (transitions, effects, music, timing, beat markers) is fixed — that is the point: one tap, professional result.
6. Export uses the normal pipeline.

Beat-synced templates additionally store **beat markers** (timestamps) on the audio track; cuts/transitions snap to them.

### Proposed ApexStudio template JSON schema (v1)

```jsonc
{
  "schemaVersion": 1,
  "template": {
    "id": "neon-travel-v1",
    "title": "Neon Travel",
    "category": "travel",            // travel | vlog | fashion | sports | wedding | business | fun
    "coverArt": "templates/neon-travel/cover.webp",
    "aspectRatio": "9:16",           // 16:9 | 9:16 | 1:1
    "durationMs": 15000,
    "author": "TEAM PK AI",
    "tags": ["trending", "night"]
  },

  // Replaceable media slots shown in the "Use template" picker
  "slots": [
    {
      "id": "slot-1",
      "label": "Clip 1",
      "kind": "video_or_image",      // video | image | video_or_image
      "targetDurationMs": 2500,
      "scaleMode": "cover",          // cover | fit
      "placeholder": "templates/neon-travel/ph/slot1.mp4"  // bundled or generated placeholder
    },
    { "id": "slot-2", "label": "Clip 2", "kind": "video_or_image",
      "targetDurationMs": 3000, "scaleMode": "cover",
      "placeholder": "templates/neon-travel/ph/slot2.mp4" }
  ],

  // Editable text variables
  "textVars": [
    { "id": "title", "label": "Title", "default": "MY JOURNEY",
      "maxLength": 24, "layerRef": "text-title" },
    { "id": "subtitle", "label": "Subtitle", "default": "neon nights",
      "maxLength": 40, "layerRef": "text-sub" }
  ],

  // The actual timeline — same model as a saved project
  "timeline": {
    "videoLayers": [
      {
        "id": "v1",
        "clips": [
          {
            "id": "clip-1",
            "slotRef": "slot-1",     // <-- bound to a replaceable slot
            "startMs": 0, "durationMs": 2500,
            "filter": { "lut": "luts/neon-street.cube", "intensity": 0.85 },
            "transform": {
              "scale": 1.0,
              "keyframes": [
                { "tMs": 0, "scale": 1.15, "x": 0.5, "y": 0.5 },
                { "tMs": 2500, "scale": 1.0, "x": 0.5, "y": 0.5 }
              ]
            },
            "transitionOut": { "type": "glitch", "durationMs": 400 }
          }
        ]
      },
      {
        "id": "overlay-fx",
        "clips": [
          { "id": "grain", "kind": "effect", "effect": "film-grain",
            "startMs": 0, "durationMs": 15000, "opacity": 0.35 }
        ]
      }
    ],
    "textLayers": [
      {
        "id": "text-title",
        "textVarRef": "title",       // <-- editable variable
        "startMs": 500, "durationMs": 2500,
        "style": { "font": "Anton", "size": 64, "color": "#FFFFFF",
                   "stroke": { "color": "#000000", "width": 3 } },
        "animation": { "in": "slide-up", "inDurationMs": 400,
                       "out": "fade", "outDurationMs": 300 }
      }
    ],
    "stickerLayers": [
      { "id": "st1", "asset": "stickers/neon/arrow.webp",
        "startMs": 1000, "durationMs": 1500,
        "transform": { "x": 0.8, "y": 0.2, "scale": 0.6, "rotation": 15 } }
    ],
    "audioLayers": [
      { "id": "music", "kind": "music",
        "track": "jamendo:track:123456",   // or "builtin:neon-beat.mp3" / "sfx:riser.mp3"
        "startMs": 0, "durationMs": 15000, "volume": 0.8,
        "beatMarkersMs": [0, 1250, 2500, 5000, 7500, 10000, 12500] }
    ]
  }
}
```

**Design notes for the coding agent:**
- Reuse the existing domain models (`Project`, `MediaClip`) — the template inflater maps `timeline` → project, then swaps `slotRef` clips with user media (trimmed/covered to `targetDurationMs`).
- `textVars` bind to text layers by `layerRef`; the "Use template" screen shows one text field per var.
- Placeholders: ship tiny generated placeholders (solid + label) so templates preview without user media; keeps template packs small.
- Beat markers are informational for auto-cut templates; Phase 3 can add "snap cuts to beats" as a template-authoring tool.
- Template packs = one JSON + assets folder; distributable as a zip; versioned by `schemaVersion`.
- Future: user-created templates ("save project as template") fall out naturally — serialize current project with chosen clips marked as slots.

---

## 6. Recommended Phased Roadmap

### Phase 1 — Filters, LUTs & Transitions (the screenshot's first impression)
1. Generate ~100 original `.cube` LUTs via `lut-generator` + parametric engine, organized in 8–10 categories (Cinematic, Vintage, Vibrant, Portrait, Night, Vlog, Warm, Cool, B&W, Aerial).
2. Filter gallery UI with categories + **intensity slider** (mix uniform).
3. Curate 30–40 gl-transitions (MIT) → GLES; add to `TransitionPickerSheet` with animated previews.
4. Ship as downloadable packs; keep 20 core LUTs + 10 transitions in base APK.
5. Expose export resolution / frame-rate / bitrate / aspect-ratio (16:9, 9:16, 1:1) controls in `ExportScreen`.

### Phase 2 — Text, Stickers & Effects (the creator's toolbox)
1. Bundle Fluent Emoji (MIT, flat style) → categorized emoji picker.
2. Own shape/doodle sticker pack + 8–12 Google Fonts (OFL) + text-style preset gallery (title, lower-third, quote, glitch).
3. Animated stickers via WebP/APNG, ~50 launch assets.
4. Own shader effect pack (~15: grain, light leak, chromatic aberration, shake, VHS, glow) as export-time `GlEffect`s.
5. SFX starter pack (~20 CC0 sounds) in the audio sheet; beat markers on the timeline.

### Phase 3 — Templates (the headline feature)
1. Finalize template JSON schema v1 (§5) and the inflater on top of `TimelineTemplateManager`.
2. Ship 12–20 launch templates across categories (travel, vlog, fashion, sports, wedding, business), 2–3 with beat-synced music.
3. "Use template" flow: slot picker → text vars → preview → export. Template browser UI with covers + categories.
4. "Save project as template" (user-generated templates; shareable zip).
5. Weekly "Hot" template curation (remote JSON index; assets downloaded on demand).

### Phase 4 — Advanced / pro depth
1. Curves + HSL selective color; blending modes; video masks.
2. Reverse clip, freeze frame, audio EQ/voice effects.
3. On-device auto-captions (speech-to-text; no cloud).
4. Stabilization (if feasible on-device), motion tracking for text/stickers.
5. 4K/60 export presets, HDR tone-map note.

### Explicitly out of scope
- AI background removal / generative effects (needs ML models; revisit later).
- Cloud rendering, accounts, or social features.
- Snap Camera Kit decision is separate and independent of this program.

---

## 7. Source List (verification)

- Competitor feature data: web comparison roundups (CapCut vs KineMaster vs VN, 2025–2026) — summarized, not quoted.
- https://github.com/gl-transitions/gl-transitions — MIT (verified via downstream repo license note).
- https://github.com/microsoft/fluentui-emoji — MIT (corroborated by downstream MIT usage).
- https://github.com/mikheilkuzmidi/lut-generator — MIT.
- https://github.com/ashwinchhimpaxd/glsl-shader-image-on-scroll-transition-effect — MIT.
- https://fonts.google.com — OFL.
- https://lucide.dev — ISC.
- https://developer.jamendo.com/ — CC catalogue (already integrated).
- https://pixabay.com/music/ and https://pixabay.com/sound-effects/ — Pixabay Content License (no attribution required).
- ApexStudio current features: verified against repo source tree @ main (2026-10-09).

---

*End of report. Research only — no code was written and the repo was not modified.*
