plugins {
    id("typewright.kmp.pure")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":qa"))
                api(project(":learn:scenes"))
            }
        }
    }
}
