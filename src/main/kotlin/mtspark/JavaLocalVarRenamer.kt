package mtspark

import com.github.javaparser.ast.body.VariableDeclarator
import com.github.javaparser.ast.expr.NameExpr
import com.github.javaparser.ast.stmt.BlockStmt
import com.github.javaparser.ast.visitor.VoidVisitorAdapter
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration
import java.io.File
import java.nio.file.Path
import kotlin.random.Random

class JavaLocalVarRenamer(
    /** Optional: project source root to improve symbol resolution (e.g. repo/src/main/java). */
    private val sourceRoot: Path? = null,
    /** 0-based index of which local var to rename (if provided). */
    private val pickIndex: Int? = null,
    /** If set (and no pickIndex), pick a random local var using this seed. */
    private val randomSeed: Long? = null
) : Mutator {

    override fun mutate(file: File): MutationResult {
        if (!file.extension.equals("java", ignoreCase = true)) {
            return MutationResult(file, null, null, changed = false)
        }

        // Parser + lexical preservation via helper
        val parser = RenameHelpers.makeParser(sourceRoot)
        val cu = parser.parse(file).result.orElse(null)
            ?: return MutationResult(file, null, null, changed = false)
        RenameHelpers.setupLexical(cu)

        // Collect candidate locals (under a BlockStmt), skip names already ending with "_mt"
        val candidates = mutableListOf<VariableDeclarator>()
        cu.accept(object : VoidVisitorAdapter<Unit>() {
            override fun visit(v: VariableDeclarator, arg: Unit?) {
                val parent = v.parentNode.orElse(null)
                if (parent != null &&
                    !v.nameAsString.endsWith("_mt") &&
                    parent.findAncestor(BlockStmt::class.java).isPresent
                ) {
                    candidates += v
                }
                super.visit(v, arg)
            }
        }, Unit)

        if (candidates.isEmpty()) {
            return MutationResult(file, null, null, changed = false)
        }

        // Choose target: randomSeed (if set) > pickIndex > first
        val vd: VariableDeclarator =
            if (randomSeed != null) {
                val r = Random(randomSeed)
                candidates[r.nextInt(candidates.size)]
            } else {
                RenameHelpers.pick(candidates, pickIndex, null)
            }

        val oldName = vd.nameAsString
        val newName = "${oldName}_mt"

        // Resolve declaration to enable precise usage renaming
        val resolvedDecl = runCatching { vd.resolve() }.getOrNull()
        val typeDesc = runCatching { resolvedDecl?.type?.describe() }.getOrNull()

        // Rename declaration
        vd.setName(newName)

        // Rename usages: identity OR (same name + same type.describe())
        var renamed = 0
        if (resolvedDecl != null) {
            renamed = RenameHelpers.renameUsages(cu, oldName, newName) { ne ->
                val cand = runCatching { ne.resolve() }.getOrNull()
                cand == resolvedDecl ||
                        (cand is ResolvedValueDeclaration &&
                                cand.name == oldName &&
                                runCatching { cand.type.describe() }.getOrNull() == typeDesc)
            }
        }

        // Fallback: if nothing resolved (typical for locals), rename inside the same block
        if (renamed == 0) {
            val block = vd.findAncestor(BlockStmt::class.java).orElse(null)
            block?.findAll(NameExpr::class.java)?.forEach { ne ->
                if (ne.nameAsString == oldName) ne.setName(newName)
            }
        }

        // Write back with preserved formatting
        file.writeText(RenameHelpers.printLexical(cu))
        return MutationResult(file, oldName, newName, changed = true)
    }
}