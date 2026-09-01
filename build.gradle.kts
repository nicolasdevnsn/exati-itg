plugins {
    java
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.exati"
version = "0.0.1-SNAPSHOT"
description = "Exati ITG REST API"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

extra["springdocVersion"] = "2.7.0"
extra["jjwtVersion"]      = "0.12.6"
extra["sshdVersion"]      = "2.14.0"
extra["bouncyVersion"]    = "1.79"

dependencies {
    // ── Web ─────────────────────────────────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // ── Security ────────────────────────────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("io.jsonwebtoken:jjwt-api:${property("jjwtVersion")}")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:${property("jjwtVersion")}")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:${property("jjwtVersion")}")

    // ── Persistence ─────────────────────────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("com.h2database:h2")

    // ── SIP ticket mirror (dev env only): SSH tunnel + remote MySQL ─────
    implementation("org.apache.sshd:sshd-core:${property("sshdVersion")}")
    // PKCS#1 PEM key support for sshd (the dev VM key is BEGIN RSA PRIVATE KEY)
    implementation("org.bouncycastle:bcpkix-jdk18on:${property("bouncyVersion")}")
    runtimeOnly("com.mysql:mysql-connector-j")

    // ── Ops ─────────────────────────────────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")

    // ── API documentation (auto-generated Swagger UI) ───────────────────
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${property("springdocVersion")}")

    // ── Lombok (entities, services with logger). DTOs use Java records. ─
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // ── Dev experience ──────────────────────────────────────────────────
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // ── Test ────────────────────────────────────────────────────────────
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("exati-itg.jar")
}
