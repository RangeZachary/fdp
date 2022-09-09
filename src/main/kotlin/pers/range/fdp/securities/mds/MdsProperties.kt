package pers.range.fdp.securities.mds

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "mds")
data class MdsProperties (
    var configPath: String = "src/main/resources/mds_api_config.json",
    var username: String = "customer528",
    var password: String = "tJxCsAyr",
)
