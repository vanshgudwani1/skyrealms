plugins {
    `java-library`
}

dependencies {
    compileOnly(project(":platform:api"))
    compileOnly(project(":platform:common"))
    compileOnly(project(":platform:database-api"))
    compileOnly(project(":platform:integrations-api"))
    compileOnly(project(":plugins:SkyRealmsPlayer"))
    compileOnly(project(":plugins:SkyRealmsEconomy"))
    compileOnly(project(":plugins:SkyRealmsItems"))

    compileOnly("io.papermc.paper:paper-api:${property("paperApiVersion")}")
    compileOnly("org.slf4j:slf4j-api:${property("slf4jVersion")}")
    compileOnly("org.jetbrains:annotations:26.0.2")

    testImplementation(project(":platform:testing"))
    testImplementation(project(":platform:common"))
    testImplementation(project(":platform:database-api"))
    testImplementation(project(":platform:integrations-api"))
    testImplementation(project(":plugins:SkyRealmsDatabase"))
    testImplementation(project(":plugins:SkyRealmsPlayer"))
    testImplementation(project(":plugins:SkyRealmsEconomy"))
    testImplementation(project(":plugins:SkyRealmsItems"))
    testImplementation("io.papermc.paper:paper-api:${property("paperApiVersion")}")
    testImplementation("org.flywaydb:flyway-core:${property("flywayVersion")}")
    testImplementation("org.flywaydb:flyway-database-postgresql:${property("flywayVersion")}")
    testImplementation("com.h2database:h2:${property("h2Version")}")
    testImplementation("com.zaxxer:HikariCP:${property("hikariVersion")}")
}

tasks.jar {
    archiveBaseName.set("SkyRealmsEnd")
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

