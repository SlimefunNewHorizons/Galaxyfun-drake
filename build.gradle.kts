plugins {
    java
    id("com.gradleup.shadow") version "8.3.10"
    id("net.minecrell.plugin-yml.bukkit") version "0.6.0"
}

version = "11.0-Drake-1.21.11-Galaxyfun-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.github.drakescraft_labs:slimefun-core:11.0-Drake-1.21.11-SNAPSHOT")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    compileOnly("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")

    implementation("com.github.drakescraft_labs:infinitylib-drake:1.3.11-DRAKE-SNAPSHOT")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks.test {
    useJUnitPlatform()
}

java.sourceCompatibility = JavaVersion.VERSION_21

tasks.shadowJar {
    relocate("io.github.mooy1.infinitylib", "com.github.drakescraft_labs.galactifun.infinitylib")
    archiveFileName.set("Galaxyfun-11.0-Drake-1.21.11-SNAPSHOT.jar")
}

bukkit {
    name = "Galaxyfun"
    description = "DrakesCraft space expansion for Slimefun"
    main = "com.github.drakescraft_labs.galactifun.Galactifun"
    version = project.version.toString()
    authors = listOf("Seggan", "Mooy1", "GallowsDove", "ProfElements")
    apiVersion = "1.21"
    depend = listOf("Slimefun")

    commands {
        register("galaxyfun") {
            description = "Galaxyfun main command"
            usage = "/galaxyfun <subcommand>"
            aliases = listOf("galactifun-drake", "galactifun", "gf", "galactic")
        }
    }
}
