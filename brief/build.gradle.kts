// SPDX-License-Identifier: FSL-1.1-ALv2

plugins {
    id("typewright.kmp.pure")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                // The style atlas and the detector's measurements: every range the Brief holds a
                // drawing to comes from there (CLAUDE.md law 5).
                api(project(":qa:corpus"))
                // The Ethos record the Brief reads and writes lives in the project's brief.
                api(project(":project"))
            }
        }
    }
}
