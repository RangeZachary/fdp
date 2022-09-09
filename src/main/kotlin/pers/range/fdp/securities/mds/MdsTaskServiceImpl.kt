package pers.range.fdp.securities.mds

import com.quant360.api.client.MdsClient
import com.quant360.api.client.impl.MdsClientImpl
import com.quant360.api.model.ClientLogonReq
import com.quant360.api.model.ClientLogonRsp
import com.quant360.api.model.mds.MdsMktDataRequestEntry
import com.quant360.api.model.mds.MdsMktDataRequestReq
import com.quant360.api.model.mds.enu.*
import com.quant360.api.model.oes.enu.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import pers.range.fdp.common.KafkaAdminProperties
import pers.range.fdp.common.KafkaConsumerProperties
import pers.range.fdp.common.KafkaProducerProperties
import pers.range.fdp.common.LauncherProperties
import pers.range.fdp.securities.lsh.LshDataPreserveHandler
import pers.range.fdp.sevice.HistorySaveService
import java.time.LocalDate

@Service
class MdsTaskServiceImpl: MdsTaskService {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @Autowired
    private lateinit var launcherProperties: LauncherProperties

    @Autowired
    private lateinit var mdsProperties: MdsProperties

    @Autowired
    private lateinit var adminProperties: KafkaAdminProperties

    @Autowired
    private lateinit var kafkaConsumerProperties: KafkaConsumerProperties

    @Autowired
    private lateinit var kafkaProducerProperties: KafkaProducerProperties

    @Autowired
    private lateinit var historySaveService: HistorySaveService

    private lateinit var client: MdsClient

    private lateinit var callBack: MdsDataSubscribeCallBack

    private val indexStr = "index"
    private val stockStr = "stock"
    private val tradeStr = "trade"

    override fun init() {
        val mdsDataSendHandler = MdsDataSendHandler(LocalDate.now(), launcherProperties.filepath,
            indexStr, stockStr, tradeStr, launcherProperties, adminProperties,
            kafkaConsumerProperties, kafkaProducerProperties)
        callBack = MdsDataSubscribeCallBack(indexStr, stockStr, tradeStr, mdsDataSendHandler, this)

        client = MdsClientImpl(mdsProperties.configPath)
        client.initCallBack(callBack)
    }

    override fun startTask() {
        val logonReq = buildClientLogonReq()
        val logonRsp = client.start(logonReq)
        if (logonRsp.isSuccess) {
            logger.info("MDS客户端: ${logonReq.username} 登录成功！")
        } else {
            logonFailProcess(logonReq, logonRsp)
        }
        client.subscribeMarketData(buildMktReq(), null)
    }

    override fun stopTask() {
        client.close()
        callBack.close()
    }

    override fun saveHistory(date: LocalDate) {
        logger.info("start mds save [$date] history.")
        val topics = arrayOf("$indexStr-$date", "$stockStr-$date", "$tradeStr-$date")
        val mdsDataPreserveHandler = MdsDataPreserveHandler(launcherProperties.filepath,
            date, indexStr, stockStr, tradeStr)
        mdsDataPreserveHandler.init()
        historySaveService.saveToFile(topics, mdsDataPreserveHandler)
    }

    private fun buildClientLogonReq(): ClientLogonReq =
        ClientLogonReq().apply {
            this.username = mdsProperties.username
            this.password = mdsProperties.password
            /**
             * 心跳间隔, 单位为秒 (默认30秒, 有效范围为 [10~300], 若取值小于0则赋值为默认值30秒，否则设置为最大/最小值)
             */
            this.heartBtInt = 30
            /**
             * 硬盘序列号 (默认取API 配置文件中 "clDriverId"属性的值)
             */
            this.clientDriverId = "DAEB7F56"
        }

