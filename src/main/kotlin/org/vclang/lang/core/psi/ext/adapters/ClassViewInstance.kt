package org.vclang.lang.core.psi.ext.adapters

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.Abstract
import com.jetbrains.jetpad.vclang.term.AbstractDefinitionVisitor
import org.vclang.lang.core.Surrogate
import org.vclang.lang.core.psi.VcDefInstance

abstract class ClassViewInstanceAdapter(node: ASTNode) : DefinitionAdapter(node),
                                                         VcDefInstance {
    private var isDefault: Boolean? = null
    private var arguments: List<Surrogate.Argument>? = null
    private var classView: Surrogate.ReferenceExpression? = null
    private var classFieldImpls: List<Surrogate.ClassFieldImpl>? = null
    private var classifyingDefinition: Abstract.Definition? = null

    fun reconstruct(
            position: Surrogate.Position?,
            isDefault: Boolean?,
            name: String?,
            precedence: Abstract.Precedence?,
            arguments: List<Surrogate.Argument>?,
            classView: Surrogate.ReferenceExpression?,
            classFieldImpls: List<Surrogate.ClassFieldImpl>?
    ): ClassViewInstanceAdapter {
        super.reconstruct(position, name, precedence)
        this.isDefault = isDefault
        this.arguments = arguments
        this.classView = classView
        this.classFieldImpls = classFieldImpls
        return this
    }

    override fun isDefault(): Boolean = isDefault ?: throw IllegalStateException()

    override fun getArguments(): List<Surrogate.Argument> =
            arguments ?: throw IllegalStateException()

    override fun getClassView(): Surrogate.ReferenceExpression =
            classView ?: throw IllegalStateException()

    override fun getClassifyingDefinition(): Abstract.Definition? = classifyingDefinition

    fun setClassifyingDefinition(classifyingDefinition: Abstract.Definition?) {
        this.classifyingDefinition = classifyingDefinition
    }

    override fun getClassFieldImpls(): List<Surrogate.ClassFieldImpl> =
            classFieldImpls ?: throw IllegalStateException()

    override fun <P, R> accept(visitor: AbstractDefinitionVisitor<in P, out R>, params: P): R =
            visitor.visitClassViewInstance(this, params)
}