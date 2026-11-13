plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.orangeshield.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.orangeshield.app"
        minSdk = 29
        targetSdk = 34
        versionCode = 5
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            // Chave de assinatura do projeto (commitada de propósito: projeto
            // pessoal — permite rebuilds e atualizações instaláveis por cima.
            // Troque por uma chave privada sua antes de publicar em loja.
            storeFile = file("../orangeshield-release.keystore")
            storePassword = "OrangeShield2024!"
            keyAlias = "orangeshield"
            keyPassword = "OrangeShield2024!"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Corrotinas
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
