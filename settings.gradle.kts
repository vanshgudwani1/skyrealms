rootProject.name = "skyrealms"

// Platform shared libraries
include("platform:api")
include("platform:common")
include("platform:database-api")
include("platform:integrations-api")
include("platform:testing")

// SkyRealms Plugin Modules
include("plugins:SkyRealmsCore")
include("plugins:SkyRealmsDatabase")
include("plugins:SkyRealmsPlayer")
include("plugins:SkyRealmsEconomy")
include("plugins:SkyRealmsIslands")
include("plugins:SkyRealmsFarming")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://oss.sonatype.org/content/groups/public/")
        maven("https://jitpack.io")
    }
}
