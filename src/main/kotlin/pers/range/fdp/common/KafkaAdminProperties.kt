package pers.range.fdp.common

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "kafka.admin")
data class KafkaAdminProperties (
    var bootstrapServers: String = "",
)
