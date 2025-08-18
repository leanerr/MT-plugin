package mtspark

import com.github.javaparser.ast.expr.EnclosedExpr
import com.github.javaparser.ast.expr.UnaryExpr
import com.github.javaparser.ast.stmt.BlockStmt
import com.github.javaparser.ast.stmt.IfStmt
import java.io.File
import java.nio.file.Path

/**
 * Control-flow mutator: pick an if-statement (with or without else),
 * ensure it has an else-branch, negate the condition and swap then/else blocks.
 * Semantics are preserved:
 *
 *   if (C) { A } else { B }  ==>  if (!(C)) { B } else { A }
 *   if (C) { A }             ==>  if (!(C)) { } else { A }
 *
 * Selection: --pick-index or --random-seed (helpers handle precedence).
 */
class JavaIfFlipMutator(
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

        // Take any if-statement; we'll add an empty else if it doesn't exist.
        val candidates = cu.findAll(IfStmt::class.java)
        if (candidates.isEmpty()) {
            return MutationResult(file, null, null, changed = false)
        }

        val target = RenameHelpers.pick(candidates, pickIndex, randomSeed)

        // Ensure there is an else-branch to allow safe swap.
        if (!target.elseStmt.isPresent) {
            target.setElseStmt(BlockStmt()) // add noop else { }
        }

        // Negate condition and swap branches:
        // if (C) {A} else {B}  ==>  if (!(C)) {B} else {A}
        val originalCond = target.condition.clone()
        val negated = UnaryExpr(EnclosedExpr(originalCond), UnaryExpr.Operator.LOGICAL_COMPLEMENT)

        val thenStmt = target.thenStmt
        val elseStmt = target.elseStmt.get()

        target.setCondition(negated)
        target.setThenStmt(elseStmt)
        target.setElseStmt(thenStmt)

        // Write back with lexical preservation
        file.writeText(RenameHelpers.printLexical(cu))

        // Not an identifier rename; return descriptive strings.
        return MutationResult(file, "if", "if_negated_swapped", changed = true)
    }
}