import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
}

val appVersionName: String = (project.findProperty("VERSION_NAME") as String?) ?: "0.0.0"
val packageVersionName: String = appVersionName.substringBefore("-").split(".").let { parts ->
    val major = (parts.getOrNull(0)?.toIntOrNull() ?: 0).coerceAtLeast(1)
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
    "$major.$minor.$patch"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

sourceSets {
    main {
        resources.srcDir(rootProject.file("app/src/main/res"))
        resources.srcDir("icons")
        resources.srcDir(layout.buildDirectory.dir("generated/sillon/resources"))
        resources.include("font/*.ttf", "sillon.png", "sillon-version.txt")
    }
}

val writeVersion by tasks.registering {
    val output = layout.buildDirectory.file("generated/sillon/resources/sillon-version.txt")
    val version = appVersionName
    inputs.property("version", version)
    outputs.file(output)
    doLast {
        output.get().asFile.apply {
            parentFile.mkdirs()
            writeText(version)
        }
    }
}

tasks.named("processResources") {
    dependsOn(writeVersion)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.coil3.compose)
    implementation(libs.coil3.network.okhttp)
    implementation(libs.jna)
    implementation(libs.jna.platform)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}

tasks.withType<Test>().configureEach {
    systemProperty("sillon.mpv.dir", rootProject.file("desktop/resources/windows-x64").absolutePath)
}

compose.desktop {
    application {
        mainClass = "ovh.battistella.sillon.desktop.MainKt"
        jvmArgs += listOf("-Xmx512m", "-Dfile.encoding=UTF-8")

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Sillon"
            packageVersion = packageVersionName
            description = "Sillon, a music player for your Plex or Jellyfin server"
            vendor = "Sillon"
            copyright = "Sillon contributors"
            modules("java.base", "java.desktop", "java.logging", "java.naming", "java.xml", "jdk.unsupported", "jdk.crypto.ec")
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))

            windows {
                iconFile.set(project.file("icons/sillon.ico"))
                menuGroup = "Sillon"
                shortcut = true
                menu = true
                perUserInstall = true
                dirChooser = true
                upgradeUuid = "6f0c1f6e-3c1b-4b7e-9a51-5d6a3f0b7c21"
            }
            linux {
                iconFile.set(project.file("icons/sillon.png"))
            }
        }
    }
}
