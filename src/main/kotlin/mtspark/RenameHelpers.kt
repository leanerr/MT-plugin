// RenameHelpers.kt
package mtspark

import com.github.javaparser.JavaParser
import com.github.javaparser.ParserConfiguration
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.CallableDeclaration
import com.github.javaparser.ast.body.ConstructorDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.LambdaExpr
import com.github.javaparser.ast.expr.NameExpr
import com.github.javaparser.ast.stmt.BlockStmt
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter
import com.github.javaparser.symbolsolver.JavaSymbolSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

object RenameHelpers {

    fun makeParser(sourceRoot: Path?): JavaParser {
        val typeSolver = CombinedTypeSolver(ReflectionTypeSolver())
        if (sourceRoot != null && Files.exists(sourceRoot)) {
            typeSolver.add(JavaParserTypeSolver(sourceRoot.toFile()))
        }
        val config = ParserConfiguration().setSymbolResolver(JavaSymbolSolver(typeSolver))
        return JavaParser(config)
    }

    /** Safely initialize lexical preservation even if already initialized. */
    fun setupLexical(cu: CompilationUnit) {
        runCatching { LexicalPreservingPrinter.setup(cu) }
    }

    fun printLexical(cu: CompilationUnit): String =
        LexicalPreservingPrinter.print(cu)

    /** Pick by index (clamped), or randomSeed, or first. */
    fun <T> pick(list: List<T>, pickIndex: Int?, randomSeed: Long?): T =
        when {
            pickIndex != null -> {
                val idx = min(max(0, pickIndex), list.lastIndex)
                list[idx]
            }
            randomSeed != null -> {
                val r = Random(randomSeed)
                list[r.nextInt(list.size)]
            }
            else -> list.first()
        }

    /** Rename usages that satisfy the predicate (typically symbol-identity). */
    fun renameUsages(
        cu: CompilationUnit,
        oldName: String,
        newName: String,
        shouldRename: (NameExpr) -> Boolean
    ): Int {
        var count = 0
        cu.findAll(NameExpr::class.java).forEach { ne ->
            if (ne.nameAsString == oldName && shouldRename(ne)) {
                ne.setName(newName)
                count++
            }
        }
        return count
    }

    /** Return the body BlockStmt for a method/constructor, else null. */
    fun getCallableBody(callable: CallableDeclaration<*>): BlockStmt? =
        when (callable) {
            is MethodDeclaration      -> callable.body.orElse(null)
            is ConstructorDeclaration -> callable.body
            else -> null
        }

    /** Return the body Node for a lambda (BlockStmt or the single expression body). */
    fun getLambdaBody(lambda: LambdaExpr): Node? =
        lambda.body

    /** Return the body Node for either a CallableDeclaration or a LambdaExpr. */
    fun getOwnerBody(owner: Node): Node? =
        when (owner) {
            is CallableDeclaration<*> -> getCallableBody(owner)
            is LambdaExpr             -> getLambdaBody(owner)
            else                      -> null
        }
}