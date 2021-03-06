package org.arend.highlight

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.HighlightInfoProcessor
import com.intellij.codeInsight.daemon.impl.LineMarkersPass
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.arend.psi.ArendFile
import org.arend.psi.ext.TCDefinition
import org.arend.psi.ext.impl.ArendGroup
import org.arend.quickfix.implementCoClause.doAnnotate
import org.arend.settings.ArendSettings
import org.arend.term.concrete.Concrete
import org.arend.term.group.Group
import org.arend.typechecking.*
import org.arend.typechecking.error.NotificationErrorReporter
import org.arend.typechecking.provider.EmptyConcreteProvider
import org.arend.typechecking.visitor.DesugarVisitor
import org.arend.typechecking.visitor.DumbTypechecker
import org.arend.util.FullName

class BackgroundTypecheckerPass(file: ArendFile, group: ArendGroup, editor: Editor, textRange: TextRange, highlightInfoProcessor: HighlightInfoProcessor)
    : BaseGroupPass(file, group, editor, "Arend background typechecker annotator", textRange, highlightInfoProcessor) {

    private val typeCheckingService = myProject.service<TypeCheckingService>()
    private val definitionBlackListService = service<DefinitionBlacklistService>()
    private val arendSettings = service<ArendSettings>()
    private val definitionsToTypecheck = ArrayList<TCDefinition>()
    private var lineMarkersPass: LineMarkersPass? = null

    override fun visitDefinition(definition: Concrete.Definition, progress: ProgressIndicator) {
        DesugarVisitor.desugar(definition, file.concreteProvider, this)

        progress.checkCanceled()
        definition.accept(object : DumbTypechecker(this) {
            override fun visitFunction(def: Concrete.BaseFunctionDefinition, params: Void?): Void? {
                super.visitFunction(def, params)
                doAnnotate(def.data.data as? PsiElement, holder)
                return null
            }

            override fun visitClassFieldImpl(classFieldImpl: Concrete.ClassFieldImpl, params: Void?) {
                doAnnotate(classFieldImpl.data as? PsiElement, holder)
                super.visitClassFieldImpl(classFieldImpl, params)
            }

            override fun visitClassExt(expr: Concrete.ClassExtExpression, params: Void?): Void? {
                doAnnotate(expr.data as? PsiElement, holder)
                super.visitClassExt(expr, params)
                return null
            }

            override fun visitNew(expr: Concrete.NewExpression, params: Void?): Void? {
                if (expr.expression !is Concrete.ClassExtExpression)
                    doAnnotate(expr.data as? PsiElement, holder)
                super.visitNew(expr, params)
                return null
            }
        }, null)
    }

    private fun typecheckDefinition(typechecking: ArendTypechecking, definition: TCDefinition, progress: ProgressIndicator): Concrete.Definition? {
        val result = (typechecking.concreteProvider.getConcrete(definition) as? Concrete.Definition)?.let {
            val ok = definitionBlackListService.runTimed(definition, progress) {
                typechecking.typecheckDefinitions(listOf(it), ArendCancellationIndicator(progress))
            }

            if (!ok) {
                NotificationErrorReporter(myProject).warn("Typechecking of ${FullName(it.data)} was interrupted after ${arendSettings.typecheckingTimeLimit} second(s)")
                if (definitionsToTypecheck.isEmpty() || definitionsToTypecheck.last() != definition) {
                    DaemonCodeAnalyzer.getInstance(myProject).restart(file)
                }
            }

            it
        }

        advanceProgress(1)
        return result
    }

    override fun collectInfo(progress: ProgressIndicator) {
        when (arendSettings.typecheckingMode) {
            ArendSettings.TypecheckingMode.SMART -> if (definitionsToTypecheck.isNotEmpty()) {
                val typechecking = ArendTypechecking.create(myProject, BackgroundTypecheckerState(typeCheckingService.typecheckerState))
                val lastModified = file.lastModifiedDefinition
                if (lastModified != null) {
                    val typechecked = if (definitionsToTypecheck.remove(lastModified)) {
                        typecheckDefinition(typechecking, lastModified, progress)?.let { typeCheckingService.typecheckerState.getTypechecked(it.data) }
                    } else null
                    if (typechecked?.status()?.withoutErrors() == true) {
                        file.lastModifiedDefinition = null
                        for (definition in definitionsToTypecheck) {
                            typecheckDefinition(typechecking, definition, progress)
                        }
                    } else {
                        for (definition in definitionsToTypecheck) {
                            visitDefinition(definition, progress)
                        }
                    }
                } else {
                    for (definition in definitionsToTypecheck) {
                        typecheckDefinition(typechecking, definition, progress)
                    }
                }

                val constructor = LineMarkersPass::class.java.declaredConstructors[0]
                constructor.isAccessible = true
                val pass = constructor.newInstance(file.project, file, document, textRange, textRange) as LineMarkersPass
                pass.id = Pass.LINE_MARKERS
                pass.collectInformation(progress)
                lineMarkersPass = pass
            }
            ArendSettings.TypecheckingMode.DUMB ->
                for (definition in definitionsToTypecheck) {
                    visitDefinition(definition, progress)
                }
            ArendSettings.TypecheckingMode.OFF -> {}
        }

        file.concreteProvider = EmptyConcreteProvider.INSTANCE
    }

    override fun applyInformationWithProgress() {
        super.applyInformationWithProgress()
        lineMarkersPass?.applyInformationToEditor()
    }

    override fun countDefinition(def: TCDefinition) =
        if (!definitionBlackListService.isBlacklisted(def) && typeCheckingService.getTypechecked(def) == null) {
            definitionsToTypecheck.add(def)
            true
        } else false

    override fun numberOfDefinitions(group: Group) =
        if (arendSettings.typecheckingMode == ArendSettings.TypecheckingMode.OFF) 0 else super.numberOfDefinitions(group)
}
