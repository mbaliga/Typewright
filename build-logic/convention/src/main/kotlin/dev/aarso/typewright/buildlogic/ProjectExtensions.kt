// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType

/** The Maven group of every Typewright module (provisional; see docs/OPEN_QUESTIONS.md). */
internal const val TYPEWRIGHT_GROUP = "dev.aarso.typewright"

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.versionOf(alias: String): String = findVersion(alias).get().requiredVersion

internal fun VersionCatalog.intVersionOf(alias: String): Int = versionOf(alias).toInt()

/**
 * Kotlin package and Android namespace for a module, derived from its Gradle path:
 * `:qa:corpus` becomes `dev.aarso.typewright.qa.corpus`, `:shape-preview` becomes
 * `dev.aarso.typewright.shape.preview`.
 */
internal val Project.typewrightNamespace: String
    get() = TYPEWRIGHT_GROUP + "." + path.removePrefix(":").replace(':', '.').replace('-', '.')

/** Readable test output for every JVM test task. */
internal fun Project.configureTestLogging() {
    tasks.withType<Test>().configureEach {
        testLogging {
            events(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED)
            exceptionFormat = TestExceptionFormat.FULL
        }
    }
}
