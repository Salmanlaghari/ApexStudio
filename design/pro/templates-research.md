# ApexStudio — Real Template Sources: Broadened Research

**Date:** 2026-10-09
**Brief:** Prince asked to broaden the search beyond CapCut/Jianying: VN templates, Kinemaster assets, Alight Motion presets, Node Video presets, YouTube creator project files, marketplace free sections (Motion Array, Mixkit, Pixflow), Telegram/Discord communities, Chinese platforms (Bilibili, Xiaohongshu), open-source NLE presets (Kdenlive/Shotcut/Olive/Pitivi), FFmpeg filter-graph recipes.
**Rule applied:** "Publicly visible/downloadable" ≠ "free to reuse." Only MIT/CC0/Apache-2.0/OFL/ISC (or equivalent verified permissive) sources are marked **USABLE**. Everything else is **REFERENCE ONLY** with the reason stated.

---

## 1. USABLE — genuinely free, verified licenses

### A. Template JSON / template engines (MIT)

| # | Source | License | What it gives ApexStudio |
|---|--------|---------|--------------------------|
| 1 | https://github.com/vikram-bosak/video-edit-engine | **MIT** (verified in README) | 6 CapCut-style JSON templates in `assets/templates/`: `tiktok.json` (neon text, glitch transitions), `youtube_short.json` (subscribe CTA, end screen), `facebook_reel.json` (word-highlight subtitles), `instagram_reel.json` (warm vintage, progress bar), `cinematic.json` (film burn, teal-orange grade), `fast_paced.json` (beat-synced, camera shake, fast cuts). Plus 15+ transition definitions, 14 animated text styles, timeline compositing engine. **Best direct template-JSON reference found.** |
| 2 | https://github.com/comaniacs/miraiclip | **MIT** (verified) | `@miraiclip/templates`: template = ordinary project document + declared fields; `{{field}}` placeholders bind into text/captions/params; `asset`-type fields swap media slots; hydration is pure/deterministic; `.miraiclip-template.json` = `{version, name, doc, fields}`. **Best placeholder/slot architecture reference.** 79 effects included. |
| 3 | https://github.com/alebrito124356/remotion-video-templates | **MIT** (badge verified) | 6 programmatic templates: logo intro, lower thirds, kinetic typography, data-driven charts, TikTok-style captions, product promo — each driven by typed props (same idea as `textVars`). Good structural reference for lower-thirds/captions templates. |
| 4 | https://github.com/miasfck/video-engine | **MIT** (verified in README) | `project.yaml`-driven scene system; extensible `BaseScene` pattern — useful model for a template "scene library". |
| 5 | https://github.com/feliperun/slideshow | **MIT** (badge verified) | Beat-grid slideshow generator: 6 flicker-safe transition families (wipe, slide, geometric mask, shared motion, photo stack, zoom-through), beat-aligned cuts, collages, chapters. **Best reference for script-generated beat-sync templates.** |
| 6 | https://github.com/haivuuu/short_video_generator | **MIT** (LICENSE file) | 9:16 short-video pipeline (Gemini script → images → Ken Burns → captions → ffmpeg assemble). Useful as a recipe for auto-generated template variants. |
| 7 | https://github.com/MendesCorporation/ai-promo-video | **MIT** | Revideo-based promo video skill; neutral composition approach. Secondary reference. |
| 8 | https://github.com/jp-bennett/better-animated-lower-thirds | **MIT** (verified) | HTML/CSS animated lower thirds (OBS tool): 3 predefined styles, 10 slots, customizable colors/fonts/timing. Lower-third *designs* are adaptable as template text-style presets. |

### B. Lottie animations — animated stickers/overlays (Lottie Simple License)

The **Lottie Simple License** (used by LottieFiles free animations): free for personal **and commercial use**, no attribution required. Suitable for bundling as animated stickers/overlays rendered into exported video. Use **dotLottie** format (few KB each). Android playback via lottie-compose (already a natural fit).

**Verified individual animations (all "Free to use under the Lottie Simple License"):**

| # | Animation | Author | URL |
|---|-----------|--------|-----|
| 1 | Subscribe Button Animation | AKSHAY RAJA REDDY | https://lottiefiles.com/free-animation/subscribe-button-Mi98kAFDyg |
| 2 | Subscribe button Animation | Joon36 | http://lottiefiles.com/free-animation/subscribe-button-5OtjnYwPOO |
| 3 | Subscribe Button Animation | RAHUL KUMAR | http://lottiefiles.com/free-animation/subscribe-button-AaKK5Et7fi |
| 4 | Download Animations | Ronak Laungani | http://lottiefiles.com/free-animation/download-animations-K2sipy3al2 |
| 5 | High Tech Animation | Priyanshu | https://lottiefiles.com/animations/high-tech-bFuUeyno4L |
| 6 | Download a folder | LottieFiles | https://lottiefiles.com/animations/download-a-folder-9Pz3Ge0S4w |