    private fun logonFailProcess(logonReq: ClientLogonReq, logonRsp: ClientLogonRsp) {
        when (val code = logonRsp.errorCode) {
            ErrorCode.CLIENT_DISABLE ->
                throw RuntimeException("MDS客户端: ${logonReq.username} 登录失败 ! [客户端已被禁用] errorCode = $code")
            ErrorCode.INVALID_CLIENT_NAME ->
                throw RuntimeException("MDS客户端: ${logonReq.username} 登录失败 ！[非法的客户端登陆用户名称 ] errorCode = $code")
            ErrorCode.INVALID_CLIENT_PASSWORD ->
                throw RuntimeException("MDS客户端: ${logonReq.username} 登录失败 ！[客户端密码不正确] errorCode = $code")
            ErrorCode.CLIENT_REPEATED ->
                throw RuntimeException("MDS客户端: ${logonReq.username} 登录失败 ！[客户端重复登录] errorCode = $code")
            ErrorCode.CLIENT_CONNECT_OVERMUCH ->
                throw RuntimeException("MDS客户端: ${logonReq.username} 登录失败 ！[客户端连接数量过多] errorCode = $code")
            ErrorCode.INVALID_PROTOCOL_VERSION ->
                throw RuntimeException("MDS客户端: ${logonReq.username} 登录失败 ! [API协议版本不兼容] errorCode = $code")
            ErrorCode.OTHER_ERROR ->
                throw RuntimeException("MDS客户端: ${logonReq.username} 登录失败 ！[其他错误] errorCode = $code")
            else ->
                throw RuntimeException("MDS客户端: ${logonReq.username} 登录失败 ！[未知错误] errorCode = $code")
        }
    }

