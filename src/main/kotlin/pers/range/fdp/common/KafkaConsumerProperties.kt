package pers.range.fdp.common

import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "kafka.consumer")
data class KafkaConsumerProperties (
    var bootstrapServers: String = "",
    var keyDeserializer: String = StringSerializer::class.java.name,
    var valueDeserializer: String = ByteArraySerializer::class.java.name,
    var enableAutoCommit: String = "false",
    var autoOffsetReset: String = "earliest",
    var groupId: String = "group",
)
