plugins {
    id("java")
    id("application")
    id("com.palantir.graal") version "0.12.0"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("info.picocli:picocli:4.6.3")
    implementation("org.apache.commons:commons-lang3:3.12.0")

    annotationProcessor("info.picocli:picocli-codegen:4.6.3")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.2")

    testImplementation("org.mockito:mockito-core:4.6.1")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

val jar by tasks.getting(Jar::class) {
    manifest {
        attributes["Main-Class"] = "com.github.dhirabayashi.mytail.MyTail"
    }

    from(
            configurations.runtimeClasspath.get().map {
                if (it.isDirectory) it else zipTree(it)
            }
    )
    exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
}

graal {
    mainClass("com.github.dhirabayashi.mytail.MyTail")
    outputName("mytail")
    graalVersion("22.1.0")
    javaVersion("17")
    windowsVsVersion("2022")
    windowsVsEdition("BuildTools")
}