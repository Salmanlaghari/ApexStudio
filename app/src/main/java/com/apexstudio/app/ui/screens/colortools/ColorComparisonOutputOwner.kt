package com.apexstudio.app.ui.screens.colortools

import java.io.File

/** Owns only this panel's generated comparisons, never project/source files. Main-thread confined. */
internal class ColorComparisonOutputOwner {
    private var before: File? = null
    private var after: File? = null
    private var keepAfter = false

    fun replace(nextBefore: File, nextAfter: File) {
        dispose()
        before = nextBefore
        after = nextAfter
    }

    /** Called before launching share, so a failed chooser can retry the same completed export. */
    fun retainAfterForShare() {
        keepAfter = true
    }

    fun dispose() {
        before?.delete()
        if (!keepAfter) after?.delete()
        before = null
        after = null
        keepAfter = false
    }
}
