package org.arend.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.arend.ArendIcons
import org.arend.naming.scope.Scope
import org.arend.psi.*
import org.arend.psi.stubs.ArendDefModuleStub


abstract class ModuleAdapter : ReferableAdapter<ArendDefModuleStub>, ArendDefModule {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendDefModuleStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val scope: Scope
        get() = groupScope

    override val statements: List<ArendStatement>
        get() = where?.statementList ?: emptyList()

    override fun getPrec(): ArendPrec? = null

    override fun getParentGroup() = parent.ancestor<ArendGroup>()

    override fun getReferable() = this

    override fun getSubgroups(): List<ArendGroup> = where?.statementList?.mapNotNull { it.definition ?: it.defModule } ?: emptyList()

    override fun getDynamicSubgroups(): List<ArendGroup> = emptyList()

    override fun getNamespaceCommands(): List<ArendStatCmd> = where?.statementList?.mapNotNull { it.statCmd } ?: emptyList()

    override fun getInternalReferables(): List<ArendInternalReferable> = emptyList()

    override fun getIcon(flags: Int) = ArendIcons.MODULE_DEFINITION
}