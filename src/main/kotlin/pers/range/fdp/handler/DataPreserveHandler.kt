package pers.range.fdp.handler

import org.apache.kafka.clients.consumer.ConsumerRecord

interface DataPreserveHandler: BasicHandler {

    fun init()

    fun saveData(record: ConsumerRecord<String, ByteArray>): Boolean

    fun flushData()

}