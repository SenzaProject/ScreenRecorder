plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.senza.spphotos"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.senza.spphotos"
        minSdk = 29
        targetSdk = 34
        versionCode = 8
        versionName = "1.7"
    }

    // Keystore tetap (ikut di repo) supaya tanda tangan APK selalu sama di setiap build
    // → update bisa dipasang langsung di atas versi sebelumnya tanpa "paket bentrok".
    signingConfigs {
        create("sp") {
            storeFile = file("spphotos.jks")
            storePassword = "spphotos123"
            keyAlias = "spphotos"
            keyPassword = "spphotos123"
        }
    }
    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("sp") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("sp")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("io.coil-kt:coil-video:2.6.0")
    // Editor video: pemutar, efek, dan ekspor (Media3)
    implementation("androidx.media3:media3-common:1.3.1")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-effect:1.3.1")
    implementation("androidx.media3:media3-transformer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
}
