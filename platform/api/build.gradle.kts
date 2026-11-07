plugins {
    `java-library`
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${property("paperApiVersion")}")
    compileOnly("org.slf4j:slf4j-api:${property("slf4jVersion")}")
    compileOnly("org.jetbrains:annotations:26.0.2")
}
