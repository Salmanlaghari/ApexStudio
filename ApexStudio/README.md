# ApexStudio

High-end & Powerful Android Video Editor

## LUT Filter Pipeline — OpenGL ES + Media3 Integration

### Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                   FilterPanel (Compose UI)                  │
│  7 category tabs × 10 filters = 70 built-in LUT presets    │
└────────────────────────┬────────────────────────────────────┘
                         │ FilterPreset.id
┌────────────────────────▼────────────────────────────────────┐
│              LutFilterEngine + CubeLutParser                │
│  Loads filter_manifest.json → FilterPreset objects          │
│  Parses .cube files → float[size³ × 3] 3D LUT data         │
└────────────────────────┬────────────────────────────────────┘
                         │ float[] + FilterPreset
┌────────────────────────▼────────────────────────────────────┐
│              LutFilterGlEffect (Media3 GlEffect)            │
│  Packs 3D LUT → 2D strip texture (size² × size)            │
│  Uploads to GL_TEXTURE1 via GLES20.texImage2D               │
│  Inline GLSL ES 2.0 fragment shader:                        │
│    • Trilinear LUT lookup (floor/ceil blue slices)          │
│    • Bilinear interpolation within each slice               │
│    • uIntensity uniform for 0..100% opacity blend           │
└───────────┬────────────────────────────┬────────────────────┘
            │                            │
