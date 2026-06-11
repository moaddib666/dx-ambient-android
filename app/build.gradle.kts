import com.github.triplet.gradle.androidpublisher.ReleaseStatus
import com.github.triplet.gradle.androidpublisher.ResolutionStrategy
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.play.publisher)
}

// Release signing is read from keystore.properties (gitignored) when present, or
// from environment variables (CI). When neither is configured the release build
// falls back to the debug signing key so `assembleRelease` still works locally —
// only a properly-signed bundle should ever be uploaded to Play.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties =
    Properties().apply {
        if (keystorePropertiesFile.exists()) {
            keystorePropertiesFile.inputStream().use { load(it) }
        }
    }

fun signingValue(propKey: String, envKey: String): String? =
    keystoreProperties.getProperty(propKey) ?: System.getenv(envKey)

val releaseStoreFilePath = signingValue("storeFile", "DXA_STORE_FILE")
val hasReleaseSigning = !releaseStoreFilePath.isNullOrBlank()

android {
    namespace = "com.dx.ambient"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dx.ambient"
        minSdk = 23
        targetSdk = 35
        versionCode = 2 // floor only — Play Publisher auto-resolves the next free code at upload
        versionName = "1.1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Real OAuth sign-in (youtube.readonly via Play Services Authorization)
        // is configured against the dimension-x-live Cloud project; the Web
        // client ID lives in optional-youtube/.../youtube_config.xml.
        buildConfigField("boolean", "YOUTUBE_MODE_ENABLED", "true")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFilePath!!)
                storePassword = signingValue("storePassword", "DXA_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "DXA_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "DXA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig =
                if (hasReleaseSigning) {
                    signingConfigs.getByName("release")
                } else {
                    signingConfigs.getByName("debug")
                }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    ndkVersion = "27.1.12297006"
}

// Gradle Play Publisher — publishes the AAB, store listing, graphics and release
// notes straight to the Play Console (docs/PUBLISHING.md). Credentials come from
// play-service-account.json at the repo root (gitignored) or the
// ANDROID_PUBLISHER_CREDENTIALS env var on CI. Listing sources live in
// app/src/main/play/.
play {
    val credentials = rootProject.file("play-service-account.json")
    val hasPlayCredentials =
        credentials.exists() || !System.getenv("ANDROID_PUBLISHER_CREDENTIALS").isNullOrBlank()
    // Without credentials the plugin is disabled entirely so plain release builds
    // (bundleRelease/assembleRelease) keep working before the one-time setup.
    enabled.set(hasPlayCredentials)
    if (credentials.exists()) {
        serviceAccountCredentials.set(credentials)
    }
    defaultToAppBundles.set(true)
    track.set("internal")
    releaseStatus.set(ReleaseStatus.COMPLETED)
    // Pull the next free versionCode from Play so uploads never collide.
    resolutionStrategy.set(ResolutionStrategy.AUTO)
}

dependencies {
    // Feature & core modules
    implementation(project(":core-domain"))
    implementation(project(":core-data"))
    implementation(project(":core-playback"))
    implementation(project(":core-rendering"))
    implementation(project(":feature-scenes"))
    implementation(project(":feature-library"))
    implementation(project(":feature-settings"))
    implementation(project(":optional-youtube"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
