plugins {
    id("typewright.kmp.pure")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":core-font"))
                api(project(":qa:corpus"))
            }
        }
    }
}
