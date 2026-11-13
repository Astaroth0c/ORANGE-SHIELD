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
        versionCode = 8
        versionName = "1.3.0"
    }

    // Release signing is injected only by the build environment.
    // Never commit keystores or passwords to the repository.
    val signingStoreFile = providers.environmentVariable("ORANGE_SIGNING_STORE_FILE").orNull
    val signingStorePassword = providers.environmentVariable("ORANGE_SIGNING_STORE_PASSWORD").orNull
    val signingKeyAlias = providers.environmentVariable("ORANGE_SIGNING_KEY_ALIAS").orNull
    val signingKeyPassword = providers.environmentVariable("ORANGE_SIGNING_KEY_PASSWORD").orNull

    if (!signingStoreFile.isNullOrBlank() &&
        !signingStorePassword.isNullOrBlank() &&
        !signingKeyAlias.isNullOrBlank() &&
        !signingKeyPassword.isNullOrBlank()
    ) {
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
    // Compose — o BOM alinha todas as versões da UI
    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    // Ícones estendidos (Shield, PhoneInTalk, Block e Chat pertencem ao conjunto estendido)
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Activity / Lifecycle
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    // Navigation — NavHost com as 4 telas
    implementation("androidx.navigation:navigation-compose:2.8.1")

    // Room — histórico de triagem, reputação, listas pessoais e eventos WhatsApp
    implementation("androidx.room:room-runtime:2.7.0")
    implementation("androidx.room:room-ktx:2.7.0")
    ksp("androidx.room:room-compiler:2.7.0")

    // Corrotinas
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
