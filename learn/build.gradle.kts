// SPDX-License-Identifier: FSL-1.1-ALv2

plugins {
    id("typewright.kmp.pure")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":core-font"))
            }
        }
    }
}
