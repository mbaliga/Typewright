// SPDX-License-Identifier: FSL-1.1-ALv2

import com.asoc.typewright.buildlogic.GoldenPathRatchetTask
import com.asoc.typewright.buildlogic.GoldenPathReportTask
import org.gradle.api.tasks.testing.Test

plugins {
    id("typewright.jvm")
}

dependencies {
    // Every module GoldenPathTest drives directly, KMP modules resolved through their JVM/desktop
    // variant exactly as app-desktop's own plain kotlin-jvm plugin resolves :ui (README.md
    // "Modules"). Test-only: this module has no production code of its own to ship.
    testImplementation(project(":core-geometry"))
    testImplementation(project(":core-font"))
    testImplementation(project(":engine-trace"))
    testImplementation(project(":engine-construct"))
    testImplementation(project(":qa"))
    testImplementation(project(":qa:corpus"))
    testImplementation(project(":compile"))
    testImplementation(project(":project"))
    // CompileBackend.compile is suspend (compile/src/commonMain/.../CompileBackend.kt); step 5
    // calls it from a plain JUnit test with kotlinx.coroutines.runBlocking.
    testImplementation(libs.kotlinx.coroutines.core)
}

val goldenPathTest =
    tasks.named<Test>("test") {
        // The golden path is an acceptance harness, not a gate: a step that fails because it
        // genuinely is not built yet must never redden `test` (and therefore `check`) for every
        // other module's PR. `goldenPathRatchet`, not this task, is what enforces "may not
        // regress" (CLAUDE.md's standing rule for P10-P19).
        ignoreFailures = true
        // Real filesystem state (a python3/fontmake/fontspector lookup on PATH -- qa's
        // FontspectorCliChecker, compile's SystemPythonFontmakeBackend) that the configuration
        // cache cannot see as an input; re-running this task every invocation is the honest
        // choice; caching a stale "not built" seen once would misreport later runs (CLAUDE.md
        // law 5).
        doNotTrackState("depends on python3/fontmake/fontspector on PATH")
        systemProperty("typewright.repoRoot", isolated.rootProject.projectDirectory.asFile.absolutePath)
        systemProperty("java.awt.headless", "true")
    }

// The "test" task's own junitXml output location: `build/test-results/test/` is Gradle's default
// for a task of that name, and nothing in this build overrides it, so this is read as a plain
// path rather than through `goldenPathTest.map { it.reports.junitXml... } }`, which Gradle refuses
// to wire as a task output outside the task's own configuration block ("does not have a task
// associated with it") when read this way from another task's lazy provider chain.
val junitXmlDir = layout.buildDirectory.dir("test-results/test")

tasks.register<GoldenPathReportTask>("goldenPath") {
    description = "Runs GoldenPathTest and prints the golden-path pass count; never fails on step results."
    group = "verification"
    dependsOn(goldenPathTest)
    testResults.set(junitXmlDir)
    passingSteps.set(isolated.rootProject.projectDirectory.file("golden-path/passing-steps.txt"))
    summaryFile.set(layout.buildDirectory.file("golden-path/summary.txt"))
}

tasks.register<GoldenPathRatchetTask>("goldenPathRatchet") {
    description = "Fails when golden-path/passing-steps.txt's ratchet rule is broken (see its header, and README.md)."
    group = "verification"
    dependsOn(goldenPathTest)
    testResults.set(junitXmlDir)
    passingSteps.set(isolated.rootProject.projectDirectory.file("golden-path/passing-steps.txt"))
}
