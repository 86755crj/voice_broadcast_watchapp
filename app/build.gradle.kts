plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.crj.voicebroadcast"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.crj.voicebroadcast"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        // Universal APK：手表只支持 armeabi-v7a，但我们打 universal（不做 splits）
        // 这样 ARM 平板/模拟器也能装上调试，同时 v7a 必然包含
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    // 关键：禁用 ABI splits，强制 universal APK
    splits {
        abi {
            isEnable = false
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
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
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*"
            )
        }
    }
}

dependencies {
    // Wear OS Compose
    implementation("androidx.wear.compose:compose-material:1.4.0")
    implementation("androidx.wear.compose:compose-foundation:1.4.0")
    implementation("androidx.wear.compose:compose-navigation:1.4.0")

    // Compose 基础
    implementation("androidx.compose.ui:ui:1.7.5")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.5")
    debugImplementation("androidx.compose.ui:ui-tooling:1.7.5")

    // Activity Compose
    implementation("androidx.activity:activity-compose:1.9.3")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // OkHttp（拉 RSS + 下载 mp3）
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Media3 / ExoPlayer (Wear OS 6 兼容)
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")

    // Room（Episode 状态持久化）
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // WorkManager（每日 sync）
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Wear features
    implementation("androidx.wear:wear:1.3.0")
}
