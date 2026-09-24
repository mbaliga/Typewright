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
