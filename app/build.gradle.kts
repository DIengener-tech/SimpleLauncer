plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.simplelauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.simplelauncher"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.viewpager:viewpager:1.0.0")
}
