plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.palmabrahma.smallmodeltest"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.palmabrahma.smallmodeltest"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        //jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Core Android dependencies
    implementation(libs.bundles.android.core)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose UI
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.compose.material.icons.extended)

    // ONNX Runtime for Phi-3
    implementation(libs.onnxruntime.android)


    // Networking for model downloading
    implementation(libs.bundles.networking)

    // Coroutines for async operations
    implementation(libs.kotlinx.coroutines.android)

    // For battery and system metrics
    implementation(libs.androidx.work.runtime.ktx)

    // Charts for visualization
    implementation(libs.bundles.charts)

    // JSON handling
    implementation(libs.kotlinx.serialization.json)

    // Logging
    implementation(libs.timber)

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")

    // Testing dependencies
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.9.0")

    implementation(files("libs/onnxruntime-genai-android-0.10.0.aar"))
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))
}