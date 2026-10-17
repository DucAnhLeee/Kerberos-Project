plugins {
    id("java")
}

group = "org.ADuck"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

tasks.register<JavaExec>("runClient") {
    group = "application"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.ADuck.ClientForm")
}

tasks.register<JavaExec>("runServiceServer") {
    group = "application"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.ADuck.ServiceServerForm")
}

tasks.register<JavaExec>("runKdcAdmin") {
    group = "application"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.ADuck.KDCAdminForm")
}
