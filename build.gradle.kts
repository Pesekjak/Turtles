plugins {
    id("java")
    alias(libs.plugins.shadow)
    alias(libs.plugins.runPaper)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.xenondevs.xyz/releases")
    maven("https://maven.squiddev.cc")
}

dependencies {
    @Suppress("VulnerableLibrariesLocal", "RedundantSuppression")
    compileOnly(libs.paper.api)

    compileOnly(libs.pylonmc.rebar)
    compileOnly(libs.pylonmc.pylon)

    implementation(libs.jimfs)
    implementation(libs.configLib)
    implementation(libs.jactl)
    implementation(libs.jol)

    compileOnly(libs.jetbrains.annotations)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks {
    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }

    shadowJar {
        relocate("com.google.common.jimfs", "dev.pesek.turtles.external.com.google.common.jimfs")
        relocate("de.exlll.configlib", "dev.pesek.turtles.external.de.exlll.configlib")
        relocate("io.jactl", "dev.pesek.turtles.external.io.jactl")
        relocate("org.openjdk.jol", "dev.pesek.turtles.external.org.openjdk.jol")
        mergeServiceFiles()
        archiveBaseName = "Turtles"
        archiveClassifier = ""
    }

    runServer {
        doFirst {
            val runFolder = project.projectDir.resolve("run")
            val pluginsDir = runFolder.resolve("plugins")
            pluginsDir.deleteRecursively()
        }
        downloadPlugins {
            val rebarVer = libs.versions.rebar.get()
            github("pylonmc", "rebar", rebarVer, "rebar-$rebarVer.jar")
        }
        downloadPlugins {
            val pylonVer = libs.versions.pylon.get()
            github("pylonmc", "pylon", pylonVer, "pylon-$pylonVer.jar")
        }
        maxHeapSize = "2G"
        minecraftVersion("26.2")
    }
}
