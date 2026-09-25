// SPDX-License-Identifier: Apache-2.0

plugins {
    id("typewright.kmp.pure")
}

kotlin {
    sourceSets {
        // P2a: CubicFittingHyleDecoValidationTest reads the real `fonts/HyleDeco-Regular.ttf` off
        // disk through core-font's SfntFont, to honestly report how the corner-detect + Schneider
        // fitter (this module's own code) does against the real T/o/n/H fixtures. This is a
        // jvmTest-only dependency, deliberately not added to commonMain or commonTest: core-font
        // depends on core-geometry (never the reverse, per docs/ARCHITECTURE_REVIEW.md section 3
        // ":core-geometry"/":core-font"), and reading an arbitrary file off disk is JVM-only
        // anyway (Kotlin/Wasm under Node has no reliable relative-path access outside its own
        // module during a Gradle test run -- see core-font's HyleDecoCrossCheckTest KDoc, which
        // embeds the font as base64 for exactly that reason). A test-only, single-target
        // dependency back onto a module's own downstream consumer creates no build cycle: Gradle
        // resolves core-geometry's jvmTest compilation against core-font's jvmMain, which itself
        // depends on core-geometry's jvmMain (already built) -- not on core-geometry's tests.
        jvmTest {
            dependencies {
                implementation(project(":core-font"))
            }
        }
    }
}
