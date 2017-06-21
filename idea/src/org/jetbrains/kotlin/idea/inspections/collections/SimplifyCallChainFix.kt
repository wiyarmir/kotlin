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

package org.jetbrains.kotlin.idea.inspections.collections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.core.replaced
import org.jetbrains.kotlin.psi.*

class SimplifyCallChainFix(val newName: String) : LocalQuickFix {
    override fun getName() = "Merge call chain to '$newName'"

    override fun getFamilyName() = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        (descriptor.psiElement as? KtDotQualifiedExpression)?.let { secondQualifiedExpression ->
            val factory = KtPsiFactory(secondQualifiedExpression)
            val firstQualifiedExpression = secondQualifiedExpression.receiverExpression as? KtDotQualifiedExpression ?: return
            val firstCallExpression = firstQualifiedExpression.selectorExpression as? KtCallExpression ?: return
            val secondCallExpression = secondQualifiedExpression.selectorExpression as? KtCallExpression ?: return

            val arguments = firstCallExpression.valueArgumentList?.arguments.orEmpty() +
                            secondCallExpression.valueArgumentList?.arguments.orEmpty()
            val lambdaArgument = firstCallExpression.lambdaArguments.singleOrNull()
                                 ?: secondCallExpression.lambdaArguments.singleOrNull()
                                 ?: return // ???

            val newQualifiedExpression = factory.createExpressionByPattern(
                    "$0.$1 $2 $3",
                    firstQualifiedExpression.receiverExpression,
                    newName,
                    if (arguments.isEmpty()) "" else arguments.joinToString(prefix = "(", postfix = ")") { it.text },
                    lambdaArgument.getLambdaExpression().text
            )

            secondQualifiedExpression.replaced(newQualifiedExpression)
        }
    }
}