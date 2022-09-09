package pers.range.fdp.controller

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.logging.LogLevel
import org.springframework.boot.logging.LoggingSystem
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import pers.range.fdp.sevice.AcquirerService
import java.time.LocalDate
import javax.annotation.Resource

@RestController
@RequestMapping("acquirer")
class AcquirerController {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @Autowired
    private lateinit var acquirerService: AcquirerService

    @Resource
    private lateinit var loggingSystem: LoggingSystem

    @PostMapping("launch")
    fun launch() {
        acquirerService.manualLaunch()
    }

    @PostMapping("stop")
    fun stop() {
        acquirerService.manualStop()
    }

    @PostMapping("restart")
    fun restart() {
        acquirerService.restart()
    }

    @PostMapping("changeOperation")
    fun changeOperation(@RequestParam operation: String) {
        acquirerService.changeOperation(operation)
    }

    @PostMapping("changeMode")
    fun changeMode(@RequestParam mode: String, @RequestParam immediate: Boolean) {
        acquirerService.changeMode(mode, immediate)
    }

    @PostMapping("saveHistory")
    fun saveHistory(@RequestParam mode: String, @RequestParam date: String) {
        acquirerService.saveHistory(mode, LocalDate.parse(date))
    }

    @PostMapping("setLogLevel")
    fun setLogLevel(@RequestParam logName: String, @RequestParam logLevel: String) {
        loggingSystem.setLogLevel(logName, LogLevel.valueOf(logLevel.uppercase()))
        logger.warn("set $logName log level to $logLevel.")
    }

}