plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.arendon.sudokumaster"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.arendon.sudokumaster"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("permanent") {
            storeFile = file(System.getenv("SIGNING_STORE_FILE") ?: "keystore/sudoku-master.p12")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: "sudokumaster123"
            keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "sudoku_master"
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: "sudokumaster123"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("permanent")
        }
        release {
            signingConfig = signingConfigs.getByName("permanent")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.mlkit.digital.ink)

    testImplementation(libs.junit)
    debugImplementation(libs.androidx.ui.tooling)
}
