import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    distribution
    kotlin("jvm") version "1.4.21"
    id("com.github.ben-manes.versions") version "0.36.0"
}

apply(plugin = "io.nekohasekai.proguard-jar")

buildscript {
    repositories {
        mavenCentral()
        jcenter()
    }
    dependencies {
        classpath("io.nekohasekai:proguard:1.0-SNAPSHOT")
    }
}

repositories {
    mavenCentral()
    jcenter()
    google()
}

group = "io.nekohasekai"
version = "1.0-SNAPSHOT"

application {
    applicationName = "TdPmBot"
    mainClass.set("io.nekohasekai.pm.TdPmBot")
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2")

    val vKtLib = "1.0-SNAPSHOT"
    implementation("io.nekohasekai.ktlib:ktlib-td-cli:$vKtLib")
    implementation("io.nekohasekai.ktlib:ktlib-compress:$vKtLib")
    implementation("io.nekohasekai.ktlib:ktlib-db:$vKtLib")
    implementation("io.nekohasekai.ktlib:ktlib-td-http-api:$vKtLib")

    implementation("org.slf4j:slf4j-nop:2.0.0-alpha1")

}