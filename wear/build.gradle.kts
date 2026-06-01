import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Share the upload keystore with the phone module — same developer identity
// signs the watch APK.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) load(keystorePropsFile.inputStream())
}

android {
    namespace = "com.sliderulewatchguide.wear"
    compileSdk = 35

    // Same applicationId + signing key as the phone module so Play files
    // this watch bundle under the SAME app listing (shared reviews /
    // ratings) and delivers it to watches via multi-APK form-factor
    // delivery. The watch bundle is uploaded SEPARATELY (it is no longer
    // embedded in the phone APK), so its versionCode MUST be unique
    // across all form factors and follows its own independent scheme
    // (wear: 9, 11, 13 …) distinct from the phone's (8, 10, …).
    defaultConfig {
        applicationId = "com.sliderulewatchguide"
        minSdk = 30
        targetSdk = 35
        versionCode = 12
        versionName = "1.0.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // Ship BOTH ARM ABIs. Most Wear OS watches (e.g. Galaxy Watch 4)
            // run a 32-bit armeabi-v7a userspace and CANNOT run an
            // arm64-only build; 64-bit watches run v7a via backward-compat.
            // arm64-v8a is kept too (newer watches + Google's Sep-2026
            // 64-bit requirement). The two native libs (graphics.path,
            // datastore) ship in both ABIs.
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
            // Emulator testing on x86_64 hosts: `-PemuAbi` appends x86_64
            // so the native libs load on the emulator.
            if (project.hasProperty("emuAbi")) abiFilters += "x86_64"
        }
    }

    signingConfigs {
        if (keystoreProps.containsKey("storeFile")) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (keystoreProps.containsKey("storeFile")) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            ndk {
                debugSymbolLevel = "NONE"
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

    buildFeatures {
        compose = true
    }

    sourceSets {
        getByName("main").java.srcDirs("src/main/kotlin")
        getByName("test").java.srcDirs("src/test/kotlin")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose BOM (reused from phone catalog) covers core compose UI / foundation.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)

    // Required by the ported DialViewModel / DialCanvas (used by the
    // chronograph clock and the live time-hands layer).
    implementation(libs.kotlinx.datetime)

    // Bezel sync: phone↔watch Data Layer + persisted sync toggle.
    implementation(libs.play.services.wearable)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.play.services)

    // Wear-specific Compose libraries. Version-pinned here (not in the
    // shared catalog) because the phone module doesn't use them.
    // Using the stable wear-compose-material (not the alpha material3
    // for wear, which has not had a stable release at time of writing).
    implementation("androidx.wear.compose:compose-material:1.4.1")
    implementation("androidx.wear.compose:compose-foundation:1.4.1")
    implementation("androidx.wear:wear:1.3.0")

    debugImplementation(libs.androidx.compose.ui.tooling)
}
