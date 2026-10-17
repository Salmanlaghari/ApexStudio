# Sticker & Animation attribution

This file records the origin and licence of everything under
`app/src/main/assets/stickers/`. The pre-existing Twemoji PNG sticker
library keeps its own record in [`ATTRIBUTION.txt`](ATTRIBUTION.txt);
please do not merge the two.

---

## Animated pack — `stickers/animated/` (new)

| | |
|---|---|
| **Pack** | ApexStudio Animated (original) |
| **Quantity** | 22 animations across 6 categories: hearts (4), stars (4), fire (4), confetti (4), emojis (3), animals (3) |
| **Products** | Original Lottie JSON authored for this repository |
| **Licence** | [Lottie Simple License](https://lottiefiles.com/lottie-simple-license) |
| **Source** | `scripts/gen.sh` in this repository (regenerate with `bash scripts/gen.sh`) |
| **Manifest** | `animated/manifest.json` — one entry per animation: `id`, `file`, `name`, `category`, `tags` |

### Provenance — read this before "fixing" the attribution

These animations are **original work written for ApexStudio**, not
downloaded from a third-party marketplace. The shape geometry is built
from Lottie primitive shape objects (`el` / `rc` / `sr` / `gr`) and the
keyframes follow the same `{"t": n, "s": [...]}` style already used by
the seven original animations in `app/src/main/assets/lottie/`
(`heart_pop.json` and `rotating_star.json` serve as the structural
reference).

No CapCut, TikTok, or LottieFiles artwork was copied into this pack, and
none may be added without a matching entry here recording the real
source URL and licence. Analyses of a free pack are only licence-clean
if the licence is actually attributable — hence this section states
first-hand authorship rather than citing a marketplace.

### Licence text reference

Lottie Simple License — <https://lottiefiles.com/lottie-simple-license>
(details at <https://lottiefiles.com/license>).

---

## See also

- `stickers/manifest.json` — Twemoji static stickers (CC-BY 4.0), attribution in `ATTRIBUTION.txt`
- `stickers/packs.json` — six SVG packs (110 icons)
- `app/src/main/assets/lottie/` — seven original ApexStudio animations
