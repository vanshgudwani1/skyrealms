plugins {
    `java-library`
}

dependencies {
    api(project(":platform:api"))

    compileOnly("com.zaxxer:HikariCP:${property("hikariVersion")}")
    compileOnly("org.flywaydb:flyway-core:${property("flywayVersion")}")
    compileOnly("org.slf4j:slf4j-api:${property("slf4jVersion")}")
    compileOnly("org.jetbrains:annotations:26.0.2")
}
