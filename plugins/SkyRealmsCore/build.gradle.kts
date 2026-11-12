plugins {
    `java-library`
}

dependencies {
    implementation(project(":platform:api"))
    implementation(project(":platform:common"))
    implementation(project(":platform:database-api"))
    implementation(project(":platform:integrations-api"))

    compileOnly("io.papermc.paper:paper-api:${property("paperApiVersion")}")
    compileOnly("org.slf4j:slf4j-api:${property("slf4jVersion")}")
    compileOnly("org.jetbrains:annotations:26.0.2")

    testImplementation(project(":platform:testing"))
    testImplementation("io.papermc.paper:paper-api:${property("paperApiVersion")}")
}

tasks.jar {
    archiveBaseName.set("SkyRealmsCore")
    archiveVersion.set("1.0.0")
    archiveClassifier.set("")

    // Bundle internal platform module classes into Core runtime JAR
    from(project(":platform:api").sourceSets.main.get().output)
    from(project(":platform:common").sourceSets.main.get().output)
    from(project(":platform:database-api").sourceSets.main.get().output)
    from(project(":platform:integrations-api").sourceSets.main.get().output)
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
