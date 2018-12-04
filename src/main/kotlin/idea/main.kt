/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.buildUtils.idea

import com.google.gson.GsonBuilder
import com.google.gson.stream.JsonReader
import java.io.File

fun main() {
    val projectDir = File("/Users/jetbrains/tasks/kwjps/wjps")
    val reportsDir = File("result/idea-artifacts-cfg")
    reportsDir.mkdirs()

    val vfsRoot = File("data/02-vfs.json").reader().use {
        DistVFile.fromJson(JsonReader(it))
    }

    with(DistModelFlattener()) {
        with(DistModelIdeaArtifactBuilder()) {
            File(reportsDir, "03-flattened-vfs.txt").printWriter().use { report ->
                fun getFlattenned(vfsPath: String): DistVFile =
                    vfsRoot.relativePath("$projectDir/$vfsPath")
                        .flatten()

                val all = getFlattenned("dist")
                all.child["artifacts"]
                    ?.removeAll { it != "ideaPlugin" }
                all.child["artifacts"]
                    ?.child?.get("ideaPlugin")
                    ?.child?.get("Kotlin")
                    ?.removeAll { it != "kotlinc" && it != "lib" }
                all.removeAll { it.endsWith(".zip") }
                all.printTree(report)

                val dist = RecursiveArtifact(RecursiveArtifact.ArtifactType.DIR, "dist")
                dist.addFiles(all)

                File(reportsDir, "04-idea-artifacts.json")
                    .writeText(
                        GsonBuilder().setPrettyPrinting().create()
                            .toJson(dist)
                    )
            }
        }
    }
}