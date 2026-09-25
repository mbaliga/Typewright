// SPDX-License-Identifier: Apache-2.0

plugins {
    id("typewright.kmp.pure")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":core-geometry"))
                implementation(libs.xmlutil.core)
            }
        }
    }
}
