package com.apexstudio.app.data.engine

import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks the resolved fork's Java surface, not native codec availability or full API equivalence. */
class FFmpegDependencyContractTest {
    @Test fun resolvedForkProvidesTheExactMethodsUsedByTheEngine() {
        val loader = javaClass.classLoader
        // Do not initialize native loaders in a host JVM compatibility check.
        val kit = Class.forName("com.arthenica.ffmpegkit.FFmpegKit", false, loader)
        val config = Class.forName("com.arthenica.ffmpegkit.FFmpegKitConfig", false, loader)
        val session = Class.forName("com.arthenica.ffmpegkit.FFmpegSession", false, loader)
        val returnCode = Class.forName("com.arthenica.ffmpegkit.ReturnCode", false, loader)

        val execute = kit.getMethod("executeWithArguments", Array<String>::class.java)
        assertTrue(Modifier.isStatic(execute.modifiers))
        assertEquals(session, execute.returnType)
        val version = config.getMethod("getVersion")
        assertTrue(Modifier.isStatic(version.modifiers))
        assertEquals(String::class.java, version.returnType)
        assertEquals(returnCode, session.getMethod("getReturnCode").returnType)
        assertEquals(Int::class.javaPrimitiveType, returnCode.getMethod("getValue").returnType)
    }
}
