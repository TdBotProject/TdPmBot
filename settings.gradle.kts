rootProject.name = "TdPmBot"

includeBuild("ktlib") {
    dependencySubstitution {
        fun include(name: String) = substitute(module("io.nekohasekai.ktlib:$name")).with(project(":$name"))
        include("ktlib-td-cli")
        include("ktlib-compress")
        include("ktlib-db")
        include("ktlib-td-http-api")
    }
}

include(":bot")