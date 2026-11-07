plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Tanda tangan: jika secret keystore tersedia (GitHub Actions menyediakannya lewat environment),
// semua build memakai keystore yang SAMA sehingga APK baru bisa menimpa instalasi lama.
// Tanpa secret, build memakai debug keystore bawaan (hanya untuk uji lokal).
val ksPath: String? = System.getenv("KEYSTORE_FILE")
val hasReleaseKeystore = !ksPath.isNullOrBlank() && file(ksPath).exists() &&
    !System.getenv("KEYSTORE_PASSWORD").isNullOrEmpty() &&
    !System.getenv("KEY_ALIAS").isNullOrEmpty() &&
    !System.getenv("KEY_PASSWORD").isNullOrEmpty()

android {
    namespace = "com.senzaproject.screenrecorder"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.senzaproject.screenrecorder"
        minSdk = 29
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("senza") {
                storeFile = file(ksPath!!)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            if (hasReleaseKeystore) signingConfig = signingConfigs.getByName("senza")
        }
        release {
            isMinifyEnabled = false
            // Tanpa keystore secret: pakai debug key agar APK release tetap bisa diinstal untuk uji.
            signingConfig = if (hasReleaseKeystore) signingConfigs.getByName("senza")
            else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    lint {
        // Laporan lint dihasilkan dan diunggah oleh workflow; tidak menggagalkan build.
        abortOnError = false
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
