package org.jetbrains.dukat.ast.model.nodes

import org.jetbrains.dukat.astCommon.NameEntity
import org.jetbrains.dukat.tsmodel.types.TypeDeclaration

data class ClassNode(
        override val name: NameEntity,
        override val members: List<MemberNode>,
        val typeParameters: List<TypeDeclaration>,
        val parentEntities: List<HeritageNode>,

        override val uid: String,
        override val external: Boolean
) : ClassLikeNode
