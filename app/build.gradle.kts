plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.aryan.folderforge"
    compileSdk = 35

    buildFeatures {
        aidl = false
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.aryan.folderforge"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
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
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
