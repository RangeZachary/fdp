package pers.range.fdp.mds;

import com.quant360.api.callback.MdsCallBack;
import com.quant360.api.client.Client.QueryMode;
import com.quant360.api.client.MdsClient;
import com.quant360.api.client.impl.MdsClientImpl;
import com.quant360.api.model.ClientLogonReq;
import com.quant360.api.model.ClientLogonRsp;
import com.quant360.api.model.mds.MdsIndexSnapshotBody;
import com.quant360.api.model.mds.MdsL2BestOrdersSnapshotBody;
import com.quant360.api.model.mds.MdsL2BestOrdersSnapshotIncremental;
import com.quant360.api.model.mds.MdsL2MarketOverview;
import com.quant360.api.model.mds.MdsL2Order;
import com.quant360.api.model.mds.MdsL2StockSnapshotBody;
import com.quant360.api.model.mds.MdsL2StockSnapshotIncremental;
import com.quant360.api.model.mds.MdsL2Trade;
import com.quant360.api.model.mds.MdsMktDataRequestEntry;
import com.quant360.api.model.mds.MdsMktDataRequestReq;
import com.quant360.api.model.mds.MdsMktDataRequestRsp;
import com.quant360.api.model.mds.MdsMktDataSnapshotBase;
import com.quant360.api.model.mds.MdsMktDataSnapshotHead;
import com.quant360.api.model.mds.MdsOptionStaticInfo;
import com.quant360.api.model.mds.MdsPriceLevel;
import com.quant360.api.model.mds.MdsQryMktDataSnapshotReq;
import com.quant360.api.model.mds.MdsQryOptionStaticInfoFilter;
import com.quant360.api.model.mds.MdsQryOptionStaticInfoListFilter;
import com.quant360.api.model.mds.MdsQryOptionStaticInfoListRsp;
import com.quant360.api.model.mds.MdsQryOptionStaticInfoRsp;
import com.quant360.api.model.mds.MdsQrySecurityStatusReq;
import com.quant360.api.model.mds.MdsQrySnapshotListFilter;
import com.quant360.api.model.mds.MdsQrySnapshotListRsp;
import com.quant360.api.model.mds.MdsQryStockStaticInfoFilter;
import com.quant360.api.model.mds.MdsQryStockStaticInfoListFilter;
import com.quant360.api.model.mds.MdsQryStockStaticInfoListRsp;
import com.quant360.api.model.mds.MdsQryStockStaticInfoRsp;
import com.quant360.api.model.mds.MdsQryTrdSessionStatusReq;
import com.quant360.api.model.mds.MdsSecurityStatusMsg;
import com.quant360.api.model.mds.MdsStockSnapshotBody;
import com.quant360.api.model.mds.MdsStockStaticInfo;
import com.quant360.api.model.mds.MdsSwitche;
import com.quant360.api.model.mds.MdsTestRequestReq;
import com.quant360.api.model.mds.MdsTestRequestRsp;
import com.quant360.api.model.mds.MdsTradingSessionStatusMsg;
import com.quant360.api.model.mds.enu.MdsExchangeId;
import com.quant360.api.model.mds.enu.MdsMdLevel;
import com.quant360.api.model.mds.enu.MdsMktSubscribeFlag;
import com.quant360.api.model.mds.enu.MdsSecurityType;
import com.quant360.api.model.mds.enu.MdsSubscribeDataType;
import com.quant360.api.model.mds.enu.MdsSubscribeMode;
import com.quant360.api.model.mds.enu.MdsSubscribedTickExpireType;
import com.quant360.api.model.mds.enu.MdsSubscribedTickRebuildFlag;
import com.quant360.api.model.mds.enu.MdsSubscribedTickType;
import com.quant360.api.model.oes.enu.ErrorCode;
import com.quant360.api.model.oes.enu.OesSecurityType;
import com.quant360.api.utils.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * 
 * <p>Description: OES API MDS行情端调用实现用例</p>
 * 本用例基本实现 
 * 1.客户端连接登录
 * 2.行情订阅
 * 3.接收并打印行情信息
 * 4.查询信息
 * @author: David
 * @date:2017年1月13日
 */
public class MdsExample {
	protected Logger logger = LoggerFactory.getLogger(getClass());
	/** MDS行情客户端*/
	private MdsClient client;
	/** 行情回调函数实现样例*/
	private MdsExamCallBack callBack;
	/** API配置文件相对路径*/
	private static final String CONFIGPATH = "src/main/resources/mds_api_config.json";
	
	/** 客户端登录请求*/
	private ClientLogonReq logonReq;
	/** 客户端登录应答*/
	private ClientLogonRsp logonRsp;
	
	private CountDownLatch mainThreadLatch = new CountDownLatch(1);
	
	public MdsExample(){
		callBack = new MdsExamCallBack();
	}
	
	public void startup() throws Exception{
		// 启动客户端
		startClient();
		
		/**
		 * 样例中为了防止主线程关闭加锁阻塞(实际应用中无需这样操作)
		 */
		mainThreadLatch.await();
		Thread.sleep(10 * 1000);
		
		client.close();
		System.exit(0);
		
	}
	
	/**
	 * 启动客户端
	 */
	public void startClient(){
		logonReq = buildClientLogonReq();
		try {
			// STEP1: 创建Client对象
			client = new MdsClientImpl(CONFIGPATH);
			// STEP2: 初始化客户端回调处理对象
			client.initCallBack(callBack);
			// STEP3: 客户端登录启动
			logonRsp = client.start(logonReq);
		} catch (Exception e) {
			logger.info("MDS客户端: {} 登录失败 , 连接服务器异常！", logonReq.getUsername(), e);
			return;
		}

		if(logonRsp.isSuccess()) { // 客户端启动成功
			logger.info("MDS客户端: {} 已成功登陆！", logonReq.getUsername());
			// STEP4: 订阅行情 (注意: 当超过 MIN(两倍心跳间隔, 90秒) 时仍未订阅行情时服务端则会断开连接)
			subMkt();
			
			// 登录成功后，client其他接口调用演示操作 (为避免和行情信息输出混淆暂时注释掉)
			//otherDemoExec();
		} 
		else { // 客户端启动失败
			ErrorCode code = logonRsp.getErrorCode();
			switch (code) {
			case CLIENT_DISABLE:
				logger.info("MDS客户端: {} 登录失败 ! [客户端已被禁用] errorCode = {}", logonReq.getUsername(), code);
				break;
			case INVALID_CLIENT_NAME:
				logger.info("MDS客户端: {} 登录失败 ！[非法的客户端登陆用户名称 ] errorCode = {}", logonReq.getUsername(), code);
				break;
			case INVALID_CLIENT_PASSWORD:
				logger.info("MDS客户端: {} 登录失败 ！[客户端密码不正确] errorCode = {}", logonReq.getUsername(), code);
				break;
			case CLIENT_REPEATED:
				logger.info("MDS客户端: {} 登录失败 ！[客户端重复登录] errorCode = {}", logonReq.getUsername(), code);
				break;
			case CLIENT_CONNECT_OVERMUCH:
				logger.info("MDS客户端: {} 登录失败 ！[客户端连接数量过多] errorCode = {}", logonReq.getUsername(), code);
				break;
			case INVALID_PROTOCOL_VERSION:
				logger.info("MDS客户端: {} 登录失败 ! [API协议版本不兼容] errorCode = {}", logonReq.getUsername(), code);
				break;
			case OTHER_ERROR:
				logger.info("MDS客户端: {} 登录失败 ！[其他错误] errorCode = {}", logonReq.getUsername(), code);
				break;
			default:
				break;
			}
		}
	}
	
