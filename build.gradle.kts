import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

plugins {
	java
	kotlin("jvm") version "2.4.10"
	id("xyz.jpenilla.run-paper") version "3.0.2"
	`maven-publish`
}

group = "io.github.team-sneakymouse"

version = providers.exec {
	workingDir(rootDir)
	commandLine("git", "show", "-s", "--format=%ct:%h", "--abbrev=12", "HEAD")
}.standardOutput.asText.map { commit ->
	val (timestamp, hash) = commit.trim().split(":", limit = 2)
	val date = DateTimeFormatter.ofPattern("yyyy.MM.dd").withZone(ZoneOffset.UTC)
		.format(Instant.ofEpochSecond(timestamp.toLong()))
	"$date-$hash"
}.get()

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
	maven("https://maven.sneakyrp.com/releases")
}

dependencies {
	implementation(kotlin("stdlib"))
	compileOnly("io.papermc.paper:paper-api:26.2.build.121-stable")
	compileOnly("me.clip:placeholderapi:2.11.6")
	compileOnly("us.dynmap:dynmap-api:3.4-beta-3")
	compileOnly("us.dynmap:DynmapCoreAPI:3.4")
	compileOnly("io.github.team-sneakymouse:sneakypocketbase-api:2026.10.09-0233c4a041d5")
}

tasks.processResources {
	inputs.property("version", project.version.toString())
	filesMatching("paper-plugin.yml") {
		expand("version" to project.version.toString())
	}
}

tasks.jar {
	archiveBaseName.set("SneakyJobBoard")
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

publishing {
	publications {
		create<MavenPublication>("maven") {
			artifactId = "sneakyjobboard"
			from(components["java"])
			pom {
				name.set("SneakyJobBoard")
				description.set("Paper plugin for managing the job board on the LoM2 server")
				url.set("https://github.com/Team-Sneakymouse/SneakyJobBoard")
				scm {
					url.set("https://github.com/Team-Sneakymouse/SneakyJobBoard")
					connection.set("scm:git:https://github.com/Team-Sneakymouse/SneakyJobBoard.git")
				}
			}
		}
	}
	repositories {
		maven {
			name = "sneakyrp"
			url = uri("https://maven.sneakyrp.com/releases")
			credentials(PasswordCredentials::class)
			authentication {
				create<org.gradle.authentication.http.BasicAuthentication>("basic")
			}
		}
	}
}

tasks.withType<PublishToMavenRepository>().configureEach {
	dependsOn(tasks.check)
}
