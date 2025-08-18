plugins {
    id("java")
    application
}

group = "demo"
version = "0.1.0"

repositories { mavenCentral() }

application {
    mainClass.set("demo.App")
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}