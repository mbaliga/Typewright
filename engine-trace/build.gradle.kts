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
        // The capture and trace tests draw synthetic photographs (capture-testkit) from the repository's own Hyle Deco outlines.
        // A test-only dependency back onto a module that itself depends on engine-trace creates no cycle: Gradle builds engine-trace's main
        // output first, then the testkit, then engine-trace's tests.
        commonTest {
            dependencies {
                implementation(project(":capture-testkit"))
            }
        }
    }
}
