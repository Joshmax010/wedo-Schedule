package com.lingion.sleepy

import java.io.File

object TestProjectFiles {
    fun read(relativePath: String): String {
        val root = System.getProperty("wedo.project.root")
            ?: error("Gradle did not provide wedo.project.root")
        val file = File(root, relativePath)
        check(file.isFile) { "Missing project file: ${file.absolutePath}" }
        return file.readText()
    }
}
