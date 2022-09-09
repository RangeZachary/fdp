package pers.range.fdp.sevice

import java.time.LocalDate

interface AcquirerService {

    fun manualLaunch()

    fun manualStop()

    fun restart()

    fun changeOperation(operation: String)

    fun changeMode(mode: String, immediate: Boolean = false)

    fun saveHistory(mode:String, date: LocalDate)

}