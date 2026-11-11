// SPDX-License-Identifier: FSL-1.1-ALv2

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    id("typewright.lint")
}

group = "com.asoc"

// version.properties is the one version file (see app-android/build.gradle.kts). jpackage wants MAJOR.MINOR.PATCH
// with no suffix, so 1.0.0-rc1 is packaged as 1.0.0.
val versionName =
    Properties().apply {
        load(providers.fileContents(isolated.rootProject.projectDirectory.file("version.properties")).asText.get().reader())
    }.getProperty("VERSION_NAME")

java {
    val java = JavaVersion.toVersion(libs.versions.jvm.target.get())
    sourceCompatibility = java
    targetCompatibility = java
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(libs.versions.jvm.target.get())
    }
}

dependencies {
    implementation(project(":ui"))
    // The desktop app's real storage for imported fonts and adopted UFOs (DesktopStorageProvider).
    implementation(project(":project:storage"))
    implementation(compose.desktop.currentOs)
    testImplementation(libs.kotlin.test)
}

compose.desktop {
    application {
        mainClass = "com.asoc.typewright.app.desktop.MainKt"
        nativeDistributions {
            // Linux is the v1 desktop. Its one released format is an AppImage (docs/release/LINUX_PACKAGING.md): one file,
            // no root, no sandbox between the app and the Python on the person's machine that runs fontmake. Gradle builds
            // the jpackage "app-image" directory with the JRE bundled (TargetFormat.AppImage is that directory, not the
            // AppImage file); tools/release/build_appimage.sh wraps it into the single file. Windows and macOS are not in V1.
            targetFormats(TargetFormat.AppImage)
            packageName = "typewright"
            packageVersion = versionName.substringBefore('-')
            description = "Hand-made letterforms to fonts of Google Fonts quality"
            vendor = "A System of Cells"
            // The notices ship beside the app as plain files, so they are readable without starting it.
            appResourcesRootDir.set(layout.buildDirectory.dir("legal-resources"))
            licenseFile.set(isolated.rootProject.projectDirectory.file("LICENSE"))
            linux {
                iconFile.set(isolated.rootProject.projectDirectory.file("docs/branding/app-icon-light.png"))
                appCategory = "Graphics"
                menuGroup = "Graphics"
            }
            // The bundled runtime holds the modules the app needs and no more; `./gradlew :app-desktop:suggestRuntimeModules` lists them.
            modules("java.instrument", "jdk.unsupported")
        }
    }
}

// The licences and notices ship beside the app (docs/release/LINUX_PACKAGING.md), copied from the repository's own files.
val prepareLegalResources =
    tasks.register<Copy>("prepareLegalResources") {
        val root = isolated.rootProject.projectDirectory
        from(root.file("LICENSE"), root.file("THIRD_PARTY.md"), root.file("TRADEMARKS.md"), root.file("docs/release/PRIVACY.md"))
        from(root.dir("LICENSES")) { into("LICENSES") }
        into(layout.buildDirectory.dir("legal-resources/common"))
    }
tasks.withType<AbstractJPackageTask>().configureEach { dependsOn(prepareLegalResources) }
tasks.matching { it.name.startsWith("prepareAppResources") }.configureEach { dependsOn(prepareLegalResources) }
