plugins {
    `java-library`
}

dependencies {
    compileOnly(project(":platform:api"))
    compileOnly(project(":platform:common"))
    compileOnly(project(":platform:integrations-api"))
    compileOnly(project(":plugins:SkyRealmsPlayer"))
    compileOnly(project(":plugins:SkyRealmsEconomy"))
    compileOnly(project(":plugins:SkyRealmsIslands"))
    compileOnly(project(":plugins:SkyRealmsFarming"))
    compileOnly(project(":plugins:SkyRealmsPets"))

    compileOnly("io.papermc.paper:paper-api:${property("paperApiVersion")}")
    compileOnly("org.slf4j:slf4j-api:${property("slf4jVersion")}")
    compileOnly("org.jetbrains:annotations:26.0.2")
    compileOnly(files(rootProject.file("../plugins/PlaceholderAPI-2.12.3.jar")))
    compileOnly(files(rootProject.file("../plugins/Vault.jar")))
    compileOnly(files(rootProject.file("../plugins/mcMMO.jar")))

    testImplementation(project(":platform:testing"))
    testImplementation(project(":platform:common"))
    testImplementation(project(":platform:integrations-api"))
    testImplementation(project(":plugins:SkyRealmsPlayer"))
    testImplementation(project(":plugins:SkyRealmsEconomy"))
    testImplementation(project(":plugins:SkyRealmsIslands"))
    testImplementation(project(":plugins:SkyRealmsFarming"))
    testImplementation("io.papermc.paper:paper-api:${property("paperApiVersion")}")
    testImplementation(files(rootProject.file("../plugins/PlaceholderAPI-2.12.3.jar")))
    testImplementation(files(rootProject.file("../plugins/Vault.jar")))
    testImplementation(files(rootProject.file("../plugins/mcMMO.jar")))
}

tasks.jar {
    archiveBaseName.set("SkyRealmsPlaceholders")
    archiveVersion.set("1.0.0")
    archiveClassifier.set("")
}

val deployToServer by tasks.registering(Copy::class) {
    description = "Copies the compiled plugin JAR directly to the server plugins folder"
    group = "deployment"
    doNotTrackState("Prevents scanning locked server files")
    dependsOn(tasks.jar)
    from(tasks.jar.flatMap { it.archiveFile })
    into(rootProject.file("../plugins"))
}

tasks.build {
    finalizedBy(deployToServer)
}

