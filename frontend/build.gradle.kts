plugins {
    kotlin("multiplatform") version "1.9.0"
    kotlin("plugin.serialization") version "1.9.0"
    id("org.jetbrains.compose") version "1.5.0"
}

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

kotlin {
    jvm("desktop") {
        compilations.all {
            kotlinOptions.jvmTarget = "11"
        }
    }

    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("io.ktor:ktor-client-core:2.3.5")
                implementation("io.ktor:ktor-client-cio:2.3.5")
                implementation("io.ktor:ktor-client-content-negotiation:2.3.5")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.5")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.quizapp.MainKt"

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
            )
            packageName = "QuizApp"
            packageVersion = "1.0.0"
        }
    }
}
