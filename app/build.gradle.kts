import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "id.web.izs.nettools"
    compileSdk = 37

    // Release keystore: local `keystore.properties` (gitignored) or CI env
    // (KEYSTORE_FILE + KEYSTORE_PASSWORD + KEY_ALIAS + KEY_PASSWORD).
    // Absent = unsigned release, build still succeeds.
    signingConfigs {
        create("release") {
            val props = Properties()
            val propFile = rootProject.file("keystore.properties")
            if (propFile.exists()) propFile.inputStream().use { props.load(it) }
            val rawPath = props.getProperty("storeFile") ?: System.getenv("KEYSTORE_FILE")
            // Blank or missing file = unsigned release, build still succeeds.
            val keyFile = rawPath.takeUnless { it.isNullOrBlank() }?.let { file(it) }?.takeIf { it.exists() }
            if (keyFile != null) {
                storeFile = keyFile
                storePassword = props.getProperty("storePassword") ?: System.getenv("KEYSTORE_PASSWORD")
                keyAlias = props.getProperty("keyAlias") ?: System.getenv("KEY_ALIAS")
                keyPassword = props.getProperty("keyPassword") ?: System.getenv("KEY_PASSWORD")
            }
        }
    }

    defaultConfig {
        applicationId = "id.web.izs.nettools"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Signed only when a keystore is available (local keystore.properties
            // or CI env). Otherwise the build stays unsigned — never fails.
            signingConfig = signingConfigs.findByName("release")
                ?.takeIf { it.storeFile != null }
                ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.coroutines.android)
    implementation(libs.kotlin.stdlib)
    implementation(libs.okhttp)
    implementation(libs.serialization.json)
    implementation(libs.dnsjava)

    testImplementation(libs.junit)
}
