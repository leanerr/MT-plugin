// JavaParamRenamer.kt
package mtspark

import com.github.javaparser.JavaParser
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.CallableDeclaration
import com.github.javaparser.ast.body.Parameter
import com.github.javaparser.ast.expr.LambdaExpr
import com.github.javaparser.ast.expr.NameExpr
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter
import com.github.javaparser.resolution.declarations.ResolvedParameterDeclaration
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration
import java.io.File
import java.nio.file.Path
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Renames one method/constructor *or lambda* parameter and updates its uses.
 * Selection: --pick-index or --random-seed (random wins if both set).
 */
class JavaParamRenamer(
    private val sourceRoot: Path? = null,
    private val pickIndex: Int? = null,
    private val randomSeed: Long? = null
) : Mutator {

    private enum class OwnerKind { CALLABLE, LAMBDA }

    private data class Cand(
        val param: Parameter,
        val owner: Node,        // CallableDeclaration<*> or LambdaExpr
        val kind: OwnerKind
    )

    override fun mutate(file: File): MutationResult {
        if (!file.extension.equals("java", ignoreCase = true)) {
            return MutationResult(file, null, null, changed = false)
        }

        val parser: JavaParser = RenameHelpers.makeParser(sourceRoot)
        val cu = parser.parse(file).result.orElse(null)
            ?: return MutationResult(file, null, null, changed = false)

        // Preserve formatting/comments
        RenameHelpers.setupLexical(cu)

        // Collect parameters from callables (with body) and lambdas; skip *_mt
        val candidates = mutableListOf<Cand>()

        cu.findAll(Parameter::class.java).forEach { p ->
            // method or ctor?
            val callable: CallableDeclaration<*>? =
                p.findAncestor(CallableDeclaration::class.java).orElse(null)
            if (callable != null) {
                val hasBody = when (callable) {
                    is com.github.javaparser.ast.body.MethodDeclaration      -> callable.body.isPresent
                    is com.github.javaparser.ast.body.ConstructorDeclaration -> callable.body != null
                    else -> false
                }
                if (hasBody && !p.nameAsString.endsWith("_mt")) {
                    candidates += Cand(p, callable, OwnerKind.CALLABLE)
                }
                return@forEach
            }

            // lambda?
            val lambda: LambdaExpr? = p.findAncestor(LambdaExpr::class.java).orElse(null)
            if (lambda != null && !p.nameAsString.endsWith("_mt")) {
                candidates += Cand(p, lambda, OwnerKind.LAMBDA)
            }
        }

        if (candidates.isEmpty()) {
            return MutationResult(file, null, null, changed = false)
        }

        // Choose candidate: pick-index > random-seed > first
        val chosen: Cand = when {
            pickIndex != null -> {
                val idx = min(max(0, pickIndex), candidates.lastIndex)
                candidates[idx]
            }
            randomSeed != null -> {
                val r = Random(randomSeed)
                candidates[r.nextInt(candidates.size)]
            }
            else -> candidates.first()
        }

        val param = chosen.param
        val oldName = param.nameAsString
        val newName = "${oldName}_mt"

        // Rename the parameter declaration first
        param.setName(newName)

        var renamed = 0

        if (chosen.kind == OwnerKind.LAMBDA) {
            // LAMBDA: keep it local to this lambda’s body (avoid cross-lambda bleed).
            val bodyNode = RenameHelpers.getOwnerBody(chosen.owner)
            bodyNode?.findAll(NameExpr::class.java)?.forEach { ne ->
                if (ne.nameAsString == oldName) {
                    ne.setName(newName)
                    renamed++
                }
            }
        } else {
            // CALLABLE: try precise symbol-based rename across CU.
            val resolvedParam: ResolvedParameterDeclaration? =
                runCatching { param.resolve() as ResolvedParameterDeclaration }.getOrNull()

            if (resolvedParam != null) {
                cu.findAll(NameExpr::class.java).forEach { ne ->
                    if (ne.nameAsString != oldName) return@forEach
                    val cand: ResolvedValueDeclaration? = runCatching { ne.resolve() }.getOrNull()
                    if (cand != null && cand == resolvedParam) {
                        ne.setName(newName)
                        renamed++
                    }
                }
            }

            // Fallback / extra safety: if nothing changed, restrict to the callable body.
            if (renamed == 0) {
                val bodyNode = RenameHelpers.getOwnerBody(chosen.owner)
                bodyNode?.findAll(NameExpr::class.java)?.forEach { ne ->
                    if (ne.nameAsString == oldName) {
                        ne.setName(newName)
                        renamed++
                    }
                }
            }
        }

        // Write back
        file.writeText(LexicalPreservingPrinter.print(cu))
        return MutationResult(file, oldName, newName, changed = true)
    }
}