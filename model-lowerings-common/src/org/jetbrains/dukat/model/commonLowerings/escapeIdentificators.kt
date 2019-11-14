package org.jetbrains.dukat.model.commonLowerings

import org.jetbrains.dukat.astCommon.IdentifierEntity
import org.jetbrains.dukat.astCommon.NameEntity
import org.jetbrains.dukat.astCommon.QualifierEntity
import org.jetbrains.dukat.astModel.ClassModel
import org.jetbrains.dukat.astModel.EnumModel
import org.jetbrains.dukat.astModel.FunctionModel
import org.jetbrains.dukat.astModel.InterfaceModel
import org.jetbrains.dukat.astModel.MethodModel
import org.jetbrains.dukat.astModel.ModuleModel
import org.jetbrains.dukat.astModel.ParameterModel
import org.jetbrains.dukat.astModel.PropertyModel
import org.jetbrains.dukat.astModel.SourceSetModel
import org.jetbrains.dukat.astModel.TopLevelModel
import org.jetbrains.dukat.astModel.TypeValueModel
import org.jetbrains.dukat.astModel.VariableModel
import org.jetbrains.dukat.astModel.statements.AssignmentStatementModel
import org.jetbrains.dukat.astModel.statements.ChainCallModel
import org.jetbrains.dukat.astModel.statements.IndexStatementModel
import org.jetbrains.dukat.astModel.statements.ReturnStatementModel
import org.jetbrains.dukat.astModel.statements.StatementCallModel
import org.jetbrains.dukat.astModel.statements.StatementModel
import org.jetbrains.dukat.astModel.transform
import org.jetbrains.dukat.ownerContext.NodeOwner

private val CONTAINS_ONLY_UNDERSCORES = "_+".toRegex()

private val RESERVED_WORDS = setOf(
        "as",
        "fun",
        "in",
        "interface",
        "is",
        "object",
        "package",
        "return",
        "typealias",
        "typeof",
        "val",
        "var",
        "when"
)

private fun String.shouldEscape(): Boolean {
    val isReservedWord = RESERVED_WORDS.contains(this)
    val containsDollarSign = this.contains("$")
    val containsOnlyUnderscores = CONTAINS_ONLY_UNDERSCORES.matches(this)
    val isEscapedAlready = this.startsWith("`")

    return !isEscapedAlready && (isReservedWord || containsDollarSign || containsOnlyUnderscores)
}

private fun String.escape(): String {
    return if (shouldEscape()) {
        "`${this}`"
    } else {
        this
    }
}

private fun IdentifierEntity.escape(): IdentifierEntity {
    return if (value.shouldEscape()) {
        copy(value = value.escape())
    } else {
        this
    }
}

private fun QualifierEntity.escape(): QualifierEntity {
    return QualifierEntity(left.escape(), right.escape())
}

private fun NameEntity.escape(): NameEntity {
    return when (this) {
        is IdentifierEntity -> escape()
        is QualifierEntity -> escape()
    }
}

private class EscapeIdentificators : ModelWithOwnerTypeLowering {

    private fun StatementCallModel.escape(): StatementCallModel {
        return copy(
                value = value.escape(),
                params = params?.map { it.escape() },
                typeParameters = typeParameters.map { it.escape() }
        )
    }

    private fun StatementModel.escape(): StatementModel {
        return when (this) {
            is StatementCallModel -> escape()
            is ReturnStatementModel -> copy(statement = statement.escape())
            is ChainCallModel -> copy(left = left.escape(), right = right.escape())
            is AssignmentStatementModel -> copy(left = left.escape(), right = right.escape())
            is IndexStatementModel -> copy(array = array.escape(), index = index.escape())
            else -> this
        }
    }

    override fun lowerTypeValueModel(ownerContext: NodeOwner<TypeValueModel>): TypeValueModel {
        return super.lowerTypeValueModel(ownerContext.copy(node = ownerContext.node.copy(value = ownerContext.node.value.escape())))
    }

    override fun lowerPropertyModel(ownerContext: NodeOwner<PropertyModel>): PropertyModel {
        val declaration = ownerContext.node
        return super.lowerPropertyModel(ownerContext.copy(node = declaration.copy(name = declaration.name.escape())))
    }

    override fun lowerMethodModel(ownerContext: NodeOwner<MethodModel>): MethodModel {
        val declaration = ownerContext.node
        return super.lowerMethodModel(ownerContext.copy(node = declaration.copy(name = declaration.name.escape())))
    }

    override fun lowerFunctionModel(ownerContext: NodeOwner<FunctionModel>): FunctionModel {
        val declaration = ownerContext.node
        return super.lowerFunctionModel(ownerContext.copy(node = declaration.copy(
                name = declaration.name.escape(),
                body = declaration.body.map { it.escape() }
        )))
    }

    override fun lowerVariableModel(ownerContext: NodeOwner<VariableModel>): VariableModel {
        val declaration = ownerContext.node
        return super.lowerVariableModel(ownerContext.copy(node = declaration.copy(name = declaration.name.escape())))
    }

    override fun lowerParameterModel(ownerContext: NodeOwner<ParameterModel>): ParameterModel {
        val declaration = ownerContext.node
        val paramName = if (declaration.name == "this") {
            "self"
        } else {
            declaration.name.escape()
        }

        return super.lowerParameterModel(ownerContext.copy(node = declaration.copy(name = paramName)))
    }


    override fun lowerInterfaceModel(ownerContext: NodeOwner<InterfaceModel>): InterfaceModel {
        val declaration = ownerContext.node

        return super.lowerInterfaceModel(ownerContext.copy(node = declaration.copy(
                name = declaration.name.escape()
        )))
    }

    override fun lowerClassModel(ownerContext: NodeOwner<ClassModel>): ClassModel {
        val declaration = ownerContext.node

        return super.lowerClassModel(ownerContext.copy(node = declaration.copy(
                name = declaration.name.escape()
        )))
    }

    override fun lowerTopLevelModel(ownerContext: NodeOwner<TopLevelModel>): TopLevelModel {
        return when (val declaration = ownerContext.node) {
            is EnumModel -> declaration.copy(values = declaration.values.map { value -> value.copy(value = value.value.escape()) })
            else -> super.lowerTopLevelModel(ownerContext)
        }
    }

    override fun lowerRoot(moduleModel: ModuleModel, ownerContext: NodeOwner<ModuleModel>): ModuleModel {
        return moduleModel.copy(
                name = moduleModel.name.escape(),
                shortName = moduleModel.shortName.escape(),
                declarations = lowerTopLevelDeclarations(moduleModel.declarations, ownerContext),
                submodules = moduleModel.submodules.map { submodule -> lowerRoot(submodule, ownerContext.wrap(submodule)) }
        )
    }
}

fun ModuleModel.escapeIdentificators(): ModuleModel {
    return EscapeIdentificators().lowerRoot(this, NodeOwner(this, null))
}

fun SourceSetModel.escapeIdentificators() = transform { it.escapeIdentificators() }