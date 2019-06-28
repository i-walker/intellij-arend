package org.arend.highlight

import com.intellij.codeInsight.daemon.impl.HighlightInfoProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import org.arend.psi.ArendFile
import org.arend.psi.ext.impl.ArendGroup
import org.arend.term.concrete.Concrete
import org.arend.typechecking.typecheckable.provider.ConcreteProvider
import org.arend.typechecking.visitor.DesugarVisitor

class DumbTypecheckerPass(file: ArendFile, group: ArendGroup, editor: Editor, textRange: TextRange, highlightInfoProcessor: HighlightInfoProcessor)
    : BasePass(file, group, editor, "Arend dumb typechecker annotator", textRange, highlightInfoProcessor) {

    override fun visitDefinition(definition: Concrete.Definition, concreteProvider: ConcreteProvider, progress: ProgressIndicator) {
        DesugarVisitor.desugar(definition, concreteProvider, errorReporter)
    }
}