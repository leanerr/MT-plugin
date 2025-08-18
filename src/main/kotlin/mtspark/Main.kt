package mtspark

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val opts = Cli.parse(args) ?: run { Cli.printHelp(); exitProcess(2) }

    val repo = File(opts.repoPath)
    require(repo.isDirectory) { "Repo path not found: ${repo.absolutePath}" }

    val repoManager = RepoManager(repo)
    repoManager.stash()

    try {
        // ---- Build the file list (supports --include-tests and --limit) ----
        val files: List<File> = if (opts.userProvidedGlob) {
            GlobMatcher.find(repo, opts.fileGlob)
        } else {
            val patterns = when (opts.language.lowercase()) {
                "kotlin" -> mutableListOf("src/main/**/*.kt").apply {
                    if (opts.includeTests) add("src/test/**/*.kt")
                }
                else -> mutableListOf("src/main/**/*.java").apply {
                    if (opts.includeTests) add("src/test/**/*.java")
                }
            }
            patterns.flatMap { GlobMatcher.find(repo, it) }.distinct()
        }

        if (files.isEmpty()) {
            println("No files matched.")
            emitJsonIfRequested(
                opts, repo, target = File(""),
                language = opts.language,
                mutation = MutationResult(File(""), null, null, changed = false),
                baseline = null,
                mutated = null,
                diagLines = opts.diagnosticsLines
            )
            exitProcess(1)
        }

        val limitedFiles = files.take(opts.limit)

        // Prefer standard Maven/Gradle layout for Java symbol solving
        val javaSrcRoot = repo.toPath().resolve("src/main/java").takeIf { Files.exists(it) }

        // Choose mutator by operation (+ wire pick-index / random-seed)
        val mutator: Mutator = when (opts.operation.lowercase()) {
            "rename-param" -> JavaParamRenamer(
                sourceRoot = javaSrcRoot,
                pickIndex = opts.pickIndex,
                randomSeed = opts.randomSeed
            )
            "flip-if" -> JavaIfFlipMutator(
                sourceRoot = javaSrcRoot,
                pickIndex = opts.pickIndex,
                randomSeed = opts.randomSeed
            )
            "insert-comment" -> JavaCommentInserter(
                sourceRoot = javaSrcRoot
            )
            "double-negate-if" -> JavaDoubleNegationIfMutator(
                sourceRoot = javaSrcRoot,
                pickIndex = opts.pickIndex,
                randomSeed = opts.randomSeed
            )


            else -> JavaLocalVarRenamer(
                sourceRoot = javaSrcRoot,
                pickIndex = opts.pickIndex,
                randomSeed = opts.randomSeed
            )
        }

        // Human-friendly action/label for logs
        val (actionVerb, label) = when (opts.operation.lowercase()) {
            "rename-param"   -> "Renamed parameter" to "parameter"
            "flip-if"        -> "Flipped if/else"   to "if-statement"
            "insert-comment" -> "Inserted comment"  to "insertion point"
            "double-negate-if"   -> "Double-negated condition" to "if-statement"
            else             -> "Renamed local variable" to "local variable"
        }

        // 1) Baseline build BEFORE mutation — avoids false positives
        println("== Baseline build (pre-mutation) ==")
        val baseline = BuildRunner(repo).build()
        println(baseline.summary())
        if (!baseline.success) {
            println("\nBaseline build failed. Aborting mutation to avoid false signals.")
            emitJsonIfRequested(
                opts, repo, target = limitedFiles.firstOrNull() ?: File(""),
                language = opts.language,
                mutation = null,
                baseline = baseline,
                mutated = null,
                diagLines = opts.diagnosticsLines
            )
            exitProcess(2)
        }

        // 2) Try up to N files; stop on first mutation we actually perform
        var performedMutation = false
        var chosenTarget: File? = null
        var mutationResult: MutationResult? = null
        var mutatedBuild: BuildResult? = null

        for (target in limitedFiles) {
            repoManager.remember(target)

            val result = mutator.mutate(target)
            if (!result.changed) {
                println("No suitable $label found in ${target.relativeTo(repo)} — trying next file…")
                continue
            }

            val changeText = if (result.oldName != null || result.newName != null)
                ": ${result.oldName ?: "-"} -> ${result.newName ?: "-"}"
            else
                ""

            println("$actionVerb in ${target.relativeTo(repo)}$changeText")

            println("\n== Mutated build (post-mutation) ==")
            val mutated = BuildRunner(repo).build()
            println(mutated.summary())

            performedMutation = true
            chosenTarget = target
            mutationResult = result
            mutatedBuild = mutated
            break
        }

        if (!performedMutation) {
            // None of the inspected files had a suitable target
            val first = limitedFiles.firstOrNull() ?: File("")
            emitJsonIfRequested(
                opts, repo, first, language = opts.language,
                mutation = MutationResult(first, null, null, changed = false),
                baseline = baseline,
                mutated = null,
                diagLines = opts.diagnosticsLines
            )
            println("Tried ${limitedFiles.size} file(s), no suitable $label found.")
            exitProcess(0)
        }

        // Bind non-null locals (avoid !!)
        val target   = checkNotNull(chosenTarget)   { "internal: chosenTarget null" }
        val mutation = checkNotNull(mutationResult) { "internal: mutationResult null" }
        val mutated  = checkNotNull(mutatedBuild)   { "internal: mutatedBuild null" }

        // 3) Emit JSON diagnostics for the mutated build
        emitJsonIfRequested(
            opts, repo, target, language = opts.language,
            mutation = mutation,
            baseline = baseline,
            mutated = mutated,
            diagLines = opts.diagnosticsLines
        )

        // 4) Verdict (do not fail CLI unless --fail-on-build)
        if (mutated.success) {
            println("\n✅ Buildability preserved after mutation.")
            exitProcess(0)
        } else {
            println("\n❌ Mutation caused build failure.")
            val code = if (opts.failOnBuild) 1 else 0
            exitProcess(code)
        }
    } finally {
        if (!opts.keepMutation) {
            repoManager.reset()
        } else {
            println("⚠️  --keep-mutation was set: leaving changes on disk.")
        }
    }
}

