import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.0.1"
    id("io.spring.dependency-management") version "1.1.0"
    kotlin("jvm") version "1.8.0"
    kotlin("plugin.spring") version "1.8.0"
    java
    application
    id("com.google.cloud.tools.jib") version "3.2.0"
}

group = "solutions.wisely"
version = "SNAPSHOT"

java.sourceCompatibility = JavaVersion.VERSION_17
repositories {
    mavenCentral()
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-Xjsr305=strict"
    }
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    implementation("org.springframework.boot:spring-boot-starter-web")

    implementation("net.corda:corda-node-api:4.9") {
        exclude(group = "co.paralleluniverse", module = "quasar-core")
    }

    implementation("org.bouncycastle:bcprov-jdk15on:1.64")
    implementation("org.bouncycastle:bcpkix-jdk15on:1.64")



    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.3")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.15.3")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
    testImplementation("io.kotest:kotest-assertions-core-jvm:5.0.0")
    testImplementation("io.kotest:kotest-runner-junit5-jvm:5.0.0")
}

application {
    mainClass = "solutions.wisely.corda.nms.MainKt"
    applicationDefaultJvmArgs = listOf(
        "--add-opens","java.base/java.time=ALL-UNNAMED",
        "--add-opens","java.base/java.io=ALL-UNNAMED"
    )
}

jib {
    from {
        image = "openjdk:17-jdk-slim"
    }
    to {
        image = "wiselylda/corda-network-map:4.9"
    }
    container {
        entrypoint = listOf("java",
            "-cp", "/app/resources:/app/classes:/app/libs/*",
            "--add-opens","java.base/java.time=ALL-UNNAMED",
            "--add-opens","java.base/java.io=ALL-UNNAMED",
            "solutions.wisely.corda.nms.MainKt")
        ports = listOf("8080")
        environment = mapOf("JAVA_OPTS" to "-Xmx512m")
    }
}

tasks.test {
    useJUnitPlatform()
}