/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.buildUtils.idea

class DistModelIdeaArtifactBuilder {
    fun RecursiveArtifact.addFiles(vFile: DistVFile, inJar: Boolean = false) {
        val files = mutableSetOf<String>()

        vFile.contents.forEach {
            when (it) {
                is DistCopy -> {
                    val file = it.src.file
                    when {
                        inJar && file.name.endsWith(".jar") -> extractedDirectory(file.path)
                        file.isDirectory -> {
                            files.add(file.name)
                            directoryContent(file.path)
                        }
                        else -> {
                            files.add(file.name)
                            file(file.path)
                        }
                    }
                }
                is DistModuleOutput -> moduleOutput(it.projectId)
            }
        }

        vFile.child.values.forEach {
            if (it.name !in files) {
                when {
                    it.name.endsWith(".jar") -> archive(it.name).addFiles(it, true)
                    else -> directory(it.name).addFiles(it, inJar)
                }
            }
        }
    }
}

class RecursiveArtifact(val type: ArtifactType, val name: String) {
    private val children = mutableMapOf<String, RecursiveArtifact>()

    private fun child(type: ArtifactType, name: String): RecursiveArtifact {
        val child = RecursiveArtifact(type, name)
        if (children.put(name, child) != null) {
            println("${this.name}: duplicated child in $name")
        }
        return child
    }

    fun extractedDirectory(fileName: String) = child(ArtifactType.EXTRACTED_DIR, fileName)
    fun directoryContent(fileName: String) = child(ArtifactType.DIR_CONTENT, fileName)
    fun moduleOutput(moduleName: String) = child(ArtifactType.MODULE_OUTPUT, moduleName)
    fun file(fileName: String) = child(ArtifactType.FILE, fileName)
    fun archive(name: String) = child(ArtifactType.ARCHIVE, name)
    fun directory(name: String) = child(ArtifactType.DIR, name)

    enum class ArtifactType {
        ARTIFACT,
        DIR, ARCHIVE,
        LIBRARY_FILES, MODULE_OUTPUT, MODULE_TEST_OUTPUT, MODULE_SRC, FILE, DIR_CONTENT, EXTRACTED_DIR,
        ARTIFACT_REF
    }
}

