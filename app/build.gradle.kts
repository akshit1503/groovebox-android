plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.groovebox.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.groovebox.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")

    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Media3 / ExoPlayer — playback engine + MediaSession (lock-screen / notification controls)
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-session:1.3.1")
    implementation("androidx.media3:media3-common:1.3.1")

    // Room — local library / playlists / likes database
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    // DataStore — lightweight settings (EQ, speed, sleep timer, linked folder URIs)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("androidx.documentfile:documentfile:1.0.1")

    // Palette — extracts a dominant/vibrant color from album art so the Full Player's
    // light background and accents can follow the current track's cover, the same way
    // PixelPlay and most modern players color their "Now Playing" screen.
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Reads EXIF orientation from picked gallery photos — phone camera photos are
    // very often stored "sideways" in raw pixel data with an EXIF tag telling
    // viewers how to rotate them for display; BitmapFactory ignores that tag
    // entirely, which is why a picked playlist cover could come out rotated.
    implementation("androidx.exifinterface:exifinterface:1.3.7")
}
