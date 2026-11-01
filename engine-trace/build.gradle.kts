// SPDX-License-Identifier: Apache-2.0

plugins {
    id("typewright.kmp.pure")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":core-geometry"))
                // The printed sheets' geometry and contents: the capture reads a photograph against the same SheetLayout the
                // renderer drew from, so the two cannot disagree about where a mark or a cell is.
                api(project(":scripts"))
            }
        }
        // The capture and trace accuracy tests render the repository's own Hyle Deco outlines into
        // synthetic photographs, so they read `fonts/HyleDeco-Regular.ttf` through core-font. That is
        // JVM-only on purpose (a file read), and a test-only dependency back onto a downstream
        // module creates no cycle, the same arrangement core-geometry's own jvmTest uses.
        jvmTest {
            dependencies {
                implementation(project(":core-font"))
            }
        }
    }
}
