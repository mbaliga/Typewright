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

/** Readable test output for every JVM test task. */
internal fun Project.configureTestLogging() {
    tasks.withType<Test>().configureEach {
        testLogging {
            events(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED)
            exceptionFormat = TestExceptionFormat.FULL
        }
    }
}
