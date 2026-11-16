plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ma.sanai3i.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "ma.sanai3i.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            val ks = System.getenv("KEYSTORE_FILE")
            if (ks != null) {
                storeFile = file(ks)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // 10.0.2.2 is the host computer as seen from the Android emulator (mvn spring-boot:run on port 8080).
            // On a real phone use your computer's LAN IP, e.g. http://192.168.1.20:8080/
            buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8080/app/\"")
            manifestPlaceholders["usesCleartext"] = "true"
        }
        release {
            // TODO: put the public HTTPS address of your server here before publishing.
            buildConfigField("String", "BASE_URL", "\"https://snai3i.elguennouni.site/app/\"")
            manifestPlaceholders["usesCleartext"] = "false"
            isMinifyEnabled = false
            if (System.getenv("KEYSTORE_FILE") != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.2")
}
