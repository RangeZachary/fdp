package pers.range.fdp.sevice

import java.time.LocalDate

interface TaskService {

    fun init()

    fun startTask()

    fun stopTask()

    fun saveHistory(date: LocalDate)

}