**Browse-and-pick categories (all free, Lottie Simple License):**

| # | Category | URL | Notes |
|---|----------|-----|-------|
| 7 | Emoji (847 animations) | https://lottiefiles.com/free-animations/emoji | reactions, hearts, fire, gestures — sticker pack |
| 8 | Loading | http://lottiefiles.com/free-animations/loading?page=125 | spinners, progress |
| 9 | Sparkle | https://lottiefiles.com/free-animations/sparkle?page=2 | glitter/twinkle overlays |
| 10 | YouTube Subscribe | https://lottiefiles.com/free-animations/youtube-subscribe | CTA overlays |
| 11 | New Year | https://lottiefiles.com/free-animations/new-year | seasonal |
| 12 | Easter | https://lottiefiles.com/free-animations/easter | seasonal |
| 13 | Xmas | https://lottiefiles.com/free-animations/xmas | seasonal |

**Suggested additional picks for a video-editor sticker/overlay pack** (browse the category pages above and select; all carry the same license): like button, notification bell, confetti burst, thumbs-up, heart burst, "NEW" badge, arrow pointers, countdown timer, social icons (YouTube/Instagram/TikTok), checkmark, sale tag, music note, film strip, camera shutter. **Target: 30 curated overlays.**

**Lottie tooling (MIT):**
- https://github.com/enegaltech/lottie-marketplace — MIT metadata/skill; catalog of 197 entries scraped from MIT/Apache repos + curation (entries inherit upstream: `ua-*` = CC-BY 4.0, `lf-*` = Lottie Simple). Useful as a *discovery index*.
- https://github.com/diffusionstudio/lottie — **MIT**; text-to-Lottie framework (generate original animations with an agent). **Best path to fully-original sticker animations.**
- https://github.com/karthikeyanranasthala/lot — MIT; pipe Lottie frames to ffmpeg (useful for baking Lottie overlays into export).

### C. Transitions / effects / LUTs (MIT, from prior research — reconfirmed usable)
- https://github.com/gl-transitions/gl-transitions — MIT, 100+ GLSL transitions.
- https://github.com/mikheilkuzmidi/lut-generator — MIT, generate original `.cube` LUTs.
- https://github.com/microsoft/fluentui-emoji — MIT (static emoji fallback).
- Google Fonts — OFL (text template fonts).

---

## 2. REFERENCE ONLY — do NOT bundle into ApexStudio

