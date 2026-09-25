// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.buildlogic

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * A plain Kotlin-JVM Typewright module (no Kotlin Multiplatform, no Android target): plumbing and
 * test harnesses that only ever run on the desktop JVM, such as `golden-path`. Applies the Kotlin
 * JVM plugin and the lint convention, sets [group] and the Java/Kotlin bytecode level from the
 * catalog's `jvm-target`, wires `kotlin-test-junit` onto `testImplementation` (this repository's
 * JUnit 4 test runner -- no module here uses JUnit Platform), and turns on readable test logging.
 * A module built with this plugin still resolves a Kotlin Multiplatform dependency's JVM/desktop
 * variant normally (see `app-desktop/build.gradle.kts`'s own plain `kotlin-jvm` plugin resolving
 * `:ui`), so it can depend on `core-*`/`engine-*`/`qa`/`compile` project dependencies as usual.
 */
class JvmConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            group = TYPEWRIGHT_GROUP
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply(LintConventionPlugin::class.java)

            val jvmVersion = JavaVersion.toVersion(libs.versionOf("jvm-target"))
            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = jvmVersion
                targetCompatibility = jvmVersion
            }

            val jvmTarget = JvmTarget.fromTarget(libs.versionOf("jvm-target"))
            extensions.configure<KotlinJvmProjectExtension> {
                compilerOptions {
                    this.jvmTarget.set(jvmTarget)
                }
            }

            dependencies {
                add("testImplementation", libs.findLibrary("kotlin-test-junit").get())
            }

            configureTestLogging()
        }
}
