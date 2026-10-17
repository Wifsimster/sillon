import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Release signing. The upload key is supplied either via a local, untracked
// `keystore.properties` (developer machines — see keystore.properties.example)
// or via environment variables (CI). Neither the keystore nor its passwords are
// ever committed. When no upload key is configured we fall back to the debug
// key so local and CI APK builds still produce an installable artifact — but a
// Play-Store-bound AAB MUST be built with the real upload key.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
fun signingValue(propKey: String, envKey: String): String? =
    (keystoreProps.getProperty(propKey) ?: System.getenv(envKey))?.takeIf { it.isNotBlank() }

val releaseStoreFile: String? = signingValue("storeFile", "KEYSTORE_FILE")
val hasReleaseSigning: Boolean = releaseStoreFile != null

// Single source of truth for the app version. `VERSION_NAME` lives in
// gradle.properties and is bumped automatically by semantic-release on each
// release. versionCode is derived from the semver so it always increases.
val appVersionName: String = (project.findProperty("VERSION_NAME") as String?) ?: "0.0.0"
// Bands are 1000-wide (minor, patch each < 1000) so a long-lived minor cycle
// can run to patch 999, and minor to 999, without ever colliding with or
// regressing below the next component — Play rejects a non-increasing
// versionCode. 1.10.0 -> 1_010_000; previous 1.9.0 -> 1_009_000 (both larger
// than the old major*10000+minor*100+patch scheme, so the sequence keeps rising).
val appVersionCode: Int = appVersionName.substringBefore("-").split(".").let { parts ->
    val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
    major * 1_000_000 + minor * 1_000 + patch
}

android {
    namespace = "ovh.battistella.sillon"
    compileSdk = 36

    defaultConfig {
        applicationId = "ovh.battistella.sillon"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        // A committed, well-known debug key (the standard Android debug
        // passwords), so every CI build — and the debug-signed APKs on the
        // GitHub releases — installs over the previous one. A fresh runner
        // would otherwise generate a new key each time, and Android refuses an
        // update signed with a different key. This key protects nothing: store
        // builds use the upload key below.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = signingValue("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use the dedicated upload key when configured (keystore.properties
            // or CI env vars); otherwise fall back to the debug key so local
            // and CI APK builds still produce an installable artifact.
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    androidResources {
        // Generate a locales_config.xml from the values-* folders so the new
        // languages appear in Android 13+'s per-app language picker. The default
        // (unqualified) resources are English — declared in res/resources.properties.
        generateLocaleConfig = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests {
            // Robolectric + Compose UI tests need the merged Android resources and
            // manifest available to the JVM (host) test runtime.
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.coil.compose)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.okhttp.mockwebserver)
}
