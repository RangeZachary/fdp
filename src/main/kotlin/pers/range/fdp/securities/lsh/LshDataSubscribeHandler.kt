package pers.range.fdp.securities.lsh

import com.lsh.lv2.client.api.HandleSubData
import com.lsh.lv2.client.api.data.*
import org.slf4j.LoggerFactory
import pers.range.fdp.common.KafkaAdminProperties
import pers.range.fdp.common.KafkaConsumerProperties
import pers.range.fdp.common.KafkaProducerProperties
import pers.range.fdp.handler.KafkaConsumeHandler
import pers.range.fdp.handler.KafkaProduceHandler
import pers.range.fdp.utils.FileUtils.obtainKey
import java.time.LocalDate
import java.util.concurrent.Executors

class LshDataSubscribeHandler(private val date: LocalDate,
                              private val parentPath: String,
                              private val marketStr: String,
                              private val transactionStr: String,
                              private val orderStr: String,
                              private val ticketIndexStr: String,
                              private val adminProperties: KafkaAdminProperties,
                              private val kafkaConsumerProperties: KafkaConsumerProperties,
                              private val kafkaProducerProperties: KafkaProducerProperties): HandleSubData {

    private val logger = LoggerFactory.getLogger(this::class.java)

    private lateinit var marketTopic: String
    private lateinit var transactionTopic: String
    private lateinit var orderTopic: String
    private lateinit var ticketIndexTopic: String

    private lateinit var producer: KafkaProduceHandler
    private lateinit var consumer: KafkaConsumeHandler

    private fun init() {
        marketTopic = "$marketStr-$date"
        transactionTopic = "$transactionStr-$date"
        orderTopic = "$orderStr-$date"
        ticketIndexTopic = "$ticketIndexStr-$date"

        val topics = arrayOf(marketTopic, transactionTopic, orderTopic, ticketIndexTopic)
        producer = KafkaProduceHandler(topics, adminProperties, kafkaProducerProperties)

        val lshDataPreserveHandler = LshDataPreserveHandler(parentPath, date,
            marketStr, transactionStr, orderStr, ticketIndexStr)
        lshDataPreserveHandler.init()
        consumer = KafkaConsumeHandler(topics, lshDataPreserveHandler,
            adminProperties, kafkaConsumerProperties, false)
    }

    override fun start() {
        logger.info("开始处理订阅数据")
        init()
        Executors.newSingleThreadExecutor().execute { consumer.fetchData() }
    }

    override fun stop() {
        producer.close()
        consumer.close()
        logger.info("结束处理订阅数据")
    }

    override fun onMarket(market: Market?) {
        producer.sendData(marketTopic, obtainKey("market", market?.code), market)
    }

    override fun onTransaction(transaction: Transaction?) {
        producer.sendData(transactionTopic, obtainKey("transaction", transaction?.code), transaction)
    }

    override fun onOrder(order: Order?) {
        producer.sendData(orderTopic, obtainKey("order", order?.code), order)
    }

    override fun onTicketIndex(ticketIndex: TicketIndex?) {
        producer.sendData(ticketIndexTopic, obtainKey("ticketIndex", ticketIndex?.code), ticketIndex)
    }

}