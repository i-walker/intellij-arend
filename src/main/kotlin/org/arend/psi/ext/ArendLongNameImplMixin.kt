package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import org.arend.naming.reference.LongUnresolvedReference
import org.arend.naming.reference.UnresolvedReference
import org.arend.term.abs.Abstract
import org.arend.psi.ArendLongName


abstract class ArendLongNameImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendLongName {
    override val referenceNameElement
        get() = refIdentifierList.lastOrNull()

    override val referenceName: String
        get() = referenceNameElement?.referenceName ?: ""

    override val longName: List<String>
        get() = refIdentifierList.map { ref -> ref.referenceName }

    override val rangeInElement: TextRange
        get() = refIdentifierList.lastOrNull()?.rangeInElement ?: textRange

    override fun getData() = this

    override fun getReferent(): UnresolvedReference =
        LongUnresolvedReference.make(this, refIdentifierList.map { it.referenceName })

    override fun getHeadReference(): Abstract.Reference = refIdentifierList[0]

    override fun getTailReferences(): List<Abstract.Reference> {
        val refs = refIdentifierList
        return refs.subList(1, refs.size)
    }
}