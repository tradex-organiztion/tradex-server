WebSocket 연결 흐름
The base endpoint for testnet is: wss://testnet.binancefuture.com/ws-fapi/v1

1. REST API로 listenKey 발급
   POST https://fapi.binance.com/fapi/v1/listenKey
   Header: X-MBX-APIKEY: <apiKey>

2. WebSocket 연결
   wss://fstream.binance.com/ws/<listenKey>
   → 연결 즉시 모든 user data stream 수신 (별도 subscribe 불필요)

3. listenKey 유지 (60분마다 PUT)
   PUT https://fapi.binance.com/fapi/v1/listenKey
   → ScheduledExecutorService로 30분마다 keepalive 호출 권장

4. 24시간 후 자동 만료 → 재연결 로직 필요

포지션 이벤트 매핑 (ACCOUNT_UPDATE)
이벤트 구조
json{
"e": "ACCOUNT_UPDATE",
"E": 1564745798939,       // Event Time
"T": 1564745798938,       // Transaction Time
"a": {
"m": "ORDER",           // 이벤트 이유: ORDER, FUNDING_FEE, LIQUIDATION 등
"B": [ ... ],           // Balance 변경 (생략 가능)
"P": [                  // Position 변경
{
"s": "BTCUSDT",     // Symbol
"pa": "0",          // Position Amount ("0"이면 포지션 닫힘)
"ep": "0.00000",    // Entry Price
"bep": "0",         // Breakeven Price
"cr": "200",        // (Pre-fee) Accumulated Realized PnL
"up": "0",          // Unrealized PnL
"mt": "isolated",   // Margin Type: isolated / cross
"iw": "0.00000000", // Isolated Wallet
"ps": "BOTH"        // Position Side: BOTH / LONG / SHORT
}
]
}
}

Response Example
{
"e": "ACCOUNT_UPDATE",				// Event Type
"E": 1564745798939,            		// Event Time
"T": 1564745798938 ,           		// Transaction
"a":                          		// Update Data
{
"m":"ORDER",						// Event reason type
"B":[                     		// Balances
{
"a":"USDT",           		// Asset
"wb":"122624.12345678",    	// Wallet Balance
"cw":"100.12345678",			// Cross Wallet Balance
"bc":"50.12345678"			// Balance Change except PnL and Commission
},
{
"a":"BUSD",           
"wb":"1.00000000",
"cw":"0.00000000",         
"bc":"-49.12345678"
}
],
"P":[
{
"s":"BTCUSDT",          	// Symbol
"pa":"0",               	// Position Amount
"ep":"0.00000",            // Entry Price
"bep":"0",                // breakeven price
"cr":"200",             	// (Pre-fee) Accumulated Realized
"up":"0",						// Unrealized PnL
"mt":"isolated",				// Margin Type
"iw":"0.00000000",			// Isolated Wallet (if isolated position)
"ps":"BOTH"					// Position Side
}，
{
"s":"BTCUSDT",
"pa":"20",
"ep":"6563.66500",
"bep":"0",                // breakeven price
"cr":"0",
"up":"2850.21200",
"mt":"isolated",
"iw":"13200.70726908",
"ps":"LONG"
},
{
"s":"BTCUSDT",
"pa":"-10",
"ep":"6563.86000",
"bep":"6563.6",          // breakeven price
"cr":"-45.04000000",
"up":"-1423.15600",
"mt":"isolated",
"iw":"6570.42511771",
"ps":"SHORT"
}
]
}
}

오더 이벤트 매핑 (ORDER_TRADE_UPDATE)
Event Description
When new order created, order status changed will push such event. event type is ORDER_TRADE_UPDATE.

Side

BUY
SELL
Order Type

LIMIT
MARKET
STOP
STOP_MARKET
TAKE_PROFIT
TAKE_PROFIT_MARKET
TRAILING_STOP_MARKET
LIQUIDATION
Execution Type

NEW
CANCELED
CALCULATED - Liquidation Execution
EXPIRED
TRADE
AMENDMENT - Order Modified
Order Status

NEW
PARTIALLY_FILLED
FILLED
CANCELED
EXPIRED
EXPIRED_IN_MATCH
Time in force

