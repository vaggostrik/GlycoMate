plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.glycomate.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.glycomate.wear"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        compose = true
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
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.runtime)
    implementation(libs.activity.compose)
    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.watchface.complications.data)
    implementation(libs.wear.watchface.complications.datasource)
    implementation(libs.wear.watchface.complications.datasource.ktx)
    implementation(libs.play.services.wearable)
    implementation(libs.datastore)
    implementation(libs.coroutines)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.runtime.compose)
}
