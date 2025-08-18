package mtspark

import com.github.javaparser.ast.expr.EnclosedExpr
import com.github.javaparser.ast.expr.UnaryExpr
import com.github.javaparser.ast.stmt.IfStmt
import java.io.File
import java.nio.file.Path

/**
 * Semantics-preserving: wraps an if-condition with a double negation.
 *   if (C) { ... }  ==>  if (!!(C)) { ... }
 *
 * Selection controlled by --pick-index / --random-seed (via RenameHelpers.pick).
 */
class JavaDoubleNegationIfMutator(
    private val sourceRoot: Path? = null,
    private val pickIndex: Int? = null,
    private val randomSeed: Long? = null
) : Mutator {

    override fun mutate(file: File): MutationResult {
        if (!file.extension.equals("java", ignoreCase = true)) {
            return MutationResult(file, null, null, changed = false)
        }

        val parser = RenameHelpers.makeParser(sourceRoot)
        val cu = parser.parse(file).result.orElse(null)
            ?: return MutationResult(file, null, null, changed = false)

        RenameHelpers.setupLexical(cu)

        // Candidates: any if-statement
        val candidates = cu.findAll(IfStmt::class.java)
        if (candidates.isEmpty()) {
            return MutationResult(file, null, null, changed = false)
        }

        val target = RenameHelpers.pick(candidates, pickIndex, randomSeed)

        // Build !!(originalCondition)
        val original = target.condition.clone()
        val enclosed = EnclosedExpr(original)
        val not = UnaryExpr(enclosed, UnaryExpr.Operator.LOGICAL_COMPLEMENT)
        val doubleNot = UnaryExpr(EnclosedExpr(not), UnaryExpr.Operator.LOGICAL_COMPLEMENT)

        target.setCondition(doubleNot)

        file.writeText(RenameHelpers.printLexical(cu))
        return MutationResult(file, "if", "if_double_negated", changed = true)
    }
}