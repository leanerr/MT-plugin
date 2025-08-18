package mtspark

import java.io.File

/**
 * If repo is a Git repo, stash & reset using Git.
 * Otherwise, we back up any file we touch and restore it on reset().
 */
class RepoManager(private val repo: File) {
    private val isGit = File(repo, ".git").isDirectory
    private val backups = mutableMapOf<File, String>()

    fun stash() {
        if (!isGit) return
        runCatching {
            ProcessBuilder("git", "stash", "push", "-u", "-k", "-m", "mtspark-temp")
                .directory(repo).inheritIO().start().waitFor()
        }
    }

    fun remember(f: File) {
        if (isGit) return
        if (f.isFile && !backups.containsKey(f)) {
            backups[f] = f.readText()
        }
    }

    fun reset() {
        if (isGit) {
            runCatching {
                ProcessBuilder("git", "reset", "--hard").directory(repo).start().waitFor()
            }
            runCatching {
                ProcessBuilder("git", "stash", "pop").directory(repo).start().waitFor()
            }
        } else {
            backups.forEach { (file, text) ->
                if (file.exists()) file.writeText(text)
            }
            backups.clear()
        }
    }
}