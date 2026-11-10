plugins {
    `java-library`
}

dependencies {
    compileOnly(project(":platform:api"))
    compileOnly(project(":platform:common"))
    compileOnly(project(":platform:integrations-api"))

    compileOnly("io.papermc.paper:paper-api:${property("paperApiVersion")}")
    compileOnly("org.slf4j:slf4j-api:${property("slf4jVersion")}")
    compileOnly("org.jetbrains:annotations:26.0.2")

    testImplementation(project(":platform:testing"))
    testImplementation(project(":platform:common"))
    testImplementation(project(":platform:integrations-api"))
    testImplementation("io.papermc.paper:paper-api:${property("paperApiVersion")}")
}

tasks.jar {
    archiveBaseName.set("SkyRealmsItems")
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

