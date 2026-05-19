pluginManagement {
    includeBuild("../")   // local composite build — uses the plugin source directly
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "sample-app"
