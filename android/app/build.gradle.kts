plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.whisperorb.app"
    compileSdk = 35
    ndkVersion = "26.3.11579264"

    defaultConfig {
        applicationId = "com.whisperorb.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 220
        versionName = "2.2.0"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-O3", "-fno-finite-math-only", "-fexceptions")
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DGGML_OPENMP=OFF",
                    "-DWHISPER_BUILD_TESTS=OFF",
                    "-DWHISPER_BUILD_EXAMPLES=OFF"
                )
            }
        }
    }

    signingConfigs {
        create("permanent") {
            storeFile = file("whisperorb-release.p12")
            storeType = "PKCS12"
            storePassword = "whisperorb2026"
            keyAlias = "whisperorb"
            keyPassword = "whisperorb2026"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("permanent")
        }
        release {
            isMinifyEnabled = false
            // Signature cryptographique fixe pour que les mises à jour (adb install -r / APK GitHub)
            // ne perdent JAMAIS les préférences ni les modèles téléchargés.
            signingConfig = signingConfigs.getByName("permanent")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(project(":heliboard"))
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Jetpack Compose + Material 3 (Parité visuelle 1:1 avec SwiftUI iOS / iPadOS)
    val composeBom = platform("androidx.compose:compose-bom:2025.05.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Génération native du QR Code GitHub haute définition (Carte 6)
    implementation("com.google.zxing:core:3.5.3")
}
