package pers.range.fdp.securities.lsh

import com.lsh.lv2.client.api.data.Market
import com.lsh.lv2.client.api.data.Order
import com.lsh.lv2.client.api.data.TicketIndex
import com.lsh.lv2.client.api.data.Transaction
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import pers.range.fdp.handler.DataPreserveHandler
import pers.range.fdp.utils.FileUtils.initializeFileWriter
import pers.range.fdp.utils.FileUtils.writeToFile
import pers.range.fdp.utils.FileUtils.zipFiles
import java.io.File
import java.io.FileWriter
import java.time.LocalDate

class LshDataPreserveHandler(private val parentPath: String,
                             private val date: LocalDate,
                             private val marketStr: String,
                             private val transactionStr: String,
                             private val orderStr: String,
                             private val ticketIndexStr: String): DataPreserveHandler {

    private val logger = LoggerFactory.getLogger(this::class.java)

    private val fileList = mutableListOf<File>()

    private lateinit var marketFileWriter: FileWriter
    private lateinit var transactionFileWriter: FileWriter
    private lateinit var orderFileWriter: FileWriter
    private lateinit var ticketIndexFileWriter: FileWriter

    override fun init() {
        marketFileWriter = initializeFileWriter("$parentPath/$marketStr", Market::class, fileList)

        transactionFileWriter = initializeFileWriter("$parentPath/$transactionStr", Transaction::class, fileList)

        orderFileWriter = initializeFileWriter("$parentPath/$orderStr", Order::class, fileList)

        ticketIndexFileWriter = initializeFileWriter("$parentPath/$ticketIndexStr", TicketIndex::class, fileList)

        logger.info("lsh data preserve handler initialized. parent path: [$parentPath]")
    }

    override fun saveData(record: ConsumerRecord<String, ByteArray>): Boolean {
        when (record.key().split("-")[0]) {
            marketStr -> {
                writeToFile(record.value(), Market::class, marketFileWriter)
            }
            transactionStr -> {
                writeToFile(record.value(), Transaction::class, transactionFileWriter)
            }
            orderStr -> {
                writeToFile(record.value(), Order::class, orderFileWriter)
            }
            ticketIndexStr -> {
                writeToFile(record.value(), TicketIndex::class, ticketIndexFileWriter)
            }
            else -> {
                throw RuntimeException("unknown record key: ${record.key()}")
            }
        }
        return true
    }

    override fun flushData() {
        logger.info("lsh flush data...")
        marketFileWriter.flush()
        transactionFileWriter.flush()
        orderFileWriter.flush()
        ticketIndexFileWriter.flush()
    }

    override fun close() {
        flushData()

        marketFileWriter.close()
        transactionFileWriter.close()
        orderFileWriter.close()
        ticketIndexFileWriter.close()

        // 压缩文件到zip
        zipFiles("$parentPath/lsh_$date.zip", fileList)

        logger.info("lsh data preserve handler closed.")
    }


}