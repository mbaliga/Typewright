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
        jvmMain {
            dependencies {
                // FontspectorCliChecker (LayerOneChecker's desktop actual) parses Fontspector's
                // --json report generically as a kotlinx.serialization JsonElement tree; no
                // @Serializable classes of our own are declared here, so only the runtime library
                // is needed, not the kotlin-serialization compiler plugin. Same version qa:corpus
                // already resolves (THIRD_PARTY.md).
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }
}
