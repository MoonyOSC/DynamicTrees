import com.google.gson.Gson
import com.google.gson.JsonObject
import com.matthewprenger.cursegradle.CurseArtifact
import com.matthewprenger.cursegradle.CurseProject
import com.matthewprenger.cursegradle.CurseRelation
import java.io.InputStreamReader
import java.time.Instant
import java.time.format.DateTimeFormatter

fun property(key: String) = project.findProperty(key).toString()
fun optionalProperty(key: String) = project.findProperty(key)?.toString()

plugins {
    id("java")
    id("net.minecraftforge.gradle")
    id("org.parchmentmc.librarian.forgegradle")
    id("idea")
    id("maven-publish")
    id("com.matthewprenger.cursegradle") version "1.4.0"
    id("com.harleyoconnor.translationsheet") version "0.1.1"
}

apply(from = "https://raw.githubusercontent.com/SizableShrimp/ForgeUpdatesRemapper/main/remapper.gradle")

repositories {
    maven("https://dvs1.progwml6.com/files/maven/")
    maven("https://modmaven.dev")
    maven("https://ldtteam.jfrog.io/ldtteam/modding/")
    maven("https://maven.tehnut.info")
    maven("https://www.cursemaven.com") {
        content {
            includeGroup("curse.maven")
        }
    }
    maven("https://harleyoconnor.com/maven")
    maven("https://squiddev.cc/maven/")
}

val modName = property("modName")
val modId = property("modId")
val modVersion = property("modVersion")
val mcVersion = property("mcVersion")

version = "$mcVersion-$modVersion"
group = property("group")

minecraft {
    mappings("parchment", "${property("mappingsVersion")}-1.19.2")
    accessTransformer(file("src/main/resources/META-INF/accesstransformer.cfg"))

    runs {
        create("client") {
            workingDirectory = file("run").absolutePath

            property("forge.logging.markers", "SCAN,REGISTRIES,REGISTRYDUMP")
            property("forge.logging.console.level", "debug")

            property("mixin.env.remapRefMap", "true")
            property("mixin.env.refMapRemappingFile", "${buildDir}/createSrgToMcp/output.srg")

            if (project.hasProperty("mcUuid")) {
                args("--uuid", property("mcUuid"))
            }
            if (project.hasProperty("mcUsername")) {
                args("--username", property("mcUsername"))
            }
            if (project.hasProperty("mcAccessToken")) {
                args("--accessToken", property("mcAccessToken"))
            }

            mods {
                create(modId) {
                    source(sourceSets.main.get())
                }
            }
        }

        create("server") {
            workingDirectory = file("run").absolutePath

            property("forge.logging.markers", "SCAN,REGISTRIES,REGISTRYDUMP")
            property("forge.logging.console.level", "debug")

            property("mixin.env.remapRefMap", "true")
            property("mixin.env.refMapRemappingFile", "${buildDir}/createSrgToMcp/output.srg")

            mods {
                create(modId) {
                    source(sourceSets.main.get())
                }
            }
        }

        create("data") {
            workingDirectory = file("run").absolutePath

            property("forge.logging.markers", "SCAN,REGISTRIES,REGISTRYDUMP")
            property("forge.logging.console.level", "debug")

            property("mixin.env.remapRefMap", "true")
            property("mixin.env.refMapRemappingFile", "${buildDir}/createSrgToMcp/output.srg")

            args(
                "--mod",
                modId,
                "--all",
                "--output",
                file("src/generated/resources/"),
                "--existing",
                file("src/main/resources")
            )

            mods {
                create(modId) {
                    source(sourceSets.main.get())
                }
            }
        }
    }
}

sourceSets.main.get().resources {
    srcDir("src/generated/resources")
}

dependencies {
    // Not sure if we need this one, what is a "forge" anyway?
    minecraft("net.minecraftforge:forge:$mcVersion-${property("forgeVersion")}")


    compileOnly(fg.deobf("mezz.jei:jei-${property("jeiMcVersion")}-common-api:${property("jeiVersion")}"))
    compileOnly(fg.deobf("mezz.jei:jei-${property("jeiMcVersion")}-forge-api:${property("jeiVersion")}"))
    // at runtime, use the full JEI jar for Forge
    runtimeOnly(fg.deobf("mezz.jei:jei-${property("jeiMcVersion")}-forge:${property("jeiVersion")}"))

    // At runtime, use Patchouli mod (for the guide book, which is Json, so we don't need the API).
    runtimeOnly(fg.deobf("vazkii.patchouli:Patchouli:${property("patchouliVersion")}"))

    // At runtime use, CC for creating growth chambers.
    //runtimeOnly(fg.deobf("org.squiddev:cc-tweaked-$mcVersion:${property("ccVersion")}"))
    runtimeOnly(fg.deobf("curse.maven:cc-tweaked-282001:${property("ccTweakedFileId")}"))

    // Compile Serene Seasons.
    compileOnly(fg.deobf("curse.maven:serene-seasons-291874:${property("sereneSeasonsFileId")}"))

    // Compile Better Weather API.
//    compileOnly(fg.deobf("curse.maven:BetterWeatherAPI-400714:3403615"))

//    useSereneSeasons(this)
//    useBetterWeather(this)

    // At runtime, use suggestion provider fix mod.
//    runtimeOnly(fg.deobf("com.harleyoconnor.suggestionproviderfix:SuggestionProviderFix:$mcVersion-${property("suggestionProviderFixVersion")}"))

    runtimeOnly(fg.deobf("curse.maven:suggestion-provider-fix-469647:${property("suggestionProviderFixFileId")}"))
}

