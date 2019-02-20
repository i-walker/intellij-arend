package org.arend.refactoring.move

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.refactoring.move.MoveHandlerDelegate
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.arend.psi.*
import org.arend.psi.ext.impl.ArendGroup
import org.arend.term.abs.Abstract
import org.arend.term.group.ChildGroup

class ArendMoveHandlerDelegate: MoveHandlerDelegate() {

    override fun tryToMove(element: PsiElement?, project: Project?, dataContext: DataContext?, reference: PsiReference?, editor: Editor?): Boolean {
        if (project != null && element is ArendGroup && (element !is Abstract.Definition || element.enclosingClass == null)) {
            val elements: Array<ArendGroup> = arrayOf(element)
            if (!CommonRefactoringUtil.checkReadOnlyStatusRecursively(project, elements.toList(), true)) return false
            return showDialog(project, elements.toList())
        }

        return false
    }

    private fun showDialog(project: Project, elements: List<ArendGroup>): Boolean {
        if (elements.isNotEmpty()) {
            val group = getCommonContainer(elements)
            if (group != null && group is PsiElement) { /* Ensure elements are members of the same group */
                val module = group.module
                return if (module != null) {
                    ArendMoveMembersDialog(project, elements, group, module).show()
                    true
                } else false
            }
        }
        return false
    }

    companion object {
        fun getCommonContainer(elements: List<ArendGroup>): ChildGroup? {
            val group = elements.first().parentGroup
            return if (group != null && elements.subList(1, elements.size).map { it.parentGroup }.all { it == group }) group else null
        }
    }

}