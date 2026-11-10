package com.apexstudio.app.data.export

import androidx.media3.common.util.Size
import com.apexstudio.app.domain.model.ClipTransition
import com.apexstudio.app.domain.model.MediaClip
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectTransitionCompositorSettingsTest {
    private fun clip(id: String, durationMs: Long = 1_000L) = MediaClip(
        id = id,
        name = id,
        uri = "file:///$id.mp4",
        durationMs = durationMs,
        trimEndMs = durationMs
    )

    private fun transitionPlan(): ProjectTransitionTimeline.Plan {
        val a = clip("a")
        val b = clip("b")
        return ProjectTransitionTimeline.build(
            listOf(a, b),
            listOf(ClipTransition(id = "a-b", fromClipId = "a", toClipId = "b", durationMs = 200L))
        )
    }

    @Test
    fun `role is left for normal active clip and hidden for inactive clip`() {
        val plan = ProjectTransitionTimeline.build(listOf(clip("a"), clip("b")))

        assertEquals(ProjectTransitionCompositorRole.LEFT, roleAt(plan, 0, 500L))
        assertEquals(ProjectTransitionCompositorRole.HIDDEN, roleAt(plan, 1, 500L))
    }

    @Test
    fun `transition maps outgoing left and incoming right`() {
        val plan = transitionPlan()
        val transition = plan.transitions.single()

        assertEquals(ProjectTransitionCompositorRole.LEFT, roleAt(plan, 0, transition.startMs))
        assertEquals(ProjectTransitionCompositorRole.RIGHT, roleAt(plan, 1, transition.startMs))
        assertEquals(ProjectTransitionCompositorRole.HIDDEN, roleAt(plan, 0, transition.endMs))
        assertEquals(ProjectTransitionCompositorRole.LEFT, roleAt(plan, 1, transition.endMs))
    }

    @Test
    fun `settings expose a doubled packed frame size`() {
        val plan = transitionPlan()
        val settings = ProjectTransitionCompositorSettings(plan, 1_920, 1_080)

        assertEquals(Size(3_840, 1_080), settings.getOutputSize(listOf(Size(640, 360))))
    }

    @Test
    fun `dimensions clamp without integer overflow`() {
        val settings = ProjectTransitionCompositorSettings(
            transitionPlan(),
            Int.MAX_VALUE,
            -10
        )

        assertEquals(Size(Int.MAX_VALUE - 1, 0), settings.getOutputSize(emptyList()))
    }
}
