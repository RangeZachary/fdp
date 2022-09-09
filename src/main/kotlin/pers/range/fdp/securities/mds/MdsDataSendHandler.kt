package pers.range.fdp.securities.mds

import org.slf4j.LoggerFactory
import pers.range.fdp.common.*
import pers.range.fdp.handler.KafkaConsumeHandler
import pers.range.fdp.handler.KafkaProduceHandler
import java.time.LocalDate
import java.util.concurrent.Executors

class MdsDataSendHandler(private val date: LocalDate,
                         private val parentPath: String,
                         private val indexStr: String,
                         private val stockStr: String,
                         private val tradeStr: String,
                         private val launcherProperties: LauncherProperties,
                         private val adminProperties: KafkaAdminProperties,
                         private val kafkaConsumerProperties: KafkaConsumerProperties,
                         private val kafkaProducerProperties: KafkaProducerProperties) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    private lateinit var indexTopic: String
    private lateinit var stockTopic: String
    private lateinit var tradeTopic: String

    private lateinit var producer: KafkaProduceHandler
    private lateinit var consumer: KafkaConsumeHandler

    private lateinit var ringBuffer: RingBuffer

    private var runFlag = false

    private var endFlag = false

    fun init() {
        indexTopic = "$indexStr-$date"
        stockTopic = "$stockStr-$date"
        tradeTopic = "$tradeStr-$date"

        ringBuffer = RingBuffer(launcherProperties.bufferSize)

        val topics = arrayOf(indexTopic, stockTopic, tradeTopic)
        producer = KafkaProduceHandler(topics, adminProperties, kafkaProducerProperties)

        val mdsDataPreserveHandler = MdsDataPreserveHandler(parentPath,
            date, indexStr, stockStr, tradeStr)
        mdsDataPreserveHandler.init()
        consumer = KafkaConsumeHandler(topics, mdsDataPreserveHandler,
            adminProperties, kafkaConsumerProperties, false)
    }

    fun put(type: String, key: String, value: Any) {
        if (ringBuffer.isCloseToFull) {
            logger.warn("ring buffer is closed to full, put will pause 1 second.")
            Thread.sleep(1000)
        }

        for (i in 1..launcherProperties.sendRetryTimes) {
            if (ringBuffer.put(SendObject(type, key, value))) {
                break
            } else {
                if (i < launcherProperties.sendRetryTimes) {
                    logger.warn("put into ring buffer failed retry times: $i.")
                    Thread.sleep(launcherProperties.sendRetryIntervalMs.toLong())
                } else {
                    throw RuntimeException("put into ring buffer failed in retry $i times")
                }
            }
        }
    }

    fun start() {
        init()
        val executors = Executors.newFixedThreadPool(2)
        executors.execute { send() }
        executors.execute { consumer.fetchData() }
    }

    fun close() {
        if (!runFlag) {
            logger.info("mds data send handler is closed, don't need close.")
            return
        }

        while (!ringBuffer.isEmpty) {
            logger.info("wait for ring buffer empty, size: ${ringBuffer.size}")
            Thread.sleep(5 * 1000)
        }
        runFlag = false
        while (!endFlag) {
            Thread.sleep(1 * 1000)
        }
        producer.close()
        consumer.close()
        logger.info("mds data send handler closed.")
    }

    private fun send() {
        runFlag = true
        var sendCount = 0
        var sendLogTime = System.currentTimeMillis()
        while (runFlag) {
            val sendData = ringBuffer.take() ?: continue

            val sd = sendData as SendObject
            producer.sendData(getTopic(sd.type), sd.key, sd.value)

            sendCount++
            if (logger.isDebugEnabled && System.currentTimeMillis() - sendLogTime > 1000) {
                logger.debug("data send count = $sendCount, buffer remaining = ${ringBuffer.size}")
                sendLogTime = System.currentTimeMillis()
            }
        }
        endFlag = true
    }

    private fun getTopic(type: String): String =
        when (type) {
            indexStr -> indexTopic
            stockStr -> stockTopic
            tradeStr -> tradeTopic
            else -> throw RuntimeException("no such topic: $type")
        }

    data class SendObject(val type: String, val key: String, val value: Any)

}