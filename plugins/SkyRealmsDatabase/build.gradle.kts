plugins {
    `java-library`
    id("com.gradleup.shadow") version "8.3.6"
}

dependencies {
    compileOnly(project(":platform:api"))
    compileOnly(project(":platform:common"))
    compileOnly(project(":platform:database-api"))

    // Database Driver, Pooling and Migration
    implementation("com.zaxxer:HikariCP:${property("hikariVersion")}")
    implementation("org.flywaydb:flyway-core:${property("flywayVersion")}")
    implementation("org.flywaydb:flyway-database-postgresql:${property("flywayVersion")}")
    implementation("org.postgresql:postgresql:${property("postgresqlVersion")}")
    implementation("com.h2database:h2:${property("h2Version")}")

    compileOnly("io.papermc.paper:paper-api:${property("paperApiVersion")}")
    compileOnly("org.slf4j:slf4j-api:${property("slf4jVersion")}")
    compileOnly("org.jetbrains:annotations:26.0.2")

    testImplementation(project(":platform:testing"))
    testImplementation(project(":platform:common"))
    testImplementation(project(":platform:database-api"))
}

tasks.jar {
    archiveClassifier.set("thin")
}

tasks.shadowJar {
    archiveBaseName.set("SkyRealmsDatabase")
    archiveVersion.set("1.0.0")
    archiveClassifier.set("")

    mergeServiceFiles()
}

val deployToServer by tasks.registering(Copy::class) {
    description = "Copies the compiled plugin JAR directly to the server plugins folder"
    group = "deployment"
    doNotTrackState("Prevents scanning locked server files")
    dependsOn(tasks.shadowJar)
    from(tasks.shadowJar.flatMap { it.archiveFile })
    into(rootProject.file("../plugins"))
}

tasks.build {
    dependsOn(tasks.shadowJar)
    finalizedBy(deployToServer)
}
