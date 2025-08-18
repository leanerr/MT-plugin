package mtspark

import java.io.File
import kotlin.math.max
import kotlin.math.min

data class CompileError(
    val file: File?,          // may be null if path isn't local
    val line: Int?,           // 1-based
    val col: Int?,            // 1-based (when caret ^ is present)
    val message: String,
    val raw: String           // original lines from the tool output
)

object Diagnostics {

    /**
     * Parse classic javac-format errors, which Gradle/Maven forward, e.g.:
     *   /path/App.java:7: error: cannot find symbol
     *     System.out.println(count);
     *                            ^
     */
    fun parseJavac(text: String): List<CompileError> {
        val lines = text.lines()
        val out = mutableListOf<CompileError>()
        var i = 0
        val head = Regex("""^(.+\.java):(\d+):\s+(?:error|warning):\s+(.*)$""")

        while (i < lines.size) {
            val m = head.find(lines[i])
            if (m != null) {
                val path = m.groupValues[1]
                val lineNum = m.groupValues[2].toIntOrNull()
                val msg = m.groupValues[3]

                var col: Int? = null
                var raw = lines[i]

                // Try to read code line + caret line
                if (i + 2 < lines.size) {
                    val codeLine = lines[i + 1]
                    val caret = lines[i + 2]
                    val caretIdx = caret.indexOf('^')
                    if (caretIdx >= 0) {
                        col = caretIdx + 1 // 1-based
                        raw += "\n$codeLine\n$caret"
                        i += 3
                    } else {
                        i += 1
                    }
                } else {
                    i += 1
                }

                val f = File(path)
                out += CompileError(
                    file = if (f.exists()) f else null,
                    line = lineNum,
                    col = col,
                    message = msg,
                    raw = raw
                )
            } else {
                i++
            }
        }
        return out
    }

    fun renderSnippet(file: File, line: Int?, col: Int?, highlight: String?, context: Int = 2): String {
        if (!file.exists() || line == null) return "(no local source to show)"
        val all = file.readLines()
        val idx = line - 1
        val from = max(0, idx - context)
        val to = min(all.lastIndex, idx + context)

        val sb = StringBuilder()
        for (ln in from..to) {
            val mark = if (ln == idx) ">>" else "  "
            val num = (ln + 1).toString().padStart(4, ' ')
            var text = all[ln]
            if (!highlight.isNullOrBlank() && ln == idx) {
                // visually bracket occurrences of the highlight token
                text = text.replace(Regex("""\b$highlight\b"""), "«$highlight»")
            }
            sb.append("$mark $num | $text\n")
            if (ln == idx && col != null && col > 0) {
                sb.append("   ____| ").append(" ".repeat(max(0, col - 1))).append("^\n")
            }
        }
        return sb.toString().trimEnd()
    }

    /**
     * Build a friendly diagnostic report from a failed BuildResult.
     * If it looks like our rename caused a symbol error, add a hint.
     */
    fun explain(build: BuildResult, mutation: MutationResult): String {
        val combined = build.out + "\n" + build.err
        val errors = parseJavac(combined)
        if (errors.isEmpty()) {
            return """
                === Failure diagnostics ===
                (No structured compiler errors found. Showing tail of output)

                ---- LAST 2000 CHARS ----
                ${combined.takeLast(2000)}
            """.trimIndent()
        }

        val sb = StringBuilder()
        sb.appendLine("=== Failure diagnostics ===")
        sb.appendLine("Parsed ${errors.size} compile error(s):")
        sb.appendLine()

        for ((idx, e) in errors.withIndex()) {
            sb.appendLine("[$idx] ${e.message}")
            sb.appendLine("File: ${e.file?.absolutePath ?: "(unknown)"}  Line: ${e.line ?: "?"}  Col: ${e.col ?: "?"}")
            if (e.file != null) {
                sb.appendLine()
                sb.appendLine(renderSnippet(e.file, e.line, e.col, mutation.oldName ?: mutation.newName))
            }
            sb.appendLine()
        }

        // Heuristics / hints
        val old = mutation.oldName
        val new = mutation.newName
        if (!old.isNullOrBlank() && combined.contains("cannot find symbol")) {
            // If the error text or raw lines show the old name, we likely missed a reference.
            val mentionsOld = combined.contains(Regex("""\b$old\b"""))
            val mentionsNew = !new.isNullOrBlank() && combined.contains(Regex("""\b${Regex.escape(new!!)}\b"""))
            sb.appendLine("Hints:")
            when {
                mentionsOld && !mentionsNew -> {
                    sb.appendLine("• Looks like references to '$old' remain but declaration was renamed to '$new'.")
                    sb.appendLine("  This usually means the reference is outside the renamer's scope (e.g., lambda/inner class) or symbol resolution failed.")
                    sb.appendLine("  Try enabling source-root symbol solving, or broaden the reference update strategy.")
                }
                mentionsNew -> {
                    sb.appendLine("• The new name '$new' appears in errors; check for shadowing or a name clash in the same scope.")
                }
                else -> {
                    sb.appendLine("• Rename likely triggered a type/flow issue not directly mentioning the identifier. Inspect the snippets above.")
                }
            }
        }

        return sb.toString().trimEnd()
    }
}