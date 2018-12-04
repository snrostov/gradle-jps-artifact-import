/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.buildUtils.idea

class DistModelFlattener() {
    val stack = mutableSetOf<DistVFile>()
    val visited = mutableMapOf<DistVFile, String>()
    val common = mutableSetOf<DistVFile>()

    fun DistVFile.flatten(): DistVFile {
        val new = DistVFile(parent, name, file)
        copyFlattenedContentsTo(new)
        return new
    }

    private fun DistVFile.copyFlattenedContentsTo(new: DistVFile, inJar: Boolean = false) {
        if (new.name == "kotlin-jps-plugin.jar") {
            println("debug")
        }

        if (!stack.add(this)) {
            println("SKIPPED RECUESIVE ARTIFACT:\n - ${stack.joinToString("\n - ") { it.file.path }}\n - ${new.file.path}")
            return
        }

        try {
            contents.forEach {
                when (it) {
                    is DistCopy -> {
                        val srcName = it.customTargetName ?: it.src.name
                        if (it.src.file.exists()) {
                            DistCopy(new, it.src.newRef(), srcName)
                        }

                        if (!inJar && srcName.endsWith(".jar")) {
                            val newChild = new.getOrCreateChild(srcName)
                            it.src.copyFlattenedContentsTo(newChild, inJar = true)
                        } else {
                            it.src.copyFlattenedContentsTo(new, inJar)
                        }
                    }
                    is DistModuleOutput -> DistModuleOutput(new, it.projectId)
                }
            }

            child.values.forEach { oldChild ->
                if (inJar) {
                    oldChild.copyFlattenedContentsTo(new, inJar = true)
                } else {
                    val newChild = new.getOrCreateChild(oldChild.name)
                    oldChild.copyFlattenedContentsTo(newChild)
                }
            }
        } finally {
            stack.remove(this)
        }
    }
}

