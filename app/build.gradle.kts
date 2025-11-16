import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application) // Android app plugin
    alias(libs.plugins.kotlin.android) // Kotlin Android plugin
    alias(libs.plugins.kotlin.compose) // Compose compiler plugin
    alias(libs.plugins.google.ksp) // KSP for Room database
}

android {
    namespace = "com.weproz.superreader" // App package name
    compileSdk = 36 // Target Android API

    defaultConfig {
        applicationId = "com.weproz.superreader" // Unique app ID
        minSdk = 26 // Minimum Android version
        targetSdk = 36 // Target Android version
        versionCode = 1 // App version number
        versionName = "1.0" // App version string
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" // Test runner
    }

    // Resolve duplicate files
    packaging {
        resources {
            pickFirsts.add("META-INF/LICENSE.md") // Pick first license file
            pickFirsts.add("META-INF/LICENSE-notice.md") // Pick first notice file
            pickFirsts.add("META-INF/DEPENDENCIES") // Pick first dependencies file
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false // Don't shrink code
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), // Default rules
                "proguard-rules.pro" // Custom rules
            )
        }
    }

    compileOptions {
        // Enable modern Java APIs on older Android
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17 // Java source version
        targetCompatibility = JavaVersion.VERSION_17 // Java target version
    }

    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17 // Kotlin JVM target
        }
    }

    buildFeatures {
        compose = true // Enable Jetpack Compose
        viewBinding = true // Enable view binding
    }

    // Asset directories
    sourceSets {
        getByName("main") {
            assets {
                srcDirs("src/main/assets", "src/main/assets") // Asset source folders
            }
        }
    }
}


dependencies {

    // Room Database (local storage)
    implementation(libs.androidx.room.runtime) // Room runtime
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.compose.foundation.layout) // Room Kotlin extensions
    ksp(libs.androidx.room.compiler) // Room compiler

    // JSON serialization for data
    implementation(libs.google.gson) // ✅ from version catalog


    // Modern Java APIs on old Android
    coreLibraryDesugaring(libs.tools.desugar.jdk.libs)

    // Navigation between screens
    implementation(libs.androidx.navigation.compose)

    // PDF viewing library
    implementation(libs.android.pdf.viewer.mhiew)

    // Fragment utilities
    implementation(libs.androidx.fragment.ktx)

    // Lifecycle & ViewModel (app state)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Material Design icons & components
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.google.material)

    // Core Android & Compose UI
    implementation(libs.androidx.core.ktx) // Android core utilities
    implementation(libs.androidx.activity.compose) // Activity with Compose
    implementation(platform(libs.androidx.compose.bom)) // Compose version alignment
    implementation(libs.androidx.ui) // Basic UI components
    implementation(libs.androidx.ui.graphics) // Graphics utilities
    implementation(libs.androidx.ui.tooling.preview) // Preview support
    implementation(libs.androidx.material3) // Material 3 design
    implementation(libs.androidx.appcompat) // App compatibility
    implementation(libs.androidx.core.splashscreen)  // Splash Screen
    implementation(libs.androidx.datastore.preferences) // Data Store for theme saving


    // Testing libraries
    testImplementation(libs.junit) // Unit testing
    androidTestImplementation(libs.androidx.junit) // Android testing
    androidTestImplementation(libs.androidx.espresso.core) // UI testing
    androidTestImplementation(platform(libs.androidx.compose.bom)) // Compose test BOM
    androidTestImplementation(libs.androidx.ui.test.junit4) // Compose UI testing
    debugImplementation(libs.androidx.ui.tooling) // Debug UI tools
    debugImplementation(libs.androidx.ui.test.manifest) // Test manifest
}