package pers.range.fdp.handler

import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import pers.range.fdp.common.KafkaAdminProperties
import pers.range.fdp.common.KafkaConsumerProperties
import pers.range.fdp.utils.KafkaUtils
import java.time.Duration

class KafkaConsumeHandler(private val topics: Array<String>,
                          private val preserver: DataPreserveHandler,
                          adminProperties: KafkaAdminProperties,
                          kafkaConsumerProperties: KafkaConsumerProperties,
                          useNewGroup: Boolean): BasicHandler {

    private val logger = LoggerFactory.getLogger(this::class.java)

    private val consumer: KafkaConsumer<String, ByteArray>

    private var runFlag = false

    private var endFlag = false

    private var emptyTimes = 0

    private val offsetMap = mutableMapOf<TopicPartition, Long>();

    init {
        KafkaUtils.initializeTopics(adminProperties, topics)

        consumer = KafkaUtils.newKafkaConsumer(kafkaConsumerProperties, useNewGroup)

        logger.info("kafka consumer handler initialize finished. topics: [${topics.joinToString(",")}]")
    }

    fun fetchData() {
        consumer.subscribe(topics.toList())

        runFlag = true
        var receiveCount = 0
        var receiveTime = System.currentTimeMillis()
        var flushTime = System.currentTimeMillis()
        var recordTime = System.currentTimeMillis()
        while (runFlag) {
            if (System.currentTimeMillis() - flushTime > 10 * 1000) {
                preserver.flushData()
                flushTime = System.currentTimeMillis()
            }

            val records = consumer.poll(Duration.ofMillis(100))

            // debug消费数量
            if (logger.isDebugEnabled && System.currentTimeMillis() - receiveTime > 1000) {
                logger.debug("data receive count = $receiveCount")
                receiveTime = System.currentTimeMillis()
            }

            // 消费者收到数据为空的心跳log
            if (records.isEmpty) {
                if (System.currentTimeMillis() - recordTime > 10 * 1000) {
                    logger.info("consumer poll received records count: ${records.count()}")
                    recordTime = System.currentTimeMillis()
                    emptyTimes++
                }
                continue
            }

            emptyTimes = 0
            receiveCount += records.count()

            // 消费者收到数据定时的心跳log
            if (System.currentTimeMillis() - recordTime > 5 * 1000) {
                logger.info("consumer poll received records count: ${records.count()}")
                recordTime = System.currentTimeMillis()
            }

            // 处理数据
            var isSuccess = true
            for (record in records) {
                try {
                    if (preserver.saveData(record)) {
                        val tp = TopicPartition(record.topic(), record.partition())
                        offsetMap[tp] = record.offset() + 1
                    } else {
                        logger.error("save data [${record.topic()}-${record.offset()}] error, no exception.")
                        isSuccess = false
                        break
                    }
                } catch (e: Exception) {
                    logger.error("save data [${record.topic()}-${record.offset()}] error: ", e)
                    isSuccess = false
                    break
                }
            }

            if (isSuccess) {
                consumer.commitSync()
            } else {
                topics.forEach { topic ->
                    val tp = TopicPartition(topic, 0)
                    consumer.seek(tp, offsetMap.getOrDefault(tp, 0))
                }
            }
        }

        endFlag = true
    }

    override fun close() {
        if (!runFlag) {
            logger.info("kafka consumer handler is closed, don't need close.")
            return
        }

        // 10秒记一次，超过18则表示超过3分钟没收到任何数据，表示可close
        while (emptyTimes < 18) {
            logger.info("wait for consumer finish, empty times: $emptyTimes")
            Thread.sleep(10 * 1000)
        }
        runFlag = false
        while (!endFlag) {
            logger.debug("wait for receive proc end, empty times: $emptyTimes")
            Thread.sleep(1 * 1000)
        }
        offsetMap.clear()
        consumer.close()
        preserver.close()
        logger.info("kafka consumer handler closed.")
    }

}