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

package org.jetbrains.kotlin.jps.incremental

import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.cli.js.K2JSCompiler
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.incremental.Difference
import org.jetbrains.kotlin.incremental.utils.TestMessageCollector
import org.jetbrains.kotlin.js.incremental.IncrementalJsService
import org.jetbrains.kotlin.js.incremental.IncrementalJsServiceImpl
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.serialization.ProtoBuf
import org.jetbrains.kotlin.serialization.deserialization.NameResolver
import org.jetbrains.kotlin.serialization.deserialization.NameResolverImpl
import org.jetbrains.kotlin.serialization.js.JsProtoBuf
import org.junit.Assert
import java.io.File

sealed class JsProtoData(val nameResolver: NameResolver) {
    class ClassProto(val classData: ProtoBuf.Class, nameResolver: NameResolver) : JsProtoData(nameResolver)
    class PackagePartProto(val packagePartData: ProtoBuf.Package, nameResolver: NameResolver) : JsProtoData(nameResolver)
}

abstract class AbstractJsProtoComparisonTest : AbstractProtoComparisonTest<JsProtoData>() {
    override fun compileAndGetClasses(sourceDir: File, outputDir: File): Map<ClassId, JsProtoData> {
        val incrementalService = IncrementalJsServiceImpl()
        // todo: find out if it is safe to call directly
        val services = Services.Builder().run {
            register(IncrementalJsService::class.java, incrementalService)
            build()
        }

        val ktFiles = sourceDir.walkMatching { it.name.endsWith(".kt") }.map { it.canonicalPath }.toList()
        val messageCollector = TestMessageCollector()
        val args = K2JSCompilerArguments().apply {
            outputFile = File(outputDir, "out.js").canonicalPath
            metaInfo = true
            freeArgs.addAll(ktFiles)
        }

        K2JSCompiler().exec(messageCollector, services, args).let { exitCode ->
            val expectedOutput = "OK"
            val actualOutput = (listOf(exitCode.name) + messageCollector.errors).joinToString("\n")
            Assert.assertEquals(expectedOutput, actualOutput)
        }

        val classes = hashMapOf<ClassId, JsProtoData>()

        for ((sourceFile, proto) in incrementalService.packageParts) {
            val nameResolver = NameResolverImpl(proto.strings, proto.qualifiedNames)

            proto.class_List.forEach {
                val classId = nameResolver.getClassId(it.fqName)
                classes[classId] = JsProtoData.ClassProto(it, nameResolver)
            }

            val packagePartProto = proto.`package`
            val packageFqName = if (packagePartProto.hasExtension(JsProtoBuf.packageFqName)) {
                nameResolver.getPackageFqName(packagePartProto.getExtension(JsProtoBuf.packageFqName))
            }
            else FqName.ROOT

            val packagePartClassId = ClassId(packageFqName, Name.identifier(sourceFile.nameWithoutExtension + "Kt"))
            classes[packagePartClassId] = JsProtoData.PackagePartProto(packagePartProto, nameResolver)
        }

        return classes
    }

    override fun difference(oldData: JsProtoData, newData: JsProtoData): Difference? {
        /*val oldNameResolver = NameResolverImpl(old.strings, old.qualifiedNames)
        val newNameResolver = NameResolverImpl(new.strings, new.qualifiedNames)
        val compare = ProtoCompareGenerated(oldNameResolver, newNameResolver)

        val packageDiff = compare.difference(old.`package`, old.`package`)

        val oldClasses = old.class_List.map { oldNameResolver.getClassId(it.fqName) to it }.toMap()
        val newClasses = new.class_List.map { newNameResolver.getClassId(it.fqName) to it }.toMap()

        val addedClasses = arrayListOf<ClassId>()
        val removedClasses = arrayListOf<ClassId>()
        val modifiedClasses = hashMapOf<ClassId, EnumSet<ProtoCompareGenerated.ProtoBufClassKind>>()

        for (classId in oldClasses.keys + newClasses.keys) {
            val oldProto = oldClasses[classId]
            val newProto = newClasses[classId]

            when {
                oldProto == null -> {
                    addedClasses.add(classId)
                }
                newProto == null -> {
                    removedClasses.add(classId)
                }
                else -> {
                    modifiedClasses[classId] = compare.difference(oldProto, newProto)
                }
            }
        }*/

        return null
    }
}