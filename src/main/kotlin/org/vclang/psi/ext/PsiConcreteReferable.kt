package org.vclang.psi.ext

import com.jetbrains.jetpad.vclang.term.Concrete


interface PsiConcreteReferable: PsiGlobalReferable {
    fun computeConcrete(): Concrete.ReferableDefinition
}