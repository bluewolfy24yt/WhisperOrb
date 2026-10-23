plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("plugin.serialization")
    kotlin("plugin.compose")
}

android {
    namespace = "helium314.keyboard.latin"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        buildConfigField("String", "APPLICATION_ID", "\"com.whisperorb.app\"")
        buildConfigField("int", "VERSION_CODE", "220")
        buildConfigField("String", "VERSION_NAME", "\"2.2.0\"")

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        consumerProguardFiles("proguard-rules.pro")
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }

    externalNativeBuild {
        ndkBuild {
            path = File("src/main/jni/Android.mk")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    // androidx
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.autofill:autofill:1.1.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")

    // kotlin
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    // compose
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation(platform("androidx.compose:compose-bom:2025.05.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("sh.calvin.reorderable:reorderable:2.4.3")
    implementation("com.github.skydoves:colorpicker-compose:1.1.2")
}
