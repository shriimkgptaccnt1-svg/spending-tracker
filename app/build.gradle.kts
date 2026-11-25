plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("app.cash.paparazzi")
}

android {
    namespace = "com.smsledger"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.smsledger"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.2"
    }

    signingConfigs {
        // Release signing key is kept OUT of the repo. Provide it via environment variables:
        // SMSLEDGER_KEYSTORE (path), SMSLEDGER_KEYSTORE_PASSWORD, SMSLEDGER_KEY_ALIAS, SMSLEDGER_KEY_PASSWORD.
        create("release") {
            val ks = System.getenv("SMSLEDGER_KEYSTORE")
            if (ks != null) {
                storeFile = file(ks)
                storePassword = System.getenv("SMSLEDGER_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SMSLEDGER_KEY_ALIAS")
                keyPassword = System.getenv("SMSLEDGER_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (System.getenv("SMSLEDGER_KEYSTORE") != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}

// Screenshot tests save full-size images with java.awt, which Android compilation hides by default.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().matching { it.name.contains("UnitTest") }.configureEach {
    compilerOptions.noJdk.set(false)
}
