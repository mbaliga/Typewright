// SPDX-License-Identifier: Apache-2.0

plugins {
    `kotlin-dsl`
}

group = "com.asoc.typewright.buildlogic"

dependencies {
    // compileOnly: the root build puts these plugins on the classpath with `apply false`.
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.compiler.gradle.plugin)
    compileOnly(libs.compose.gradle.plugin)
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.spotless.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("lint") {
            id = "typewright.lint"
            implementationClass = "com.asoc.typewright.buildlogic.LintConventionPlugin"
        }
        register("kmpPure") {
            id = "typewright.kmp.pure"
            implementationClass = "com.asoc.typewright.buildlogic.KmpPureConventionPlugin"
        }
        register("kmpPlatform") {
            id = "typewright.kmp.platform"
            implementationClass = "com.asoc.typewright.buildlogic.KmpPlatformConventionPlugin"
        }
        register("jvm") {
            id = "typewright.jvm"
            implementationClass = "com.asoc.typewright.buildlogic.JvmConventionPlugin"
        }
    }
}
