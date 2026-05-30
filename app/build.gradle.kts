import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release keystore credentials are read from keystore.properties (an
// untracked file in the project root). See PLAYSTORE.md for the format.
// If the file is missing the release build still succeeds — it just
// falls back to the debug keystore (suitable for local testing but not
// for Play Store uploads).
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) load(keystorePropsFile.inputStream())
}

android {
    namespace = "com.sliderulewatchguide"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sliderulewatchguide"
        minSdk = 30
        targetSdk = 35
        versionCode = 7
        versionName = "1.0.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Ship only 64-bit ARM. Drops x86/x86_64 (emulator-only) and
        // armeabi-v7a (legacy 32-bit phones) from the bundle. Modern
        // Play-store devices are arm64-v8a; combined with Play's
        // per-ABI AAB split, this minimises the native code surface
        // shipped to end-user devices.
        ndk {
            abiFilters += listOf("arm64-v8a")
            // Emulator testing on x86_64 hosts: `-PemuAbi` appends x86_64
            // so native libs (graphics.path, datastore) load on the
            // emulator. Distribution builds omit the flag → arm64-only.
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
            // Explicit NONE keeps native function names opaque in any
            // crash report — no symbol table is generated or uploaded
            // alongside the bundle. Play will surface a non-blocking
            // "native debug symbols missing" warning; that is intended.
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
        getByName("androidTest").java.srcDirs("src/androidTest/kotlin")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Embed the paired wear-OS APK so the phone AAB ships both
    // form-factor artefacts under one Play Store listing. Play pushes
    // the wear APK to a paired watch when the phone app installs.
    wearApp(project(":wear"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.window)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)

    implementation(libs.kotlinx.datetime)

    // Bezel sync: phone↔watch Data Layer + persisted sync toggle.
    implementation(libs.play.services.wearable)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.play.services)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.junit.ext)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
