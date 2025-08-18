// intellij.gradle.kts  (apply only when -PwithIdeaPlugin is set)

apply(plugin = "org.jetbrains.intellij")

intellij {
    version.set("2024.1.7")
    type.set("IC")
    plugins.set(listOf("java"))
}

// (Optional) plugin-specific tweaks can live here too
tasks.patchPluginXml {
    sinceBuild.set("241")
    untilBuild.set("243.*")
}