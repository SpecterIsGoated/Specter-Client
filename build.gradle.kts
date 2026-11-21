plugins {
    id("net.fabricmc.fabric-loom") version "1.19.4"
}

val minecraftVersion = "26.3"
val loaderVersion = "0.20.1"
val fabricApiVersion = "0.166.2+26.3"

base {
    archivesName = providers.gradleProperty("archives_base_name").get()
    group = providers.gradleProperty("maven_group").get()
    version = "${providers.gradleProperty("mod_version").get()}+$minecraftVersion"
}

repositories {
    maven {
        name = "meteor-maven"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "meteor-maven-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
    mavenCentral()
    exclusiveContent {
        forRepository {
            maven {
                name = "modrinth"
                url = uri("https://api.modrinth.com/maven")
            }
        }
        filter { includeGroup("maven.modrinth") }
    }
    exclusiveContent {
        forRepository {
            maven {
                name = "local-libs"
                url = uri("libs/repo")
            }
        }
        filter { includeGroup("com.github.labystudio") }
    }
}

val modInclude = configurations.create("modInclude")
val jij = configurations.create("jij")
configurations {
    // mods (Fabric API modules) and plain libraries are nested through these, so `include` itself stays free of
    // direct dependencies and the jar-in-jar transitive resolution below (defaultDependencies) applies to it
    implementation.configure { extendsFrom(modInclude); extendsFrom(jij) }
    include.configure { extendsFrom(modInclude); extendsFrom(jij) }
}

val launcher = sourceSets.create("launcher") {
    java.srcDir("src/launcher/java")
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:$loaderVersion")

    modInclude(fabricApi.module("fabric-api-base", fabricApiVersion))
    modInclude(fabricApi.module("fabric-resource-loader-v1", fabricApiVersion))

    // Optional compat targets (compile only)
    compileOnly(fabricApi.module("fabric-renderer-indigo", fabricApiVersion))
    compileOnly("maven.modrinth:sodium:mc26.3-0.9.2-fabric") { isTransitive = false }
    compileOnly("maven.modrinth:lithium:mc26.3-0.26.2-fabric") { isTransitive = false }
    compileOnly("maven.modrinth:iris:1.11.7+26.3-fabric") { isTransitive = false }
    compileOnly("meteordevelopment:baritone:26.1-SNAPSHOT")
    compileOnly("maven.modrinth:modmenu:21.0.0")

    // Bundled libraries (jar-in-jar), versions matching Meteor 26.3
    jij("meteordevelopment:orbit:0.2.4")
    jij("org.meteordev:starscript:0.2.5")
    // No org.reflections: it bundles javassist, which malware scanners flag (see ReflectInit)
    jij("io.netty:netty-handler-proxy:4.2.16.Final") { isTransitive = false }
    jij("io.netty:netty-codec-socks:4.2.16.Final") { isTransitive = false }
    jij("de.florianreuth:waybackauthlib:1.1.0")
    jij("net.raphimc:MinecraftAuth:5.0.2") {
        exclude("com.google.code.gson")
        exclude("com.google.errorprone")
    }

    // Spotify API (only published on JitPack; the jar from the original release lives in libs/repo)
    jij("com.github.labystudio:java-spotify-api:1.4.7")

    // Unit tests for the pure helpers (perf/SectionThrottle, ping/PingMath)
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Jar-in-jar transitive dependencies (same approach as Meteor's build)
val jijExcluded = setOf("org.slf4j", "jsr305")
listOf("api", "implementation", "include").forEach { configName ->
    configurations.named(configName).configure {
        defaultDependencies {
            configurations.getByName("jij").incoming.resolutionResult.allComponents
                .mapNotNull { it.id as? ModuleComponentIdentifier }
                .forEach { id ->
                    val notation = "${id.group}:${id.module}:${id.version}"
                    if (jijExcluded.none { notation.contains(it) }) {
                        add(project.dependencies.create(notation) { isTransitive = false })
                    }
                }
        }
    }
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
}

loom {
    accessWidenerPath = file("src/main/resources/specter.classtweaker")
}

tasks {
    named<JavaCompile>("compileJava").configure {
        options.encoding = "UTF-8"
        options.release = 25
        options.compilerArgs.addAll(listOf("-Xmaxerrs", "100000"))
    }
    processResources {
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filesMatching("fabric.mod.json") { expand(props) }
    }

    // Launcher (shown when the jar is double-clicked) targets Java 8
    named<JavaCompile>("compileLauncherJava").configure {
        options.release = 8
        options.compilerArgs.add("-Xlint:-options")
    }

    test { useJUnitPlatform() }

    jar {
        from("LICENSE") { rename { "LICENSE_specter" } }
        from(launcher.output)
        manifest { attributes["Main-Class"] = "com.jfant.specter.core.Main" }
    }
}
