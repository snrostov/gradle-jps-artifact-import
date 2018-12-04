package org.jetbrains.kotlin.buildUtils.idea

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.File
import java.io.PrintWriter

class DistVFile(
    val parent: DistVFile?,
    val name: String,
    val file: File = File(parent!!.file, name)
) {
    val child = mutableMapOf<String, DistVFile>()

    val contents = mutableSetOf<DistContentElement>()

    override fun toString(): String = name

    val hasContents: Boolean = file.exists() || contents.isNotEmpty()

    fun relativePath(path: String): DistVFile {
        val pathComponents = path.split(File.separatorChar)
        return pathComponents.fold(this) { parent: DistVFile, childName: String ->
            try {
                parent.getOrCreateChild(childName)
            } catch (t: Throwable) {
                throw Exception(
                    "Error while processing path `$path`, components: `$pathComponents`, element: `$childName`",
                    t
                )
            }
        }
    }

    fun getOrCreateChild(name: String): DistVFile = child.getOrPut(name) {
        DistVFile(this, name)
    }

    fun addContents(contents: DistContentElement) {
        this.contents.add(contents)
    }

    fun removeAll(matcher: (String) -> Boolean) {
        child.keys.filter(matcher).forEach {
            child.remove(it)
        }
    }

    fun printTree(p: PrintWriter, depth: String = "") {
        p.println("$depth${file.path} ${if (file.exists()) "EXISTED" else ""}:")
        contents.forEach {
            p.println("$depth  $it")
        }
        child.values.forEach {
            it.printTree(p, "$depth  ")
        }
    }

    fun toJson(writer: JsonWriter) {
        writer.beginObject()
        writer.name("@id").value(file.path)
        writer.name("name").value(name)
        writer.name("contents")
        writer.beginArray()
        for (item in contents) {
            writer.beginObject()
            item.toJson(writer)
            writer.endObject()
        }
        writer.endArray()
        writer.name("children")
        writer.beginArray()
        child.values.forEach {
            it.toJson(writer)
        }
        writer.endArray()
        writer.endObject()
    }

    fun newRef() = DistVFileRef(file.path).also { it.vFile = this }

    companion object {
        class Loader {
            val fileById = mutableMapOf<String, DistVFile>()
            val toLink = mutableListOf<DistVFileRef>()

            fun addRef(id: String) = DistVFileRef(id).also { toLink.add(it) }

            fun JsonReader.nextString(key: String): String {
                check(nextName() == key)
                return nextString()
            }

            fun JsonReader.nextNullableString(key: String): String? {
                check(nextName() == key)
                return if (peek() == JsonToken.NULL) {
                    nextNull()
                    null
                } else nextString()
            }

            inline fun <T> JsonReader.nextObject(contents: () -> T): T {
                beginObject()
                val result = contents()
                endObject()
                return result
            }

            inline fun <T> JsonReader.nextArray(contents: () -> T): List<T> {
                beginArray()
                val result = mutableListOf<T>()
                while (peek() != JsonToken.END_ARRAY) {
                    result.add(contents())
                }
                endArray()
                return result
            }


            inline fun <T> JsonReader.nextArray(key: String, contents: () -> T): List<T> {
                check(nextName() == key)
                return nextArray(contents)
            }

            private fun nextDistContentElement(parent: DistVFile, json: JsonReader) = json.nextObject {
                when (val type = json.nextString("@type")) {
                    "copy" -> DistCopy(
                        parent,
                        addRef(json.nextString("src")),
                        json.nextNullableString("customTargetName")
                    )
                    "compile" -> DistModuleOutput(
                        parent,
                        json.nextString("project")
                    )
                    else -> error("Unsupported content @type: $type")
                }
            }

            fun link() {
                toLink.forEach {
                    it.vFile = fileById[it.id]!!
                }
            }

            fun fromJson(json: JsonReader, parent: DistVFile? = null): DistVFile {
                json.beginObject()

                val id = json.nextString("@id")
                val name = json.nextString("name")
                val result = DistVFile(parent, name, File(id))
                fileById[id] = result

                json.nextArray("contents") {
                    nextDistContentElement(result, json)
                }.also {
                    result.contents.addAll(it)
                }

                json.nextArray("children") {
                    fromJson(json, result)
                }.forEach {
                    result.child[it.name] = it
                }

                json.endObject()

                return result
            }
        }

        fun fromJson(json: JsonReader): DistVFile {
            val loader = Loader()
            val root = loader.fromJson(json)
            loader.link()
            return root
        }
    }
}


sealed class DistContentElement(val targetDir: DistVFile) {
    abstract fun toJson(writer: JsonWriter)
}

///////

class DistVFileRef(val id: String) {
    lateinit var vFile: DistVFile
}

class DistCopy(
    target: DistVFile,
    val srcRef: DistVFileRef,
    val customTargetName: String? = null
//    val copyActions: Collection<Action<in FileCopyDetails>> = listOf()
) : DistContentElement(target) {
    val src
        get() = srcRef.vFile

    init {
        target.addContents(this)
    }

    override fun toString(): String =
        "COPY OF ${src.file}" +
                if (customTargetName != null) " -> $customTargetName" else ""

    override fun toJson(writer: JsonWriter) {
        writer.name("@type").value("copy")
            .name("src").value(src.file.path)
            .name("customTargetName").value(customTargetName)
    }
}

class DistModuleOutput(parent: DistVFile, val projectId: String) : DistContentElement(parent) {
    init {
        parent.addContents(this)
    }

    override fun toString(): String = "COMPILE OUTPUT $projectId"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DistModuleOutput

        if (projectId != other.projectId) return false

        return true
    }

    override fun hashCode(): Int {
        return projectId.hashCode()
    }

    override fun toJson(writer: JsonWriter) {
        writer.name("@type").value("compile")
            .name("project").value(projectId)
    }
}