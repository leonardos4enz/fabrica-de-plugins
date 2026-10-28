version = "1.11.1"

repositories {
    // Integraciones opcionales (spec 012): solo para compilar, no se empaquetan.
    maven("https://jitpack.io") { content { includeGroup("com.github.MilkBowl") } }
    maven("https://maven.enginehub.org/repo/")
}

dependencies {
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1") { isTransitive = false }
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.13") { exclude(group = "org.bukkit"); exclude(group = "io.papermc.paper") }
    // Spec 016 (ADR 0004): lectura de los paquetes de chat; Maven Central.
    compileOnly("net.dmulloy2:ProtocolLib:5.4.0") { isTransitive = false }
    // Valida los ejemplos contra el esquema generado (spec 007); solo en pruebas.
    testImplementation("com.networknt:json-schema-validator:1.5.6")
}

// DocsSiteTest (spec 017) lee la documentación pública: si cambia, las pruebas vuelven a correr.
tasks.named<Test>("test") {
    inputs.dir(rootProject.layout.projectDirectory.dir("docs/site")).withPropertyName("docsSite")
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("OmniEvents")
}

// Esquema JSON y referencia generados del registro del código (spec 007): ./gradlew :omnievents:schema
tasks.register<JavaExec>("schema") {
    group = "documentation"
    description = "Genera docs/omnievents/omnievents.schema.json y referencia.md"
    val sourceSets = project.extensions.getByType<SourceSetContainer>()
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dev.leo.omnievents.schema.SchemaMain")
    args(rootProject.layout.projectDirectory.dir("docs/omnievents").asFile.absolutePath)
}