fun useSereneSeasons(depHandler: DependencyHandlerScope) {
    // At runtime, use full Serene Seasons mod.
    depHandler.runtimeOnly(fg.deobf("curse.maven:serene-seasons-291874:${property("sereneSeasonsFileId")}"))
}

fun useBetterWeather(depHandler: DependencyHandlerScope) {
    // At runtime, use the full Better Weather mod.
    depHandler.runtimeOnly(fg.deobf("curse.maven:BetterWeather-400714:3420517"))
}

translationSheet {
    this.sheetId.set("1xjxEh2NdbeV_tQc6fDHPgcRmtchqCZJKt--6oifq1qc")
    this.sectionColour.set(0xF9CB9C)
    this.sectionPattern.set("Dynamic Trees")
    this.outputDir("src/generated/resources/assets/dynamictrees/lang/")

    this.useJson()
}

tasks.jar {
    manifest.attributes(
        "Specification-Title" to project.name,
        "Specification-Vendor" to "ferreusveritas",
        "Specification-Version" to "1",
        "Implementation-Title" to project.name,
        "Implementation-Version" to project.version,
        "Implementation-Vendor" to "ferreusveritas",
        "Implementation-Timestamp" to DateTimeFormatter.ISO_INSTANT.format(Instant.now())
    )

    archiveBaseName.set(modName)
    finalizedBy("reobfJar")
}

java {
    withSourcesJar()

    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

fun readChangelog(): String? {
    val versionInfoFile = file(
        (optionalProperty("dynamictrees.version_info_repo.directory") ?: return null)
                + File.separatorChar + "DynamicTrees.json"
    )
    val jsonObject = Gson().fromJson(InputStreamReader(versionInfoFile.inputStream()), JsonObject::class.java)
    return jsonObject
        .get(mcVersion)?.asJsonObject
        ?.get(project.version.toString())?.asString
}

//curseforge {
//    if (project.hasProperty("curseApiKey") && project.hasProperty("curseFileType")) {
//        apiKey = property("curseApiKey")
//
//        project {
//            id = "252818"
//
//            addGameVersion("1.18.1")
//            addGameVersion(mcVersion)
//
//            changelog = readChangelog() ?: "No changelog provided."
//            changelogType = "markdown"
//            releaseType = property("curseFileType")
//
//            addArtifact(tasks.findByName("sourcesJar"))
//
//            mainArtifact(tasks.findByName("jar")) {
//                relations {
//                    optionalDependency("dynamictreesplus")
//                    optionalDependency("chunk-saving-fix")
//                }
//            }
//        }
//    } else {
//        project.logger.log(
//            LogLevel.WARN,
//            "API Key and file type for CurseForge not detected; uploading will be disabled."
//        )
//    }
//}

tasks.withType<GenerateModuleMetadata> {
    enabled = false
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "$modName-$mcVersion"
            version = modVersion

            from(components["java"])

            pom {
                name.set(modName)
                url.set("https://github.com/ferreusveritas/$modName")
                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://mit-license.org")
                    }
                }
                developers {
                    developer {
                        id.set("ferreusveritas")
                        name.set("Ferreus Veritas")
                    }
                    developer {
                        id.set("supermassimo")
                        name.set("Max Hyper")
                    }
                    developer {
                        id.set("Harleyoc1")
                        name.set("Harley O'Connor")
                        email.set("Harleyoc1@gmail.com")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/ferreusveritas/$modName.git")
                    developerConnection.set("scm:git:ssh://github.com/ferreusveritas/$modName.git")
                    url.set("https://github.com/ferreusveritas/$modName")
                }
            }

            pom.withXml {
                val element = asElement()

                // Clear dependencies.
                for (i in 0 until element.childNodes.length) {
                    val node = element.childNodes.item(i)
                    if (node?.nodeName == "dependencies") {
                        element.removeChild(node)
                    }
                }
            }
        }
    }
    repositories {
        maven("file:///${project.projectDir}/mcmodsrepo")
        if (hasProperty("harleyOConnorMavenUsername") && hasProperty("harleyOConnorMavenPassword")) {
            maven("https://harleyoconnor.com/maven") {
                name = "HarleyOConnor"
                credentials {
                    username = property("harleyOConnorMavenUsername")
                    password = property("harleyOConnorMavenPassword")
                }
            }
        } else {
            logger.log(LogLevel.WARN, "Credentials for maven not detected; it will be disabled.")
        }
    }
}

tasks.register("publishToAllPlatforms") {
    this.dependsOn("publishMavenJavaPublicationToHarleyOConnorRepository", "curseforge")
}

// Extensions to make CurseGradle extension slightly neater.

fun com.matthewprenger.cursegradle.CurseExtension.project(action: CurseProject.() -> Unit) {
    this.project(closureOf(action))
}

fun CurseProject.mainArtifact(artifact: Task?, action: CurseArtifact.() -> Unit) {
    this.mainArtifact(artifact, closureOf(action))
}

fun CurseArtifact.relations(action: CurseRelation.() -> Unit) {
    this.relations(closureOf(action))
}