private fun emitJsonIfRequested(
    opts: CliOptions,
    repo: File,
    target: File,
    language: String,
    mutation: MutationResult?,
    baseline: BuildResult?,
    mutated: BuildResult?,
    diagLines: Int
) {
    val outPath = opts.jsonOut ?: return

    fun head(text: String?, n: Int): String {
        if (text.isNullOrEmpty()) return ""
        return text.lineSequence().take(n).joinToString("\n")
    }

    val obj = JSONObject()
        .put("repo", repo.absolutePath)
        .put("language", language)
        .put("operation", opts.operation)
        .put("target_file", target.takeIf { it.path.isNotEmpty() }?.relativeTo(repo)?.path ?: "")

    val mutObj = JSONObject()
        .put("file", target.takeIf { it.path.isNotEmpty() }?.relativeTo(repo)?.path ?: "")
        .put("old", mutation?.oldName)
        .put("new", mutation?.newName)
        .put("changed", mutation?.changed ?: false)
    obj.put("mutation", mutObj)

    baseline?.let {
        obj.put(
            "baseline_build",
            JSONObject()
                .put("success", it.success)
                .put("exitCode", it.exitCode)
                .put("cmd", JSONArray(it.cmd))
                .put("stdout_head", head(it.out, diagLines))
                .put("stderr_head", head(it.err, diagLines))
        )
    }

    mutated?.let {
        obj.put(
            "mutated_build",
            JSONObject()
                .put("success", it.success)
                .put("exitCode", it.exitCode)
                .put("cmd", JSONArray(it.cmd))
                .put("stdout_head", head(it.out, diagLines))
                .put("stderr_head", head(it.err, diagLines))
        )
    }

    // NEW: top-level verdict + buildability flag
    val verdict = when {
        baseline?.success == false -> "baseline_failed"
        (mutation?.changed == false) && mutated == null -> "no_mutation"
        mutated?.success == true -> "ok"
        mutated != null && !mutated.success -> "mutated_build_failed"
        else -> "ok"
    }
    val preserved = verdict == "ok" || verdict == "no_mutation"

    obj.put("buildability_preserved", preserved)
    obj.put("verdict", verdict)

    val file = File(outPath)
    file.parentFile?.mkdirs()
    file.writeText(obj.toString(2))
    println("\n📝 Wrote diagnostics JSON → ${file.absolutePath}")
}

