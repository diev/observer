package ru.barabo.cmd

import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.util.*

object Cmd {

    private val logger = LoggerFactory.getLogger(Cmd::class.java)

    @Throws(IOException::class, InterruptedException::class)
    fun execCmd(cmd :String) {

        try {
            val process = Runtime.getRuntime().exec(cmd)

            StreamReader(process.errorStream, logger::error).start()
            StreamReader(process.inputStream, logger::info).start()

            process.waitFor()
        } catch (e :Exception) {

            logger.error("execCmd", e)
            throw Exception(e.message)
        }
    }

    private fun cTemp(prefix :String) =  "$TEMP_FOLDER/$prefix${Date().time}" // "c:/temp/$prefix${Date().time}"

    fun tempFolder(prefix :String) : File {
        val temp = File(cTemp(prefix) )

        temp.mkdirs()

        return temp
    }

    private val JAR_FOLDER = File(Cmd::class.java.protectionDomain.codeSource.location.path).parentFile.path

    val LIB_FOLDER = "$JAR_FOLDER/lib"

    private val TEMP_FOLDER = "$JAR_FOLDER/temp"

    fun createFolder(folderPath :String) :File {

        val folder = File(folderPath)

        if(!folder.exists()) {
            folder.mkdirs()
        }

        return folder
    }
}

fun File.deleteFolder() {
    this.listFiles()?.forEach { it.delete() }

    this.delete()
}