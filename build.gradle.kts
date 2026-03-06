plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.eagga"
version = "1.0.0"

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
    type.set("IC")
    plugins.set(listOf("com.intellij.java"))
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
}
