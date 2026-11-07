plugins {
    `java-library`
}

dependencies {
    api(project(":platform:api"))

    api(platform("org.junit:junit-bom:${property("junitVersion")}"))
    api("org.junit.jupiter:junit-jupiter")
    api("org.assertj:assertj-core:${property("assertjVersion")}")
    api("org.mockito:mockito-core:${property("mockitoVersion")}")
    api("org.mockito:mockito-junit-jupiter:${property("mockitoVersion")}")
}
