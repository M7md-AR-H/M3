plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
}

// Network isolation: only :research may use networking libraries (docs/adr/0005).
val forbiddenNetworkGroups = setOf(
    "com.squareup.okhttp3",
    "com.squareup.retrofit2",
    "org.jsoup",
    "io.ktor",
)

subprojects {
    if (path != ":research") {
        configurations.configureEach {
            val configurationName = name
            val projectPath = path
            dependencies.configureEach {
                if (group in forbiddenNetworkGroups) {
                    throw GradleException(
                        "Network isolation violated: $group:$name declared in " +
                            "$projectPath ($configurationName). Only :research may use " +
                            "networking libraries (see docs/adr/0005).",
                    )
                }
            }
        }
    }
}
