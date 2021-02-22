plugins {
    application
    distribution
}

application {
    applicationName = "main"
    mainClass.set("io.nekohasekai.pm.TdPmBot")
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
}

dependencies {
    val vKtLib = "1.0-SNAPSHOT"
    implementation("io.nekohasekai.ktlib:ktlib-td-cli:$vKtLib")
    implementation("io.nekohasekai.ktlib:ktlib-compress:$vKtLib")
    implementation("io.nekohasekai.ktlib:ktlib-db:$vKtLib")
    implementation("io.nekohasekai.ktlib:ktlib-td-http-api:$vKtLib")

    implementation("org.slf4j:slf4j-nop:2.0.0-alpha1")
}