/*
 * Copyright 2010-2016 JetBrains s.r.o.
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

package org.jetbrains.kotlin.asJava

import com.intellij.psi.*
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.PluginTestCaseBase
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.uast.java.annotations

// see KtFileLightClassTest
class LightClassFromTextTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE

    fun testSimple() {
        myFixture.configureByText("Dummy.kt", "") as KtFile
        val classes = classesFromText("class C {}\nobject O {}")
        assertEquals(2, classes.size)
        assertEquals("C", classes[0].qualifiedName)
        assertEquals("O", classes[1].qualifiedName)
    }

    fun testFileClass() {
        myFixture.configureByText("A.kt", "fun f() {}") as KtFile
        val classes = classesFromText("fun g() {}", fileName = "A.kt")

        assertEquals(1, classes.size)
        val facadeClass = classes.single()
        assertEquals("AKt", facadeClass.qualifiedName)

        val gMethods = facadeClass.findMethodsByName("g", false)
        assertEquals(1, gMethods.size)
        assertEquals(PsiType.VOID, gMethods.single().returnType)

        val fMethods = facadeClass.findMethodsByName("f", false)
        assertEquals(0, fMethods.size)
    }

    fun testMultifileClass() {
        myFixture.configureByFiles("multifile1.kt", "multifile2.kt")

        val facadeClass = classesFromText("""
        @file:kotlin.jvm.JvmMultifileClass
        @file:kotlin.jvm.JvmName("Foo")

        fun jar() {
        }

        fun boo() {
        }
         """).single()

        assertEquals(1, facadeClass.findMethodsByName("jar", false).size)
        assertEquals(1, facadeClass.findMethodsByName("boo", false).size)
        assertEquals(0, facadeClass.findMethodsByName("bar", false).size)
        assertEquals(0, facadeClass.findMethodsByName("foo", false).size)
    }

    fun testReferenceToOuterContext() {
        val contextFile = myFixture.configureByText("Example.kt", "package foo\n class Example") as KtFile

        val syntheticClass = classesFromText("""
        package bar

        import foo.Example

        class Usage {
            fun f(): Example = Example()
        }
         """).single()

        val exampleClass = contextFile.classes.single()
        assertEquals("Example", exampleClass.name)

        val f = syntheticClass.findMethodsByName("f", false).single()
        assertEquals(exampleClass, (f.returnType as PsiClassType).resolve())
    }

    fun testHeaderDeclarations() {
        val contextFile = myFixture.configureByText("Header.kt", "header class Foo\n\nheader fun foo()\n") as KtFile
        val headerClass = contextFile.declarations.single { it is KtClassOrObject }
        assertEquals(0, headerClass.toLightElements().size)
        val headerFunction = contextFile.declarations.single { it is KtNamedFunction }
        assertEquals(0, headerFunction.toLightElements().size)
    }

    fun testAnnotationsInAnnotationsDeclarations() {
        myFixture.addClass("""
            import java.lang.annotation.Documented;
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;

            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.TYPE)
            @Documented
            public @interface ComponentScan {

                Filter[] includeFilters() default {};

                @Retention(RetentionPolicy.RUNTIME)
                @Target({})
                @interface Filter {
                    Class<?>[] value() default {};
                }

            }
        """.trimIndent())

        myFixture.configureByText("KtCSApp.kt", """
            @ComponentScan(includeFilters = arrayOf(ComponentScan.Filter(MyClass::class)))
            open class KtCSApp
            """.trimIndent()) as KtFile
        myFixture.testHighlighting("ComponentScan.java", "KtCSApp.kt")


        val headerClass = listOf(myFixture.findClass("KtCSApp"))
        assertEquals(1, headerClass.size)
        val annotations = headerClass.first().annotations
        assertEquals(1, annotations.size)
        val annotation = annotations.first()

        val initializers = (annotation.findDeclaredAttributeValue("includeFilters") as PsiArrayInitializerMemberValue).initializers
        val anchors = initializers.map { i ->
            PsiAnchor.create(i)
        }
        assertEquals(1, anchors.size)

    }

    private fun classesFromText(text: String, fileName: String = "A.kt"): Array<out PsiClass> {
        val file = KtPsiFactory(project).createFileWithLightClassSupport(fileName, text, myFixture.file)
        val classes = file.classes
        return classes
    }

    override fun getTestDataPath(): String {
        return PluginTestCaseBase.getTestDataPathBase() + "/asJava/fileLightClass/"
    }
}