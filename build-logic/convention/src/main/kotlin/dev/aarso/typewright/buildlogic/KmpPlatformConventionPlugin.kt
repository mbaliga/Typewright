package dev.aarso.typewright.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * A Typewright module with platform actuals: Kotlin Multiplatform with an Android target
 * (AGP 9's `com.android.kotlin.multiplatform.library`), a JVM target named `desktop`, and a
 * Kotlin/Wasm browser target. Common tests run on the desktop JVM and as Android host tests.
 * Wasm tests are opt-in per module with `kotlin { wasmJs { nodejs() } }`: modules without
 * Compose can run them under Node; Compose modules cannot, because Skiko's Wasm runtime only
 * loads in a browser (CMP-4906). Browser test runs are off here.
 */
class KmpPlatformConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            group = TYPEWRIGHT_GROUP
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply("com.android.kotlin.multiplatform.library")
            pluginManager.apply(LintConventionPlugin::class.java)

            val jvmTarget = JvmTarget.fromTarget(libs.versionOf("jvm-target"))
            extensions.configure<KotlinMultiplatformExtension> {
                (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
                    namespace = typewrightNamespace
                    compileSdk = libs.intVersionOf("android-compileSdk")
                    minSdk = libs.intVersionOf("android-minSdk")
                    compilerOptions {
                        this.jvmTarget.set(jvmTarget)
                    }
                    withHostTest {}
                }
                jvm("desktop") {
                    compilerOptions {
                        this.jvmTarget.set(jvmTarget)
                    }
                }
                @OptIn(ExperimentalWasmDsl::class)
                wasmJs {
                    browser {
                        testTask {
                            enabled = false
                        }
                    }
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
