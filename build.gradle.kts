import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.4.30" apply false
    id("com.github.ben-manes.versions") version "0.36.0" apply false
}

group = "io.nekohasekai"
version = "1.0-SNAPSHOT"

allprojects {
    repositories {
        mavenCentral()
        jcenter()
        google()
    }

    apply(plugin = "com.github.ben-manes.versions")
    apply(plugin = "org.jetbrains.kotlin.jvm")

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "11"
            useIR = true
        }
    }
}