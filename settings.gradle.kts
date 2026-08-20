plugins {
    // Lets Gradle auto-provision the JDK the toolchain asks for (Java 21)
    // on machines that only have an older local JDK.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "exati-itg"
