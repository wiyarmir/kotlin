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

package org.jetbrains.kotlin.js.parser.sourcemaps

import org.jetbrains.kotlin.js.backend.ast.*

class SourceMapLocationRemapper(val sourceMap: SourceMap) {
    fun remap(node: JsNode) {
        node.accept(visitor)
    }

    private val visitor = object : RecursiveJsVisitor() {
        private var lastGroup: SourceMapGroup? = null
        private var lastGroupIndex = 0
        private var lastSegment: SourceMapSegment? = null
        private var lastSegmentIndex = 0
        private var currentNodeSegments = mutableSetOf<SourceMapSegment>()
        private var currentExclusiveNodeSegments = mutableSetOf<SourceMapSegment>()
        private val usedSegments = mutableSetOf<SourceMapSegment>()

        override fun visitIf(x: JsIf) {
            remapNode(x) {
                accept(x.ifExpression)
            }
            accept(x.thenStatement)
            x.elseStatement?.accept(this)
        }

        override fun visitWhile(x: JsWhile) {
            remapNode(x) {
                accept(x.condition)
            }
            accept(x.body)
        }

        override fun visitDoWhile(x: JsDoWhile) = remapNode(x) {
            accept(x.body)
            accept(x.condition)
        }

        override fun visitFor(x: JsFor) {
            remapNode(x) {
                x.initExpression?.accept(this)
                x.initVars?.accept(this)
                x.condition?.accept(this)
                x.incrementExpression?.accept(this)
            }
            accept(x.body)
        }

        override fun visitForIn(x: JsForIn) {
            remapNode(x) {
                x.iterExpression?.accept(this)
                x.objectExpression?.accept(this)
            }
            accept(x.body)
        }

        override fun visit(x: JsSwitch) {
            remapNode(x) {
                accept(x.expression)
            }
            x.cases.forEach { accept(it) }
        }

        override fun visitCase(x: JsCase) {
            remapNode(x) {
                accept(x.caseExpression)
            }
            x.statements.forEach { accept(it) }
        }

        override fun visitCatch(x: JsCatch) {
            remapNode(x) {
                accept(x.parameter)
            }
            accept(x.body)
        }

        override fun visitFunction(x: JsFunction) {
            remapNode(x) {
                x.parameters.forEach { accept(it) }
                skip {
                    accept(x.body)
                }
            }
        }

        override fun visitTry(x: JsTry) = skipNode(x)

        override fun visitBlock(x: JsBlock) = skipNode(x)

        override fun visitExpressionStatement(x: JsExpressionStatement) = skipNode(x)

        override fun visitLabel(x: JsLabel) = skipNode(x)

        override fun visitPropertyInitializer(x: JsPropertyInitializer) = x.acceptChildren(this)

        override fun visitProgram(x: JsProgram) = skipNode(x)

        override fun visitElement(node: JsNode) = remapNode(node) {
            node.acceptChildren(this)
        }

        private fun skipNode(node: JsNode) {
            if (node is SourceInfoAwareJsNode) {
                node.source = null
            }
            skip {
                node.acceptChildren(this)
            }
        }

        private fun skip(action: () -> Unit) {
            val parentNodeSegments = currentNodeSegments
            val parentExclusiveNodeSegments = currentExclusiveNodeSegments
            currentNodeSegments = mutableSetOf()
            currentExclusiveNodeSegments = mutableSetOf()

            action()

            currentNodeSegments = parentNodeSegments
            currentExclusiveNodeSegments = parentExclusiveNodeSegments
        }

        private fun remapNode(node: JsNode, action: () -> Unit) {
            val parentNodeSegments = currentNodeSegments
            currentNodeSegments = mutableSetOf()
            val parentExclusiveNodeSegments = currentExclusiveNodeSegments
            currentExclusiveNodeSegments = mutableSetOf()

            if (node is SourceInfoAwareJsNode) {
                if (!startNode(node, parentNodeSegments)) {
                    node.source = null
                }
            }
            action()

            currentExclusiveNodeSegments.removeAll { it in usedSegments }
            currentNodeSegments.removeAll { it in usedSegments }
            val currentSegment = (currentExclusiveNodeSegments + currentNodeSegments).firstOrNull()
            val sourceFileName = currentSegment?.sourceFileName
            node.source = if (sourceFileName != null) {
                val location = JsLocation(currentSegment.sourceFileName, currentSegment.sourceLineNumber, currentSegment.sourceColumnNumber)
                JsLocationWithSource(location, sourceMap) { sourceMap.sourceContentResolver(currentSegment.sourceFileName) }
            }
            else {
                null
            }

            usedSegments += currentExclusiveNodeSegments
            currentNodeSegments = parentNodeSegments
            currentExclusiveNodeSegments = parentExclusiveNodeSegments
        }

        private fun startNode(
                node: SourceInfoAwareJsNode,
                parentNodeSegments: MutableSet<SourceMapSegment>
        ): Boolean {
            val source = node.source as? JsLocation ?: return false

            val group = sourceMap.groups.getOrElse(source.startLine) { return false }

            if (lastGroup != group) {
                if (lastGroup != null) {
                    val segmentsToSkip = lastGroup!!.segments.drop(lastSegmentIndex).toMutableList()
                    if (lastGroupIndex + 1 < source.startLine) {
                        segmentsToSkip += sourceMap.groups.subList((lastGroupIndex + 1), source.startLine).flatMap { it.segments }
                    }

                    parentNodeSegments += segmentsToSkip
                    segmentsToSkip.lastOrNull()?.let { lastSegment = it }
                }
                lastGroup = group
                lastGroupIndex = source.startLine
                lastSegmentIndex = 0
            }

            var exclusive = false
            while (lastSegmentIndex  < group.segments.size) {
                if (group.segments[lastSegmentIndex].generatedColumnNumber > source.startChar) break

                exclusive = true
                parentNodeSegments.add(group.segments[lastSegmentIndex++].also { lastSegment = it })
            }

            lastSegment?.let {
                (if (exclusive) currentExclusiveNodeSegments else currentNodeSegments).add(it)
            }


            return true
        }
    }
}