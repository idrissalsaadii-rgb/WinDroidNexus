plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.windroid.nexus"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.windroid.nexus"
        minSdk = 26          // Android 8.0 — required for adoptable storage + modern process APIs
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // No ABI filter: the app bundles no native code of its own, so we let
        // AGP include every ABI its dependencies ship (armeabi-v7a, arm64-v8a,
        // x86, x86_64). This yields a universal APK that installs on all
        // devices, including 32-bit ARM. Any x86/Windows translation is still
        // delegated to the native runtime the user provisions into the engine.
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // Keep the native runtime payloads uncompressed so they can be
        // mmap'd / exec'd directly out of the APK's lib dir.
        jniLibs.useLegacyPackaging = false
    }
}

dependencies {
    // ---- Compose (BOM keeps all Compose artifacts version-aligned) ----
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")

    // ---- Lifecycle / ViewModel ----
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    // ---- Dependency Injection (Hilt) ----
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // ---- Coroutines ----
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // ---- Persistence of desktop state (DataStore) ----
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ---- Core library desugaring (java.time on API 26) ----
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")

    // ---- Test ----
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