GTC
IOC
FOK
GTX
Working Type

MARK_PRICE
CONTRACT_PRICE
Liquidation and ADL:

If user gets liquidated due to insufficient margin balance:

c shows as "autoclose-XXX"，X shows as "NEW"
If user has enough margin balance but gets ADL:

c shows as “adl_autoclose”，X shows as “NEW”
Expiry Reason

0: None, the default value
1: Order has expired to prevent users from inadvertently trading against themselves
2: IOC order could not be filled completely, remaining quantity is canceled
3: IOC order could not be filled completely to prevent users from inadvertently trading against themselves, remaining quantity is canceled
4: Order has been canceled, as it's knocked out by another higher priority RO (market) order or reversed positions would be opened
5: Order has expired when the account was liquidated
6: Order has expired as GTE condition unsatisfied
7: Order has been canceled as the symbol is delisted
8: The initial order has expired after the stop order is triggered
9: Market order could not be filled completely, remaining quantity is canceled

이벤트 구조
json{
"e": "ORDER_TRADE_UPDATE",
"E": 1568879465651,      // Event Time
"T": 1568879465650,      // Transaction Time
"o": {
"s": "BTCUSDT",        // Symbol
"c": "TEST",           // Client Order Id
"S": "SELL",           // Side: BUY / SELL
"o": "MARKET",         // Order Type
"f": "GTC",            // Time in Force
"q": "0.001",          // Original Quantity
"p": "0",              // Original Price (limit)
"ap": "0",             // Average Price (체결 평균가)
"sp": "7103.04",       // Stop Price
"x": "NEW",            // Execution Type: NEW/TRADE/CANCELED/EXPIRED/AMENDMENT
"X": "NEW",            // Order Status: NEW/PARTIALLY_FILLED/FILLED/CANCELED/EXPIRED
"i": 8886774,          // Order Id
"l": "0",              // Last Filled Quantity (이번 체결량)
"z": "0",              // Filled Accumulated Quantity (누적 체결량)
"L": "0",              // Last Filled Price
"N": "USDT",           // Commission Asset
"n": "0",              // Commission (수수료)
"T": 1568879465650,    // Order Trade Time
"t": 0,                // Trade Id
"R": false,            // Is Reduce Only
"wt": "CONTRACT_PRICE",// Working Type
"ot": "MARKET",        // Original Order Type
"ps": "LONG",          // Position Side
"cp": false,           // Close-All
"rp": "0",             // Realized Profit of the trade
"m": false,            // Is maker
"b": "0",              // Bids Notional
"a": "9.91"            // Ask Notional
}
}

### 주요 주의 사항
- 수수료 누적: Binance ORDER_TRADE_UPDATE의 n은 이번 체결분만 → 반드시 직접 합산
- leverage: ACCOUNT_UPDATE에 레버리지 없음 → REST API(GET /fapi/v2/positionRisk)로 별도 조회 필요 (포지션 신규 감지 시 딱 1번만 REST로 조회하면 됨)
- FUNDING_FEE: m == "FUNDING_FEE"일 때 P 배열이 없거나 제한적 → 포지션 open/close로 오판하지 않도록 필터링
- listenKey 만료: 60분 유효, 30분마다 PUT 갱신, 24시간 후 강제 재연결
- Testnet URL: https://testnet.binancefuture.com (fapi), wss://stream.binancefuture.com (ws)
- 헷지 모드: ps가 "LONG" 또는 "SHORT" → Bybit의 positionIdx 1, 2에 대응
  원웨이 모드: ps == "BOTH" → Bybit의 positionIdx 0에 대응
- ps 필드: "BOTH" / "LONG" / "SHORT"

### 저장할 오더 데이터 필터링: 
if ("FILLED".equals(orderStatus)) {
saveOrder(data);
}
else if (("CANCELED".equals(orderStatus)
|| "EXPIRED".equals(orderStatus)
|| "EXPIRED_IN_MATCH".equals(orderStatus))
&& filledQty.compareTo(BigDecimal.ZERO) > 0) {
saveOrder(data);
}