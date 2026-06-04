plugins {
    kotlin("jvm")
    application
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    google()
}

dependencies {
    // CloudStream runtime used by providers in this repo
    implementation("com.github.recloudstream.cloudstream:library:-SNAPSHOT")

    // Common libs used by providers
    implementation("org.jsoup:jsoup:1.21.2")
    implementation("com.github.Blatzar:NiceHttp:0.4.13")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
}

application {
    mainClass.set("com.yourorg.ProviderTesterMainKt")
}

kotlin {
    jvmToolchain(17)
}

