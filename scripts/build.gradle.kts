// SPDX-License-Identifier: Apache-2.0

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

// The sheet-writer tests that need no Python run with everything else. The one that opens the exported PDF with pypdf and
// pdfinfo (*PythonIntegrationTest) is its own task, so the SDK-free CI job and a machine without Python keep a green
// `jvmTest`; `pythonIntegrationTest` fails with the pip command when TYPEWRIGHT_PYTHON does not point at a Python with pypdf.
tasks.named<Test>("jvmTest") {
    filter { excludeTestsMatching("*PythonIntegrationTest") }
}

val jvmTestCompilation = kotlin.targets.getByName("jvm").compilations.getByName("test")

tasks.register<Test>("pythonIntegrationTest") {
    description = "Opens the exported template PDF with pypdf from the Python TYPEWRIGHT_PYTHON names; fails, never skips, without one."
    group = "verification"
    testClassesDirs = jvmTestCompilation.output.classesDirs
    classpath = files(jvmTestCompilation.output.allOutputs, jvmTestCompilation.runtimeDependencyFiles)
    // Real processes on the machine: the result depends on what is installed, so it is never cached or up to date.
    doNotTrackState("runs pypdf from the Python on this machine")
    filter {
        includeTestsMatching("*PythonIntegrationTest")
        isFailOnNoMatchingTests = true
    }
}
