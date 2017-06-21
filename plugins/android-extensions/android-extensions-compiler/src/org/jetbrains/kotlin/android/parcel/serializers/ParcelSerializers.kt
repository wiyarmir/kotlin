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

import org.jetbrains.org.objectweb.asm.Label
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.commons.InstructionAdapter

private val PARCEL_TYPE = Type.getObjectType("android/os/Parcel")

internal object GenericParcelSerializer : ParcelSerializer {
    override val asmType: Type = Type.getObjectType("java/lang/Object")

    override fun writeValue(v: InstructionAdapter) {
        v.invokevirtual(PARCEL_TYPE.internalName, "writeValue", "(Ljava/lang/Object;)V", false)
    }

    override fun readValue(v: InstructionAdapter) {
        v.invokevirtual(PARCEL_TYPE.internalName, "readValue", "()Ljava/lang/Object;", false)
    }
}

internal class ArrayParcelSerializer(override val asmType: Type, private val elementSerializer: ParcelSerializer) : ParcelSerializer {
    override fun writeValue(v: InstructionAdapter) {
        v.dupX1() // -> arr, parcel, arr
        v.arraylength() // -> arr, parcel, length
        v.dup2X1() // -> parcel, length, arr, parcel, length

        // Write array size
        v.invokevirtual(PARCEL_TYPE.internalName, "writeInt", "(I)V", false) // -> parcel, length, arr
        v.swap() // -> parcel, arr, length
        v.aconst(0) // -> parcel, arr, length, <index>

        val labelLoop = Label()
        val labelReturn = Label()

        v.visitLabel(labelLoop)

        // Loop
        v.dup2() // -> parcel, arr, length, index, length, index
        v.ificmple(labelReturn) // -> parcel, arr, length, index

        v.swap() // -> parcel, arr, index, length
        v.dupX2() // -> parcel, length, arr, index, length
        v.pop() // -> parcel, length, arr, index
        v.dup2X2() // -> arr, index, parcel, length, arr, index
        v.aload(elementSerializer.asmType) // -> arr, index, parcel, length, obj
        v.castIfNeeded(elementSerializer.asmType)

        v.swap() // -> arr, index, parcel, obj, length
        v.dupX2() // -> arr, index, length, parcel, obj, length
        v.pop() // -> arr, index, length, parcel, obj
        v.swap() // -> arr, index, length, obj, parcel
        v.dupX2() // -> arr, index, parcel, length, obj, parcel
        v.swap() // -> arr, index, parcel, length, parcel, obj
        elementSerializer.writeValue(v) // -> arr, index, parcel, length

        v.dup2X2() // -> parcel, length, arr, index, parcel, length
        v.pop2() // -> parcel, length, arr, index
        v.swap() // -> parcel, length, index, arr
        v.dupX2() // -> parcel, arr, length, index, arr
        v.pop() // -> parcel, arr, length, index

        v.aconst(1)
        v.add(Type.INT_TYPE)
        v.goTo(labelLoop)

        v.visitLabel(labelReturn)
        v.pop2()
        v.pop2()
    }

