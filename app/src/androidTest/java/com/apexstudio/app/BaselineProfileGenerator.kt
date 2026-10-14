package com.apexstudio.app

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the Baseline Profile for release builds.
 *
 * Run on a connected device (NOT in CI — this VM has no device):
 * ```
 * ./gradlew :app:generateReleaseBaselineProfile
 * ```
 * then copy the generated profile into
 * `app/src/main/baseline-prof.txt` (replacing the empty placeholder)
 * and rebuild release. `profileinstaller` (already wired) ships the
 * profile into the APK/AAB so the Play-installed app starts warm.
 *
 * Journey: cold launch to Home (startup profile) + best-effort open
 * of the editor via the bottom-nav "Edit" tab (or a project card's
 * Edit button — whichever UiAutomator finds first), a few timeline
 * swipes, then back. The editor part is wrapped in try/catch on
 * purpose: if the UI texts ever drift, generation still succeeds
 * with the startup profile instead of failing the whole run.
 *
 * The generated profile is device-specific (it records the classes
 * + methods actually executed). Regenerate after big editor changes
 * (new panels, new timeline code) so the profile stays truthful.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(
            packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName,
            includeInStartupProfile = true
        ) {
            // 1. Startup: cold launch to the Home screen.
            pressHome()
            startActivityAndWait()
            device.waitForIdle()

            // 2. Best-effort editor journey (never fatal).
            try {
                // Bottom-nav "Edit" tab (or a project card's Edit
                // button) opens EditorScreen. First match wins —
                // either lands us in the editor.
                val editEntry = device.wait(Until.findObject(By.text("Edit")), 3_000)
                editEntry?.clickAndWait(Until.newWindow(), 3_000)
                device.waitForIdle()

                // 3. Timeline swipes: horizontal (scroll the timeline)
                // + vertical (scroll tracks). Coordinates are fractions
                // of the display so any phone size works.
                val w = device.displayWidth
                val h = device.displayHeight
                val timelineY = (h * 0.72f).toInt()
                device.swipe((w * 0.8f).toInt(), timelineY, (w * 0.2f).toInt(), timelineY, 20)
                device.waitForIdle()
                device.swipe((w * 0.2f).toInt(), timelineY, (w * 0.8f).toInt(), timelineY, 20)
                device.waitForIdle()
                val tracksY = (h * 0.6f).toInt()
                device.swipe(w / 2, (h * 0.8f).toInt(), w / 2, tracksY, 20)
                device.waitForIdle()

                device.pressBack()
                device.waitForIdle()
            } catch (_: Exception) {
                // Editor journey skipped — the startup profile above
                // still generates. Harden the selectors if this fires.
            }
        }
    }
}
