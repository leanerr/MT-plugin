package mtspark

import java.io.File
import java.util.concurrent.TimeUnit

data class BuildResult(
    val success: Boolean,
    val cmd: List<String>,
    val exitCode: Int,
    val out: String,
    val err: String
) {
    fun summary() = """
        Command: ${cmd.joinToString(" ")}
        Exit: $exitCode
        Success: $success
        ---- STDOUT ----
        $out
        ---- STDERR ----
        $err
    """.trimIndent()
}

class BuildRunner(private val repo: File) {

    fun build(skipTests: Boolean = true, timeoutMinutes: Long = 30): BuildResult {
        val cmd = detectCommand(skipTests)
        val pb = ProcessBuilder(cmd).directory(repo)

        // Prefer a Java 17 home if provided
        val env = pb.environment()
        listOf("JAVA_HOME_17", "JAVA17_HOME").firstOrNull { System.getenv(it) != null }?.let {
            env["JAVA_HOME"] = System.getenv(it)
        }

        val process = pb.start()

        val outSb = StringBuilder()
        val errSb = StringBuilder()

        val outThread = Thread {
            process.inputStream.bufferedReader().forEachLine { outSb.appendLine(it) }
        }
        val errThread = Thread {
            process.errorStream.bufferedReader().forEachLine { errSb.appendLine(it) }
        }
        outThread.start()
        errThread.start()

        val finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES)
        val exit = if (finished) process.exitValue() else {
            process.destroyForcibly()
            -9 // timed out
        }

        outThread.join()
        errThread.join()

        val result = BuildResult(
            success = exit == 0,
            cmd = cmd,
            exitCode = exit,
            out = outSb.toString(),
            err = errSb.toString()
        )
        return result
    }

    private fun detectCommand(skipTests: Boolean): List<String> {
        val isWindows = System.getProperty("os.name").lowercase().contains("win")

        val gradlew = File(repo, if (isWindows) "gradlew.bat" else "gradlew")
        val mvnw    = File(repo, if (isWindows) "mvnw.cmd" else "mvnw")

        if (gradlew.exists()) {
            if (!isWindows) gradlew.setExecutable(true)
            val base = mutableListOf(gradlew.absolutePath, "--no-daemon", "clean", "build")
            if (skipTests) base += listOf("-x", "test")
            return base
        }

        if (mvnw.exists()) {
            if (!isWindows) mvnw.setExecutable(true)
            val base = mutableListOf(mvnw.absolutePath, "-B", "-q", "clean", "package")
            if (skipTests) base += listOf("-DskipTests=true")
            return base
        }

        // Fallbacks if wrappers aren’t present but project type is obvious
        val hasGradle = File(repo, "build.gradle").exists() || File(repo, "build.gradle.kts").exists()
        val hasMaven  = File(repo, "pom.xml").exists()

        return when {
            hasGradle -> {
                val base = mutableListOf("gradle", "--no-daemon", "clean", "build")
                if (skipTests) base += listOf("-x", "test")
                base
            }
            hasMaven -> {
                val base = mutableListOf("mvn", "-B", "-q", "clean", "package")
                if (skipTests) base += listOf("-DskipTests=true")
                base
            }
            else -> error("Could not detect build tool in ${repo.absolutePath}")
        }
    }
}