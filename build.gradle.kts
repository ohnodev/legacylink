plugins {
    id("net.fabricmc.fabric-loom") version "1.15-SNAPSHOT"
    id("java")
}

val minecraft_version: String by project
val fabric_version: String by project
val loader_version: String by project

group = "dev.ohno"
version = "0.1.1"

repositories {
    maven("https://maven.fabricmc.net/")
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraft_version")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabric_version")
    implementation("net.fabricmc:fabric-loader:$loader_version")
    val mixinExtras = "io.github.llamalad7:mixinextras-fabric:0.5.3"
    annotationProcessor(mixinExtras)
    implementation(include(mixinExtras)!!)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
}

loom {
    accessWidenerPath = file("src/main/resources/legacylink.accesswidener")
}

tasks.jar {
    archiveBaseName.set("legacylink")
}

tasks.processResources {
    inputs.property("version", version)
    filesMatching("fabric.mod.json") {
        expand(mapOf("version" to version))
    }
}
