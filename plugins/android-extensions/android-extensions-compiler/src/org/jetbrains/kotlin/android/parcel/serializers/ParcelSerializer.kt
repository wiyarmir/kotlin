/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.android.parcel.serializers

import com.intellij.util.containers.ConcurrentList
import org.jetbrains.kotlin.codegen.state.KotlinTypeMapper
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.builtIns
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.commons.InstructionAdapter
import java.util.*

interface ParcelSerializer {
    val asmType: Type

    fun writeValue(v: InstructionAdapter)
    fun readValue(v: InstructionAdapter)

    companion object {
        fun get(type: KotlinType, asmType: Type, typeMapper: KotlinTypeMapper, forceBoxed: Boolean = false): ParcelSerializer = when {
            asmType.sort == Type.ARRAY -> {
                val elementType = type.builtIns.getArrayElementType(type)

                wrapToNullAwareIfNeeded(
                        type,
                        ArrayParcelSerializer(asmType, get(elementType, typeMapper.mapType(elementType), typeMapper)))
            }
            asmType.isPrimitive() -> {
                if (forceBoxed || type.isMarkedNullable)
                    wrapToNullAwareIfNeeded(type, BoxedPrimitiveTypeParcelSerializer.forUnboxedType(asmType))
                else
                    PrimitiveTypeParcelSerializer.getInstance(asmType)
            }
            asmType.isString() -> NullCompliantObjectParcelSerializer(
                    asmType,
                    Method("writeString", "(Ljava/lang/String;)V"),
                    Method("readString", "()Ljava/lang/String;"))
            asmType.className == List::class.java.canonicalName
                    || asmType.className == ArrayList::class.java.canonicalName
                    || asmType.className == LinkedList::class.java.canonicalName
                    || asmType.className == ConcurrentList::class.java.canonicalName
                    || asmType.className == Set::class.java.canonicalName
                    || asmType.className == HashSet::class.java.canonicalName
                    || asmType.className == LinkedHashSet::class.java.canonicalName
                    || asmType.className == TreeSet::class.java.canonicalName
            -> {
                val elementType = type.arguments.single().type
                val elementSerializer = get(elementType, typeMapper.mapType(elementType), typeMapper, forceBoxed = true)
                wrapToNullAwareIfNeeded(type, CollectionParcelSerializer(asmType, elementSerializer))
            }
            asmType.isBoxedPrimitive() -> wrapToNullAwareIfNeeded(type, BoxedPrimitiveTypeParcelSerializer.forBoxedType(asmType))
            asmType.isBlob() -> NullCompliantObjectParcelSerializer(asmType,
                    Method("writeBlob", "([B)V"),
                    Method("readBlob", "()[B"))
            asmType.isSize() -> wrapToNullAwareIfNeeded(type, NullCompliantObjectParcelSerializer(asmType,
                    Method("writeSize", "(Landroid/util/Size;)V"),
                    Method("readSize", "()Landroid/util/Size;")))
            asmType.isSizeF() -> wrapToNullAwareIfNeeded(type, NullCompliantObjectParcelSerializer(asmType,
                    Method("writeSize", "(Landroid/util/SizeF;)V"),
                    Method("writeSize", "()Landroid/util/SizeF;")))
            else -> {
                // TODO Support custom collections here

                if (type.isSerializable()) {
                    NullCompliantObjectParcelSerializer(asmType,
                            Method("writeSerializable", "(Ljava/io/Serializable;)V"),
                            Method("readSerializable", "()Ljava/io/Serializable;"))
                }
                else {
                    GenericParcelSerializer
                }
            }
        }

        private fun wrapToNullAwareIfNeeded(type: KotlinType, serializer: ParcelSerializer) = when {
            type.isMarkedNullable -> NullAwareParcelSerializerWrapper(serializer)
            else -> serializer
        }

        private fun Type.isBlob() = this.sort == Type.ARRAY && this.elementType == Type.BYTE_TYPE
        private fun Type.isString() = this.descriptor == "Ljava/lang/String;"
        private fun Type.isSize() = this.descriptor == "Landroid/util/Size;"
        private fun Type.isSizeF() = this.descriptor == "Landroid/util/SizeF;"
        private fun KotlinType.isSerializable() = matchesFqNameWithSupertypes("java.io.Serializable")

        private fun Type.isPrimitive(): Boolean = when (this.sort) {
            Type.BOOLEAN, Type.CHAR, Type.BYTE, Type.SHORT, Type.INT, Type.FLOAT, Type.LONG, Type.DOUBLE -> true
            else -> false
        }

        private fun Type.isBoxedPrimitive(): Boolean = when(this.descriptor) {
            "Ljava/lang/Boolean;",
            "Ljava/lang/Character;",
            "Ljava/lang/Byte;",
            "Ljava/lang/Short;",
            "Ljava/lang/Integer;",
            "Ljava/lang/Float;",
            "Ljava/lang/Long;",
            "Ljava/lang/Double;" -> true
            else -> false
        }

        private fun KotlinType.matchesFqNameWithSupertypes(fqName: String): Boolean {
            if (this.matchesFqName(fqName)) {
                return true
            }

            return this.constructor.supertypes.any { it.matchesFqName(fqName) }
        }

        private fun KotlinType.matchesFqName(fqName: String): Boolean {
            return this.constructor.declarationDescriptor?.fqNameSafe?.asString() == fqName
        }
    }
}