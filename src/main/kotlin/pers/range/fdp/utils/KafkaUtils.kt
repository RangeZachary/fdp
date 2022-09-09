package pers.range.fdp.utils

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import pers.range.fdp.common.KafkaAdminProperties
import pers.range.fdp.common.KafkaConsumerProperties
import pers.range.fdp.common.KafkaProducerProperties
import java.util.*

object KafkaUtils {

    @JvmStatic
    fun initializeTopics(kafkaAdminProperties: KafkaAdminProperties, topics: Array<String>) =
        AdminClient.create(Properties().apply {
            this[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = kafkaAdminProperties.bootstrapServers
        }).use { adminClient ->
            val existTopics = adminClient.listTopics().names().get();
            val needCreateTopics = mutableListOf<NewTopic>()
            topics.filter { !existTopics.contains(it) }
                .map { needCreateTopics.add(NewTopic(it, 1, 1)) }
            if (needCreateTopics.isNotEmpty()) {
                adminClient.createTopics(needCreateTopics).all().get()
            }
        }

    @JvmStatic
    fun newKafkaProducer(kafkaProducerProperties: KafkaProducerProperties): KafkaProducer<String, ByteArray> =
        KafkaProducer<String, ByteArray>(Properties().apply {
            this[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaProducerProperties.bootstrapServers
            this[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = kafkaProducerProperties.keySerializer
            this[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = kafkaProducerProperties.valueSerializer
            this[ProducerConfig.ACKS_CONFIG] = kafkaProducerProperties.acks
            this[ProducerConfig.BUFFER_MEMORY_CONFIG] = kafkaProducerProperties.bufferMemory
            this[ProducerConfig.BATCH_SIZE_CONFIG] = kafkaProducerProperties.batchSize
            this[ProducerConfig.MAX_REQUEST_SIZE_CONFIG] = kafkaProducerProperties.maxRequestSize
            this[ProducerConfig.LINGER_MS_CONFIG] = kafkaProducerProperties.lingerMs
        })

    @JvmStatic
    fun newKafkaConsumer(kafkaConsumerProperties: KafkaConsumerProperties, useNewGroup: Boolean = false): KafkaConsumer<String, ByteArray> =
        KafkaConsumer<String, ByteArray>(Properties().apply {
            this[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaConsumerProperties.bootstrapServers
            this[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = kafkaConsumerProperties.keyDeserializer
            this[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = kafkaConsumerProperties.valueDeserializer
            this[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = kafkaConsumerProperties.enableAutoCommit
            this[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = kafkaConsumerProperties.autoOffsetReset
            this[ConsumerConfig.GROUP_ID_CONFIG] = if (useNewGroup) "${kafkaConsumerProperties.groupId}-${System.currentTimeMillis()}"
                                                    else kafkaConsumerProperties.groupId
        })

}