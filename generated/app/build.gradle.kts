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
        versionCode = 1
        versionName = "1.0.0"
    }
    // This repository contains other Android prototypes under the same folder.
    // Compile only the DIST Grabber source tree for this standalone application.
    sourceSets {
        getByName("main") {
            java {
                setSrcDirs(listOf("src/main/java"))
                include("com/rui/aibuilder/**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
