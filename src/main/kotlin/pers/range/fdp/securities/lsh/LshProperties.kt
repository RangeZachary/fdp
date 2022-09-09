package pers.range.fdp.securities.lsh

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "lsh")
data class LshProperties (
    var webSocketUrl: String = "ws://124.221.102.98:29393/lv2/data",
    var token: String = "test-abc123-01",
    var code: String = "600000,000001",
)
