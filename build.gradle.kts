import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.3.20"
    id("com.gradleup.shadow") version "9.4.1"
    id("org.jlleitschuh.gradle.ktlint") version "14.0.1"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.formdev:flatlaf:3.7.1")
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        freeCompilerArgs.add("-Xjdk-release=17")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

application {
    mainClass = "napoleon.app.MainKt"
}

tasks.register<JavaExec>("runRegression") {
    group = "verification"
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "napoleon.regression.RegressionKt"
}

tasks.register<JavaExec>("runAiStats") {
    group = "verification"
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "napoleon.stats.AiMatchStatsKt"
}

tasks.register<JavaExec>("regenerateImages") {
    group = "build setup"
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "napoleon.ui.ImageGeneratorKt"
}
