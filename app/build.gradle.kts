plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.shogun.speedtest"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.shogun.speedtest"
        minSdk = 26
        targetSdk = 34
        versionCode = 7
        versionName = "1.1.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
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
        viewBinding = true
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
    applicationVariants.all {
        outputs.all {
            (this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl)
                ?.outputFileName = "speed_test_monitor-v${versionName}.apk"
        }
    }
}

dependencies {
    // Jetpack Compose（UI）
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // AppCompat + ConstraintLayout (View Binding用)
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // WorkManager（定期実行・バックグラウンド管理）
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Room（ローカルDB・リトライキュー）
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // OkHttp（InfluxDB v2 API送信）
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Gson（CLI jsonl出力パース）
    implementation("com.google.code.gson:gson:2.11.0")

    // USB Serial（ATコマンド送信）
    implementation("com.github.mik3y:usb-serial-for-android:3.7.3")

    // 認証情報暗号化（InfluxDB Token管理）
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Google Play Services Location（GPS取得）
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

}
