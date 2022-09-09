package pers.range.fdp.securities.lsh

import com.lsh.lv2.client.api.Client
import com.lsh.lv2.client.api.LV2_MESSAGE_CONSTANT
import com.lsh.lv2.client.api.data.Lv2Param
import com.lsh.lv2.client.config.ClientConfig
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import pers.range.fdp.common.KafkaAdminProperties
import pers.range.fdp.common.KafkaConsumerProperties
import pers.range.fdp.common.KafkaProducerProperties
import pers.range.fdp.common.LauncherProperties
import pers.range.fdp.sevice.HistorySaveService
import pers.range.fdp.sevice.TaskService
import java.time.LocalDate

@Service
class LshTaskServiceImpl: LshTaskService {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @Autowired
    private lateinit var launcherProperties: LauncherProperties

    @Autowired
    private lateinit var lshProperties: LshProperties

    @Autowired
    private lateinit var adminProperties: KafkaAdminProperties

    @Autowired
    private lateinit var kafkaConsumerProperties: KafkaConsumerProperties

    @Autowired
    private lateinit var kafkaProducerProperties: KafkaProducerProperties

    @Autowired
    private lateinit var historySaveService: HistorySaveService

    private val marketStr = "market"
    private val transactionStr = "transaction"
    private val orderStr = "order"
    private val ticketIndexStr = "ticketIndex"

    private lateinit var client: Client

    override fun init() {
        val dataSubHandler = LshDataSubscribeHandler(LocalDate.now(), launcherProperties.filepath,
            marketStr, transactionStr, orderStr, ticketIndexStr, adminProperties,
            kafkaConsumerProperties, kafkaProducerProperties)
        val clientConfig = ClientConfig(lshProperties.webSocketUrl)
        val lv2Param = Lv2Param(lshProperties.code, LV2_MESSAGE_CONSTANT.SUB_ALL)
        client = Client(clientConfig, dataSubHandler, lv2Param, lshProperties.token)
    }

    override fun startTask() {
        client.start()
    }

    override fun stopTask() {
        client.stop()
    }

    override fun saveHistory(date: LocalDate) {
        logger.info("start lsh save [$date] history.")
        val topics = arrayOf("$marketStr-$date", "$transactionStr-$date",
            "$orderStr-$date", "$ticketIndexStr-$date")
        val lshDataPreserveHandler = LshDataPreserveHandler(launcherProperties.filepath,
            date, marketStr, transactionStr, orderStr, ticketIndexStr)
        lshDataPreserveHandler.init()
        historySaveService.saveToFile(topics, lshDataPreserveHandler)
    }

}