package pers.range.fdp.sevice.impl

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import pers.range.fdp.common.KafkaAdminProperties
import pers.range.fdp.common.KafkaConsumerProperties
import pers.range.fdp.handler.DataPreserveHandler
import pers.range.fdp.handler.KafkaConsumeHandler
import pers.range.fdp.sevice.HistorySaveService
import java.util.concurrent.Executors

@Service
class HistorySaveServiceImpl: HistorySaveService {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @Autowired
    private lateinit var adminProperties: KafkaAdminProperties

    @Autowired
    private lateinit var kafkaConsumerProperties: KafkaConsumerProperties

    private val waitSeconds = 60

    override fun saveToFile(topics: Array<String>, dataPreserveHandler: DataPreserveHandler) {
        val consumer = KafkaConsumeHandler(topics, dataPreserveHandler,
            adminProperties, kafkaConsumerProperties, true)
        Executors.newSingleThreadExecutor().execute { consumer.fetchData() }
        Thread.sleep(waitSeconds * 1000L)
        consumer.close()
        logger.info("save history finished.")
    }

}