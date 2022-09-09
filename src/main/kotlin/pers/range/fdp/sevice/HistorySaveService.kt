package pers.range.fdp.sevice

import pers.range.fdp.handler.DataPreserveHandler

interface HistorySaveService {

    fun saveToFile(topics: Array<String>, dataPreserveHandler: DataPreserveHandler)

}