    override fun readValue(v: InstructionAdapter) {
        v.dup() // -> parcel, parcel
        v.invokevirtual(PARCEL_TYPE.internalName, "readInt", "()I", false) // -> parcel, length
        v.dup() // -> parcel, length, length
        v.newarray(elementSerializer.asmType) // -> parcel, length, arr
        v.swap() // -> parcel, arr, length
        v.aconst(0) // -> parcel, arr, length, index

        val labelLoop = Label()
        val labelReturn = Label()

        v.visitLabel(labelLoop)
        v.dup2() // -> parcel, arr, length, index, length, index
        v.ificmple(labelReturn) // -> parcel, arr, length, index

        v.dup2X2() // -> length, index, parcel, arr, length, index
        v.swap() // -> length, index, parcel, arr, index, length
        v.pop() // -> length, index, parcel, arr, index
        v.swap() // -> length, index, parcel, index, arr
        v.dupX2() // -> length, index, arr, parcel, index, arr
        v.swap() // -> length, index, arr, parcel, arr, index
        v.dupX2() // -> length, index, arr, index, parcel, arr, index
        v.dup2X1() // -> length, index, arr, index, arr, index, parcel, arr, index
        v.pop2() // -> length, index, arr, index, arr, index, parcel
        v.dupX2() // -> length, index, arr, index, parcel, arr, index, parcel

        elementSerializer.readValue(v) // -> length, index, arr, index, parcel, arr, index, obj
        v.castIfNeeded(elementSerializer.asmType)
        v.astore(elementSerializer.asmType) // -> length, index, arr, index, parcel

        v.swap() // -> length, index, arr, parcel, index
        v.pop() // -> length, index, arr, parcel
        v.swap() // -> length, index, parcel, arr
        v.dup2X2() // -> parcel, arr, length, index, parcel, arr
        v.pop2() // -> parcel, arr, length, index

        v.aconst(1)
        v.add(Type.INT_TYPE)
        v.goTo(labelLoop)

        v.visitLabel(labelReturn)
        v.pop2() // -> parcel, arr
        v.swap() // -> arr, parcel
        v.pop() // -> arr
    }
}

fun InstructionAdapter.castIfNeeded(targetType: Type) {
    if (targetType.sort != Type.OBJECT && targetType.sort != Type.ARRAY) return
    if (targetType.descriptor == "Ljava/lang/Object;") return
    checkcast(targetType)
}

internal class CollectionParcelSerializer(override val asmType: Type, private val elementSerializer: ParcelSerializer) : ParcelSerializer {
    private val listType: Type = Type.getObjectType(when (asmType.internalName) {
        "java/util/List" -> "java/util/ArrayList"
        "java/util/Set" -> "java/util/LinkedHashSet"
        else -> asmType.internalName
    })

    override fun writeValue(v: InstructionAdapter) {
        val labelIteratorLoop = Label()
        val labelReturn = Label()

        v.swap() // -> collection, parcel
        v.dupX1() // -> parcel, collection, parcel

        // Write collection type
        v.dup() // -> parcel, collection, parcel, parcel
        v.aconst(asmType.internalName)
        v.invokevirtual(PARCEL_TYPE.internalName, "writeString", "(Ljava/lang/String;)V", false) // -> parcel, collection, parcel

        // Write collection size
        v.swap() // -> parcel, parcel, collection
        v.dupX1() // -> parcel, collection, parcel, collection
        v.invokeinterface("java/util/Collection", "size", "()I")
        v.invokevirtual(PARCEL_TYPE.internalName, "writeInt", "(I)V", false) // -> parcel, collection

        // Iterate through elements
        v.invokeinterface("java/util/Collection", "iterator", "()Ljava/util/Iterator;") // -> parcel, iterator
        v.visitLabel(labelIteratorLoop)
        v.dup() // -> parcel, iterator, iterator
        v.invokeinterface("java/util/Iterator", "hasNext", "()Z") // -> parcel, iterator, hasNext
        v.ifeq(labelReturn) // The list is empty, -> parcel, iterator

        v.dup() // -> parcel, iterator, iterator
        v.invokeinterface("java/util/Iterator", "next", "()Ljava/lang/Object;") // parcel, iterator, obj
        v.swap() // -> parcel, obj, iterator

        v.dupX2() // -> iterator, parcel, obj, iterator
        v.pop() // -> iterator, parcel, obj
        v.swap() // -> iterator, obj, parcel
        v.dupX2() // -> parcel, iterator, obj, parcel
        v.swap() // -> parcel, iterator, parcel, obj

        v.castIfNeeded(elementSerializer.asmType)
        elementSerializer.writeValue(v) // -> parcel, iterator
        v.goTo(labelIteratorLoop)

        v.visitLabel(labelReturn)
        v.pop2()
    }

