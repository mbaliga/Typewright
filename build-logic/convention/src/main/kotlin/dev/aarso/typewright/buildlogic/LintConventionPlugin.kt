// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.buildlogic

import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * ktlint through Spotless, on every Kotlin source and build script of the module.
 * Spotless wires `spotlessCheck` into `check`; `spotlessApply` fixes what it can.
 * Rules come from the root `.editorconfig`.
 */
class LintConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("base")
            pluginManager.apply("com.diffplug.spotless")
            val ktlintVersion = libs.versionOf("ktlint")
            val editorConfig = isolated.rootProject.projectDirectory.file(".editorconfig")
            // Spotless reads .editorconfig while configuring; reading it through a provider makes
            // it a configuration-cache input, so editing the rules invalidates the cached setup.
            providers.fileContents(editorConfig).asText.get()
            extensions.configure<SpotlessExtension> {
                kotlin {
                    target("src/**/*.kt")
                    ktlint(ktlintVersion).setEditorConfigPath(editorConfig.asFile)
                }
                kotlinGradle {
                    target("*.gradle.kts")
                    ktlint(ktlintVersion).setEditorConfigPath(editorConfig.asFile)
                }
            }
        }
}
