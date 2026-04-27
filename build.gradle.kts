
plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.14.0"
}

group = "net.chikach"
version = "0.1.0-SNAPSHOT"

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
        bundledModule("intellij.platform.vcs.impl")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        name = "Jujutsu"

        description = """
            First-class <a href="https://jj-vcs.github.io/jj/">Jujutsu</a> (jj) VCS integration for IntelliJ IDEA.
            <br/>
            <em>Early scaffolding — CLI wrapper, version detection, and settings panel.</em>
        """.trimIndent()

        changeNotes = """
            <h3>0.1.0</h3>
            <ul>
                <li>Initial scaffolding: jj CLI wrapper, version probe, settings panel under <b>Tools | Jujutsu</b>.</li>
            </ul>
        """.trimIndent()

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
