package pers.range.fdp.securities.mds

import com.alibaba.fastjson2.JSON
import com.quant360.api.model.mds.MdsIndexSnapshotBody
import com.quant360.api.model.mds.MdsL2StockSnapshotBody
import com.quant360.api.model.mds.MdsL2Trade
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import pers.range.fdp.handler.DataPreserveHandler
import pers.range.fdp.utils.FileUtils.initializeFileWriter
import pers.range.fdp.utils.FileUtils.writeToFile
import pers.range.fdp.utils.FileUtils.zipFiles
import java.io.File
import java.io.FileWriter
import java.time.LocalDate

class MdsDataPreserveHandler(private val parentPath: String,
                             private val date: LocalDate,
                             private val indexStr: String,
                             private val stockStr: String,
                             private val tradeStr: String): DataPreserveHandler {

    private val logger = LoggerFactory.getLogger(this::class.java)

    private val fileList = mutableListOf<File>()

    private lateinit var indexFileWriter: FileWriter
    private lateinit var stockFileWriter: FileWriter
    private lateinit var tradeFileWriter: FileWriter

    override fun init() {
        indexFileWriter = initializeFileWriter("$parentPath/$indexStr", MdsIndexSnapshotBody::class, fileList)

        stockFileWriter = initializeFileWriter("$parentPath/$stockStr", MdsL2StockSnapshotBody::class, fileList)

        tradeFileWriter = initializeFileWriter("$parentPath/$tradeStr", MdsL2Trade::class, fileList)

        logger.info("lsh data preserve handler initialized. parent path: [$parentPath]")
    }

    override fun saveData(record: ConsumerRecord<String, ByteArray>): Boolean {
        when (record.key().split("-")[0]) {
            indexStr -> {
                writeToFile(getBody(record.value(), MdsIndexSnapshotBody::class.java), MdsIndexSnapshotBody::class, indexFileWriter)
            }
            stockStr -> {
                writeToFile(getBody(record.value(), MdsL2StockSnapshotBody::class.java), MdsL2StockSnapshotBody::class, stockFileWriter)
            }
            tradeStr -> {
                writeToFile(getBody(record.value(), MdsL2Trade::class.java), MdsL2Trade::class, tradeFileWriter)
            }
            else -> {
                logger.error("unknown record key: ${record.key()}")
                return false
            }
        }
        return true
    }

    private fun <T> getBody(dataBytes: ByteArray, clazz: Class<T>) =
        JSON.to(clazz, JSON.parseObject(dataBytes, MdsDataSubscribeCallBack.ConformData::class.java).data)

    override fun flushData() {
        logger.info("lsh flush data...")
        indexFileWriter.flush()
        stockFileWriter.flush()
        tradeFileWriter.flush()
    }

    override fun close() {
        flushData()

        indexFileWriter.close()
        stockFileWriter.close()
        tradeFileWriter.close()

        // 压缩文件到zip
        zipFiles("$parentPath/mds_$date.zip", fileList)

        logger.info("lsh data preserve handler closed.")
    }


}