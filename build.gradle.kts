plugins {
    kotlin("jvm") version "1.5.0"
    id("com.github.johnrengelman.shadow") version "7.0.0"
    id("java")
}

group = "me.fru1t.sddumper"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("com.google.guava:guava:30.1.1-jre")
}

val jar by tasks.getting(Jar::class) {
    manifest {
        attributes["Main-Class"] = "me.fru1t.sddumper.SDDumperApplicationMain"
    }
}