┌───────────▼──────────────┐ ┌───────────▼────────────────────┐
│   ExoPlayer Preview      │ │  Media3 Transformer Export     │
│   player.setVideoEffects │ │  EditedMediaItem.setEffects    │
│   ([LutFilterGlEffect])  │ │  ([LutFilterGlEffect])         │
└──────────────────────────┘ └────────────────────────────────┘
```

### Key Files

| File | Purpose |
|------|---------|
| `data/filter/LutFilterGlEffect.kt` | Media3 `GlEffect` implementation — GLSL shaders, GL texture upload, intensity blending |
| `data/filter/LutFilterEngine.kt` | Loads `.cube` files via `CubeLutParser`, reads `filter_manifest.json` |
| `data/filter/LutFilterBuilder.kt` | GPUImage-based alternative path (for bitmap processing) |
| `data/filter/FilterManifest.kt` | `FilterPreset`, `FilterCategory`, `FilterManifest` data models |
| `data/export/ExportEngine.kt` | Wires crop + LUT + FX + keyframes + text overlays into the Transformer export pipeline |
| `ui/screens/editor/FilterPanel.kt` | Compose UI — category tabs, filter chips, intensity slider |
| `assets/shaders/lut_shader.glsl` | Standalone reference GLSL ES 3.0 fragment shader (512×512 PNG LUT) |
| `assets/shaders/vertex_shader.glsl` | Standalone reference passthrough vertex shader |
| `assets/luts/filter_manifest.json` | 70 filters across 7 categories |
| `assets/luts/*.cube` | 70 industry-standard .cube 3D LUT files |

### Shader Details

The inline GLSL ES 2.0 fragment shader in `LutFilterGlEffect.kt`:

```glsl
precision mediump float;
varying vec2 vTextureCoord;
uniform sampler2D uTexSampler;   // source video frame
uniform sampler2D uLutSampler;   // 2D-strip LUT texture
uniform float uLutSize;          // LUT resolution (e.g. 33)
uniform float uIntensity;        // 0.0 … 1.0 blend factor

void main() {
    vec4 color = texture2D(uTexSampler, vTextureCoord);
    float bIdx = color.b * (uLutSize - 1.0);
    float bLow = floor(bIdx);
    float bHigh = min(bLow + 1.0, uLutSize - 1.0);
    float bT = bIdx - bLow;
    float gF = color.g * (uLutSize - 1.0);
    float rF = color.r * (uLutSize - 1.0);
    float xLow = (bLow + gF) / uLutSize;
    float xHigh = (bHigh + gF) / uLutSize;
    float yCoord = rF / uLutSize;
    vec3 lo = texture2D(uLutSampler, vec2(xLow, yCoord)).rgb;
    vec3 hi = texture2D(uLutSampler, vec2(xHigh, yCoord)).rgb;
    vec3 graded = mix(lo, hi, bT);
    vec3 outRgb = mix(color.rgb, graded, uIntensity);
    gl_FragColor = vec4(outRgb, color.a);
}
```

### 3D LUT → 2D Strip Packing

The `.cube` file contains a 3D lookup table (e.g. 33³ or 17³ entries). For GPU compatibility, it's packed into a 2D strip texture:

- **Width** = `size × size` (blue × green axes)
- **Height** = `size` (red axis)
- For blue slice `b`, the (g, r) plane lives at row `r`, columns `[b×size .. b×size+size)`

This is the same technique used by GPUImage's `GPUImageLookupFilter` and CapCut/Instagram filters.

### Filter Categories (70 presets)

1. **Cinematic** — Teal & Orange, Hollywood, Blockbuster, Matrix Green, Film Noir, etc.
2. **Retro & Film** — Kodak 35mm, Fuji Chrome, Vintage Sepia, 80s Grain, Super 8, VHS
3. **Cyberpunk & Neon** — Neon Purple, Cyan Glow, Synthwave, Laser Grid, Ultraviolet
4. **Portrait & Beauty** — Soft Skin Glow, Natural Warmth, Pastel, Studio Glow, Rose Gold
5. **B&W & Monochromatic** — Noir Classic, Silver Oxide, Ink Wash, Graphite, Classic Mono
6. **Urban & Moody** — Cold City, Street Blue, Muted Tones, Industrial, Rainy Window
7. **Food & Landscape** — Vibrant Punch, Forest Green, Sunset Gold, Ocean Blue, Golden Hour

## FX Pipeline — Real-Time Effects

Companion to the LUT filters, the FX tool runs spatial/temporal looks
(Vignette, Film Grain, VHS, Glitch, Pixelate, Chromatic Aberration,
Scanlines, Soft Blur) through a single GLSL ES 2.0 fragment-shader
pipeline (`FxGlEffect`). Every preset consumes `uIntensity` (the FX
slider) plus `uTime`/`uTexel` uniforms so grain flickers, glitch
slices tear, and VHS tracking bars roll with the frame's presentation
time — live in the ExoPlayer preview and baked into the export.

| File | Purpose |
|------|---------|
| `data/fx/FxPreset.kt` | The 8 FX presets (id + label) |
| `data/fx/FxGlEffect.kt` | Media3 `GlEffect` — per-preset GLSL fragment shaders, intensity/time/texel uniforms |
| `ui/screens/editor/FxPanel.kt` | Bottom-sheet FX picker — preset tiles + intensity slider |

## Text Overlays / Captions

Captions and titles are stored per-clip as `TextOverlay` models with
normalised (0..1) coordinates so the on-screen preview and the baked
MP4 line up 1:1:

- `TextSpriteRenderer` rasterises the caption set (auto-fit font,
  colour, optional pill background) onto a transparent sprite.
- The editor preview composites that sprite over the video content
  rect and lets the user drag captions while the Text panel is open.
- `TextOverlayGlEffect` uploads the same sprite as a second GL
texture and alpha-composites it per export frame.
- `ui/screens/editor/TextPanel.kt` — add / select / edit text, colour,
  pill and size for every caption on the clip.

## Export Integration

The `ExportEngine` creates `LutFilterGlEffect` instances and attaches them to `EditedMediaItem.Effects`:

```kotlin
val videoEffects = mutableListOf<androidx.media3.common.Effect>()
if (config.filterPreset != null && config.filterIntensity > 0f) {
    videoEffects.add(LutFilterGlEffect(context, config.filterPreset, config.filterIntensity))
}
val editedMediaItem = EditedMediaItem.Builder(inputMediaItem)
    .setEffects(Effects(emptyList(), videoEffects))
    .build()
transformer.start(editedMediaItem, outputFile.absolutePath)
```

### Adding New Filters

1. Generate a `.cube` LUT file (DaVinci Resolve, Photoshop, or online tools)
2. Place it in `assets/luts/`
3. Add an entry to `filter_manifest.json` with `id`, `name`, `category`, and `asset` path
4. The filter automatically appears in the FilterPanel UI

## Project Structure

```
ApexStudio/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── .github/workflows/build.yml        ← CI (assembleDebug + lint)
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── assets/
        │   ├── shaders/
        │   │   ├── lut_shader.glsl     ← Standalone reference shader
        │   │   └── vertex_shader.glsl
        │   └── luts/
        │       ├── filter_manifest.json  ← 70 filters × 7 categories
        │       └── *.cube              ← 70 industry-standard .cube files
        ├── java/com/apexstudio/app/
        │   ├── data/filter/
        │   │   ├── LutFilterGlEffect.kt ← Media3 GlEffect + GLSL shaders
        │   │   ├── LutFilterEngine.kt   ← .cube loader + manifest parser
        │   │   ├── LutFilterBuilder.kt  ← GPUImage alternative path
        │   │   └── FilterManifest.kt    ← Data models
        │   ├── data/fx/
        │   │   ├── FxPreset.kt          ← FX preset catalog
        │   │   └── FxGlEffect.kt        ← Media3 GlEffect (VHS/Glitch/Grain/…)
        │   ├── data/text/
        │   │   └── TextSpriteRenderer.kt ← caption → transparent sprite
        │   ├── data/effect/
        │   │   ├── VideoCropGlEffect.kt  ← Media3 GlEffect crop window
        │   │   └── TextOverlayGlEffect.kt ← caption sprite compositor
        │   ├── data/export/
        │   │   └── ExportEngine.kt      ← Transformer export with LUT+FX+captions
        │   └── ui/screens/editor/
        │       ├── FilterPanel.kt       ← Compose filter picker UI
        │       ├── FxPanel.kt           ← Compose FX picker UI
        │       └── TextPanel.kt         ← Compose caption editor UI
        └── res/
```
