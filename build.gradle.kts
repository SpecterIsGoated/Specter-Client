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
    flatDir { dirs("libs") }
}

val jij = configurations.create("jij")
configurations {
    implementation.configure { extendsFrom(jij) }
    include.configure { extendsFrom(jij) }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:$loaderVersion")

    implementation(fabricApi.module("fabric-api-base", fabricApiVersion))
    include(fabricApi.module("fabric-api-base", fabricApiVersion))
    implementation(fabricApi.module("fabric-resource-loader-v1", fabricApiVersion))
    include(fabricApi.module("fabric-resource-loader-v1", fabricApiVersion))

    // Optional compat targets (compile only)
    compileOnly(fabricApi.module("fabric-renderer-indigo", fabricApiVersion))
    compileOnly("maven.modrinth:sodium:mc26.3-0.9.2-fabric") { isTransitive = false }
    compileOnly("maven.modrinth:lithium:mc26.3-0.26.2-fabric") { isTransitive = false }
    compileOnly("maven.modrinth:iris:1.11.7+26.3-fabric") { isTransitive = false }
    compileOnly("meteordevelopment:baritone:26.1-SNAPSHOT")
    compileOnly("maven.modrinth:modmenu:21.0.0")

    // Bundled libraries (jar-in-jar)
    jij("org.reflections:reflections:0.10.2") { isTransitive = false }
    jij("io.netty:netty-handler-proxy:4.2.7.Final") { isTransitive = false }
    jij("io.netty:netty-codec-socks:4.2.7.Final") { isTransitive = false }
    jij("com.google.code.gson:gson:2.14.0") { isTransitive = false }
    jij("com.google.errorprone:error_prone_annotations:2.48.0") { isTransitive = false }
    // Taken from the original 1.21.11 jar (not all available on reachable Maven repos)
    for (name in listOf(
        "orbit-0.2.4", "starscript-0.2.5", "javassist-3.28.0-GA", "MinecraftAuth-5.0.2",
        "WaybackAuthLib-1.0.1", "gson-1.9.2", "httpclient-1.9.2", "java-spotify-api-1.4.7"
    )) {
        val file = files("libs/$name.jar")
        implementation(file)
    }
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
}

loom {
    accessWidenerPath = file("src/main/resources/specter.classtweaker")
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = 25
        options.compilerArgs.addAll(listOf("-Xmaxerrs", "100000"))
    }
    jar {
        from("LICENSE") { rename { "LICENSE_specter" } }
    }
}
