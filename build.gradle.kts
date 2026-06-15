import org.jetbrains.changelog.Changelog

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.14.0"
    id("org.jetbrains.changelog") version "2.2.1"
}

group = "net.chikach"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    testImplementation(kotlin("test"))

    intellijPlatform {
        intellijIdea("2025.2.4")
        bundledPlugin("com.intellij.modules.vcs")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        name = "Jujutsu"

        changeNotes = provider {
            with(changelog) {
                renderItem(
                    (getOrNull(project.version.toString()) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }

        vendor {
            name = "chikach"
            email = "kazu.chika.shima@gmail.com"
            url = "https://github.com/chika3742/jujutsu-intellij"
        }

        ideaVersion {
            sinceBuild = "252.25557"
        }
    }
}

changelog {
    repositoryUrl = "https://github.com/chika3742/jujutsu-intellij"
    groups = listOf("Added", "Changed", "Fixed", "Removed")
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
