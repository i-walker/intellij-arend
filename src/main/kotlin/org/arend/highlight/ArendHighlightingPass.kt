package org.arend.highlight

import com.intellij.codeInsight.daemon.impl.HighlightInfoProcessor
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import org.arend.naming.reference.ErrorReference
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.Referable
import org.arend.naming.reference.TCClassReferable
import org.arend.naming.resolving.ResolverListener
import org.arend.naming.resolving.visitor.DefinitionResolveNameVisitor
import org.arend.psi.*
import org.arend.psi.ext.ArendIPNameImplMixin
import org.arend.psi.ext.ArendReferenceElement
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.TCDefinition
import org.arend.psi.ext.impl.ArendGroup
import org.arend.psi.listener.ArendDefinitionChangeListenerService
import org.arend.resolving.ArendResolveCache
import org.arend.resolving.PsiConcreteProvider
import org.arend.resolving.TCReferableWrapper
import org.arend.resolving.WrapperReferableConverter
import org.arend.term.NameRenaming
import org.arend.term.NamespaceCommand
import org.arend.term.concrete.Concrete
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.error.ErrorService

class ArendHighlightingPass(file: ArendFile, group: ArendGroup, editor: Editor, textRange: TextRange, highlightInfoProcessor: HighlightInfoProcessor)
    : BaseGroupPass(file, group, editor, "Arend resolver annotator", textRange, highlightInfoProcessor) {

    private val psiListenerService = myProject.service<ArendDefinitionChangeListenerService>()

    init {
        myProject.service<TypeCheckingService>().initialize()
    }

    override fun collectInfo(progress: ProgressIndicator) {
        val concreteProvider = PsiConcreteProvider(myProject, WrapperReferableConverter, this, null, false)
        file.concreteProvider = concreteProvider
        val resolverCache = myProject.service<ArendResolveCache>()
        DefinitionResolveNameVisitor(concreteProvider, this, object : ResolverListener {
            private fun replaceCache(reference: ArendReferenceElement, resolvedRef: Referable?) {
                val newRef = if (resolvedRef is ErrorReference) null else resolvedRef?.underlyingReferable
                val oldRef = resolverCache.replaceCache(newRef, reference)
                if (oldRef != null && oldRef != newRef && !(newRef == null && oldRef == TCClassReferable.NULL_REFERABLE)) {
                    resetDefinition = true
                }
            }

            private fun replaceCache(list: List<ArendReferenceElement>, resolvedRefs: List<Referable?>) {
                var i = 0
                for (reference in list) {
                    replaceCache(reference, if (i < resolvedRefs.size) resolvedRefs[i++] else null)
                }
            }

            private fun resolveReference(data: Any?, referent: Referable, resolvedRefs: List<Referable?>) {
                val list = when (data) {
                    is ArendLongName -> data.refIdentifierList
                    is ArendIPNameImplMixin -> {
                        val last: List<ArendReferenceElement> = listOf(data)
                        data.parentLongName?.let { it.refIdentifierList + last } ?: last
                    }
                    is ArendReferenceElement -> listOf(data)
                    else -> return
                }
                val lastReference = list.lastOrNull() ?: return
                if (lastReference is ArendRefIdentifier && referent is GlobalReferable && referent.precedence.isInfix) {
                    holder.createInfoAnnotation(lastReference, null).textAttributes = ArendHighlightingColors.OPERATORS.textAttributesKey
                }

                var index = 0
                while (index < resolvedRefs.size - 1 && resolvedRefs[index] !is ErrorReference) {
                    index++
                }

                if (index > 0) {
                    val last = list[index]
                    val textRange = if (last is ArendIPNameImplMixin) {
                        last.parentLiteral?.let { literal ->
                            literal.longName?.let { longName ->
                                TextRange(longName.textRange.startOffset, (literal.dot ?: longName).textRange.endOffset)
                            }
                        }
                    } else {
                        (last.parent as? ArendLongName)?.let { longName ->
                            last.extendLeft.prevSibling?.let { nextToLast ->
                                TextRange(longName.textRange.startOffset, nextToLast.textRange.endOffset)
                            }
                        }
                    }

                    if (textRange != null) {
                        holder.createInfoAnnotation(textRange, null).textAttributes = ArendHighlightingColors.LONG_NAME.textAttributesKey
                    }
                }

                replaceCache(list, resolvedRefs)
            }

            override fun referenceResolved(argument: Concrete.Expression?, originalRef: Referable, refExpr: Concrete.ReferenceExpression, resolvedRefs: List<Referable?>) {
                resolveReference(refExpr.data, refExpr.referent, resolvedRefs)
            }

            override fun patternResolved(originalRef: Referable, pattern: Concrete.ConstructorPattern, resolvedRefs: List<Referable?>) {
                resolveReference(pattern.data, pattern.constructor, resolvedRefs)
            }

            override fun coPatternResolved(element: Concrete.CoClauseElement, originalRef: Referable?, referable: Referable, resolvedRefs: List<Referable?>) {
                val data = element.data
                (((data as? ArendCoClauseDef)?.parent ?: data) as? CoClauseBase)?.longName?.let {
                    resolveReference(it, referable, resolvedRefs)
                }
            }

            override fun overriddenFieldResolved(overriddenField: Concrete.OverriddenField, originalRef: Referable?, referable: Referable, resolvedRefs: List<Referable?>) {
                (overriddenField.data as? ArendOverriddenField)?.longName?.let {
                    resolveReference(it, referable, resolvedRefs)
                }
            }

            override fun namespaceResolved(namespaceCommand: NamespaceCommand, resolvedRefs: List<Referable?>) {
                (namespaceCommand as? ArendStatCmd)?.longName?.let {
                    replaceCache(it.refIdentifierList, resolvedRefs)
                }
            }

            override fun renamingResolved(renaming: NameRenaming, originalRef: Referable?, resolvedRef: Referable?) {
                (renaming as? ArendNsId)?.refIdentifier?.let {
                    replaceCache(it, resolvedRef)
                }
            }

            private fun highlightParameters(definition: Concrete.ReferableDefinition) {
                for (parameter in Concrete.getParameters(definition, true) ?: emptyList()) {
                    if (parameter.type?.underlyingTypeClass != null) {
                        val list = when (val param = parameter.data) {
                            is ArendFieldTele -> param.fieldDefIdentifierList
                            is ArendNameTele -> param.identifierOrUnknownList
                            is ArendTypeTele -> param.typedExpr?.identifierOrUnknownList
                            is TCReferableWrapper -> (param.data as? ArendFieldDefIdentifier)?.let { listOf(it) }
                            else -> null
                        }
                        for (id in list ?: emptyList()) {
                            holder.createInfoAnnotation(id, null).textAttributes = ArendHighlightingColors.CLASS_PARAMETER.textAttributesKey
                        }
                    }
                }
            }

            private var resetDefinition = false
            private var currentDef: Concrete.Definition? = null

            override fun beforeDefinitionResolved(definition: Concrete.Definition?) {
                currentDef = definition
                resetDefinition = false
            }

            override fun definitionResolved(definition: Concrete.Definition) {
                progress.checkCanceled()

                if (resetDefinition) {
                    (definition.data.underlyingReferable as? TCDefinition)?.let {
                        psiListenerService.updateDefinition(it, file, true)
                    }
                }

                (definition.data.underlyingReferable as? PsiLocatedReferable)?.nameIdentifier?.let {
                    holder.createInfoAnnotation(it, null).textAttributes = ArendHighlightingColors.DECLARATION.textAttributesKey
                }

                highlightParameters(definition)
                if (definition is Concrete.DataDefinition) {
                    for (constructorClause in definition.constructorClauses) {
                        for (constructor in constructorClause.constructors) {
                            highlightParameters(constructor)
                        }
                    }
                }

                advanceProgress(1)
            }
        }).resolveGroup(group, WrapperReferableConverter, group.scope)
    }

    override fun applyInformationWithProgress() {
        myProject.service<ErrorService>().clearNameResolverErrors(file)
        super.applyInformationWithProgress()
    }
}