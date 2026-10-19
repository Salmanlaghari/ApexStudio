# PR #168: remaining editor fixes

This continues `fix/pr168-editor-reliability`; it does not merge the PR. The owner's reference screenshots show build `633e8504` (merged main), whereas this work started at PR head `707f95f`. Test the new APK's build badge before comparing behavior. Do not uninstall or clear app storage to install it: existing projects and media must remain intact.

## Changes in this continuation

- Export settings replace the editor in the root content slot. Previously, the editor remained composed above a second export screen, retaining its main/PiP preview decoders. Disposing those previews before rendering avoids competing for limited codecs and fixes the duplicate-screen layout. This is a source-level defect fix, not proof of the reported device stall's cause.
- Whole-project main-video export now carries global LUT filters, standard color adjustments and FX into each rendered item. The UI resolves the selected LUT for the same fail-closed preflight used when starting export. Invalid/unavailable effects are not silently dropped. Preparation/render/postmix progress is monotonic; a preparation percentage is not a stall root-cause fix.
- The busy export footer is now a reachable Cancel action; Back cancels an active run without reopening preview decoders. Photo preparation has tracked coroutine ownership so a cancelled preparation cannot later start or cancel a replacement export. Cancellation preserves projects/media and output settings.
- Fit is the default preview mode. Actual PlayerView FIT/ZOOM updates, manual/dynamic crop output aspect, source-dimension reset and main-player unregistration are covered by additional regressions. Phone GPU/decoder behavior still needs testing.
- Adjust content is bounded and scrollable on short windows. Dashboard header/card sizing accommodates narrow windows and larger fonts, and the project list no longer uses blocking DataStore reads on the UI thread.
- Image Editing Session → Edit Photo → Canvas offers Original, 1:1, 4:5, 16:9, 9:16 and 3:2, Fit/Fill and five solid backgrounds. Settings persist per photo using the existing autosave/history path. The shared preview/photo-export renderer applies the canvas after grading so its background is not filtered. Crop mode temporarily displays the original photo coordinate space; it does not destroy canvas settings. Existing projects default to Original.
- Photo decode/render temporary allocations are released when ownership is private. Timeline image thumbnails now include persisted edits in cache identity and render the actual edited still; corrupt cached JPEGs are evicted. This does not establish measured cold-start or scrolling FPS on a phone.
- AudioRecord startup failure/stop cleanup attempts release reliably. Player disposal clears only its own audio-effect registration, not a replacement player's reference. Neither change is represented as a proven native-crash fix.
- Explicit project IDs must resolve exactly. An unavailable ID cannot silently select another project's media.

## Still intentionally unavailable

Whole-project export is not a full multilayer compositor: photos, visible PiP tracks, AR, GPU-only filters, crop, stickers and transitions retain explicit capability checks. The new photo canvas uses the selected-photo export path, not whole-project video composition. The entire editor/layer system has not been replaced with a CapCut clone.

Protected AI remains locked. Project-only scope and absence of free-form generation prompts do not guarantee harmless source media. Validated offline moderation for sexual, violent and hateful text **and** image/video frames is still incomplete; there is no model-validation bypass or cloud fallback. Hash verification of existing model assets is not moderation-accuracy validation.

## Phone acceptance checks

1. Update-install the signed APK and confirm its build badge. If Android reports a signature conflict, stop; do not uninstall or reset data.
2. Reopen the same failing project. Test selected-clip export to completion, playback/audio, cancel during preparation and retry. Then test a main-video-only whole-project export with a LUT, adjustments and FX. Unsupported edits should explain the exact block rather than disappear from output.
3. Check Fit/Cover on landscape and portrait sources, switch clips with different dimensions, test manual crop and fullscreen, and inspect vignette/filter boundaries. Fit should keep the full source visible; Cover intentionally crops to fill the viewport. The preview toggle is not a new persisted video export-fit setting.
4. On three-button navigation, scroll Adjust and mask/overlay controls fully. Test the dashboard with large fonts and portrait/landscape windows.
5. Import a photo, choose Canvas → 1:1 → Fit → White, then Fill. Switch to Crop and back, undo/redo, reopen the project and selected-photo export. Compare canvas/background and image boundaries. Original restores the original output dimensions without deleting other photo edits.
6. Cold-open a long timeline, scroll, change photo edits and verify thumbnails update; test video/PiP/audio/text selection, trim, move, hide/lock and undo. Host tests do not establish target-device performance.
7. If a crash recurs, relaunch → Settings → View Crash Log → Share crash evidence. The OS exit reason and retained native tombstone (when available) are needed; `EditorScreen: composable start` is only a breadcrumb. No automatic upload is performed. If unavailable, capture `adb logcat` Fatal signal/backtrace.

## References

The implementation follows [Media3 single-application-thread and codec-release guidance](https://developer.android.com/media/media3/transformer/getting-started), the [official Transformer demo](https://github.com/androidx/media/tree/release/demos/transformer), and [Compose inset consumption guidance](https://developer.android.com/develop/ui/compose/system/insets-ui). No third-party editor source/assets were copied.
