plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.velocityreport"
version = "2.3.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")
    implementation("org.xerial:sqlite-jdbc:3.45.1.0")
    implementation("org.yaml:snakeyaml:2.2")
    implementation("com.zaxxer:HikariCP:5.1.0")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        // Relocate libraries to avoid classpath conflicts with other plugins
        relocate("org.sqlite", "com.velocityreport.libs.sqlite")
        relocate("org.yaml.snakeyaml", "com.velocityreport.libs.snakeyaml")
        relocate("com.zaxxer.hikari", "com.velocityreport.libs.hikari")
    }

    build {
        dependsOn(shadowJar)
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}
