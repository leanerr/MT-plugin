package mtspark

import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.PathMatcher

/** Contract for any code mutation you add (Java/Kotlin renamers, etc.). */
interface Mutator {
    fun mutate(file: File): MutationResult
}

/** What changed after a mutation attempt. */
data class MutationResult(
    val file: File,            // file we tried to mutate
    val oldName: String?,      // e.g., original identifier name (or null if none)
    val newName: String?,      // e.g., new identifier name (or null if none)
    val changed: Boolean       // true if we actually edited the file
)

/** Simple glob finder rooted at a repo path. */
object GlobMatcher {
    private val DEFAULT_IGNORES = setOf(".git", ".gradle", "build", "out", "target", "node_modules")

    fun find(root: File, pattern: String, ignoreDirs: Set<String> = DEFAULT_IGNORES): List<File> {
        val rootPath = root.toPath()
        val matcher: PathMatcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")

        Files.walk(rootPath).use { stream ->
            return stream
                .filter { Files.isRegularFile(it) }
                .filter { p ->
                    val rel = rootPath.relativize(p)
                    val relStr = rel.toString()
                    // skip common build/metadata folders
                    val skip = relStr.split(File.separatorChar).any { it in ignoreDirs }
                    !skip && matcher.matches(rel)
                }
                .map { it.toFile() }
                .toList()
        }
    }
}