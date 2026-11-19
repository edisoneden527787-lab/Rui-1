plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.rui.aibuilder"
    compileSdk = 36
    defaultConfig {
        applicationId = "tw.edu.distgrabber"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "2.0.0"
    }
    sourceSets {
        getByName("main") {
            java.setSrcDirs(listOf("src/main/java"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
