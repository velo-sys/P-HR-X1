import java.util.Properties
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "de.y2k.player"
    compileSdk = 35
    defaultConfig {
        applicationId = "de.y2k.player"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "0.8"
    }
    // Signatur: Zugangsdaten aus keystore.properties (nicht im Repo). Fehlt sie, wird mit dem Debug-Schlüssel signiert.
    val ksFile = rootProject.file("keystore.properties")
    val ks = Properties().apply { if (ksFile.exists()) ksFile.inputStream().use { load(it) } }
    signingConfigs {
        create("local") {
            if (ks.isNotEmpty()) {
                storeFile = rootProject.file(ks.getProperty("storeFile")); storePassword = ks.getProperty("storePassword")
                keyAlias = ks.getProperty("keyAlias"); keyPassword = ks.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = if (ks.isNotEmpty()) signingConfigs.getByName("local") else signingConfigs.getByName("debug")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
}