    override fun readValue(v: InstructionAdapter) {
        val nextLoopIteration = Label()
        val loopIsOverLabel = Label()

        v.dup() // -> parcel, parcel
        v.invokevirtual(PARCEL_TYPE.internalName, "readString", "()Ljava/lang/String;", false)
        v.pop() // TODO use this

        // Read list size
        v.invokevirtual(PARCEL_TYPE.internalName, "readInt", "()I", false)
        v.dup() // -> size, size

        v.anew(listType)
        v.dupX1() // -> size, list, size, list
        v.swap() // -> size, list, list, size
        v.invokespecial(listType.internalName, "<init>", "(I)V", false) // -> size, list

        v.visitLabel(nextLoopIteration)
        v.swap() // -> list, size
        v.dupX1() // -> size, list, size
        v.ifeq(loopIsOverLabel) // -> size, list

        v.dup() // -> size, list, list

        v.load(1, PARCEL_TYPE)
        elementSerializer.readValue(v) // -> size, list, list, element

        v.invokevirtual(listType.internalName, "add", "(Ljava/lang/Object;)Z", false)
        v.pop() // -> size, list

        v.swap()
        v.aconst(-1)
        v.add(Type.INT_TYPE) // -> list, (size - 1)

        v.swap()
        v.goTo(nextLoopIteration)

        v.visitLabel(loopIsOverLabel)
        v.swap()
        v.pop()
    }
}

internal class NullAwareParcelSerializerWrapper(val delegate: ParcelSerializer) : ParcelSerializer {
    override val asmType: Type
        get() = delegate.asmType

    override fun writeValue(v: InstructionAdapter) = writeValueNullAware(v) {
        delegate.writeValue(v)
    }

    override fun readValue(v: InstructionAdapter) = readValueNullAware(v) {
        delegate.readValue(v)
    }
}

/** write...() and get...() methods in Android should support passing `null` values. */
internal class NullCompliantObjectParcelSerializer(
        override val asmType: Type,
        val writeMethod: Method,
        val readMethod: Method
) : ParcelSerializer {
    override fun writeValue(v: InstructionAdapter) {
        v.invokevirtual(PARCEL_TYPE.internalName, writeMethod.name, writeMethod.signature, false)
    }

    override fun readValue(v: InstructionAdapter) {
        v.invokevirtual(PARCEL_TYPE.internalName, readMethod.name, readMethod.signature, false)
    }
}

internal class BoxedPrimitiveTypeParcelSerializer private constructor(val unboxedType: Type) : ParcelSerializer {
    companion object {
        private val BOXED_TO_UNBOXED_TYPE_MAPPINGS = mapOf(
                "java/lang/Boolean" to Type.BOOLEAN_TYPE,
                "java/lang/Character" to Type.CHAR_TYPE,
                "java/lang/Byte" to Type.BYTE_TYPE,
                "java/lang/Short" to Type.SHORT_TYPE,
                "java/lang/Integer" to Type.INT_TYPE,
                "java/lang/Float" to Type.FLOAT_TYPE,
                "java/lang/Long" to Type.LONG_TYPE,
                "java/lang/Double" to Type.DOUBLE_TYPE)

        private val UNBOXED_TO_BOXED_TYPE_MAPPINGS = BOXED_TO_UNBOXED_TYPE_MAPPINGS.map { it.value to it.key }.toMap()

        private val BOXED_VALUE_METHOD_NAMES = mapOf(
                "java/lang/Boolean" to "booleanValue",
                "java/lang/Character" to "charValue",
                "java/lang/Byte" to "byteValue",
                "java/lang/Short" to "shortValue",
                "java/lang/Integer" to "intValue",
                "java/lang/Float" to "floatValue",
                "java/lang/Long" to "longValue",
                "java/lang/Double" to "doubleValue")

        private val INSTANCES = BOXED_TO_UNBOXED_TYPE_MAPPINGS.values.map { type ->
            type to BoxedPrimitiveTypeParcelSerializer(type)
        }.toMap()

        fun forUnboxedType(type: Type) = INSTANCES[type] ?: error("Unsupported type $type")
        fun forBoxedType(type: Type) = INSTANCES[BOXED_TO_UNBOXED_TYPE_MAPPINGS[type.internalName]] ?: error("Unsupported type $type")
    }

