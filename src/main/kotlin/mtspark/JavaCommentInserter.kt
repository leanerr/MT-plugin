package mtspark

import com.github.javaparser.ast.comments.LineComment
import java.io.File
import java.nio.file.Path
import java.time.Instant

/**
 * Inserts an orphan line comment at the top of the CU. Zero behavior change.
 * Useful as a "touch" mutation that should never break builds.
 */
class JavaCommentInserter(
    private val sourceRoot: Path? = null
) : Mutator {

    override fun mutate(file: File): MutationResult {
        if (!file.extension.equals("java", ignoreCase = true)) {
            return MutationResult(file, null, null, changed = false)
        }

        val parser = RenameHelpers.makeParser(sourceRoot)
        val cu = parser.parse(file).result.orElse(null)
            ?: return MutationResult(file, null, null, changed = false)

        RenameHelpers.setupLexical(cu)

        val stamp = Instant.now().toString()
        cu.addOrphanComment(LineComment("mt: touched by mtspark at $stamp"))

        file.writeText(RenameHelpers.printLexical(cu))
        return MutationResult(file, "comment", "comment_inserted", changed = true)
    }
}