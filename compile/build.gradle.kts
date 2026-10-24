// SPDX-License-Identifier: Apache-2.0

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("typewright.kmp.platform")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // No Compose here, so the common tests also run on Wasm under Node.
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
    }
    sourceSets {
        commonMain {
            dependencies {
                api(project(":core-font"))
                // ProjectDirectory is now a typealias of com.asoc.typewright.project.ProjectFiles (docs/PROJECT_MODEL.md §2).
                api(project(":project"))
                // HostedBuildProtocol's request/response wire encoding (docs/HOSTED_BUILD_ENDPOINT.md).
                // Already a build-wide dependency (qa:corpus's node-economy loader; see its own
                // build.gradle.kts comment); same version, so nothing new resolves.
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }
}

// The desktop backend's tests that need no Python run with everything else. The ones that run the
// real fontmake (*PythonIntegrationTest) are their own task, so a machine without Python still has
// a green `desktopTest`; `pythonIntegrationTest` fails with a named reason when TYPEWRIGHT_PYTHON
// does not point at a Python that has fontmake, and CI's golden-path job runs it with one that does.
val repoRoot = isolated.rootProject.projectDirectory.asFile.absolutePath
tasks.named<Test>("desktopTest") {
    systemProperty("typewright.repoRoot", repoRoot)
    filter { excludeTestsMatching("*PythonIntegrationTest") }
}

// The same compiled tests as desktopTest, taken from the compilation so that running this task does not
// first run all of desktopTest.
val desktopTestCompilation = kotlin.targets.getByName("desktop").compilations.getByName("test")

tasks.register<Test>("pythonIntegrationTest") {
    description = "Builds real fonts with the Python TYPEWRIGHT_PYTHON names (fontmake required); fails, never skips, without one."
    group = "verification"
    testClassesDirs = desktopTestCompilation.output.classesDirs
    classpath = files(desktopTestCompilation.output.allOutputs, desktopTestCompilation.runtimeDependencyFiles)
    systemProperty("typewright.repoRoot", repoRoot)
    // Real processes on the machine: the result depends on what is installed, so it is never cached or up to date.
    doNotTrackState("runs fontmake from the Python on this machine")
    filter {
        includeTestsMatching("*PythonIntegrationTest")
        isFailOnNoMatchingTests = true
    }
}
