# Building ApexStudio on GitHub Actions

Your repo already has a full CI pipeline at `.github/workflows/android-build.yml`.
It produces a **debug APK**, a **release APK**, and a **release AAB** — all on GitHub's
runners, which have unrestricted access to `dl.google.com` and `repo1.maven.org`.
That is exactly what the local sandbox cannot do.

---

## 1. ⚠️ The trigger gotcha — read this first

The workflow only fires on **specific branch names**:

```yaml
on:
  push:
    branches: [ main, master, develop, 'session/**', 'perf/**' ]
  pull_request:
    branches: [ main, master, develop ]
  workflow_dispatch:
```

**If you push `feature/my-thing`, nothing runs.** Pick one of these three:

| Option | How |
|---|---|
| **A. Manual run (fastest)** | GitHub → **Actions** → *Android CI Build* → **Run workflow** → pick `main`. No push needed. |
| **B. Branch name that matches** | `git checkout -b session/ai-audio` (or `perf/...`) → push → CI fires automatically. |
| **C. Open a Pull Request** | Any branch → PR into `main` → the `pull_request` trigger fires. |

---

## 2. Get the APK

After the run finishes (first cold run ≈ 20–40 min, cached runs ≈ 8–15 min):

**Actions → your run → Artifacts (bottom of page)**

| Artifact | Contents |
|---|---|
| `apexstudio-debug-<sha>-run-<id>` | **Installable debug APK** + provenance + alignment JSONs + debug AAB |
| `apexstudio-release-apk` | Release APK (signed with a **throwaway CI key**) |
| `apexstudio-release-aab` | Release AAB — **not** signed with your real Play upload key |
| `size-reports` | APK/AAB size breakdown, `bundletool` download-size estimate |
| `phase0-test-results-<id>` | JUnit HTML + XML reports |
| `lint-report` | Lint HTML |

> **Install the debug APK on your phone**, not the release one. The release key is
> generated fresh on every CI run, so a release APK will not install over a previous
> release build (signature conflict).

---

## 3. What CI already validates before the APK is produced

1. `verify-ai-models.py` — 13 pinned model assets, SHA-256
2. `verify-free-ai-policy.py` — *(added in this pass)* permissive licence, pinned
   size+hash, on-demand-only, download budget, pinned source URL
3. Host GLES shader pixel regression (`verify-workspace-shaders.py`,
   `verify-keyframe-shaders.py`) — real OpenGL on the runner, not Android
4. Full JVM suite: `:buildSrc:test :app:testDebugUnitTest :feature:camerakit:testDebugUnitTest`
5. Instrumentation **compile** (`:app:compileDebugAndroidTestKotlin`) — compiled, not executed
6. `zipalign -c -P 16` — 16 KB page alignment check
7. `native-alignment.py` — ELF static check
8. `apk-provenance.py` — SHA + signer recorded for the exact HEAD commit
9. `bundletool get-size total` — Play download-size estimate

**CI does NOT run:** on-device UI tests, visual/export-parity checks, real-phone performance.
Those still need your phone.

---

## 4. Changes made to the workflow in this pass

| Change | Why |
|---|---|
| `gradle-version: '9.3.1'` → `'8.11.1'` (4 places) | The wrapper is **8.11.1** and AGP is **8.7.3**. Gradle 9.x dropped APIs AGP 8.7 needs. `./gradlew` masked the mismatch, but any bare `gradle` invocation would have failed. Now consistent. |
| Added `verify-free-ai-policy.py --report` to the model-integrity step | Enforces the free-AI rules on every push; fails the build if a non-permissive or unpinned model ever lands. |

---

## 5. Apply the audit changes, then let CI verify them

The audit pass produced 5 new/changed files. Drop them into your clone:

```
docs/AUDIT_2026-10-23.md                                          (new)
docs/CI_BUILD_GUIDE.md                                            (new)
scripts/setup-linux-toolchain.sh                                  (new, chmod +x)
scripts/verify-free-ai-policy.py                                  (new)
app/src/main/java/com/apexstudio/app/ai/safety/ImageAiScope.kt    (new)
app/src/main/java/com/apexstudio/app/ai/audio/AudioIntelligence.kt (new)
app/src/main/java/com/apexstudio/app/ai/safety/GlobalModerationEnforcement.kt (modified)
app/src/main/assets/models/models.lock.json                       (modified — 4 URLs pinned)
.github/workflows/android-build.yml                               (modified)
```

```bash
# in your local clone of ApexStudio
git checkout -b session/audit-ai-free
# ...copy the files above to their paths...
git add -A
git commit -m "Audit: image AI scope, audio intelligence, free-AI policy guard, pinned model URLs"
git push -u origin session/audit-ai-free
```

The branch name starts with `session/`, so CI fires on push. ✅

**Important:** `ImageAiScope.kt` and `AudioIntelligence.kt` are **new, source-only** — they
have never been compiled. CI's Kotlin compilation is the first time they will be type-checked.
Expect to iterate on the first run; read the *Build APK (Debug)* job log if it fails.

---

## 6. If the build fails

Read the job log bottom-up. Most likely causes, in order:

1. **Kotlin compile error in the two new files** — never compiled before. Fix + push again.
2. **`verify-free-ai-policy.py` exit 1** — it prints the exact violation lines.
3. **A real test failure** in `:app:testDebugUnitTest`.
4. **SDK/AGP download flake** — just re-run the job.

Re-runs with a warm cache are fast.

---

## 7. Secrets (optional)

Two features stay dormant unless you add repo secrets
(*Settings → Secrets and variables → Actions → New repository secret*):

| Secret | Unlocks |
|---|---|
| `SNAP_CAMERA_KIT_TOKEN` | Snap Camera Kit AR lenses |
| `JAMENDO_CLIENT_ID` | Jamendo Discover music tab |

Both are optional — offline music, import, and everything else work without them.
