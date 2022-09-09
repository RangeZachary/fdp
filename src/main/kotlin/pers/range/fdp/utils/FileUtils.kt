package pers.range.fdp.utils

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONWriter
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.time.LocalTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

object FileUtils {

    @JvmStatic
    fun obtainKey(type: String?, code: String?) = "$type-$code-${System.currentTimeMillis()}"

    @JvmStatic
    fun <T: Any> initializeFileWriter(filename: String, kClass: KClass<T>, fileList: MutableList<File>): FileWriter {
        val file = File(filename)
        val isExisted = file.exists()
        fileList.add(file)

        val fileWriter = FileWriter(file, true)
        if (!isExisted) {
            // 初始化字段名
            val fields = kClass.memberProperties.map(KProperty1<T, *>::name).joinToString(",")
            fileWriter.write("$fields${System.lineSeparator()}")
            fileWriter.flush()
        }
        return fileWriter
    }

    @JvmStatic
    fun <T: Any> writeToFile(data: ByteArray, kClass: KClass<T>, fileWriter: FileWriter) =
        fileWriter.write("${valueToStr(kClass, JSON.parseObject(data, kClass.java))}${System.lineSeparator()}")

    @JvmStatic
    fun <T: Any> writeToFile(data: T, kClass: KClass<T>, fileWriter: FileWriter) =
        fileWriter.write("${valueToStr(kClass, data)}${System.lineSeparator()}")

    @JvmStatic
    private fun <T: Any> valueToStr(kClass: KClass<T>, value: T): String =
        kClass.memberProperties.map {
            it.isAccessible = true
            var v = it.call(value)
            if (v != null && !isBasicType(v)) {
                v = "\"${JSON.toJSONString(v, JSONWriter.Feature.UseSingleQuotes)}\""
            }
            v
        }.joinToString(",")

    @JvmStatic
    private fun isBasicType(v: Any): Boolean =
        v is String || v is Boolean || v is Int || v is Long
                || v is Float || v is Double || v is Enum<*>

    @JvmStatic
    fun zipFiles(filename: String, fileList: List<File>) {
        var zipFile = File(filename)
        if (!zipFile.exists()) {
            zipFile.createNewFile()
        } else {
            zipFile = File(filename + "-${LocalTime.now()}")
            zipFile.createNewFile()
        }
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            fileList.forEach { file ->
                zos.putNextEntry(ZipEntry(file.name))
                file.inputStream().use { fs ->
                    var len: Int
                    val buffer = ByteArray(1024)
                    while (fs.read(buffer).also { len = it } > 0) {
                        zos.write(buffer, 0, len)
                    }
                    zos.closeEntry()
                }
            }
        }
        fileList.forEach { it.delete() }
    }
}