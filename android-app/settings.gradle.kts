// ORANGE SHIELD — configuração de build do cliente Android
pluginManagement {
    repositories {
        google()
        // Maven Central pelo endpoint canônico (repo1) — repo.maven.apache.org
        // responde 429 em IPs compartilhados. Aliyun como espelho de reserva.
        maven("https://repo1.maven.org/maven2")
        maven("https://maven.aliyun.com/repository/public")
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        maven("https://repo1.maven.org/maven2")
        maven("https://maven.aliyun.com/repository/public")
    }
}

rootProject.name = "ORANGE-SHIELD"
include(":app")
