package pers.range.fdp.common

import org.slf4j.LoggerFactory

class RingBuffer(
    private val length: Int // ringBuffer的总长度，最大能够存储的成员数
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * ringBuffer内部存储的现有的成员数
     */
    var size: Int = 0

    /**
     * 写指针,代表下一次操作执行的位置
     */
    private var writePos: Int = 0

    /**
     * 读指针,代表下一次操作执行的位置
     */
    private var readPos: Int = 0

    /**
     * 放置成员的循环数组，长度为length
     */
    private val list: Array<Any?> = arrayOfNulls(length)

    /**
     * 显示ringBuffer已满，是否还能插入新的元素
     *
     * @return 如果数组已满，不能插入，返回；如果能插入，返回false
     */
    private val isFull: Boolean
        get() = size == length

    /**
     * 显示ringBuffer是否为空，是否还能取出元素
     *
     * @return 如果数组为空，不能取出元素，返回true；如果能取出，返回false
     */
    val isEmpty: Boolean
        get() = size == 0

    /**
     * 显示ringBuffer是否接近满
     *
     * @return 如果数组成员数超过总数的95%，返回true；否则，返回false
     */
    val isCloseToFull: Boolean
        get() = size > length * 0.95

    private var allCount = 0
    private var sizeLogTime = System.currentTimeMillis()

    /**
     * 在ringBuffer中插入一个元素，在写指针处插入元素
     *
     * @param obj 被插入的元素
     * @return 如果成功插入，返回true<br></br> 如果队列已满，返回false
     */
    fun put(obj: Any): Boolean {
        if (isFull) {
            //如果队列已满
            logger.warn("队列已满，请即使消费")
            return false
        }
        if (writePos == length - 1) {
            //如果这次写入的位置在队列的最后一个元素
            list[writePos] = obj
            //那么下次写入的位置为0
            writePos = 0
        } else {
            //这次写入的位置不是队列最后一个元素，那么现在writePos处写入，然后++
            list[writePos++] = obj
        }
        //ringBuffer中的成员数增加，size的处理在最后操作，防止size增加了，读操作却没有读到
        size++

        // debug
        allCount++
        logSize()

        return true
    }

    /**
     * 从ringBuffer中取出一个元素
     *
     * @return 如果队列为空，返回null；否则，返回读指针对应的元素
     */
    fun take(): Any? {
        if (isEmpty) {
            return null
        }
        val result: Any?
        if (readPos == length - 1) {
            //如果这次取出的位置在队列的最后一个元素
            result = list[readPos]
            //将对于位置的元素清空
            list[readPos] = null
            readPos = 0
        } else {
            result = list[readPos]
            list[readPos++] = null
        }
        size--
        return result
    }

    private fun logSize() {
        if (logger.isDebugEnabled && System.currentTimeMillis() - sizeLogTime > 1000) {
            logger.debug("buffer: all count = $allCount, buffer size = $size")
            sizeLogTime = System.currentTimeMillis()
        }
    }

}