plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    id("kotlin-parcelize")
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt.android)
    // Firebase / Google Services plugins are intentionally NOT applied here.
    // Step 5 is an offline POC (AppHost.bootstrapMode = LOCAL_ONLY); Firebase/Remote Config/
    // Crashlytics are deferred release services (see docs/DEFERRED_SERVICES.md). Adding the
    // google-services plugin here would require a real google-services.json — out of scope.
}

android {
    namespace = "com.ivistatect.qrscanner"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ivistatect.qrscanner"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Sign release with the debug key so it installs on the test device (no release keystore yet).
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        noCompress += listOf("ogg", "mp4", "webp", "jpg", "png", "db", "tflite")
    }
}

// base-application AAR already bundles Android-SpinKit's classes; drop the standalone transitive copy
// so R8 (release) doesn't fail on the duplicate com.github.ybq.android.spinkit.BuildConfig.
configurations.all {
    exclude(group = "com.github.ybq", module = "Android-SpinKit")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    // Material Components (View) — provides the Theme.Material3.* XML themes the app theme
    // inherits, so the AAR's AppCompat-based activities don't crash.
    implementation(libs.material)
    implementation("androidx.print:print:1.0.0")
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    // The shared runtime (ads / IAP / language / entitlement). Real project dependency.
    implementation(project(":base-application-wrapper"))

    // Hilt — required by BaseLibApplication (@HiltAndroidApp) and the wrapper's Hilt graph.
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // ── QR Scanner domain deps (each for a concrete feature) ────────────────────────────────
    // Live + gallery decode: MLKit barcode-scanning (bundled models variant; no .tflite copied).
    implementation(libs.mlkit.barcode.scanning)
    // Camera preview + frame analysis.
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    // QR / barcode bitmap generation for the Create flow.
    implementation(libs.zxing.core)
    // History persistence (scanned / created / favorites, search, delete).
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    debugImplementation(libs.androidx.ui.tooling)
}
