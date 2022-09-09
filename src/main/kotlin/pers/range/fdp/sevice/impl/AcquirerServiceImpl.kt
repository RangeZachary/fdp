package pers.range.fdp.sevice.impl

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import pers.range.fdp.common.LauncherProperties
import pers.range.fdp.securities.lsh.LshTaskService
import pers.range.fdp.securities.mds.MdsTaskService
import pers.range.fdp.sevice.AcquirerService
import pers.range.fdp.sevice.TaskService
import java.time.LocalDate
import java.time.LocalTime

@EnableScheduling
@Service
class AcquirerServiceImpl: AcquirerService {

    private val logger = LoggerFactory.getLogger(this::class.java)

    private val startHour = 9
    private val startMinute = 20
    private val stopHour = 15
    private val stopMinute = 10

    @Autowired
    private lateinit var launcherProperties: LauncherProperties

    @Autowired
    private lateinit var lshTaskService: LshTaskService

    @Autowired
    private lateinit var mdsTaskService: MdsTaskService

    private var currentRunningMode = ""

    private var currentRunningDate = LocalDate.MIN

    private var isRunning = false

    private var isStopping = false

    private var isManual = false

    private var isChanging = false

    override fun manualLaunch() {
        isManual = true
        launch()
    }

    override fun manualStop() {
        isManual = true
        stop()
    }

    override fun restart() {
        logger.info("acquirer will restart.")
        stop()
        Thread.sleep(30 * 1000)
        launch()
    }

    override fun changeOperation(operation: String) =
        when (operation) {
            "manual" -> {
                logger.info("change to manual")
                isManual = true
            }
            "auto" -> {
                logger.info("change to auto")
                isManual = false
            }
            else -> throw RuntimeException("no such operation [$operation], only support manual/auto.")
        }

    override fun changeMode(mode: String, immediate: Boolean) {
        if (currentRunningMode == mode) {
            logger.info("current running mode is $currentRunningMode, don't need restart.")
            return
        }

        launcherProperties.mode = mode
        if (immediate) {
            logger.warn("mode had changed from $currentRunningMode to $mode, execute immediately.")
            isChanging = true
            restart()
            isChanging = false
        } else {
            logger.warn("mode had changed, will execute from $currentRunningMode to $mode at next time.")
        }
    }

    override fun saveHistory(mode:String, date: LocalDate) =
        when (mode) {
            "lsh" -> lshTaskService.saveHistory(date)
            "mds" -> mdsTaskService.saveHistory(date)
            else -> throw RuntimeException("no such mode.")
        }

    /**
     * 每天9:20触发启动
     */
    @Scheduled(cron = "0 20 9 * * ?")
    private fun launch() {
        if (isRunning) {
            logger.warn("program is running, can not launch again.")
            return
        }

        val today = LocalDate.now()
        if (!currentRunningDate.equals(today)) {
            getTask(launcherProperties.mode).init()
        }

        getTask(launcherProperties.mode).startTask()
        isRunning = true
        currentRunningMode = launcherProperties.mode
        currentRunningDate = today
        logger.info("acquirer launched.")
    }

    /**
     * 每天15:10触发停止
     */
    @Scheduled(cron = "0 10 15 * * ?")
    private fun stop() {
        if (!isRunning || isStopping) {
            logger.warn("program is not running or is stopping, can not stop again.")
            return
        }

        isStopping = true
        getTask(currentRunningMode).stopTask()
        isRunning = false
        isStopping = false
        logger.info("acquirer stopped.")
    }

    /**
     * 检查任务起停状态，每5分钟执行
     */
    @Scheduled(initialDelay = 10 * 1000 + 999, fixedRate = 5 * 60 * 1000)
    private fun checkStatus() {
        if (isManual || isChanging) {
            logger.info("check status is pausing, now is manual or changing.")
            return
        }

        val currentTime = LocalTime.now()
        logger.info("check status at [$currentTime]")

        if (shouldRunning(currentTime) && !isRunning) {
            logger.warn("check status stopped, launch.")
            launch()
        } else if (shouldStopped(currentTime) && isRunning) {
            logger.warn("check status running, stop.")
            stop()
        }
    }

    /**
     * 每天1:00设置为自动模式
     */
    @Scheduled(cron = "0 0 1 * * ?")
    private fun setToAuto() {
        logger.info("change to auto.")
        isManual = false
    }

    private fun getTask(mode: String): TaskService =
        when (mode) {
            "lsh" -> lshTaskService
            "mds" -> mdsTaskService
            else -> throw RuntimeException("no such mode: [$mode].")
        }

    /**
     * 在 9:25 - 15:05 期间内，未运行状态则启动
     */
    private fun shouldRunning(currentTime: LocalTime): Boolean =
        currentTime.isAfter(LocalTime.of(startHour, startMinute + 5))
                && currentTime.isBefore(LocalTime.of(stopHour, stopMinute - 5))

    /**
     * 在 15:15 - 9:15(第二天) 期间内，运行状态则停止
     */
    private fun shouldStopped(currentTime: LocalTime): Boolean =
        currentTime.isBefore(LocalTime.of(startHour, startMinute - 5))
                || currentTime.isAfter(LocalTime.of(stopHour, stopMinute + 5))

}