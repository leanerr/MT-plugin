package ui

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.FormBuilder
import runner.SweAgentRunner
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

class MtToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val issueField = JTextField()
        val runBtn     = JButton("Run SWE-agent")
        val mutateBtn  = JButton("Mutate → rename var")
        val output     = JTextArea(18, 50).apply { isEditable = false }

        // 1) Run the agent and display JSON
        runBtn.addActionListener {
            val id = issueField.text.trim().takeIf { it.isNotEmpty() } ?: return@addActionListener
            runBtn.isEnabled = false
            output.text = "Running agent on $id …\n"
            thread {
                try {
                    val resultJson = SweAgentRunner.run(id).toString(2)
                    SwingUtilities.invokeLater {
                        output.append(resultJson)
                        runBtn.isEnabled = true
                    }
                } catch (ex: Exception) {
                    SwingUtilities.invokeLater {
                        output.append("\nERROR: ${ex.message}")
                        runBtn.isEnabled = true
                    }
                }
            }
        }

        // 2) Mutate code, re-run both, compare & show diff
        mutateBtn.addActionListener {
            val id = issueField.text.trim().takeIf { it.isNotEmpty() } ?: return@addActionListener

            // A) locate editor & PSI file
            val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return@addActionListener
            PsiDocumentManager.getInstance(project).commitAllDocuments()
            val psiFile: PsiFile = PsiDocumentManager.getInstance(project)
                .getPsiFile(editor.document) ?: return@addActionListener

            // B) find first local var, compute names
            val varDecl = PsiTreeUtil.findChildOfType(psiFile, PsiLocalVariable::class.java)
                ?: return@addActionListener
            val oldName = varDecl.name
            val newName = "${oldName}_mt"

            // C) apply rename inside write command
            WriteCommandAction.runWriteCommandAction(project) {
                varDecl.setName(newName)
            }
            PsiDocumentManager.getInstance(project).commitAllDocuments()

            SwingUtilities.invokeLater {
                output.append("\n\n--- After rename: $oldName → $newName ---\n")
                mutateBtn.isEnabled = false
            }

            // D) run original & mutated, then diff
            thread {
                try {
                    val originalJson = SweAgentRunner.run(id).toString(2)
                    val mutatedJson  = SweAgentRunner.run(id).toString(2)

                    SwingUtilities.invokeLater {
                        output.append("\n\n=== Original Output ===\n")
                        output.append(originalJson)
                        output.append("\n\n=== Mutated Output ===\n")
                        output.append(mutatedJson)

                        if (originalJson == mutatedJson) {
                            output.append("\n\n✅ No behavioral change detected.")
                        } else {
                            output.append("\n\n❌ Behavioral change detected!")

                            // build two DiffContents and titles in lists
                            val factory = DiffContentFactory.getInstance()
                            val contentList = listOf(
                                factory.create(project, originalJson),
                                factory.create(project, mutatedJson)
                            )
                            val titleList = listOf("Original", "Mutated")

                            val request = SimpleDiffRequest(
                                "SWE-agent Output Difference",
                                contentList,
                                titleList
                            )
                            DiffManager.getInstance().showDiff(project, request)
                        }

                        mutateBtn.isEnabled = true
                    }
                } catch (ex: Exception) {
                    SwingUtilities.invokeLater {
                        output.append("\nERROR during variant run: ${ex.message}")
                        mutateBtn.isEnabled = true
                    }
                }
            }
        }

        // put everything on screen
        val panel: JPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("SWE-bench issue ID:", issueField)
            .addComponent(runBtn)
            .addComponent(mutateBtn)
            .addComponentFillVertically(JBScrollPane(output), 0)
            .panel

        toolWindow.component.add(panel)
    }
}