    override val asmType = Type.getObjectType(UNBOXED_TO_BOXED_TYPE_MAPPINGS[unboxedType] ?: error("Unsupported type $unboxedType"))
    val unboxedSerializer = PrimitiveTypeParcelSerializer.getInstance(unboxedType)
    val typeValueMethodName = BOXED_VALUE_METHOD_NAMES[asmType.internalName]!!

    override fun writeValue(v: InstructionAdapter) {
        v.invokevirtual(asmType.internalName, typeValueMethodName, "()${unboxedType.descriptor}", false)
        unboxedSerializer.writeValue(v)
    }

    override fun readValue(v: InstructionAdapter) {
        unboxedSerializer.readValue(v)
        v.invokestatic(asmType.internalName, "valueOf", "(${unboxedType.descriptor})${asmType.descriptor}", false)
    }
}

internal class PrimitiveTypeParcelSerializer private constructor(override val asmType: Type) : ParcelSerializer {
    companion object {
        private val WRITE_METHOD_NAMES = mapOf(
                Type.BOOLEAN_TYPE to Method("writeInt", "(I)V"),
                Type.CHAR_TYPE to Method("writeInt", "(I)V"),
                Type.BYTE_TYPE to Method("writeByte", "(B)V"),
                Type.SHORT_TYPE to Method("writeInt", "(I)V"),
                Type.INT_TYPE to Method("writeInt", "(I)V"),
                Type.FLOAT_TYPE to Method("writeFloat", "(F)V"),
                Type.LONG_TYPE to Method("writeLong", "(J)V"),
                Type.DOUBLE_TYPE to Method("writeDouble", "(D)V"))

        private val READ_METHOD_NAMES = mapOf(
                Type.BOOLEAN_TYPE to Method("readInt", "()I"),
                Type.CHAR_TYPE to Method("readInt", "()I"),
                Type.BYTE_TYPE to Method("readByte", "()B"),
                Type.SHORT_TYPE to Method("readInt", "()I"),
                Type.INT_TYPE to Method("readInt", "()I"),
                Type.FLOAT_TYPE to Method("readFloat", "()F"),
                Type.LONG_TYPE to Method("readLong", "()J"),
                Type.DOUBLE_TYPE to Method("readDouble", "()D"))

        private val INSTANCES = READ_METHOD_NAMES.keys.map { it to PrimitiveTypeParcelSerializer(it) }.toMap()

        fun getInstance(type: Type) = INSTANCES[type] ?: error("Unsupported type ${type.descriptor}")
    }

    private val writeMethod = WRITE_METHOD_NAMES[asmType]!!
    private val readMethod = READ_METHOD_NAMES[asmType]!!

    override fun writeValue(v: InstructionAdapter) {
        v.invokevirtual(PARCEL_TYPE.internalName, writeMethod.name, writeMethod.signature, false)
    }

    override fun readValue(v: InstructionAdapter) {
        v.invokevirtual(PARCEL_TYPE.internalName, readMethod.name, readMethod.signature, false)
    }
}

private fun readValueNullAware(v: InstructionAdapter, block: () -> Unit) {
    val labelNull = Label()
    val labelReturn = Label()

    v.invokevirtual(PARCEL_TYPE.internalName, "readInt", "()I", false)
    v.ifeq(labelNull)

    v.load(1, PARCEL_TYPE)
    block()
    v.goTo(labelReturn)

    // Just push null on stack if the value is null
    v.visitLabel(labelNull)
    v.aconst(null)

    v.visitLabel(labelReturn)
}

private fun writeValueNullAware(v: InstructionAdapter, block: () -> Unit) {
    val labelReturn = Label()
    val labelNull = Label()
    v.dup()
    v.ifnull(labelNull)

    // Write 1 if non-null, 0 if null

    v.load(1, PARCEL_TYPE)
    v.aconst(1)
    v.invokevirtual(PARCEL_TYPE.internalName, "writeInt", "(I)V", false)
    block()

    v.goTo(labelReturn)

    labelNull@ v.visitLabel(labelNull)
    v.pop()
    v.aconst(0)
    v.invokevirtual(PARCEL_TYPE.internalName, "writeInt", "(I)V", false)

    labelReturn@ v.visitLabel(labelReturn)
}

internal class Method(val name: String, val signature: String)