plugins {
    kotlin("jvm") version "2.0.20"
    java
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"

}

group = "de.matul.rephrasor"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("com.cjcrafter:openai:2.1.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("de.matul.rephrasor.MainWindowKt")
}