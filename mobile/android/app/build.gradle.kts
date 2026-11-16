plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Push notifications: put the google-services.json of your Firebase project in mobile/android/app/
// (CI: secret GOOGLE_SERVICES_JSON). Without it the app builds and works, only push is off.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "ma.sanai3i.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "ma.sanai3i.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.2"
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
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-messaging")
}
