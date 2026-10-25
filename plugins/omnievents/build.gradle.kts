version = "1.3.0"

dependencies {
    compileOnly("me.clip:placeholderapi:2.11.6")
    // Valida los ejemplos contra el esquema generado (spec 007); solo en pruebas.
    testImplementation("com.networknt:json-schema-validator:1.5.6")
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