| Source | Why reference-only |
|--------|-------------------|
| **VN community templates** (.vnt files, QR codes via TikTok/YouTube/Google Drive; e.g. https://vnvideoseditor.com/vn-templates/) | User-generated, **no license stated**. VN's own in-app templates are licensed for use *inside VN*. |
| **KineMaster Asset Store** (2,500+ transitions/effects/stickers/fonts) | **Proprietary ToS** (resource.kinemaster.com/document/tos.html): assets licensed for use *within KineMaster only*; premium assets paywalled. |
| **Alight Motion XML presets** (YouTube descriptions → Google Drive; Telegram channels like t.me/ash1zxml, t.me/editor0790) | **No license**; creators routinely write "don't reupload without permission." The *XML keyframe-curve format* is fine to study structurally, but the presets themselves are not reusable. |
| **Node Video presets** (Telegram: t.me/vaulttube, t.me/fxlyarmy, t.me/ZmexEditZ) | **No license**; several of these channels also distribute **mod/pirated APKs** — avoid entirely. |
| **Mixkit** free AE templates (https://mixkit.co/free-after-effects-templates/) | **Mixkit License** (custom): free to use *in your video projects*, but **no redistribution** as standalone assets/templates. Design inspiration only. |
| **Motion Array** free plan | Royalty-free **per end-use** under subscription; **no redistribution** into another app; rights lapse for new projects after cancellation. |
| **Pixflow** freebies (Action FX Builder free plugin, free lower-thirds, TypoKing starter pack) | **Pixflow's own license**: free for end-use in projects; **not** for redistribution/bundling. The *categories* (cartoon FX, kinetic typography) are good product references. |
| **Bilibili creator project files** (AMV/edit project files; some free via YouTube playlists, some paid via Payhip) | Creators state **"Repost is prohibited without the creator's permission."** |
| **Xiaohongshu creators** | Same as Bilibili: personal sharing, no reuse license. |
| **Kdenlive / Shotcut / Olive / Pitivi** | **GPL-3.0 copyleft**. Effect *preset formats* (MLT XML, Kdenlive effect stacks) are excellent **structural references** for designing our own JSON schema, but GPL content cannot be bundled into a proprietary Play app. |
| **Shotcut luma transition PNGs** (https://github.com/Jonray1/Luma-files-for-video-transitions-in-Shotcut-and-other-video-editors) | Community-made, **no license file**. Idea (grayscale wipe maps) is public-domain *technique*; the files themselves aren't cleared. |
| **YouTube tutorial creator packs** (XML/preset Google Drive links) | Almost never carry a license; "free download" ≠ redistribution rights. Treat as reference. |
| **Jianying/CapCut China templates, ripped CapCut templates** | **Copyrighted** — bundling risks Play suspension (cf. PK-AI precedent). Never. |

**Bottom line for Prince:** the Telegram/YouTube/Discord/Bilibili "free template" world is a *usage* commons, not a *redistribution* commons. Nothing there can legally ship inside ApexStudio.

---

## 3. Honest assessment: truly-free vs must-create

**Truly-free, production-ready CapCut-style templates found: ~12**
- 6 from `vikram-bosak/video-edit-engine` (tiktok, youtube_short, facebook_reel, instagram_reel, cinematic, fast_paced)
- 6 from `alebrito124356/remotion-video-templates` (logo intro, lower thirds, kinetic type, charts, captions, product promo)
- Plus the **miraiclip placeholder-hydration architecture** (MIT) as the slot/textVar mechanism.

**Caveats (say plainly):**
1. None are mobile-editor-ready — all are Python/Remotion/web pipelines. Each needs **adaptation** into ApexStudio's template JSON schema (slots + textVars + timeline) and the existing `TimelineTemplateManager`.
2. None come with cover art, preview videos, or category metadata — those must be produced (render previews on-device/CI).
3. Music is not included — pair with Pixabay/Jamendo tracks (already integrated/licensed).
4. Quality bar: these are solid *starting* templates, not CapCut-trending-tier. The differentiator still has to be **original** templates.

**Must create originally: ~12–20 launch templates** (per the Phase 3 roadmap: travel, vlog, fashion, sports, wedding, business; 2–3 beat-synced). This is the real work — and the moat.

---

## 4. Recommendation: original template pack + mass-production approaches

1. **Adapt, don't adopt:** Port the 6 `video-edit-engine` templates + 6 `remotion-video-templates` into ApexStudio's schema v1 (`~/workspace/apexstudio-pro/research-report.md` §5) as the *seed* pack (12 templates). Use miraiclip's `{{field}}` placeholder discipline for slots/textVars.
2. **Script-generate beat-sync templates** (fastest volume lever): parametric generator — N media slots + beat grid from audio analysis (see `feliperun/slideshow` MIT approach) + auto-assigned transitions from the gl-transitions GLSL pack + parametric text animations (14 styles in `video-edit-engine` as reference). One script → dozens of template variants (travel-beat, vlog-beat, sports-beat…).
3. **Generate original sticker animations** with `diffusionstudio/lottie` (MIT) text-to-Lottie: subscribe buttons, like bursts, arrows, badges — fully owned, zero license risk. Curate 30 LottieFiles Simple-License overlays as the interim pack.
4. **Cover art & previews:** render 3-second previews per template via CI/on-device; covers as WebP.
5. **"Save project as template"** (roadmap Phase 3.4): turns every user into a template author — the long-term content flywheel no competitor can copy from us.
6. **Weekly "Hot" curation** via remote JSON index (assets downloaded on demand) — keeps the app small, templates fresh.

**What NOT to do:** rip VN `.vnt` files, KineMaster assets, Alight Motion XMLs, Telegram preset packs, Mixkit/Motion Array/Pixflow items, or Bilibili/Xiaohongshu creator files into the app. Any of these risks a Play policy strike.

---

## 5. Quick-start asset list for the coding agent

| Asset | Source | License | Use |
|-------|--------|---------|-----|
| 6 CapCut-style JSON templates | vikram-bosak/video-edit-engine `assets/templates/` | MIT | Seed pack (adapt to schema v1) |
| Placeholder/slot architecture | comaniacs/miraiclip `@miraiclip/templates` | MIT | slots + textVars binding design |
| 6 motion-graphic templates | alebrito124356/remotion-video-templates | MIT | Lower-thirds/captions/intro structures |
| Beat-sync slideshow logic | feliperun/slideshow | MIT | Parametric beat-template generator |
| 14 text animation styles | video-edit-engine `text_animation` module | MIT | Parametric text presets |
| 100+ GLSL transitions | gl-transitions | MIT | Template transitions |
| 30 Lottie overlays | LottieFiles free (list §1B) | Lottie Simple | Animated stickers |
| Text-to-Lottie generator | diffusionstudio/lottie | MIT | Original sticker animations |
| HTML lower-thirds | jp-bennett/better-animated-lower-thirds | MIT | Title/lower-third designs |
| LUT generation | mikheilkuzmidi/lut-generator | MIT | Template color grades |

*End of report. Research only — nothing downloaded, no code written, no copyrighted material reproduced.*
