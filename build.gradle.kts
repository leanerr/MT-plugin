plugins {
    id("java")
    kotlin("jvm") version "1.9.25"
    id("org.jetbrains.intellij") version "1.17.4"   // keep if you still build the plugin
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Kotlin runtime (fixes NoClassDefFoundError: kotlin/jvm/internal/Intrinsics)
    implementation(kotlin("stdlib"))

    // Parsing + symbol solving for Java
    implementation("com.github.javaparser:javaparser-core:3.25.8")
    implementation("com.github.javaparser:javaparser-symbol-solver-core:3.25.8")

    // Git ops + JSON
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.9.0.202403050737-r")
    implementation("org.json:json:20240303")

    // Silence SLF4J binding warning at runtime
    runtimeOnly("org.slf4j:slf4j-nop:2.0.13")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

application {
    mainClass.set("mtspark.MainKt")
    // Optional: keep things predictable
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8")
}

intellij {
    version.set("2024.1.7")
    type.set("IC")
    plugins.set(listOf("java"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

tasks.withType<Test> {
    useJUnitPlatform()
}