plugins {
	java
	id("org.jetbrains.kotlin.jvm") version "2.4.10"
	id("xyz.jpenilla.run-paper") version "3.0.2"
}

repositories {
	maven {
		url = uri("https://plugins.gradle.org/m2/")
	}
	maven {
		url = uri("https://repo.extendedclip.com/content/repositories/placeholderapi/")
	}
    maven {
		url = uri("https://repo.mikeprimm.com/")
	}
	mavenCentral()
	maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
	implementation(kotlin("stdlib"))
    compileOnly("io.papermc.paper:paper-api:26.2.build.121-stable")
	compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("us.dynmap:dynmap-api:3.4-beta-3")
    compileOnly("us.dynmap:DynmapCoreAPI:3.4")
    implementation("com.squareup.okhttp3:okhttp:4.9.3")
    implementation("org.json:json:20211205")
}

tasks.jar {
	manifest {
		attributes["Main-Class"] = "net.sneakyjobboard.SneakyJobBoard"
	}

	from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}

configure<JavaPluginExtension> {
	sourceSets {
		main {
			java.srcDir("src/main/kotlin")
			resources.srcDir(file("src/resources"))
		}
	}
}

java {
	toolchain {
		languageVersion.set(JavaLanguageVersion.of(25))
	}
}

tasks {
	runServer {
		minecraftVersion("26.2")
	}
}
