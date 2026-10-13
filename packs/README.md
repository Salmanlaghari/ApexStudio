# ApexStudio On-Demand Packs ("Blake" — everything heavy downloads on demand)

This directory is the **source of truth** for downloadable content packs.
Nothing heavy is bundled in the APK; packs download on demand, CapCut-style.

## How it works

1. `manifest.json` in this directory describes every pack (see schema below).
2. The manifest + pack files are served straight from this repo via
   `raw.githubusercontent.com` (always current with `main`) — no separate
   server, no release ceremony. (If a pinned/immutable catalog is ever wanted,
   attach `packs-manifest.json` + pack files to a `packs-vN` GitHub Release and
   point `PackManifestRepo.MANIFEST_URL` at
   `https://github.com/Salmanlaghari/ApexStudio/releases/download/packs-vN/packs-manifest.json`.)
3. The app fetches the manifest at runtime from `PackManifestRepo.MANIFEST_URL`,
   caches it locally, and the Pack Manager UI (Settings → Downloadable Packs)
   handles download / delete / retry with progress, SHA-256 verification and
   atomic install.

## Manifest schema

```jsonc
{
  "manifestVersion": 1,          // bump when the schema changes
  "updatedAt": "2026-10-13",     // informational
  "packs": [
    {
      "id": "neon-filters",      // stable, unique; also the install dir name
      "name": "Neon Nights Filter Pack",
      "description": "...",
      "category": "filters | transitions | effects | models | camera",
      "version": 1,              // integer; higher = update available
      "versionName": "1.0",      // display string
      "sizeBytes": 1796,         // exact byte size of the download
      "url": "https://.../releases/download/packs-v1/neon-filters.json",
      "sha256": "…",             // hex SHA-256 of the download; "" when n/a
      "minAppVersion": "1.0.0",  // hide pack on older app versions
      "entry": "filters.json",   // content descriptor inside the installed pack
      "status": "available | coming_soon"
      // coming_soon packs render disabled ("Coming soon") — never a dead button
    }
  ]
}
```

Pack payloads: a single file (`.json`) is used as-is; a `.zip` is extracted
after verification. The `entry` file is what the app reads after install
(e.g. `filters.json` in the existing `PackFilter` schema).

## Publishing / updating packs

1. Add or edit pack content under `packs/<pack-id>/`.
2. Update `packs/manifest.json` (bump `version`, refresh `sizeBytes` + `sha256`:
   `sha256sum packs/<pack-id>/<file>`).
3. Commit & push to `main` — the app picks up the new manifest automatically
   (raw.githubusercontent caches ~5 minutes).
4. The app detects `version` increases and offers "Update" in the Packs UI.

## Design notes

- Storage: app-specific external files dir (`…/Android/data/com.apexstudio.app/files/packs/<id>/`);
  no storage permission needed, removed on uninstall.
- Downloads: `HttpURLConnection` with `Range` resume, 3 retries w/ backoff,
  SHA-256 check, atomic `.part` → rename install. Zero new dependencies.
- Feature gating: `PackManager.requirePack(context, id)` — features call it and
  show the download prompt when the pack is missing (see `DownloadedPacks` for
  the filter-pack integration example).
- Snap Camera Kit: the SDK itself is a Gradle dependency (native code cannot be
  safely downloaded/executed at runtime on Android). APK weight is controlled
  via `arm64-v8a`-only ABI filters + R8; lenses already stream on demand from
  Snap's servers via the lens group ID.
