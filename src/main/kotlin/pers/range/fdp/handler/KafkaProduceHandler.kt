package pers.range.fdp.handler

import com.alibaba.fastjson.JSON
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import pers.range.fdp.common.KafkaAdminProperties
import pers.range.fdp.common.KafkaConsumerProperties
import pers.range.fdp.common.KafkaProducerProperties
import pers.range.fdp.utils.KafkaUtils

class KafkaProduceHandler(private val topics: Array<String>,
                          adminProperties: KafkaAdminProperties,
                          kafkaProducerProperties: KafkaProducerProperties): BasicHandler {

    private val logger = LoggerFactory.getLogger(this::class.java)

    private val producer: KafkaProducer<String, ByteArray>

    init {
        KafkaUtils.initializeTopics(adminProperties, topics)

        producer = KafkaUtils.newKafkaProducer(kafkaProducerProperties)

        logger.info("kafka producer handler initialize finished. topics: [${topics.joinToString(",")}]")
    }

    fun sendData(topic: String, key: String, value: Any?) {
        if (!topics.contains(topic)) {
            logger.error("did not create topic, can not send data to $topic.")
        }
        if (value == null) {
            logger.error("send value is null, topic: $topic, key: $key")
        }
        val valueJson = JSON.toJSONBytes(value)
        producer.send(ProducerRecord(topic, key, valueJson)).get()
        if (logger.isTraceEnabled) {
            logger.trace("send data bytes size: ${valueJson.size}")
        }
    }

    override fun close() {
        producer.close()
        logger.info("kafka producer handler closed.")
    }

}