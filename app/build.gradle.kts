plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.localupload"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.localupload"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
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
        compose      = true
        buildConfig  = true   // required for BuildConfig.DEBUG used in RetrofitClient
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    // Retrofit + OkHttp — multipart upload to HTTP server
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)

    // Coil — load images from URI / local file path
    implementation(libs.coil.compose)

    // Material 1 — pull-to-refresh in PhotoPickerScreen
    implementation(libs.androidx.compose.material)

    // Gson — JSON serialization in RemoteDataSource
    implementation(libs.gson)

    // Coroutines — async upload operations
    implementation(libs.kotlinx.coroutines.android)

    // WorkManager — background upload that survives app kill and device reboot
    implementation(libs.work.runtime.ktx)

    debugImplementation(libs.androidx.ui.tooling)
}
