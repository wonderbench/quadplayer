pluginManagement {
    repositories {
        if (providers.gradleProperty("useChinaMirrors").orNull == "true") {
            maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/central")
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (providers.gradleProperty("useChinaMirrors").orNull == "true") {
            maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/central")
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "QuadPlayer"
include(":app")