	/**
	 * client接口调用演示样例
	 */
	public void otherDemoExec(){
		try {
			testMktChannel(); 			// 行情通道测试
			testQryChannel(); 			// 查询通道测试
			
			qryMktData(); 				// 查询行情
			qryTrdSessionStatus(); 		// 查询市场状态(上证)
			qrySecurityStatus(); 		// 查询证券实时状态(深证)
			qryStockStaticInfo(); 		// 查询证券静态信息
			qryOptionStaticInfo(); 		// 查询期权静态信息
			qrySnapshotList(); 			// 批量查询行情快照
			qryStockStaticInfoList(); 	// 批量查询现货静态信息列表
			qryOptionStaticInfoList(); 	// 批量查询期权静态信息列表
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/** 构建客户端登录消息*/
	private ClientLogonReq buildClientLogonReq(){
		ClientLogonReq logonReq = new ClientLogonReq();
		logonReq.setHeartBtInt(30); 				// 心跳间隔, 单位为秒 (默认30秒, 有效范围为 [10~300], 若取值小于0则赋值为默认值30秒，否则设置为最大/最小值)
		logonReq.setUsername(Main.USERNAME);		// 账号
		logonReq.setPassword(Main.PASSWORD);		// 密码
		logonReq.setClientDriverId("DAEB7F56"); 	// 硬盘序列号 (默认取API 配置文件中 "clDriverId"属性的值)
		return logonReq;
	}
	
	/** 行情订阅 */
	private void subMkt(){
		MdsMktDataRequestReq req = new MdsMktDataRequestReq();
		/**
	     * 订阅模式
	     * - 0: (Set) 重新订阅，设置为订阅列表中的股票
	     * - 1: (Append) 追加订阅，增加订阅列表中的股票
	     * - 2: (Delete) 删除订阅，删除订阅列表中的股票
	     * - 10: (BatchBegin)   批量订阅-开始订阅, 开始一轮新的批量订阅 (之前的订阅参数将被清空,
	     *                      行情推送也将暂停直到批量订阅结束)
	     * - 11: (BatchAppend)  批量订阅-追加订阅, 增加订阅列表中的股票
	     * - 12: (BatchDelete)  批量订阅-删除订阅, 删除订阅列表中的股票
	     * - 13: (BatchEnd)     批量订阅-结束订阅, 结束本轮的批量订阅, 提交和启用本轮的订阅参数
	     */
		req.setSubMode(MdsSubscribeMode.SUB_MODE_SET);
		/**
		 * 数据模式 (TickType) 定义 (仅对快照行情生效, 用于标识订阅最新的行情快照还是所有时点的行情快照)
		 *
		 * 取值说明:
		 * -  0: (LatestSimplified) 只订阅最新的行情快照数据, 并忽略和跳过已经过时的数据
		 *       - 该模式推送的数据量最小, 服务器端会做去重处理, 不会再推送重复的和已经过时的快照数据
		 *       - 优点: 该模式在时延和带宽方面都更加优秀, 该模式优先关注快照行情的时效性, 并避免推送没有实质变化的重复快照
		 *       - 缺点: 当没有行情变化时 (例如没有交易或盘中休市等), 就不会推送任何快照行情了, 这一点可能会带来困扰, 不好确定是发生丢包了还是没有行情导致的
		 *       - 注意: 过时和重复的快照都会被过滤掉
		 * -  1: (LatestTimely) 只订阅最新的行情快照数据, 并立即发送最新数据
		 *       - 只要有行情更新事件, 便立即推送该产品的最新行情, 但行情数据本身可能是重复的, 即只有行情时间有变化, 行情数据本身没有变化
		 *       - 优点: 可以获取到时间点更完整的快照行情, 不会因为行情数据没有变化而跳过 (如果是因为接收慢等原因导致快照已经过时了, 该快照还是会被忽略掉)
		 *       - 缺点: 会收到仅更新时间有变化, 但行情数据本身并没有更新的重复数据, 带宽和数据量上会有一定的浪费
		 *       - 注意: 重复的快照不会被过滤掉, 但过时的快照还是会被过滤掉
		 * -  2: (AllIncrements) 订阅所有时点的行情快照数据 (包括Level2增量更新消息)
		 *       - 该模式会推送所有时点的行情数据, 包括Level2行情快照的增量更新消息
		 *       - 如果需要获取全量的行情明细, 或者需要直接使用Level2的增量更新消息, 可以使用该模式
		 *       - 如果没有特别需求的话, 不需要订阅增量更新消息, 增量消息处理起来比较麻烦
		 *          - 增量更新消息 (字段级别的增量更新) 只有上交所Level-2快照有, 深交所行情里面没有
		 *          - 在对下游系统进行推送时, 增量快照和完整快照在推送时间上是没有区别的, 增量更新并不会比完整快照更快, 只是信息角度不一样
		 *
		 * 补充说明:
		 * - 当以 tickType=0 的模式订阅行情时, 服务器端会对重复的快照行情做去重处理, 不会再推送重复的和已经过时的快照数据。
		 * - 如果需要获取到"所有时点"的快照, 可以使用 tickType=1 的模式订阅行情。此模式下只要行情时间有变化, 即使数据重复也会对下游推送。但过时的快照还是会被忽略掉。
		 * - 只有通过 tickType=2 的模式才能接收到完整的所有时间点的行情数据。
		 * - 快照行情 "过时" 表示: 不是当前最新的快照即为"过时", 即存在时间点比该快照更新的快照 (同一只股票)。
		 * - @note  上交所行情存在更新时间相同但数据不同的Level-2快照。(频率不高, 但会存在这样的数据)
		 */
		req.setTickType(MdsSubscribedTickType.MDS_TICK_TYPE_LATEST_SIMPLIFIED);
		
		/**
	         * 逐笔数据的过期时间类型
	    * -  0: 不过期
	    * -  1: 立即过期 (1秒, 若落后于快照1秒则视为过期)
	    * -  2: 及时过期 (3秒)
	    * -  3: 超时过期 (30秒)
	    *
	    * @see     MdsSubscribedTickExpireType
	    * @note    仅对压缩行情端口生效, 普通的非压缩行情端口不支持该选项
	    * @note    因为存在不可控的网络因素, 所以做不到百分百的精确过滤, 如果对数据的时效性有精确要求, 就需要在前端对数据再进行一次过滤
	    */
		req.setTickExpireType(MdsSubscribedTickExpireType.MDS_TICK_EXPIRE_TYPE_NONE);
	    
	    /**
	     * 逐笔数据的数据重建标识 (标识是否订阅重建到的逐笔数据)
	     * -  MDS_TICK_REBUILD_FLAG_EXCLUDE_REBUILDED 0: 不订阅重建到的逐笔数据 (仅实时行情)
	     * -  MDS_TICK_REBUILD_FLAG_INCLUDE_REBUILDED 1: 订阅重建到的逐笔数据 (实时行情+重建数据)
	     * -  MDS_TICK_REBUILD_FLAG_ONLY_REBUILDED 2: 只订阅重建到的逐笔数据 (仅重建数据 @note 需要通过压缩行情端口进行订阅, 非压缩行情端口不支持该选项)
	     *
	     * @see	MdsSubscribedTickRebuildFlag
	     */
		req.setTickRebuildFlag(MdsSubscribedTickRebuildFlag.MDS_TICK_REBUILD_FLAG_EXCLUDE_REBUILDED);
		
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
//		req.setDataTypes(MdsSubscribeDataType.SUB_DATA_TYPE_ALL.value());
		
		/** 如果组合订阅种类  位或操作  如下:*/
		req.setDataTypes(MdsSubscribeDataType.SUB_DATA_TYPE_L2_SNAPSHOT.value() |
				MdsSubscribeDataType.SUB_DATA_TYPE_L2_TRADE.value()  |
				MdsSubscribeDataType.SUB_DATA_TYPE_INDEX_SNAPSHOT.value() );

		/**
	     * 上证  产品的订阅标志
	     * -  0: (Default) 根据订阅列表订阅产品行情
	     * -  1: (All) 订阅该市场和证券类型下的所有产品行情 (为兼容之前的版本，也可以赋值为 -1)
	     * -  2: (Disable) 禁用该市场下的所有股票/债券/基金行情
	     */
		req.setSseStockFlag(MdsMktSubscribeFlag.SUB_FLAG_ALL); //股票(基金/债券)
		req.setSseIndexFlag(MdsMktSubscribeFlag.SUB_FLAG_ALL); //指数
		req.setSseOptionFlag(MdsMktSubscribeFlag.SUB_FLAG_DISABLE);//期权
		
		/**
	     * 深圳 产品的订阅标志
	     * -  0: (Default) 根据订阅列表订阅产品行情
	     * -  1: (All) 订阅该市场和证券类型下的所有产品行情
	     * -  2: (Disable) 禁用该市场下的所有股票/债券/基金行情
	     */
		req.setSzseStockFlag(MdsMktSubscribeFlag.SUB_FLAG_ALL);  //股票(基金/债券)
		req.setSzseIndexFlag(MdsMktSubscribeFlag.SUB_FLAG_ALL);  //指数
		req.setSzseOptionFlag(MdsMktSubscribeFlag.SUB_FLAG_DISABLE); //期权
		
		/**
         * 在推送实时行情数据之前, 是否需要推送已订阅产品的初始的行情快照
	    * -  0: 不需要推送初始的行情快照
	    * -  1: 需要推送初始的行情快照, 即确保客户端可以至少收到一幅已订阅产品的快照行情 (如果有的话)
	    *
	    * @note 从 0.15.9.1 开始, 允许在会话过程中任意时间指定 isRequireInitialMktData标志来订阅初始快照。不过频繁订阅初始行情快照, 会对当前客户端的行情获取速度产生不利影响。应谨慎使用, 避免频繁订阅
	    * @note 当订阅模式为 Append/Delete/BatchDelete 时将忽略isRequireInitialMktData、beginTime 这两个订阅参数
	    */
		req.setRequireInitialMktData(false);
		
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
		req.setBeginTime(0);
		
		/**
		 * 本次订阅的产品数量 (订阅列表中的产品数量)
	     * - 该字段表示后续报文为subSecurityCnt个订阅产品条目结构体, 通过这样的方式可以实现同时订阅多只产品的行情快照
	     * - 每个订阅请求中最多能同时指定 4000 只产品, 可以通过追加订阅的方式订阅更多数量的产品
	     * - 订阅产品总数量的限制如下:
	     *   - 对于沪/深两市的现货产品没有订阅数量限制, 可以订阅任意数量的产品
	     *   - 对于沪/深两市的期权产品, 限制对每个市场最多允许同时订阅 2000 只期权产品
	     *   
		 * @see MdsMktDataRequestEntry
		 */
		req.setSubSecurityCnt(0);
		//req.setSubSecurityCnt(2);
		
		/**
		 * 添加产品列表, 当"产品的订阅标志"为 default标志时起作用
		 * 每条产品信息要保证正确(市场、类别、产品代码)，否则无法成功订阅上该产品的行情
		 * 
		 */
		List<MdsMktDataRequestEntry> entries = null;
//		entries = new ArrayList<MdsMktDataRequestEntry>();

//		MdsMktDataRequestEntry entry0 = new MdsMktDataRequestEntry();
//		entry0.setExchId(MdsExchangeId.MDS_EXCH_SSE);
//		entry0.setSecurityType(MdsSecurityType.MDS_SECURITY_TYPE_STOCK);
//		entry0.setInstrId(Integer.parseInt("600000"));
//		entries.add(entry0);
		
//		MdsMktDataRequestEntry entry1 = new MdsMktDataRequestEntry();
//		entry1.setExchId(MdsExchangeId.MDS_EXCH_SZSE);
//		entry1.setSecurityType(MdsSecurityType.MDS_SECURITY_TYPE_STOCK);
//		entry1.setInstrId(Integer.parseInt("000002"));
//		entries.add(entry1);
		
		try {
			client.subscribeMarketData(req, entries);
		} catch (Exception e) {
			logger.error("行情订阅失败!");
		}
	}
	
	/** 测试行情通道*/
	private void testMktChannel(){
		try {
			MdsTestRequestReq req = new MdsTestRequestReq();
			req.setTestReqId("test001");
			req.setSendTime(TimeUtils.timeToyyyyMMddHHmmssSSS());
			
			client.testMktChannel(req);
		} catch (Exception e) {
			logger.error("提交 行情通道测试请求失败 !", e);
		}
	}
	
	/** 测试查询通道*/
	private void testQryChannel(){
		try {
			MdsTestRequestReq req = new MdsTestRequestReq();
			req.setTestReqId("test002");
			req.setSendTime(TimeUtils.timeToyyyyMMddHHmmssSSS());
			MdsTestRequestRsp testRsp = client.testQryChannel(req);
			
			testQryChannelHandle(testRsp);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/** 查询证券行情信息*/
	private void qryMktData(){
		MdsQryMktDataSnapshotReq req = new MdsQryMktDataSnapshotReq();

		/** 设置交易所  1:上海   2:  深圳*/
		req.setExchId(MdsExchangeId.MDS_EXCH_SSE);
		/** 设置证券类型 1:股票(基金/债券); 2:指数; 3:期权(衍生品)*/
		req.setSecurityType(MdsSecurityType.MDS_SECURITY_TYPE_STOCK);
		/** 设置证券代码*/
		req.setInstrId(600000);
		
		try {
			qryMktDataHandle(client.qryMktDataSnapshot(req));
		} catch (Exception e) {
			logger.error("查询行情失败!");
		}
		
	}
	
	/** 查询市场状态(上证)*/
	private void qryTrdSessionStatus(){
		MdsQryTrdSessionStatusReq req = new MdsQryTrdSessionStatusReq();

		/** 设置交易所  1:上海   2:  深圳*/
		req.setExchId(MdsExchangeId.MDS_EXCH_SSE);
		/** 设置证券类型 1:股票(基金/债券); 2:指数; 3:期权(衍生品)*/
		req.setSecurityType(MdsSecurityType.MDS_SECURITY_TYPE_STOCK);
		
		try {
			qryTrdSessionStatusHandle(client.qryTrdSessionStatus(req));
		} catch (Exception e) {
			logger.error("查询市场状态失败!");
		}
	}
	
	/** 查询证券实时状态(深证)*/
	private void qrySecurityStatus(){
		MdsQrySecurityStatusReq req = new MdsQrySecurityStatusReq();

		/** 设置交易所  1:上海   2:  深圳*/
		req.setExchId(MdsExchangeId.MDS_EXCH_SZSE);
		/** 设置证券类型 1:股票(基金/债券); 2:指数; 3:期权(衍生品)*/
		req.setSecurityType(MdsSecurityType.MDS_SECURITY_TYPE_STOCK);
		/** 设置证券代码*/
		req.setInstrId(Integer.parseInt("000002"));	
		
		try {
			qrySecurityStatusHandle(client.qrySecurityStatus(req));
		} catch (Exception e) {
			logger.error("查询证券实时状态失败!");
		}
	}
	
	/** 查询证券静态信息*/
	private void qryStockStaticInfo(){
		MdsQryStockStaticInfoFilter qryFilter = new MdsQryStockStaticInfoFilter();
		
		
		/*
		 * 股票产品信息较多，用例中加了查询条件: 市场-上证A; 证券类型-股票
		 * 查询条件为可选项，如果不加查询条件则默认查询所有产品
		 */
		qryFilter.setExchId(MdsExchangeId.MDS_EXCH_SSE);
		qryFilter.setSecurityType(OesSecurityType.OES_SECURITY_TYPE_STOCK);
		try {
			/**
			 * 查询模式(QueryMode): ALL; PAGE
			 * ALL: API会代替客户进行分页查询操作，直到收集到全部查询结果，最后统一返回
			 * PAGE: 客户需要自己对分页查询进行处理
			 * 
			 * 以下为QueryMode.ALL 查询全部的参考实现:
			 */
			MdsQryStockStaticInfoRsp rsp = client.qryStockStaticInfo(qryFilter, QueryMode.ALL);
			if(rsp != null) {
				qryStockStaticInfoHandle(rsp.getQryItems());
			}
		} catch (Exception e) {			
			logger.error("查询证券静态信息失败 !", e);
		}
	}
	
	/** 查询期权静态信息*/
	private void qryOptionStaticInfo(){
		MdsQryOptionStaticInfoFilter qryFilter = new MdsQryOptionStaticInfoFilter();
		
		
		/*
		 * 产品信息较多，用例中加了查询条件: 市场-上证;
		 * 查询条件为可选项，如果不加查询条件则默认查询所有产品
		 */
		qryFilter.setExchId(MdsExchangeId.MDS_EXCH_SSE);
		try {
			/**
			 * 查询模式(QueryMode): ALL; PAGE
			 * ALL: API会代替客户进行分页查询操作，直到收集到全部查询结果，最后统一返回
			 * PAGE: 客户需要自己对分页查询进行处理
			 * 
			 * 以下为QueryMode.ALL 查询全部的参考实现:
			 */
			MdsQryOptionStaticInfoRsp rsp = client.qryOptionStaticInfo(qryFilter, QueryMode.ALL);
			if(rsp != null) {
				qryOptionStaticInfoHandle(rsp.getQryItems());
			}
		} catch (Exception e) {			
			logger.error("查询期权静态信息失败 !", e);
		}
	}
	
	/** 批量查询行情快照*/
	private void qrySnapshotList(){
		MdsQrySnapshotListFilter qryFilter = new MdsQrySnapshotListFilter();
		
		/*
		 * 行情信息较多，用例中加了查询条件: 市场-上证A; 行情数据类型-股票(基金/债券)
		 * 查询条件为可选项，如果不加查询条件则默认查询所有产品
		 */
		qryFilter.setExchId(MdsExchangeId.MDS_EXCH_SSE);
		qryFilter.setMdProductType(MdsSecurityType.MDS_SECURITY_TYPE_STOCK);
		qryFilter.setMdLevel(MdsMdLevel.MDS_MD_LEVEL_1);
		
		/*
		  *  注: 
		 *  1. 如果查询指定证券产品行情需要设置证券代码列表
		 */
//		qryFilter.setSecurityCodeCnt(2);
//		List<MdsQrySecurityCodeEntry> entrys = new ArrayList<MdsQrySecurityCodeEntry>();
//		
//		MdsQrySecurityCodeEntry entry1 = new MdsQrySecurityCodeEntry();
//		entry1.setInstrId(Integer.parseInt("399001"));
//		entry1.setExchId(MdsExchangeId.MDS_EXCH_SZSE);
//		entry1.setMdProductType(MdsSecurityType.MDS_SECURITY_TYPE_INDEX);
//		entrys.add(entry1);
//		
//		MdsQrySecurityCodeEntry entry2 = new MdsQrySecurityCodeEntry();
//		entry2.setInstrId(Integer.parseInt("000002"));
//		entry2.setExchId(MdsExchangeId.MDS_EXCH_SZSE);
//		entry2.setMdProductType(MdsSecurityType.MDS_SECURITY_TYPE_STOCK);
//		entrys.add(entry2);
		
//		qryFilter.setSecurityCodeList(entrys);
		
		try {
			/**
			 * 查询模式(QueryMode): ALL; PAGE
			 * ALL: API会代替客户进行分页查询操作，直到收集到全部查询结果，最后统一返回
			 * PAGE: 客户需要自己对分页查询进行处理
			 * 
			 * 以下为QueryMode.ALL 查询全部的参考实现:
			 */
			MdsQrySnapshotListRsp rsp = client.qrySnapshotList(qryFilter, QueryMode.ALL);
			if(rsp != null) {
				qrySnapshotListHandle(rsp.getQryItems());
			}
		} catch (Exception e) {
			logger.error("查询批量快照信息失败 !", e);
		}
	}
	
	/** 批量查询证券静态信息列表*/
	private void qryStockStaticInfoList(){
		MdsQryStockStaticInfoListFilter qryFilter = new MdsQryStockStaticInfoListFilter();
		
		/*
		 * 产品信息较多，用例中加了查询条件: 市场-上证;
		 * 查询条件为可选项，如果不加查询条件则默认查询所有产品
		 */
		qryFilter.setExchId(MdsExchangeId.MDS_EXCH_SSE);
//		qryFilter.setSecurityType(OesSecurityType.OES_SECURITY_TYPE_STOCK);
//		qryFilter.setSubSecurityType(OesSubSecurityType.OES_SUB_SECURITY_TYPE_STOCK_ASH);
		
		/*
		  *  注: 
		 *  1. 如果查询指定证券产品信息需要设置证券代码列表
		 */
//		qryFilter.setSecurityCodeCnt(2);
//		
//		List<MdsQrySecurityCodeEntry> entrys = new ArrayList<MdsQrySecurityCodeEntry>();
//		
//		MdsQrySecurityCodeEntry entry1 = new MdsQrySecurityCodeEntry();
//		entry1.setInstrId(Integer.parseInt("399001"));
//		entry1.setExchId(MdsExchangeId.MDS_EXCH_SZSE);
//		entry1.setMdProductType(MdsSecurityType.MDS_SECURITY_TYPE_INDEX);
//		entrys.add(entry1);
//		
//		MdsQrySecurityCodeEntry entry2 = new MdsQrySecurityCodeEntry();
//		entry2.setInstrId(Integer.parseInt("000002"));
//		entry2.setExchId(MdsExchangeId.MDS_EXCH_SZSE);
//		entry2.setMdProductType(MdsSecurityType.MDS_SECURITY_TYPE_STOCK);
//		entrys.add(entry2);
		
//		qryFilter.setSecurityCodeList(entrys);
		
		try {
			/**
			 * 查询模式(QueryMode): ALL; PAGE
			 * ALL: API会代替客户进行分页查询操作，直到收集到全部查询结果，最后统一返回
			 * PAGE: 客户需要自己对分页查询进行处理
			 * 
			 * 以下为QueryMode.ALL 查询全部的参考实现:
			 */
			MdsQryStockStaticInfoListRsp rsp = client.qryStockStaticInfoList(qryFilter, QueryMode.ALL);
			if(rsp != null) {
				qryStockStaticInfoHandle(rsp.getQryItems());
			}
		} catch (Exception e) {
			logger.error("查询证券静态信息列表失败 !", e);
		}
	}
	
	/** 批量查询期权静态信息列表*/
	private void qryOptionStaticInfoList(){
		MdsQryOptionStaticInfoListFilter qryFilter = new MdsQryOptionStaticInfoListFilter();
		
		/*
		 * 产品信息较多，用例中加了查询条件: 市场-上证;
		 * 查询条件为可选项，如果不加查询条件则默认查询所有产品
		 */
		qryFilter.setExchId(MdsExchangeId.MDS_EXCH_SZSE);
//		qryFilter.setSecurityType(OesSecurityType.OES_SECURITY_TYPE_OPTION);
		
		/*
		  *  注: 
		 *  1. 如果查询指定证券产品信息需要设置证券代码列表
		 */
//		qryFilter.setSecurityCodeCnt(2);
//		
//		List<MdsQrySecurityCodeEntry> entrys = new ArrayList<MdsQrySecurityCodeEntry>();
//		MdsQrySecurityCodeEntry entry1 = new MdsQrySecurityCodeEntry();
//		entry1.setInstrId(Integer.parseInt("10002200"));
//		entry1.setExchId(MdsExchangeId.MDS_EXCH_SSE);
//		entrys.add(entry1);
//		
//		MdsQrySecurityCodeEntry entry2 = new MdsQrySecurityCodeEntry();
//		entry2.setInstrId(Integer.parseInt("90000090"));
//		entry2.setExchId(MdsExchangeId.MDS_EXCH_SZSE);
//		entrys.add(entry2);
//		
//		qryFilter.setSecurityCodeList(entrys);
		
		try {
			/**
			 * 查询模式(QueryMode): ALL; PAGE
			 * ALL: API会代替客户进行分页查询操作，直到收集到全部查询结果，最后统一返回
			 * PAGE: 客户需要自己对分页查询进行处理
			 * 
			 * 以下为QueryMode.ALL 查询全部的参考实现:
			 */
			MdsQryOptionStaticInfoListRsp rsp = client.qryOptionStaticInfoList(qryFilter, QueryMode.ALL);
			if(rsp != null) {
				qryOptionStaticInfoHandle(rsp.getQryItems());
			}
		} catch (Exception e) {
			logger.error("查询期权静态信息列表失败 !", e);
		}
	}
	
	/**
	 * Level1 市场行情查询回调
	 * stock  股票、债券、基金行情数据
	 * index  Level1/Level2 指数行情
	 * option Level1/Level2 期权行情
	 * @param data 行情全幅消息
	 */
	private void qryMktDataHandle( MdsMktDataSnapshotBase data){
		logger.info("##########################  查询证券行情返回信息  ##########################");
		
		if(data == null){
			logger.info("查询数据为空，数据不存在!");
			return;
		}
		MdsSecurityType serType = data.getHead().getSecurityType();
		switch (serType) {
		case MDS_SECURITY_TYPE_STOCK:
			qryStockHandle(data.getHead(), data.getStock());
			break;
		case MDS_SECURITY_TYPE_INDEX:
			qryIndexHandle(data.getHead(), data.getIndex());
			break;
		case MDS_SECURITY_TYPE_OPTION:
			qryOptionHandle(data.getHead(), data.getOption());
			break;
		default:
			break;
		}
	}
	
	/**
	 * Level1 市场行情查询回调 (股票、债券、基金行情数据)
	 * @param head 行情全幅消息的消息头
	 * @param data 股票(A、B股)、债券 、基金的行情全幅消息
	 */
	private void qryStockHandle(MdsMktDataSnapshotHead head, MdsStockSnapshotBody data){
		logger.info("##########################  查询现货行情返回信息  ##########################");
		logger.info("产品代码:{}, 市场 :{}, 证券类型:{}, 交易日期:{}, 更新时间:{}, "
				+ "昨收盘价:{}, 今开盘价:{}, 最高价:{}, 最低价:{}, 最新成交价:{}, 成交量:{}, 成交额:{}, 今收盘价:{}", 
				data.getSecurityID(), head.getExchId(), head.getSecurityType(), head.getTradeDate(), head.getUpdateTime(),
				data.getPrevClosePx(), data.getOpenPx(), data.getHighPx(), data.getLowPx(), data.getTradePx(), 
				data.getTotalVolumeTraded(), data.getTotalValueTraded(), data.getClosePx());	
		logger.info("-------------------------  五档行情  ----------------------------");
		/** 设置5档行情 */
		List<MdsPriceLevel> mdsPriceLevels = data.getBidLevels();
		logger.info("********************* 五档买盘价位信息 **********************");
		for (MdsPriceLevel mdsPriceLevel : mdsPriceLevels) {				
			logger.info("委托价:{}, 委托笔数:{}, 委托数量:{}", 
					mdsPriceLevel.getPrice(), mdsPriceLevel.getNumberOfOrders(), mdsPriceLevel.getOrderQty());
		}
		mdsPriceLevels = data.getOfferLevels();
		logger.info("********************* 五档卖盘价位信息 **********************");
		for (MdsPriceLevel mdsPriceLevel : mdsPriceLevels) {
			logger.info("委托价:{}, 委托笔数:{}, 委托数量:{}", 
					mdsPriceLevel.getPrice(), mdsPriceLevel.getNumberOfOrders(), mdsPriceLevel.getOrderQty());
		}	
		
	}
	
	/**
	 * Level1/Level2 指数行情查询回调
	 * @param head 行情全幅消息的消息头
	 * @param data 指数的行情全幅消息
	 */
	private void qryIndexHandle(MdsMktDataSnapshotHead head, MdsIndexSnapshotBody data){
		logger.info("##########################  查询指数行情返回信息  ##########################");
		logger.info("产品代码:{}, 市场 :{}, 证券类型:{}, 交易日期:{}, 更新时间:{}, "
				+ "昨收盘指数:{}, 今开盘指数:{}, 最高指数:{}, 最低指数:{}, 最新指数:{}, 今收盘指数:{}", 
				data.getSecurityID(), head.getExchId(), head.getSecurityType(), head.getTradeDate(), head.getUpdateTime(),
				data.getPrevCloseIdx(), data.getOpenIdx(), data.getHighIdx(), data.getLowIdx(), data.getLastIdx(), data.getCloseIdx());
		
	}
	
	/**
	 * Level1/Level2 期权行情查询回调 
	 * @param head 行情全幅消息的消息头
	 * @param data 期权的行情全幅消息
	 */
	private void qryOptionHandle(MdsMktDataSnapshotHead head, MdsStockSnapshotBody data){
		logger.info("##########################  查询期权行情返回信息  ##########################");
		logger.info("产品代码:{}, 市场 :{}, 证券类型:{}, 交易日期:{}, 更新时间:{}, "
				+ "昨收盘价:{}, 今开盘价:{}, 最高价:{}, 最低价:{}, 最新成交价:{}, 成交量:{}, 成交额:{}, 今收盘价:{}", 
				data.getSecurityID(), head.getExchId(), head.getSecurityType(), head.getTradeDate(), head.getUpdateTime(),
				data.getPrevClosePx(), data.getOpenPx(), data.getHighPx(), data.getLowPx(), data.getTradePx(), 
				data.getTotalVolumeTraded(), data.getTotalValueTraded(), data.getClosePx());	
		logger.info("-------------------------  五档行情  ----------------------------");
		/** 设置5档行情 */
		List<MdsPriceLevel> mdsPriceLevels = data.getBidLevels();
		logger.info("********************* 五档买盘价位信息 **********************");
		for (MdsPriceLevel mdsPriceLevel : mdsPriceLevels) {				
			logger.info("委托价:{}, 委托笔数:{}, 委托数量:{}", 
					mdsPriceLevel.getPrice(), mdsPriceLevel.getNumberOfOrders(), mdsPriceLevel.getOrderQty());
		}
		mdsPriceLevels = data.getOfferLevels();
		logger.info("********************* 五档卖盘价位信息 **********************");
		for (MdsPriceLevel mdsPriceLevel : mdsPriceLevels) {
			logger.info("委托价:{}, 委托笔数:{}, 委托数量:{}", 
					mdsPriceLevel.getPrice(), mdsPriceLevel.getNumberOfOrders(), mdsPriceLevel.getOrderQty());
		}
		
	}
	
	/**
	 * 证券实时状态定义 (仅适用于深圳市场, 上海市场没有该行情)
	 * @param data 证券状态信息
	 */
	private void qrySecurityStatusHandle(MdsSecurityStatusMsg data){
		logger.info("##########################  查询证券实时状态(深证)返回信息  ##########################");
		if(data == null){
			logger.info("查询数据为空，数据不存在!");
			return;
		}
		logger.info("交易所代码:{}, 证券类型:{}, 交易日期:{}, 更新时间:{}, "
				+ "产品代码:{}, 产品代码 C6/C8:{}, 证券状态:{}", 
				data.getExchId(), data.getSecurityType(), data.getTradeDate(), data.getUpdateTime(),
				data.getInstrId(), data.getSecurityID(), data.getFinancialStatus());	
		logger.info("-------------------------  证券业务开关列表  ----------------------------");
		/** 设置开关列表 */
		List<MdsSwitche> switches = data.getSwitches();
		for (int i = 0; i<switches.size(); i++) {				
			logger.info("业务项:{}, 使能标志:{}, 开关状态:{}", 
					i, switches.get(i).isSwitchFlag(), switches.get(i).isSwitchStatus());
		}
	}

	/**
	 * 市场状态消息 (仅适用于上海市场, 深圳市场没有该行情)
	 * @param data 证券状态信息
	 */
	private void qryTrdSessionStatusHandle(MdsTradingSessionStatusMsg data){
		logger.info("##########################  查询市场状态(上证)返回信息  ##########################");
		if(data == null){
			logger.info("查询数据为空，数据不存在!");
			return;
		}
		logger.info("交易所代码:{}, 证券类型:{}, 交易日期:{}, 更新时间:{}, "
				+ "最大产品数目:{}, 全市场行情状态:{}", 
				data.getExchId(), data.getSecurityType(), data.getTradeDate(), data.getUpdateTime(),
				data.getTotNoRelatedSym(), data.getTradingSessionID());	
	}	
	
	/**
	 * 证券静态信息消息 
	 * @param items 证券静态信息
	 */
	private void qryStockStaticInfoHandle(List<MdsStockStaticInfo> items){
		logger.info("##########################  证券静态信息查询记录  Size {}  ##########################", items.size());
		for(MdsStockStaticInfo item : items){
			logger.info("产品代码:{}, 产品名称 :{}, 市场代码 :{}, 证券类型:{}, 证券子类型:{},"
					+ "昨日收盘价:{}, 市值:{}", 
					item.getSecurityId(), item.getSecurityName(), item.getExchId(), item.getSecurityType(),
					item.getSubSecurityType(), item.getPrevClose(), item.getParValue());	
		}
	}	
	
	/**
	 * 期权静态信息消息 
	 * @param items 期权静态信息
	 */
	private void qryOptionStaticInfoHandle(List<MdsOptionStaticInfo> items){
		logger.info("##########################  期权静态信息查询记录  Size {}  ##########################", items.size());
		for(MdsOptionStaticInfo item : items){
			logger.info("产品代码:{}, 产品名称 :{}, 市场代码 :{}, 合约类型:{}, 证券类型:{}, 证券子类型:{}, 标的证券代码:{},  合约前收盘价:{}, 合约前结算价:{}",
					item.getSecurityId(), item.getSecurityName(), item.getExchId(), item.getContractType(), item.getSecurityType(), item.getSubSecurityType(),
					item.getUnderlyingSecurityId(), item.getPrevClosePrice(), item.getPrevSettlPrice());
		}
	}
	
	/**
	 * 行情快照信息消息 
	 * @param items 行情快照信息
	 */
	private void qrySnapshotListHandle(List<MdsMktDataSnapshotBase> items){
		logger.info("##########################  行情快照信息查询记录  Size {} ##########################", items.size());
		
		for(MdsMktDataSnapshotBase item : items){
			qryMktDataHandle(item);
		}
	}
	
	/**
	 * 查询通道测试
	 * @param rsp 测试应答消息
	 */
	private void testQryChannelHandle(MdsTestRequestRsp rsp){
		logger.info("##########################  查询通道测试应答信息  ##########################");
		logger.info("测试请求标识符:{}, 请求发送时间:{}, 应答发送时间:{}, 消息实际接收时间:{}, 处理完成时间:{}, 推送时间:{}", 
				rsp.getTestReqId(), rsp.getOrigSendTime(), rsp.getRespTime(), 
				rsp.getLatencyFields().getRecvTime().toString(),
				rsp.getLatencyFields().getCollectedTime().toString(),
				rsp.getLatencyFields().getPushingTime().toString());
	}	
	
	//回调实现
	class MdsExamCallBack extends MdsCallBack{
		@Override
		public void onDisConn(MdsClient client) {
			/**
			 * TODO: 连接中断后，由另外的线程发起重连操作，不要在回调函数中直接操作，这样会占用API的回调处理线程
			 */
			logger.error("行情连接已中断！");
		}
		
		/**
		 * 行情订阅回调
		 */
		@Override
		public void onMktReq(MdsMktDataRequestRsp rsp) {
			logger.info("行情订阅成功! {}", rsp);
		}
		
		/**
		 * Level1 市场行情行情回调 (股票、债券、基金行情数据)
		 * @param head 行情全幅消息的消息头
		 * @param data 股票(A、B股)、债券 、基金的行情全幅消息
		 */
		@Override
		public void onMktStock(MdsMktDataSnapshotHead head, MdsStockSnapshotBody data) {
			/**
			 * TODO: 回调处理注意事项(以下回调皆同)：
			 * 		 1.在回调函数中获取行情回报后，将回报消息封装后放到类似RingBuffer的队列中，由另外的线程做后续处理。
			 *       2.不要在回调函数中做复杂耗时的操作，这样会占用API的回调处理线程。
			 * 		 3.行情消息信息量很大，要及时释放掉对消息对象的引用，方便JVM进行GC防止堆内存占用过大。
			 */
			
			logger.info("##########################  现货行情信息  ##########################");
			logger.info("产品代码:{}, 市场 :{}, 证券类型:{}, 交易日期:{}, 更新时间:{}, "
					+ "昨收盘价:{}, 今开盘价:{}, 最高价:{}, 最低价:{}, 最新成交价:{}, 成交量:{}, 成交额:{}, 今收盘价:{}", 
					data.getSecurityID(), head.getExchId(), head.getSecurityType(), head.getTradeDate(), head.getUpdateTime(),
					data.getPrevClosePx(), data.getOpenPx(), data.getHighPx(), data.getLowPx(), data.getTradePx(), 
					data.getTotalVolumeTraded(), data.getTotalValueTraded(), data.getClosePx());	
			logger.info("-------------------------  五档行情  ----------------------------");
			/** 设置5档行情 */
			List<MdsPriceLevel> mdsPriceLevels = data.getBidLevels();
			logger.info("********************* 五档买盘价位信息 **********************");
			for (MdsPriceLevel mdsPriceLevel : mdsPriceLevels) {				
				logger.info("委托价:{}, 委托笔数:{}, 委托数量:{}", 
						mdsPriceLevel.getPrice(), mdsPriceLevel.getNumberOfOrders(), mdsPriceLevel.getOrderQty());
			}
			mdsPriceLevels = data.getOfferLevels();
			logger.info("********************* 五档卖盘价位信息 **********************");
			for (MdsPriceLevel mdsPriceLevel : mdsPriceLevels) {
				logger.info("委托价:{}, 委托笔数:{}, 委托数量:{}", 
						mdsPriceLevel.getPrice(), mdsPriceLevel.getNumberOfOrders(), mdsPriceLevel.getOrderQty());
			}
		}
		
		/**
		 * 指数行情回调
		 * @param head 证券行情全幅消息的消息头
		 * @param data 指数的行情全幅消息
		 */
		@Override
		public void onMktIndex(MdsMktDataSnapshotHead head, MdsIndexSnapshotBody data) {
			logger.info("##########################  指数行情信息  ##########################");
			logger.info("产品代码:{}, 市场 :{}, 证券类型:{}, 交易日期:{}, 更新时间:{}, "
					+ "昨收盘指数:{}, 今开盘指数:{}, 最高指数:{}, 最低指数:{}, 最新指数:{}, 今收盘指数:{}", 
					data.getSecurityID(), head.getExchId(), head.getSecurityType(), head.getTradeDate(), head.getUpdateTime(),
					data.getPrevCloseIdx(), data.getOpenIdx(), data.getHighIdx(), data.getLowIdx(), data.getLastIdx(), data.getCloseIdx());	
			
		}

		/**
		 * Level1/Level2 期权行情回调 
		 * @param head 行情全幅消息的消息头
		 * @param data 期权的行情全幅消息
		 */
		@Override
		public void onMktOption(MdsMktDataSnapshotHead head, MdsStockSnapshotBody data) {			
			logger.info("##########################  期权行情信息  ##########################");
			logger.info("产品代码:{}, 市场 :{}, 证券类型:{}, 交易日期:{}, 更新时间:{}, "
					+ "昨收盘价:{}, 今开盘价:{}, 最高价:{}, 最低价:{}, 最新成交价:{}, 成交量:{}, 成交额:{}, 今收盘价:{}", 
					data.getSecurityID(), head.getExchId(), head.getSecurityType(), head.getTradeDate(), head.getUpdateTime(),
					data.getPrevClosePx(), data.getOpenPx(), data.getHighPx(), data.getLowPx(), data.getTradePx(), 
					data.getTotalVolumeTraded(), data.getTotalValueTraded(), data.getClosePx());	
			logger.info("-------------------------  五档行情  ----------------------------");
			/** 设置5档行情 */
			List<MdsPriceLevel> mdsPriceLevels = data.getBidLevels();
			logger.info("********************* 五档买盘价位信息 **********************");
			for (MdsPriceLevel mdsPriceLevel : mdsPriceLevels) {				
				logger.info("委托价:{}, 委托笔数:{}, 委托数量:{}", 
						mdsPriceLevel.getPrice(), mdsPriceLevel.getNumberOfOrders(), mdsPriceLevel.getOrderQty());
			}
			mdsPriceLevels = data.getOfferLevels();
			logger.info("********************* 五档卖盘价位信息 **********************");
			for (MdsPriceLevel mdsPriceLevel : mdsPriceLevels) {
				logger.info("委托价:{}, 委托笔数:{}, 委托数量:{}", 
						mdsPriceLevel.getPrice(), mdsPriceLevel.getNumberOfOrders(), mdsPriceLevel.getOrderQty());
			}
		}
		
		/**
		 * Level2 快照行情(股票、债券、基金)回调
		 * @param head 行情的消息头
		 * @param data Level2 快照行情
		 */
		@Override
		public void onMktL2StockSnapshot(MdsMktDataSnapshotHead head, MdsL2StockSnapshotBody data){
			logger.info("##########################  Level2 快照行情信息(股票、债券、基金)  ##########################");
			logger.info("产品代码:{}, 市场 :{}, 证券类型:{}, 交易日期:{}, 更新时间:{}, "
					+ "昨收盘价:{}, 今开盘价:{}, 最高价:{}, 最低价:{}, 最新成交价:{}, 成交量:{}, 成交额:{}, 今收盘价:{}", 
					data.getSecurityID(), head.getExchId(), head.getSecurityType(), head.getTradeDate(), head.getUpdateTime(),
					data.getPrevClosePx(), data.getOpenPx(), data.getHighPx(), data.getLowPx(), data.getTradePx(), 
					data.getTotalVolumeTraded(), data.getTotalValueTraded(), data.getClosePx());
			logger.info("[Level2 [ 委托买入总量 :{}, 委托卖出总量:{}, 加权平均委买价格:{}, 加权平均委卖价格:{}, 买方委托价位数:{}, "
					+ "卖方委托价位数:{}", 
					data.getTotalBidQty(), data.getTotalOfferQty(), data.getWeightedAvgBidPx(), data.getWeightedAvgOfferPx(),
					data.getBidPriceLevel(), data.getOfferPriceLevel());
			logger.info("-------------------------  十档行情  ----------------------------");
			/** 设置10档行情 */
			List<MdsPriceLevel> mdsPriceLevels = data.getBidLevels();
			logger.info("********************* 十档买盘价位信息 **********************");
			for (MdsPriceLevel mdsPriceLevel : mdsPriceLevels) {				
				logger.info("委托价:{}, 委托笔数:{}, 委托数量:{}", 
						mdsPriceLevel.getPrice(), mdsPriceLevel.getNumberOfOrders(), mdsPriceLevel.getOrderQty());
			}
			mdsPriceLevels = data.getOfferLevels();
			logger.info("********************* 十档卖盘价位信息 **********************");
			for (MdsPriceLevel mdsPriceLevel : mdsPriceLevels) {
				logger.info("委托价:{}, 委托笔数:{}, 委托数量:{}", 
						mdsPriceLevel.getPrice(), mdsPriceLevel.getNumberOfOrders(), mdsPriceLevel.getOrderQty());
			}
		}
		
		/**
		 * Level2 快照行情的增量更新消息回调(仅上证)
		 * @param head 行情的消息头
		 * @param data 快照行情消息
		 */
		@Override
		public void onMktL2StockSnapshotIncremental(MdsMktDataSnapshotHead head, MdsL2StockSnapshotIncremental data){
			logger.info("##########################  Level2 快照行情的增量更新消息  ##########################");
			logger.info("产品代码:{}, 市场 :{}, 证券类型:{}, 交易日期:{}, 更新时间:{}, ",
					head.getInstrId(), head.getExchId(), head.getSecurityType(), head.getTradeDate(), head.getUpdateTime());
			logger.info("成交笔数:{}, 成交总量 :{}, 成交总金额:{}, 今开盘价:{}, 成交价:{}, "
					+ "今收盘价/期权收盘价:{}, ETF申购/赎回的单位参考净值 :{}, 委托买入总量:{}, 委托卖出总量:{}, 加权平均委买价格:{}, "
					+ "加权平均委卖价格:{}, 买方委托价位数:{}, 卖方委托价位数:{}, 最优申买价:{}, 买盘价位数量:{}, 最优申卖价:{}, 卖盘价位数量:{}", 
					data.getNumTrades(), data.getTotalVolumeTraded(), data.getTotalValueTraded(), data.getOpenPx(), data.getTradePx(),
					data.getClosePx(), data.getOpenPx(), data.getIopv(), data.getTotalBidQty(), data.getTotalOfferQty(), data.getWeightedAvgBidPx(),
					data.getWeightedAvgOfferPx(), data.getBidPriceLevel(), data.getOfferPriceLevel(), data.getBestBidPrice(), data.getNoBidLevel(), 
					data.getBestOfferPrice(), data.getNoOfferLevel());
			
			logger.info("-------------------------  发生变更的价位列表  ----------------------------");
			
			List<MdsPriceLevel> mdsPriceLevels = data.getPriceLevels();
			logger.info("********************* 价位信息 **********************");
			for (MdsPriceLevel mdsPriceLevel : mdsPriceLevels) {				
				logger.info("委托价:{}, 委托笔数:{}, 委托数量:{}", 
						mdsPriceLevel.getPrice(), mdsPriceLevel.getNumberOfOrders(), mdsPriceLevel.getOrderQty());
			}
			
		}
		
		/**
		 * Level2 买一／卖一前五十笔委托明细回调
		 * @param head 行情的消息头
		 * @param data 行情消息体
		 */
		@Override
		public void onMktL2BestOrdersSnapshot(MdsMktDataSnapshotHead head, MdsL2BestOrdersSnapshotBody data){
			logger.info("##########################  Level2 买一／卖一前五十笔委托明细  ##########################");
			logger.info("产品代码:{}, 市场 :{}, 证券类型:{}, 交易日期:{}, 更新时间:{}, ",
					head.getInstrId(), head.getExchId(), head.getSecurityType(), head.getTradeDate(), head.getUpdateTime());
			logger.info("买一价位的揭示委托笔数:{}, 卖一价位的揭示委托笔数 :{}, 成交总量:{}, 最优申买价:{}, 最优申卖价:{}", 
					data.getNoBidOrders(), data.getNoOfferOrders(), data.getTotalVolumeTraded(), data.getBestBidPrice(), data.getBestOfferPrice());
			
			logger.info("-------------------------  前50笔委托明细   ----------------------------");
			
			logger.info("********************* 买一价位的委托明细(前50笔) **********************");
			logger.info("委托明细:{}", data.getBidOrderQty());
			logger.info("********************* 卖一价位的委托明细(前50笔) **********************");
			logger.info("委托明细:{}", data.getOfferOrderQty());
			
		}
		
		/**
		 * Level2 买一／卖一前五十笔委托明细的增量更新消息回调(仅上证)
		 * @param head 行情的消息头
		 * @param data 行情消息体 
		 */
		@Override
		public void onMktL2BestOrdersSnapshotIncremental(MdsMktDataSnapshotHead head, MdsL2BestOrdersSnapshotIncremental data){
			logger.info("##########################  Level2 委托队列的增量更新信息（买一／卖一前五十笔委托明细）   ##########################");
			logger.info("产品代码:{}, 市场 :{}, 证券类型:{}, 交易日期:{}, 更新时间:{}, ",
					head.getInstrId(), head.getExchId(), head.getSecurityType(), head.getTradeDate(), head.getUpdateTime());
			logger.info("最优申买价:{}, 增量更新消息中是否已经包含了最优申买价位:{}, 当前最优申买价下被连续删除掉的订单笔数:{}, 买盘需要更新的笔数:{},"
					+ "最优申卖价:{}, 增量更新消息中是否已经包含了最优申买价位:{}, 当前最优申卖价下被连续删除掉的订单笔数:{}, 卖盘需要更新的笔数:{} ", 
					data.getBestBidPrice(), data.isHasContainedBestBidLevel(), data.getContinualDeletedBidOrders(), data.getNoBidOrders(), 
					data.getBestOfferPrice(), data.isHasContainedBestOfferLevel(), data.getContinualDeletedOfferOrders(), data.getNoOfferOrders()
					);
			
			logger.info("-------------------------  发生变更的委托明细   ----------------------------");
			logger.info("订单位置状态:{}", data.getOperatorEntryID());
			logger.info("委托明细:{}", data.getOrderQty());
		}
		
		/**
		 * Level2 市场总览 (仅上证)回调
		 * @param head 行情的消息头
		 * @param data 行情消息体  
		 */
		@Override
		public void onMktMdsL2MarketOverview(MdsMktDataSnapshotHead head, MdsL2MarketOverview data){
			logger.info("##########################  Level2 市场总览 (仅上证)   ##########################");
			logger.info("产品代码:{}, 市场 :{}, 证券类型:{}, 交易日期:{}, 更新时间:{}, ",
					head.getInstrId(), head.getExchId(), head.getSecurityType(), head.getTradeDate(), head.getUpdateTime());
			logger.info("市场日期 :{}, 市场时间 :{}", 
					data.getOrigDate(), data.getOrigTime());
			
		}
		
		/**
		 * Level2 逐笔成交行情
		 * @param data 行情消息体 
		 */
		@Override
		public void onMktL2Trade(MdsL2Trade data){
			logger.info("##########################   Level2 逐笔成交行情  ##########################");
			logger.info("产品代码:{}, 交易所代码 :{}, 证券类型:{}, 交易日期:{}, 成交时间:{}, "
					+ "成交通道/频道代码:{}, 成交序号/消息记录号 :{}, 成交类别 :{}, 成交价格:{}, 成交数量:{}, "
					+ "成交金额 :{}, 买方订单号:{}, 卖方订单号:{}", 
					data.getSecurityID(), data.getExchId(), data.getSecurityType(), data.getTradeDate(), data.getTransactTime(),
					data.getChannelNo(), data.getApplSeqNum(), data.getExecType(), data.getTradePrice(), data.getTradeQty(), 
					data.getTradeMoney(), data.getBidApplSeqNum(), data.getOfferApplSeqNum());
		}
		
		/**
		 * Level2 逐笔委托行情回调
		 * @param data 行情消息体   
		 */
		@Override
		public void onMktL2Order(MdsL2Order data){
			logger.info("##########################  Level2 逐笔委托行情  ##########################");
			logger.info("产品代码:{}, 交易所代码 :{}, 证券类型:{}, 交易日期:{}, 委托时间:{}, "
					+ "频道代码:{}, 委托序号/消息记录号 :{}, 买卖方向 :{}, 订单类型:{}, 委托价格:{}, "
					+ "委托数量 :{}", 
					data.getSecurityID(), data.getExchId(), data.getSecurityType(), data.getTradeDate(), data.getTransactTime(),
					data.getChannelNo(), data.getApplSeqNum(), data.getSide(), data.getOrderType(), data.getPrice(), 
					data.getOrderQty());
		}
		
		/**
		 * 证券实时状态定义 (仅适用于深圳市场, 上海市场没有该行情)
		 * @param data 证券状态信息
		 */
		@Override
		public void onSecurityStatus(MdsSecurityStatusMsg data){
			logger.info("##########################  证券实时状态  ##########################");
			logger.info("交易所代码 :{}, 产品代码:{},  证券类型:{}, 交易日期:{}, 更新时间:{}, "
					+ "证券状态:{}, 开关个数 :{}", 
					data.getExchId(), data.getSecurityID(),  data.getSecurityType(), data.getTradeDate(), data.getUpdateTime(),
					data.getFinancialStatus(), data.getNoSwitch());
		}
		
		/**
		 * 市场状态消息 (仅适用于上海市场, 深圳市场没有该行情)
		 * @param data 证券状态信息
		 */
		@Override
		public void onTrdSessionStatus(MdsTradingSessionStatusMsg data){
			logger.info("##########################  市场状态  ##########################");
			logger.info("交易所代码 :{}, 证券类型:{}, 交易日期:{}, 更新时间:{}, "
					+ "最大产品数目:{}, 全市场行情状态 :{}", 
					data.getExchId(), data.getSecurityType(), data.getTradeDate(), data.getUpdateTime(),
					data.getTotNoRelatedSym(), data.getTradingSessionID());
		}
		
		/**
		 * 测试消息回调
		 */
		@Override
		public void onTest(MdsTestRequestRsp rsp){
			logger.info("##########################  行情通道测试应答  ##########################");
			logger.info("测试请求标识符:{}, 请求发送时间:{}, 应答发送时间:{}, 消息实际接收时间:{}, 处理完成时间:{}, 推送时间:{}", 
					rsp.getTestReqId(), rsp.getOrigSendTime(), rsp.getRespTime(), 
					rsp.getLatencyFields().getRecvTime().toString(),
					rsp.getLatencyFields().getCollectedTime().toString(),
					rsp.getLatencyFields().getPushingTime().toString());			
		}
		
	}
}
