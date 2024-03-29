import com.github.gradle.node.yarn.task.YarnTask

buildscript {
    repositories {
        mavenCentral()
    }
}

plugins {
    kotlin("jvm") version Versions.KOTLIN_VERSION
    kotlin("plugin.allopen") version Versions.KOTLIN_VERSION
    id("io.quarkus") apply false
    id("com.github.node-gradle.node") version "3.2.1"
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project

allprojects {
    group = "org.anthaathi"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenCentral()
        mavenLocal()
    }
}

val quarkusCommonProjects = listOf(
    // project(":apps:anthaathi-cms"),
    project(":apps:anthaathi-crm")
)

val quarkusWebAppDeps = mapOf(
    project(":apps:anthaathi-crm") to listOf(
        project(":apps:anthaathi-crm-web-client")
    ),
)

// This needs to be calculate in future
val libraryDeps = mapOf(
    project(":libs:anthaathi-form-baseui") to listOf(
        project(":libs:anthaathi-form-builder"),
        project(":libs:anthaathi-web-lib")
    ),
    project(":libs:anthaathi-form-builder") to listOf(
        project(":libs:anthaathi-json-in-action")
    )
)

val webClients = listOf(
    // project(":apps:anthaathi-cms-web-client"),
    project(":apps:anthaathi-crm-web-client")
)

val webLibraries = listOf(
    project(":libs:anthaathi-web-lib"),
    project(":libs:anthaathi-form-builder"),
    project(":libs:anthaathi-form-baseui"),
    project(":libs:anthaathi-json-in-action")
)

configure(subprojects.filter { it in webLibraries }) {
    apply {
        plugin("com.github.node-gradle.node")
    }

    tasks.register<YarnTask>("buildLib") {
        args.set(listOf("build"))

        libraryDeps[this.project]?.forEach { itt ->
            this.dependsOn.add(itt.tasks.getByName("buildLib"))
        }
    }

    tasks.register<YarnTask>("lint") {
        args.set(listOf("lint"))
    }

    tasks.register<YarnTask>("test") {
        args.set(listOf("test", "--coverage", "--coverageReporters=lcov"))
    }

    tasks.register("check") {
        dependsOn("lint", "test", "buildLib")

        finalizedBy(project(":tools:node-tooling").tasks.getByName("coverageMerger"))
    }
}

configure(subprojects.filter { it in webClients }) {
    apply {
        plugin("com.github.node-gradle.node")
    }

    tasks.register<YarnTask>("i18nExtract") {
        args.set(listOf("extract"))
    }

    tasks.register<YarnTask>("i18nCompile") {
        args.set(listOf("compile"))

        dependsOn.add("i18nExtract")
    }

    tasks.register<YarnTask>("runDev") {
        args.set(listOf("dev"))

        dependsOn.add("i18nCompile")

        webLibraries.forEach {
            dependsOn.add(it.tasks.find { task -> task.name == "buildLib" })
        }
    }

    tasks.register<YarnTask>("buildProd") {
        args.set(listOf("build"))

        dependsOn.add("i18nCompile")

        webLibraries.forEach {
            dependsOn.add(it.tasks.find { task -> task.name == "buildLib" })
        }
    }
}

configure(subprojects.filter { it in quarkusCommonProjects }) {
    apply {
        plugin("org.jetbrains.kotlin.jvm")
        plugin("org.jetbrains.kotlin.plugin.allopen")
        plugin("io.quarkus")
    }

    tasks.register("buildDocker") {
        if (quarkusWebAppDeps.containsKey(this.project)) {
            val fileProjectPath = File(this.project.projectDir, "src/main/resources/META-INF/resources").toString()

            quarkusWebAppDeps[this.project]?.forEach { itt ->
                val moveFileTaskName = "${ this.project.name }-${ itt.name }moveFile"

                val task = itt.tasks.register<Copy>(moveFileTaskName) {
                    doFirst {
                        mkdir(fileProjectPath)
                    }
                    from(File(this.project.projectDir, "dist").toString())
                    into(fileProjectPath)

                    dependsOn("buildProd")
                }

                dependsOn.add(task)
            }
        }

        finalizedBy(tasks.build)
    }

    dependencies {
        implementation(enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
        implementation("io.quarkus:quarkus-kotlin")
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
        implementation("io.quarkus:quarkus-arc")
        implementation("io.quarkus:quarkus-resteasy-reactive")

        testImplementation("io.quarkus:quarkus-junit5")
        testImplementation("io.rest-assured:rest-assured")
    }

    allOpen {
        annotation("javax.ws.rs.Path")
        annotation("javax.enterprise.context.ApplicationScoped")
        annotation("io.quarkus.test.junit.QuarkusTest")
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = JavaVersion.VERSION_11.toString()
        kotlinOptions.javaParameters = true
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
