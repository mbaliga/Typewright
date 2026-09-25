// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * A pure Typewright module (CLAUDE.md law 2): Kotlin Multiplatform with a JVM target and a
 * Kotlin/Wasm target whose tests run under Node. There is deliberately no Android target and
 * no Android SDK on the classpath, so an `android.*` import cannot compile here. Android
 * modules consume these through their JVM variant.
 */
class KmpPureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            group = TYPEWRIGHT_GROUP
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply(LintConventionPlugin::class.java)

            val jvmTarget = JvmTarget.fromTarget(libs.versionOf("jvm-target"))
            extensions.configure<KotlinMultiplatformExtension> {
                jvm {
                    compilerOptions {
                        this.jvmTarget.set(jvmTarget)
                    }
                }
                @OptIn(ExperimentalWasmDsl::class)
                wasmJs {
                    nodejs()
                }
                sourceSets.named("commonTest") {
                    dependencies {
                        implementation(libs.findLibrary("kotlin-test").get())
                    }
                }
            }
            configureTestLogging()
        }
}
