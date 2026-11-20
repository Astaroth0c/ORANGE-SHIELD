plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.orangeshield.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.orangeshield.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 21
        versionName = "2.11.0"
    }

    val signingStoreFile = providers.environmentVariable("ORANGE_SIGNING_STORE_FILE").orNull
    val signingStorePassword = providers.environmentVariable("ORANGE_SIGNING_STORE_PASSWORD").orNull
    val signingKeyAlias = providers.environmentVariable("ORANGE_SIGNING_KEY_ALIAS").orNull
    val signingKeyPassword = providers.environmentVariable("ORANGE_SIGNING_KEY_PASSWORD").orNull

    if (!signingStoreFile.isNullOrBlank() && !signingStorePassword.isNullOrBlank() && !signingKeyAlias.isNullOrBlank() && !signingKeyPassword.isNullOrBlank()) {
        signingConfigs {
            create("release") {
                storeFile = file(signingStoreFile)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (!signingStoreFile.isNullOrBlank() && !signingStorePassword.isNullOrBlank() && !signingKeyAlias.isNullOrBlank() && !signingKeyPassword.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.1")
    implementation("androidx.room:room-runtime:2.7.0")
    implementation("androidx.room:room-ktx:2.7.0")
    ksp("androidx.room:room-compiler:2.7.0")
    implementation("com.googlecode.libphonenumber:libphonenumber:9.0.43")
    implementation("com.googlecode.libphonenumber:geocoder:3.43")
    implementation("com.googlecode.libphonenumber:carrier:2.43")
    testImplementation("junit:junit:4.13.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
