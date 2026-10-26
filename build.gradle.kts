// Convenciones comunes de la fábrica: Java 21, Paper API, JUnit 5, UTF-8.
subprojects {
    apply(plugin = "java")

    group = "dev.leo"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.extendedclip.com/releases/")
    }

    dependencies {
        "compileOnly"("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
        "testImplementation"("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
        "testImplementation"(platform("org.junit:junit-bom:5.11.4"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        // Spec 015, F-01: el validador del esquema JSON (SchemaTest) junta los mensajes de cada rama del anyOf de
        // acciones; con las de la 015 ya no entra en los 512 MB por defecto.
        maxHeapSize = "1g"
        testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
    }

    tasks.withType<ProcessResources>().configureEach {
        filteringCharset = "UTF-8"
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filesMatching("plugin.yml") { expand(props) }
    }

    // El jar final se copia a dist/ en la raíz: un lugar para todos los plugins de la fábrica.
    tasks.named<Jar>("jar") {
        archiveBaseName.set(project.name.replaceFirstChar { it.uppercase() })
    }
    tasks.register<Copy>("dist") {
        dependsOn("jar")
        from(tasks.named("jar"))
        into(rootProject.layout.projectDirectory.dir("dist"))
    }
}
