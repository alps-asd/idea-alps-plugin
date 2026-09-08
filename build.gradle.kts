import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    kotlin("jvm") version "2.3.21"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Build against 2026.1: its class files are Java 21, matching the
        // `--release 21` output below. 2026.2 is built with Java 25 and cannot
        // be a compile target here; JetBrains' guidance is to build with 2026.1
        // and stay runtime-compatible with 2026.2.
        phpstorm(providers.gradleProperty("platformVersion"))
        bundledPlugin("com.intellij.modules.platform")
        // Mirrors <depends>com.intellij.modules.jcef</depends> in plugin.xml.
        // JetBrains' 2026.2 note says bundledPlugin("intellij.platform.ui.jcef"),
        // but that only exists as a plugin when building against 2026.2; on the
        // 2026.1 target it fails to resolve, while the alias id below resolves.
        bundledPlugin("com.intellij.modules.jcef")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

intellijPlatform {
    pluginConfiguration {
        id.set(providers.gradleProperty("pluginGroup").map { "$it.idea-alps-plugin" })
        name.set(providers.gradleProperty("pluginName"))
        version.set(providers.gradleProperty("pluginVersion"))
        ideaVersion {
            sinceBuild.set(providers.gradleProperty("pluginSinceBuild"))
        }
    }

    // Verify against the compile-target PhpStorm (already cached), plus an
    // optional locally installed IDE (`-PlocalIdePath=~/Applications/PhpStorm.app`)
    // so a newer runtime than the build target can be checked without a
    // ~1 GB download. Note the verifier resolves classes IDE-wide and does NOT
    // model plugin-classloader isolation: it reported "Compatible" on 2026.2
    // for a build that crashed there with NoClassDefFoundError on JCEF. Only a
    // real IDE run proves that class of dependency-declaration bug.
    pluginVerification {
        ides {
            current()
            providers.gradleProperty("localIdePath").orNull?.let { local(it) }
        }
    }
}

tasks {
    withType<JavaCompile> {
        options.release.set(21)
    }

    withType<KotlinCompile> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    // The web bundle (web/src/main.ts -> asd.bundle.js) is built separately via
    // `npm run build` in web/ and is gitignored (generated, not checked in).
    // Fail loudly instead of silently packaging a blank preview.
    processResources {
        doFirst {
            val bundle = layout.projectDirectory.file("src/main/resources/web/asd.bundle.js").asFile
            check(bundle.exists()) {
                "Missing $bundle — run `cd web && npm install && npm run build` first."
            }
        }
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    // Plugin Verifier does not model plugin-classloader isolation and passed a
    // build that lacked this dependency (it crashed on 2026.2 with
    // NoClassDefFoundError). Assert on the packaged descriptor instead, so an
    // accidental removal fails every buildPlugin locally and in CI.
    val verifyPluginDescriptor by registering {
        val jar = named<org.jetbrains.intellij.platform.gradle.tasks.ComposedJarTask>("composedJar").flatMap { it.archiveFile }
        inputs.file(jar)
        doLast {
            val descriptor = zipTree(jar).matching { include("META-INF/plugin.xml") }.singleFile.readText()
            check("<depends>com.intellij.modules.jcef</depends>" in descriptor) {
                "Packaged META-INF/plugin.xml lacks <depends>com.intellij.modules.jcef</depends>; " +
                    "JCEF classes are not on the plugin classloader on 2026.2+ without it."
            }
        }
    }

    buildPlugin {
        dependsOn(verifyPluginDescriptor)
    }
}