/* =======================
   CLI parsing (with operation + pick/random + fail-on-build)
   ======================= */

data class CliOptions(
    val repoPath: String,
    val fileGlob: String,
    val language: String,
    val keepMutation: Boolean,
    val jsonOut: String?,
    val diagnosticsLines: Int,
    val limit: Int,
    val includeTests: Boolean,
    val userProvidedGlob: Boolean,
    val pickIndex: Int?,
    val randomSeed: Long?,
    val operation: String,        // rename-local (default) | rename-param | flip-if | insert-comment
    val failOnBuild: Boolean      // NEW: exit non-zero if mutated build fails
)

object Cli {
    fun parse(args: Array<String>): CliOptions? {
        val kv = args.toList()
            .windowed(2, 2, partialWindows = true)
            .filter { it.size == 2 && it[0].startsWith("--") }
            .associate { it[0] to it[1] }

        val flags = args.toSet()

        val repo = kv["--repo"] ?: return null
        val language = (kv["--language"] ?: "java").lowercase()

        val userProvidedGlob = kv.containsKey("--file-glob")
        val defaultGlob = if (language == "kotlin") "src/**/*.kt" else "src/**/*.java"
        val fileGlob = kv["--file-glob"] ?: defaultGlob

        val keep = flags.contains("--keep-mutation")
        val jsonOut = kv["--json-out"]
        val diagLines = (kv["--diagnostics-lines"] ?: "50").toIntOrNull() ?: 50

        val limit = (kv["--limit"] ?: "1").toIntOrNull()?.coerceAtLeast(1) ?: 1
        val includeTests = flags.contains("--include-tests")

        val pickIndex = kv["--pick-index"]?.toIntOrNull()
        val randomSeed = kv["--random-seed"]?.toLongOrNull()
        val operation = (kv["--operation"] ?: "rename-local").lowercase()
        val failOnBuild = flags.contains("--fail-on-build")   // NEW

        return CliOptions(
            repoPath = repo,
            fileGlob = fileGlob,
            language = language,
            keepMutation = keep,
            jsonOut = jsonOut,
            diagnosticsLines = diagLines,
            limit = limit,
            includeTests = includeTests,
            userProvidedGlob = userProvidedGlob,
            pickIndex = pickIndex,
            randomSeed = randomSeed,
            operation = operation,
            failOnBuild = failOnBuild
        )
    }

    fun printHelp() = println(
        """
    Usage:
      mtspark --repo /path/to/repo 
              [--language java|kotlin] 
              [--operation rename-local|rename-param|flip-if|insert-comment]   # default: rename-local
              [--file-glob "<glob>"] 
              [--include-tests] 
              [--limit N]
              [--pick-index N]            
              [--random-seed SEED]        
              [--keep-mutation] 
              [--json-out /path/to/result.json] 
              [--diagnostics-lines N]
              [--fail-on-build]           # exit non-zero iff mutated build fails
              
    Operations:
      rename-local     - Rename a local variable
      rename-param     - Rename a method/constructor parameter (incl. lambda params)
      flip-if          - Flip 'if' condition (control flow mutation)
      double-negate-if   - Wrap if-condition with !! (semantics-preserving)
      insert-comment   - Insert a random comment in the code
    """.trimIndent()
    )
}