    private fun buildMktReq(): MdsMktDataRequestReq =
        MdsMktDataRequestReq().apply {
            /**
             * 订阅模式
             * - 0: (Set) 重新订阅，设置为订阅列表中的股票
             * - 1: (Append) 追加订阅，增加订阅列表中的股票
             * - 2: (Delete) 删除订阅，删除订阅列表中的股票
             * - 10: (BatchBegin)   批量订阅-开始订阅, 开始一轮新的批量订阅 (之前的订阅参数将被清空,
             * 行情推送也将暂停直到批量订阅结束)
             * - 11: (BatchAppend)  批量订阅-追加订阅, 增加订阅列表中的股票
             * - 12: (BatchDelete)  批量订阅-删除订阅, 删除订阅列表中的股票
             * - 13: (BatchEnd)     批量订阅-结束订阅, 结束本轮的批量订阅, 提交和启用本轮的订阅参数
             */
            this.subMode = MdsSubscribeMode.SUB_MODE_SET
            /**
             * 数据模式 (TickType) 定义 (仅对快照行情生效, 用于标识订阅最新的行情快照还是所有时点的行情快照)
             *
             * 取值说明:
             * -  0: (LatestSimplified) 只订阅最新的行情快照数据, 并忽略和跳过已经过时的数据
             * - 该模式推送的数据量最小, 服务器端会做去重处理, 不会再推送重复的和已经过时的快照数据
             * - 优点: 该模式在时延和带宽方面都更加优秀, 该模式优先关注快照行情的时效性, 并避免推送没有实质变化的重复快照
             * - 缺点: 当没有行情变化时 (例如没有交易或盘中休市等), 就不会推送任何快照行情了, 这一点可能会带来困扰, 不好确定是发生丢包了还是没有行情导致的
             * - 注意: 过时和重复的快照都会被过滤掉
             * -  1: (LatestTimely) 只订阅最新的行情快照数据, 并立即发送最新数据
             * - 只要有行情更新事件, 便立即推送该产品的最新行情, 但行情数据本身可能是重复的, 即只有行情时间有变化, 行情数据本身没有变化
             * - 优点: 可以获取到时间点更完整的快照行情, 不会因为行情数据没有变化而跳过 (如果是因为接收慢等原因导致快照已经过时了, 该快照还是会被忽略掉)
             * - 缺点: 会收到仅更新时间有变化, 但行情数据本身并没有更新的重复数据, 带宽和数据量上会有一定的浪费
             * - 注意: 重复的快照不会被过滤掉, 但过时的快照还是会被过滤掉
             * -  2: (AllIncrements) 订阅所有时点的行情快照数据 (包括Level2增量更新消息)
             * - 该模式会推送所有时点的行情数据, 包括Level2行情快照的增量更新消息
             * - 如果需要获取全量的行情明细, 或者需要直接使用Level2的增量更新消息, 可以使用该模式
             * - 如果没有特别需求的话, 不需要订阅增量更新消息, 增量消息处理起来比较麻烦
             * - 增量更新消息 (字段级别的增量更新) 只有上交所Level-2快照有, 深交所行情里面没有
             * - 在对下游系统进行推送时, 增量快照和完整快照在推送时间上是没有区别的, 增量更新并不会比完整快照更快, 只是信息角度不一样
             *
             * 补充说明:
             * - 当以 tickType=0 的模式订阅行情时, 服务器端会对重复的快照行情做去重处理, 不会再推送重复的和已经过时的快照数据。
             * - 如果需要获取到"所有时点"的快照, 可以使用 tickType=1 的模式订阅行情。此模式下只要行情时间有变化, 即使数据重复也会对下游推送。但过时的快照还是会被忽略掉。
             * - 只有通过 tickType=2 的模式才能接收到完整的所有时间点的行情数据。
             * - 快照行情 "过时" 表示: 不是当前最新的快照即为"过时", 即存在时间点比该快照更新的快照 (同一只股票)。
             * - @note  上交所行情存在更新时间相同但数据不同的Level-2快照。(频率不高, 但会存在这样的数据)
             */
            this.tickType = MdsSubscribedTickType.MDS_TICK_TYPE_LATEST_SIMPLIFIED
            /**
             * 逐笔数据的过期时间类型
             * -  0: 不过期
             * -  1: 立即过期 (1秒, 若落后于快照1秒则视为过期)
             * -  2: 及时过期 (3秒)
             * -  3: 超时过期 (30秒)
             *
             * @see MdsSubscribedTickExpireType
             *
             * @note    仅对压缩行情端口生效, 普通的非压缩行情端口不支持该选项
             * @note    因为存在不可控的网络因素, 所以做不到百分百的精确过滤, 如果对数据的时效性有精确要求, 就需要在前端对数据再进行一次过滤
             */
            this.tickExpireType = MdsSubscribedTickExpireType.MDS_TICK_EXPIRE_TYPE_NONE
            /**
             * 逐笔数据的数据重建标识 (标识是否订阅重建到的逐笔数据)
             * -  MDS_TICK_REBUILD_FLAG_EXCLUDE_REBUILDED 0: 不订阅重建到的逐笔数据 (仅实时行情)
             * -  MDS_TICK_REBUILD_FLAG_INCLUDE_REBUILDED 1: 订阅重建到的逐笔数据 (实时行情+重建数据)
             * -  MDS_TICK_REBUILD_FLAG_ONLY_REBUILDED 2: 只订阅重建到的逐笔数据 (仅重建数据 @note 需要通过压缩行情端口进行订阅, 非压缩行情端口不支持该选项)
             *
             * @see MdsSubscribedTickRebuildFlag
             */
            this.tickRebuildFlag = MdsSubscribedTickRebuildFlag.MDS_TICK_REBUILD_FLAG_EXCLUDE_REBUILDED
            /**
             * 订阅的数据种类
             * - 0/SUB_DATA_TYPE_DEF:                            	默认数据种类(所有)
             * - 0x0001/SUB_DATA_TYPE_L1_SNAPSHOT: 					L1快照
             * - 0x0002/SUB_DATA_TYPE_L2_SNAPSHOT: 					L2快照
             * - 0x0004/SUB_DATA_TYPE_L2_BEST_ORDERS: 				L2委托队列
             * - 0x0008/SUB_DATA_TYPE_L2_TRADE: 					L2逐笔成交
             * - 0x0010/SUB_DATA_TYPE_L2_ORDER: 					L2逐笔委托(深圳)
             * - 0x0020/SUB_DATA_TYPE_L2_SSE_ORDER: 				L2逐笔委托(上海)
             * - 0x0040/SUB_DATA_TYPE_L2_MARKET_OVERVIEW: 			L2市场总览(上海)
             * - 0x0100/SUB_DATA_TYPE_TRADING_SESSION_STATUS: 		市场状态(上海)
             * - 0x0200/SUB_DATA_TYPE_SECURITY_STATUS: 				证券实时状态(深圳)
             * - 0x0400/SUB_DATA_TYPE_INDEX_SNAPSHOT:               L1指数行情 (与L1_SNAPSHOT的区别在于, INDEX_SNAPSHOT可以单独订阅指数行情)
             * - 0x0800/SUB_DATA_TYPE_OPTION_SNAPSHOT               L1期权行情 (与L1_SNAPSHOT的区别在于, OPTION_SNAPSHOT可以单独订阅期权行情)
             * - 0xFFFF/SUB_DATA_TYPE_ALL							所有种类
             */
//            this.setDataTypes(MdsSubscribeDataType.SUB_DATA_TYPE_ALL.value());
            /** 如果组合订阅种类  位或操作  如下: */
            this.dataTypes = MdsSubscribeDataType.SUB_DATA_TYPE_L2_SNAPSHOT.value() or
                    MdsSubscribeDataType.SUB_DATA_TYPE_L2_TRADE.value() or
                    MdsSubscribeDataType.SUB_DATA_TYPE_INDEX_SNAPSHOT.value()
            /**
             * 上证  产品的订阅标志
             * -  0: (Default) 根据订阅列表订阅产品行情
             * -  1: (All) 订阅该市场和证券类型下的所有产品行情 (为兼容之前的版本，也可以赋值为 -1)
             * -  2: (Disable) 禁用该市场下的所有股票/债券/基金行情
             */
            this.sseStockFlag = MdsMktSubscribeFlag.SUB_FLAG_ALL //股票(基金/债券)
            this.sseIndexFlag = MdsMktSubscribeFlag.SUB_FLAG_ALL //指数
            this.sseOptionFlag = MdsMktSubscribeFlag.SUB_FLAG_DISABLE //期权
            /**
             * 深圳 产品的订阅标志
             * -  0: (Default) 根据订阅列表订阅产品行情
             * -  1: (All) 订阅该市场和证券类型下的所有产品行情
             * -  2: (Disable) 禁用该市场下的所有股票/债券/基金行情
             */
            this.szseStockFlag = MdsMktSubscribeFlag.SUB_FLAG_ALL //股票(基金/债券)
            this.szseIndexFlag = MdsMktSubscribeFlag.SUB_FLAG_ALL //指数
            this.szseOptionFlag = MdsMktSubscribeFlag.SUB_FLAG_DISABLE //期权
            /**
             * 在推送实时行情数据之前, 是否需要推送已订阅产品的初始的行情快照
             * -  0: 不需要推送初始的行情快照
             * -  1: 需要推送初始的行情快照, 即确保客户端可以至少收到一幅已订阅产品的快照行情 (如果有的话)
             *
             * @note 从 0.15.9.1 开始, 允许在会话过程中任意时间指定 isRequireInitialMktData标志来订阅初始快照。不过频繁订阅初始行情快照, 会对当前客户端的行情获取速度产生不利影响。应谨慎使用, 避免频繁订阅
             * @note 当订阅模式为 Append/Delete/BatchDelete 时将忽略isRequireInitialMktData、beginTime 这两个订阅参数
             */
            this.isRequireInitialMktData = false
            /**
             * 请求订阅的行情数据的起始时间 (格式为: HHMMSS 或 HHMMSSsss)
             * - -1: 从头开始获取
             * -  0: 从最新位置开始获取实时行情
             * - >0: 从指定的起始时间开始获取 (HHMMSS / HHMMSSsss)
             * - 对于应答数据，若为 0 则表示当前没有比起始时间更加新的行情数据
             *
             * @note 从 0.15.9.1 开始, 允许在会话过程中任意时间指定 beginTime 订阅参数。不过频繁指定起始时间, 会对当前客户端的行情获取速度产生不利影响。应谨慎使用, 避免频繁订阅
             * @note 当订阅模式为 Append/Delete/BatchDelete 时将忽略isRequireInitialMktData、beginTime 这两个订阅参数
             */
            this.beginTime = 0
            /**
             * 本次订阅的产品数量 (订阅列表中的产品数量)
             * - 该字段表示后续报文为subSecurityCnt个订阅产品条目结构体, 通过这样的方式可以实现同时订阅多只产品的行情快照
             * - 每个订阅请求中最多能同时指定 4000 只产品, 可以通过追加订阅的方式订阅更多数量的产品
             * - 订阅产品总数量的限制如下:
             * - 对于沪/深两市的现货产品没有订阅数量限制, 可以订阅任意数量的产品
             * - 对于沪/深两市的期权产品, 限制对每个市场最多允许同时订阅 2000 只期权产品
             *
             * @see MdsMktDataRequestEntry
             */
            this.subSecurityCnt = 0
        }

}