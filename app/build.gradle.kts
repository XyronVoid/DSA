plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing: pass these as environment variables to sign the Play Store bundle directly.
// Without them the release bundle is built unsigned.
val releaseStoreFile: String? = System.getenv("SB_KEYSTORE_PATH")
val releaseStorePassword: String? = System.getenv("SB_KEYSTORE_PASSWORD")
val releaseKeyAlias: String? = System.getenv("SB_KEY_ALIAS")
val releaseKeyPassword: String? = System.getenv("SB_KEY_PASSWORD")
val hasReleaseKey = !releaseStoreFile.isNullOrBlank() && file(releaseStoreFile!!).exists()

android {
    namespace = "com.spacebachao.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.spacebachao.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"
    }

    signingConfigs {
        // Fixed test key (checked in on purpose) so test builds can update each other.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            if (hasReleaseKey) {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // No key on this machine = unsigned bundle (it gets signed separately with the upload key).
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("release") else null
        }
        // Same as release (small, fast), but signed with the debug key so it can be installed for testing.
        create("preview") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
        disable += "UnsafeOptInUsageError"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Video compression (on-device, hardware encoder)
    val media3 = "1.5.1"
    implementation("androidx.media3:media3-transformer:$media3")
    implementation("androidx.media3:media3-common:$media3")
    implementation("androidx.media3:media3-container:$media3")
    implementation("androidx.media3:media3-muxer:$media3")

    // Pro unlock (Google Play payments go through the Play Store app)
    implementation("com.android.billingclient:billing:8.0.0")
}
