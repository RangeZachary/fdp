package pers.range.fdp.securities.mds

import com.quant360.api.callback.MdsCallBack
import com.quant360.api.client.MdsClient
import com.quant360.api.model.mds.MdsMktDataRequestRsp
import com.quant360.api.model.mds.MdsMktDataSnapshotHead
import com.quant360.api.model.mds.MdsIndexSnapshotBody
import com.quant360.api.model.mds.MdsL2StockSnapshotBody
import com.quant360.api.model.mds.MdsL2Trade
import com.quant360.api.model.mds.enu.MdsSubStreamType
import org.slf4j.LoggerFactory
import pers.range.fdp.sevice.TaskService
import pers.range.fdp.utils.FileUtils.obtainKey

class MdsDataSubscribeCallBack(private val indexStr: String,
                               private val stockStr: String,
                               private val tradeStr: String,
                               private val dataSendHandler: MdsDataSendHandler,
                               private val mdsTask: TaskService) : MdsCallBack() {

    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * 行情订阅回调，回调处理注意事项(以下回调皆同)：
     * 1.在回调函数中获取行情回报后，将回报消息封装后放到类似RingBuffer的队列中，由另外的线程做后续处理。
     * 2.不要在回调函数中做复杂耗时的操作，这样会占用API的回调处理线程。
     * 3.行情消息信息量很大，要及时释放掉对消息对象的引用，方便JVM进行GC防止堆内存占用过大。
     */
    override fun onMktReq(rsp: MdsMktDataRequestRsp) {
        logger.info("行情订阅成功! {}", rsp)
        dataSendHandler.start()
    }

    /**
     * 连接中断后，由另外的线程发起重连操作，不要在回调函数中直接操作，这样会占用API的回调处理线程
     */
    override fun onDisConn(client: MdsClient) {
        logger.error("行情连接已中断！重启订阅。")
        mdsTask.stopTask()
        mdsTask.startTask()
    }

    /**
     * 指数行情回调
     * @param head 证券行情全幅消息的消息头
     * @param data 指数的行情全幅消息
     */
    override fun onMktIndex(head: MdsMktDataSnapshotHead, data: MdsIndexSnapshotBody) {
        // 只需要股票数据
        if (head.subStreamType != MdsSubStreamType.MDS_SUB_STREAM_TYPE_STOCK) {
            return
        }
        dataSendHandler.put(indexStr, obtainKey(indexStr, data.securityID), ConformData(head, data))
    }

    /**
     * Level2 快照行情(股票、债券、基金)回调
     * @param head 行情的消息头
     * @param data Level2 快照行情
     */
    override fun onMktL2StockSnapshot(head: MdsMktDataSnapshotHead, data: MdsL2StockSnapshotBody) {
        // 只需要股票数据
        if (head.subStreamType != MdsSubStreamType.MDS_SUB_STREAM_TYPE_STOCK) {
            return
        }
        dataSendHandler.put(stockStr, obtainKey(stockStr, data.securityID), ConformData(head, data))
    }

    /**
     * Level2 逐笔成交行情
     * @param data 行情消息体
     */
    override fun onMktL2Trade(data: MdsL2Trade) {
        // 只需要股票数据
        if (data.subStreamType != MdsSubStreamType.MDS_SUB_STREAM_TYPE_STOCK) {
            return
        }
        dataSendHandler.put(tradeStr, obtainKey(tradeStr, data.securityID), ConformData(null, data))
    }

    fun close() {
        dataSendHandler.close()
    }

    data class ConformData(val head: Any?, val data: Any)

}