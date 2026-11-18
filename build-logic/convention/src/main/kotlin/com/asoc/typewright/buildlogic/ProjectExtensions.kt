// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest

/** The Maven group of every Typewright module. */
internal const val TYPEWRIGHT_GROUP = "com.asoc"

/** The root of every Typewright Kotlin package and Android namespace. */
internal const val TYPEWRIGHT_PACKAGE_ROOT = "com.asoc.typewright"

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.versionOf(alias: String): String = findVersion(alias).get().requiredVersion

internal fun VersionCatalog.intVersionOf(alias: String): Int = versionOf(alias).toInt()

/**
 * Kotlin package and Android namespace for a module, derived from its Gradle path:
 * `:qa:corpus` becomes `com.asoc.typewright.qa.corpus`, `:shape-preview` becomes
 * `com.asoc.typewright.shape.preview`.
 */
internal val Project.typewrightNamespace: String
    get() = TYPEWRIGHT_PACKAGE_ROOT + "." + path.removePrefix(":").replace(':', '.').replace('-', '.')

/**
 * True when Gradle was started with `-Ptypewright.ios=true` (docs/PORTING_PLAN.md IOS-1). Off by
 * default, so every existing build, test and CI job configures exactly the targets it did before.
 * Kotlin/Native iOS targets need Apple tooling to link and run; on any other host they are
 * declared but disabled by KGP, so this flag changes nothing there beyond a warning.
 */
internal val Project.iosEnabled: Boolean
    get() = providers.gradleProperty("typewright.ios").orNull?.toBoolean() ?: false

/** Readable test output for every JVM test task. */
internal fun Project.configureTestLogging() {
    tasks.withType<Test>().configureEach {
        testLogging {
            events(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED)
            exceptionFormat = TestExceptionFormat.FULL
        }
    }
}

/**
 * Lets the iOS simulator tests find the data packs and an installed simulator (docs/IOS_PORT.md).
 * `TYPEWRIGHT_RESOURCES` is the folder `app-ios/scripts/stage-resources.sh` fills; `simctl` passes a
 * variable prefixed `SIMCTL_CHILD_` to the spawned test process without the prefix. `TYPEWRIGHT_IOS_SIM`
 * names the simulator device (ios.yml picks one that is installed). Both are optional, and unset
 * means KGP's defaults, so nothing changes outside that workflow.
 */
internal fun Project.configureIosSimulatorTests() {
    val resources = providers.environmentVariable("TYPEWRIGHT_RESOURCES").orNull
    val device = providers.environmentVariable("TYPEWRIGHT_IOS_SIM").orNull
    tasks.withType<KotlinNativeSimulatorTest>().configureEach {
        if (resources != null) environment("SIMCTL_CHILD_TYPEWRIGHT_RESOURCES", resources)
        if (device != null) this.device.set(device)
    }
}
