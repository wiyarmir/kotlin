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

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.fileEditor.FileDocumentManager
import org.jetbrains.kotlin.idea.intentions.ConvertToExpressionBodyIntention
import org.jetbrains.kotlin.psi.*

class UseExpressionBodyInspection : AbstractKotlinInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
            object : KtVisitorVoid() {
                private fun KtExpression.isOneLiner(): Boolean {
                    val file = containingFile?.virtualFile ?: return false
                    val document = FileDocumentManager.getInstance().getDocument(file) ?: return false
                    return document.getLineNumber(textRange.startOffset) == document.getLineNumber(textRange.endOffset)
                }

                override fun visitReturnExpression(expression: KtReturnExpression) {
                    super.visitReturnExpression(expression)

                    val blockExpression = expression.parent as? KtBlockExpression ?: return
                    if (blockExpression.statements.size != 1) return

                    blockExpression.parent as? KtDeclarationWithBody ?: return

                    val suffix = when {
                        expression.returnedExpression is KtIfExpression -> "'return if'"
                        expression.returnedExpression is KtWhenExpression -> "'return when'"
                        expression.isOneLiner() -> "one-line return"
                        else -> return
                    }
                    holder.registerProblem(
                            expression.returnKeyword,
                            "Use expression body instead of $suffix",
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                            IntentionWrapper(ConvertToExpressionBodyIntention(), expression.containingKtFile)
                    )
                }
            }
}