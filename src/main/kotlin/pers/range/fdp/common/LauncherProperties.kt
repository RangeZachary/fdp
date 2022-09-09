package pers.range.fdp.common

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "fdp")
data class LauncherProperties (
    var mode: String = "history",
    var kafkaUrl: String = "localhost:9092",
    var filepath: String = "/data",
    var bufferSize: Int = 64 * 1024,
    var sendRetryTimes: Int = 10,
    var sendRetryIntervalMs: Int = 100,
    var saveLocal: Boolean = true,
)
