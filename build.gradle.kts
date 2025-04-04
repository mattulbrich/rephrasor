import java.text.SimpleDateFormat
import java.util.Date

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

tasks.register("generateVersionResource") {
    val outputDir = file("${layout.buildDirectory}/generated/resources")
    val outputFile = file("$outputDir/version.txt")

    doLast {
        outputDir.mkdirs()
        val currentDate = SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date())
        outputFile.writeText(currentDate)
    }
}

tasks.processResources {
    dependsOn("generateVersionResource")
    from("${layout.buildDirectory}/generated/resources") {
        into("")
    }
 }