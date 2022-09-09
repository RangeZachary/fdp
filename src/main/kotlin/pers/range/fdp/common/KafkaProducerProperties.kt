package pers.range.fdp.common

import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "kafka.producer")
data class KafkaProducerProperties (
    var bootstrapServers: String = "localhost:9092",
    var keySerializer: String = StringSerializer::class.java.name,
    var valueSerializer: String = ByteArraySerializer::class.java.name,
    var acks: String = "1",
    var bufferMemory: Int = 33554432,
    var batchSize: Int = 16384,
    var maxRequestSize: Int = 1048576,
    var lingerMs: Int = 0,
)
