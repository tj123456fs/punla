plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.uplb.punla"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.uplb.punla"
        minSdk = 26
        targetSdk = 34
        versionCode = 17
        versionName = "2.6"
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    // Explicit pin: activity-compose/navigation-compose pull in an old transitive
    // Fragment version that predates the ActivityResult APIs used in MainActivity.
    implementation("androidx.fragment:fragment-ktx:1.8.2")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Glance (home screen widgets)
    implementation("androidx.glance:glance-appwidget:1.1.0")
    implementation("androidx.glance:glance-material3:1.1.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // DataStore (used by widgets for lightweight shared state)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Locate-me / nearest-building (Campus Map screen)
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Custom theme accent color — wraps Google's Material Color Utilities
    // (HCT algorithm) to derive a full, contrast-safe light+dark scheme from
    // a single seed color. Powers the "Custom" theme preset.
    implementation("com.materialkolor:material-kolor:1.7.0")

    // In-app interactive campus map — MapLibre + OpenFreeMap vector tiles,
    // no API key needed. Using the *-opengl artifact deliberately: as of
    // 11.x the plain android-sdk artifact defaults to a Vulkan renderer,
    // which is riskier across the range of Android versions/emulators a
    // student's phone might be running. OpenGL ES is the safer default.
    implementation("org.maplibre.gl:android-sdk-opengl:11.8.6")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")

    // Firebase Cloud Messaging (background push for imminent deadlines/classes).
    // Pinned to 33.1.2 deliberately: newer BOMs (34.x) pull in play-services-measurement
    // jars built with Kotlin metadata 2.2.0, which this project's Kotlin 1.9.24 plugin
    // can't read (kspDebugKotlin fails with "incompatible version of Kotlin"). We also
    // don't need Analytics for push, so firebase-analytics was dropped entirely rather
    // than chasing a matching Kotlin/AGP upgrade just for it.
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-messaging-ktx")
}
