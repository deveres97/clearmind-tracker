plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.clearmind.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.clearmind.app"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNumber
        versionName = "4.0.$buildNumber"
    }

    // One fixed key so every build installs over the previous one.
    signingConfigs {
        create("shared") {
            storeFile = rootProject.file("clearmind.jks")
            storePassword = "clearmind"
            keyAlias = "clearmind"
            keyPassword = "clearmind"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
        debug { signingConfig = signingConfigs.getByName("shared") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint { checkReleaseBuilds = false; abortOnError = false }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.webkit:webkit:1.11.0")
}
