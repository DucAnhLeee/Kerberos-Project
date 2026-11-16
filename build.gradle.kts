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

tasks.register<JavaExec>("runSingle") {
    group = "application"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.ADuck.DemoLauncher")
}

val desktopTest = sourceSets.create("desktopTest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

val desktopRegressionTest = tasks.register<JavaExec>("desktopRegressionTest") {
    group = "verification"
    description = "Checks validation, server lifecycle and the Swing authentication workflow when a display is available."
    dependsOn(tasks.named(desktopTest.classesTaskName))
    classpath = desktopTest.runtimeClasspath
    mainClass.set("org.ADuck.DesktopRegressionTest")
}

tasks.test {
    dependsOn(desktopRegressionTest)
}

tasks.check {
    dependsOn(desktopRegressionTest)
}
