package dev.aarso.typewright.app.android

import dev.aarso.typewright.core.geometry.CoreGeometryModule
import kotlin.test.Test
import kotlin.test.assertEquals

/** Compiles and runs against core-geometry's JVM variant, the path every pure module takes. */
class PureModuleOnAndroidTest {
    @Test
    fun pureModuleResolvesThroughItsJvmVariant() {
        assertEquals("core-geometry", CoreGeometryModule.NAME)
    }
}
