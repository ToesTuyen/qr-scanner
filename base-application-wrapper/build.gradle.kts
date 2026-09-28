plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.vnnami.appkit"
    compileSdk = 36

    defaultConfig {
        minSdk = 28

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        // KeyVault content-key — the secret that decrypts the API-key blob. Shared plumbing for
        // every app on this wrapper, read from the ROOT local.properties (gitignored) so no key is
        // ever baked into source. Missing line => "" => KeyVaultProvider.init() bails out quietly
        // and the app behaves as if KeyVault were not there.
        val kvContentKey = rootProject.file("local.properties").takeIf { it.exists() }
            ?.readLines()?.firstOrNull { it.startsWith("KV_CONTENT_KEY=") }
            ?.substringAfter("=")?.trim().orEmpty()
        buildConfigField("String", "KV_CONTENT_KEY", "\"$kvContentKey\"")
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation(libs.material)
    
    // Compose dependencies
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.activity.compose)
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation(libs.androidx.ui.tooling)

    // ── base-application AAR ────────────────────────────────────────────────────────────────
    // Resolved from libs/ through the flatDir repository declared in settings.gradle.kts.
    // `api` so everything below also reaches :app, exactly like the removed :base-application
    // module's `default` configuration did.
    api(mapOf("name" to "base-application-1.0.0", "ext" to "aar"))

    // ⚠️ The AAR ships NO POM/metadata, so EVERY transitive dependency of the library must be
    // re-declared here (guide HUONG_DAN_TICH_HOP.md §3.1). Keep in lock-step with the AAR version
    // in libs/ — a missing or older entry surfaces as NoClassDefFoundError / NoSuchMethodError at
    // runtime (e.g. AppsFlyerLib.start(Context) needs af-android-sdk:6.18.0).
    api("androidx.core:core-ktx:1.16.0")
    api("androidx.appcompat:appcompat:1.7.1")
    api("com.google.android.material:material:1.13.0")
    api("androidx.preference:preference:1.2.1")
    api("androidx.work:work-runtime:2.10.3")
    // Pinned to what the AAR was compiled against: base_iap's BillingProcessor implements
    // ProductDetailsResponseListener with the 8.x signature (BillingResult, QueryProductDetailsResult).
    // On 7.x that class is missing, so R8 fails the release build and the callback never fires at
    // runtime. 9.1.0 was verified API-compatible too if Play ever forces a higher floor.
    api("com.android.billingclient:billing:8.0.0")
    api("com.google.android.gms:play-services-ads:25.2.0")
    api("com.google.android.ump:user-messaging-platform:4.0.0")
    api("com.google.ads.mediation:facebook:6.21.0.2")
    api(platform("com.google.firebase:firebase-bom:34.1.0"))
    api("com.google.firebase:firebase-analytics")
    api("com.google.firebase:firebase-crashlytics")
    api("com.google.firebase:firebase-messaging-ktx:24.1.2")
    api("com.google.firebase:firebase-firestore:26.0.2")
    api("com.google.firebase:firebase-appcheck-playintegrity:19.0.0")
    api("com.google.firebase:firebase-config:23.0.0")
    api("com.intuit.sdp:sdp-android:1.1.1")
    api("com.intuit.ssp:ssp-android:1.1.1")
    api("com.facebook.shimmer:shimmer:0.5.0")
    api("com.airbnb.android:lottie:6.6.7")
    api("androidx.media:media:1.0.0")
    api("com.appsflyer:purchase-connector:2.1.2")
    api("de.hdodenhof:circleimageview:3.1.0")
    api("com.github.castorflex.smoothprogressbar:library-circular:1.3.0")
    api("org.jsoup:jsoup:1.21.2")
    api("com.squareup.okhttp3:okhttp:5.2.1")
    api("com.akexorcist:localization:1.2.11")
    api("androidx.lifecycle:lifecycle-process:2.9.4")
    api("com.appsflyer:af-android-sdk:6.18.0")
    api("com.adjust.sdk:adjust-android:5.6.1")
    api("com.android.installreferrer:installreferrer:2.2")
    api("com.facebook.android:facebook-android-sdk:18.1.3")
    api("com.github.bumptech.glide:glide:4.16.0")
    api("com.github.ybq:Android-SpinKit:1.4.0")
    api(platform("androidx.compose:compose-bom:2024.12.01"))
    api("androidx.compose.ui:ui")
    api("androidx.compose.foundation:foundation")
    api("androidx.compose.material3:material3")
    api("androidx.activity:activity-compose:1.9.3")
    api("com.airbnb.android:lottie-compose:6.6.7")
    // ────────────────────────────────────────────────────────────────────────────────────────
    
    // KeyVault — encrypted API-key store on a static CDN, unlocked by the content-key baked into
    // the app. secretbox XSalsa20-Poly1305 via libsodium; the native .so ships inside the AAR.
    implementation("com.goterl:lazysodium-android:5.1.0@aar")
    implementation("net.java.dev.jna:jna:5.14.0@aar")
    implementation(libs.kotlinx.coroutines.android)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.navigation.compose)

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}