plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.17.4"
}

if (JavaVersion.current() < JavaVersion.VERSION_11) {
    throw GradleException("Gradle JVM must be 11+ (recommended 17). Please set IDEA Gradle JVM to JDK 17.")
}

group = "com.eagga"
version = System.getenv("PLUGIN_VERSION")
    ?: (findProperty("pluginVersion") as String?)
    ?: "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

intellij {
    version.set("2023.3")
    type.set("IU")
    plugins.set(listOf("com.intellij.java", "com.intellij.database"))
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(17)
    }
    
    withType<Test> {
        useJUnitPlatform()
    }

    patchPluginXml {
        sinceBuild.set("233")
        untilBuild.set("")
    }

    runIde {
        jvmArgs = listOf("-Dfile.encoding=UTF-8")
    }

    signPlugin {
        certificateChain.set(
            providers.environmentVariable("CERTIFICATE_CHAIN")
                .orElse(providers.gradleProperty("certificateChain"))
        )
        privateKey.set(
            providers.environmentVariable("PRIVATE_KEY")
                .orElse(providers.gradleProperty("privateKey"))
        )
        password.set(
            providers.environmentVariable("PRIVATE_KEY_PASSWORD")
                .orElse(providers.gradleProperty("privateKeyPassword"))
        )
    }

    publishPlugin {
        dependsOn(signPlugin)
        token.set(
            providers.environmentVariable("PUBLISH_TOKEN")
                .orElse(providers.gradleProperty("intellijPublishToken"))
        )
        channels.set(
            listOf(
                providers.environmentVariable("PUBLISH_CHANNEL")
                    .orElse(providers.gradleProperty("publishChannel"))
                    .orElse("default")
                    .get()
            )
        )
    }
}
