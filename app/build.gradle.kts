plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.turbolego.rullut2"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.turbolego.rullut3"
        minSdk = 24
        targetSdk = 36
        // versionCode: derived from GITHUB_RUN_NUMBER in CI, falls back to 1 locally
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = System.getenv("GITHUB_REF_NAME")?.removePrefix("v") ?: "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // CI: read from environment variables (set by workflow)
            // Local: falls back to app/keystore.properties (gitignored)
            storeFile = providers.environmentVariable("STORE_FILE")
                .map { rootProject.file(it) }
                .orElse(rootProject.file("app/keystore/rullut-upload-keystore.jks"))
                .orNull
            storePassword = providers.environmentVariable("STORE_PASSWORD").orElse("").orNull
            keyAlias = providers.environmentVariable("KEY_ALIAS").orElse("").orNull
            keyPassword = providers.environmentVariable("KEY_PASSWORD")
                .orElse(providers.environmentVariable("STORE_PASSWORD"))
                .orElse("")
                .orNull
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
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        // Android framework stubs (Log, etc.) return defaults instead of
        // throwing "not mocked" in plain JVM unit tests.
        unitTests.isReturnDefaultValues = true
    }

    // Generate an AAB (Android App Bundle) by default for Play Store
    bundle {
        language {
            enableSplit = false
        }
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Activity + Lifecycle
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.10.0")

    // MapLibre Native (open-source, BSD license, no API key required)
    implementation("org.maplibre.gl:android-sdk:13.5.1")

    // OkHttp for API calls (GetFeatureInfo, routing, search)
    implementation("com.squareup.okhttp3:okhttp:5.5.0")

    // Kotlin serialization (JSON parsing for routing graph, Overpass API)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // DataStore for preferences (basemap, active layers)
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    // Core KTX
    implementation("androidx.core:core-ktx:1.19.0")

    // Google Play Services (location — FusedLocationProviderClient)
    implementation("com.google.android.gms:play-services-location:21.4.0")

    // Tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    // Real XmlPullParser implementation so parseCapabilitiesXml runs on JVM
    testImplementation("net.sf.kxml:kxml2:2.3.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:rules:1.7.0")
}

// UTP may keep a transient .lck file under connected test outputs while Gradle
// snapshots task outputs, which causes unreadable output-property failures.
tasks
    .matching { it.name.startsWith("connected") && it.name.endsWith("AndroidTest") }
    .configureEach {
        doNotTrackState("UTP lock files can make connected test output snapshots unreadable")
    }