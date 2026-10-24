// SPDX-License-Identifier: Apache-2.0

plugins {
    id("typewright.kmp.pure")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":core-font"))
                api(project(":qa:corpus"))
                // The Names check reads the Reserved Font Names a licence declares with the one parser the importer
                // uses (project.imports.reservedFontNames), so the two cannot disagree. :project is pure and Apache-2.0.
                implementation(project(":project"))
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

// The layer-one tests that need no Python run with everything else. The ones that run the real Fontbakery
// (*PythonIntegrationTest) are their own task, so the SDK-free CI job and a machine without Python keep a
// green `jvmTest`; `pythonIntegrationTest` fails with a named reason when TYPEWRIGHT_PYTHON does not point
// at a Python that has Fontbakery's googlefonts profile, and CI's golden-path job runs it with one that does.
val repoRoot = isolated.rootProject.projectDirectory.asFile.absolutePath

tasks.named<Test>("jvmTest") {
    systemProperty("typewright.repoRoot", repoRoot)
    filter { excludeTestsMatching("*PythonIntegrationTest") }
}

// The same compiled tests as jvmTest, taken from the compilation so that running this task does not first
// run all of jvmTest.
val jvmTestCompilation = kotlin.targets.getByName("jvm").compilations.getByName("test")

tasks.register<Test>("pythonIntegrationTest") {
    description = "Runs the real Fontbakery from the Python TYPEWRIGHT_PYTHON names; fails, never skips, without one."
    group = "verification"
    testClassesDirs = jvmTestCompilation.output.classesDirs
    classpath = files(jvmTestCompilation.output.allOutputs, jvmTestCompilation.runtimeDependencyFiles)
    systemProperty("typewright.repoRoot", repoRoot)
    // Real processes on the machine: the result depends on what is installed, so it is never cached or up to date.
    doNotTrackState("runs Fontbakery from the Python on this machine")
    filter {
        includeTestsMatching("*PythonIntegrationTest")
        isFailOnNoMatchingTests = true
    }
}
