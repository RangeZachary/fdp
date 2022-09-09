package pers.range.fdp.mds;

import com.quant360.api.callback.MdsUdpCallBack;
import com.quant360.api.client.Client.QueryMode;
import com.quant360.api.client.MdsUdpClient;
import com.quant360.api.client.impl.MdsUdpClientImpl;
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
import com.quant360.api.model.mds.enu.MdsSecurityType;
import com.quant360.api.model.oes.enu.ErrorCode;
import com.quant360.api.model.oes.enu.OesSecurityType;
import com.quant360.api.utils.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * 
 * <p>Description: OES-API MDS 组播行情调用实现用例</p>
 * 本用例基本实现 
 * 1.客户端连接登录
 * 2.行情订阅
 * 3.接收并打印行情信息
 * 4.查询信息
 * @author: David
 * @date:2018年5月22日
 */
public class MdsUdpExample {
	protected Logger logger = LoggerFactory.getLogger(getClass());
	
	/** MDS行情客户端*/
	private MdsUdpClient client;
	/** 行情回调函数实现样例*/
	private MdsExamCallBack callBack;
	/** API配置文件相对路径*/
	private static final String CONFIGPATH = "config/mds_api_config.json";
	
	/** 客户端登录请求*/
	private ClientLogonReq logonReq;
	/** 客户端登录应答*/
	private ClientLogonRsp logonRsp;
	
	private CountDownLatch mainThreadLatch = new CountDownLatch(1);
	
	public MdsUdpExample(){
		callBack = new MdsExamCallBack();
	}

	public void startup() throws Exception{
		// 启动客户端
		startClient();
		
		/**
		 * 样例中为了防止主线程关闭加锁阻塞(实际应用中无需这样操作)
		 */
		mainThreadLatch.await();
		
		
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
			client = new MdsUdpClientImpl(CONFIGPATH);
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
			/** 登录成功后等待接收组播行情*/
			logger.info("等待接收组播行情信息 ... ...");
			
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
				logger.info("MDS客户端: {} 登录失败 ！ [客户端密码不正确] errorCode = {}", logonReq.getUsername(), code);
				break;
			case CLIENT_REPEATED:
				logger.info("MDS客户端: {} 登录失败 ！ [客户端重复登录] errorCode = {}", logonReq.getUsername(), code);
				break;
			case CLIENT_CONNECT_OVERMUCH:
				logger.info("MDS客户端: {} 登录失败 ！ [客户端连接数量过多] errorCode = {}", logonReq.getUsername(), code);
				break;
			case INVALID_PROTOCOL_VERSION:
				logger.info("MDS客户端: {} 登录失败 ! [API协议版本不兼容] errorCode = {}", logonReq.getUsername(), code);
				break;
			case OTHER_ERROR:
				logger.info("MDS客户端: {} 登录失败 ！ [其他错误] errorCode = {}", logonReq.getUsername(), code);
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
	
	/** 测试查询通道 */
	public void testQryChannel(){
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
			MdsQryStockStaticInfoRsp rsp = client.queryStockStaticInfo(qryFilter, QueryMode.ALL);
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
			MdsQryOptionStaticInfoRsp rsp = client.queryOptionStaticInfo(qryFilter, QueryMode.ALL);
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
			MdsQrySnapshotListRsp rsp = client.querySnapshotList(qryFilter, QueryMode.ALL);
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
			MdsQryStockStaticInfoListRsp rsp = client.queryStockStaticInfoList(qryFilter, QueryMode.PAGE);
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
			MdsQryOptionStaticInfoListRsp rsp = client.queryOptionStaticInfoList(qryFilter, QueryMode.ALL);
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
					+ "昨日收盘价:{}, 面值:{}", 
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
	class MdsExamCallBack extends MdsUdpCallBack{		
		
		@Override
		public void onDisConn(MdsUdpClient client) {
			/**
			 * TODO: 连接中断后，由另外的线程发起重连操作，不要在回调函数中直接操作，这样会占用API的回调处理线程
			 */
			logger.error("行情连接已中断！");
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
	}
}
