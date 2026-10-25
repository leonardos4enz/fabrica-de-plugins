version = "1.0.1"

dependencies {
    compileOnly("me.clip:placeholderapi:2.11.6")
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("OmniEvents")
}
