비트겟(Bitget) USDT-M 선물 연동 가이드 (BitgetExchangeService 구현 참고)
1. 핵심 차이점: Bybit vs Binance vs Bitget

### 2. WebSocket 연결 흐름
1. WebSocket 연결
   wss://ws.bitget.com/v2/ws/private

2. login 메시지 전송 (Bybit auth와 유사)
   {
   "op": "login",
   "args": [{
   "apiKey": "<apiKey>",
   "passphrase": "<passphrase>",
   "timestamp": "<timestamp>",
   "sign": "<signature>"
   }]
   }

   서명: HMAC-SHA256(timestamp + "GET" + "/user/verify", secretKey) → Base64 인코딩

3. 채널 구독 (login 성공 후)
   {
   "op": "subscribe",
   "args": [
   { "instType": "USDT-FUTURES", "channel": "positions", "instId": "default" },
   { "instType": "USDT-FUTURES", "channel": "orders", "instId": "default" }
   ]
   }

4. keepalive: 2분마다 "ping" 문자열 전송 (JSON 아님, 단순 문자열)
   Position Channel
   Description
   Subscribe the position channel

Data will be pushed when the following events occurred:

Open/Close orders are created
Open/Close orders are filled
Orders are canceled
Request Example
{
"op": "subscribe",
"args": [
{
"instType": "USDT-FUTURES",
"channel": "positions",
"instId": "default"
}
]
}

Request Parameters
Parameter	Type	Required	Description
op	String	Yes	Operation, subscribe unsubscribe
args	List<Object>	Yes	List of channels to request subscription
> channel	String	Yes	Channel name: positions
> instType	String	Yes	Product type
USDT-FUTURES USDT-M Futures
COIN-FUTURES Coin-M Futures
USDC-FUTURES USDC-M Futures
> instId	String	Yes	Symbol name,defaultrepresents all the symbols，Only default is supported now
Response Example
{
"event": "subscribe",
"arg": {
"instType": "USDT-FUTURES",
"channel": "positions",
"instId": "default"
}
}

Response Parameters
Parameter	Type	Description
event	String	Event
arg	Object	Subscribed channels
> channel	String	Channel name: positions
> instType	String	Product type
USDT-FUTURES USDT-M Futures
COIN-FUTURES Coin-M Futures
USDC-FUTURES USDC-M Futures
> instId	String	default
code	String	Error code
msg	String	Error message
Push Data
{
"action": "snapshot",
"arg": {
"instType": "USDT-FUTURES",
"channel": "positions",
"instId": "default"
},
"data": [
{
"posId": "1",
"instId": "ETHUSDT",
"marginCoin": "USDT",
"marginSize": "9.5",
"marginMode": "crossed",
"holdSide": "short",
"posMode": "hedge_mode",
"total": "0.1",
"available": "0.1",
"frozen": "0",
"openPriceAvg": "1900",
"leverage": 20,
"achievedProfits": "0",
"unrealizedPL": "0",
"unrealizedPLR": "0",
"liquidationPrice": "5788.108475905242",
"keepMarginRate": "0.005",
"marginRate": "0.004416374196",
"cTime": "1695649246169",
"breakEvenPrice": "24778.97",
"totalFee": "1.45",
"deductedFee": "0.388",
"markPrice": "2500",
"uTime": "1695711602568",            
"assetMode": "union",
"autoMargin": "off"
}
],
"ts": 1695717430441
}


Push Parameters
Parameter	Type	Description
action	String	'snapshot'
arg	Object	Channels with successful subscription
> channel	String	Channel name: positions
> instType	String	Product type
USDT-FUTURES USDT-M Futures
COIN-FUTURES Coin-M Futures
USDC-FUTURES USDC-M Futures
> instId	String	default
data	List<Object>	Subscription data
> posId	String	Position ID
> instId	String	Product ID,
delivery contract reference：https://www.bitget.com/api-doc/common/release-note
> marginCoin	String	Currency of occupied margin
> marginSize	String	Occupied margin (amount)
> marginMode	String	Margin mode
> holdSide	String	Position direction
> posMode	String	Position mode
> total	String	Open position size
> available	String	Size of positions that can be closed
> frozen	String	Amount of frozen margin
> openPriceAvg	String	Average entry price
> leverage	String	Leverage
> achievedProfits	String	Realized PnL
> unrealizedPL	String	Unrealized PnL
> unrealizedPLR	String	Unrealized ROI
> liquidationPrice	String	Estimated liquidation price
> keepMarginRate	String	Maintenance margin rate
> isolatedMarginRate	String	Actual margin ratio under isolated margin mode
> marginRate	String	Occupancy rate of margin
> breakEvenPrice	String	Position breakeven price
> totalFee	String	Funding fee, the accumulated value of funding fee during the position,The initial value is empty, indicating that no funding fee has been charged yet.
> deductedFee	String	Deducted transaction fees: transaction fees deducted during the position
> markPrice	String	Mark Price
> assetMode	String	Account Mode
union: Union Margin
single: Single Margin
> cTime	String	Position creation time, milliseconds format of Unix timestamp, e.g.1597026383085
> uTime	String	Lastest position update time, milliseconds format of Unix timestamp, e.g.1597026383085
https://www.bitget.com/api-doc/contract/websocket/private/Positions-Channel
포지션 이벤트 매핑 (positions 채널)
   이벤트 구조
   json{
   "action": "snapshot",
   "arg": {
   "instType": "USDT-FUTURES",
   "channel": "positions",
   "instId": "default"
   },
   "data": [
   {
   "posId": "1",                     // 포지션 고유 ID ← Bybit/Binance에 없는 필드
   "instId": "ETHUSDT",              // Symbol
   "marginCoin": "USDT",
   "marginSize": "9.5",
   "marginMode": "crossed",          // crossed / isolated
   "holdSide": "short",              // long / short (원웨이도 명시)
   "posMode": "hedge_mode",          // hedge_mode / one_way_mode
   "total": "0.1",                   // 총 수량 ("0"이면 닫힘)
   "available": "0.1",               // 사용 가능 수량
   "frozen": "0",
   "openPriceAvg": "1900",           // 평균 진입가
   "leverage": 20,                   // ← WS에 포함됨! REST 불필요
   "achievedProfits": "0",           // 실현 손익
   "unrealizedPL": "0",             // 미실현 손익
   "unrealizedPLR": "0",            // 미실현 손익률
   "liquidationPrice": "5788.1",
   "keepMarginRate": "0.005",
   "marginRate": "0.004416",
   "cTime": "1695649246169",         // 생성 시간
   "breakEvenPrice": "24778.97",
   "totalFee": "1.45",              // ← 누적 수수료도 포함!
   "deductedFee": "0.388",
   "markPrice": "2500",
   "uTime": "1695711602568",         // 업데이트 시간
   "autoMargin": "off"
   }
   ],
   "ts": 1695717430441
   }

포지션 닫힘 감지
java// Bybit: size == "0"
// Binance: pa == "0"
// Bitget: total == "0"
if (new BigDecimal(positionData.getTotal()).compareTo(BigDecimal.ZERO) == 0) {
// → onPositionClosed() 호출
}

오더 이벤트 매핑 (orders 채널) https://www.bitget.com/api-doc/contract/websocket/private/Order-Channel
Order Channel
Description
Subscribe the order channel

Data will be pushed when the following events occured:

Open/Close orders are created
Open/Close orders are filled
Orders canceled
Request Example
{
"op": "subscribe",
"args": [
{
"instType": "USDT-FUTURES",
"channel": "orders",
"instId": "default"
}
]
}

Request Parameters
Parameter	Type	Required	Description
op	String	Yes	Operation, subscribe unsubscribe
args	List<Object>	Yes	List of channels to request subscription
> channel	String	Yes	Channel name: orders
> instType	String	Yes	Product type
USDT-FUTURES USDT-M Futures
COIN-FUTURES Coin-M Futures
USDC-FUTURES USDC-M Futures
> instId	String	No	Trading pair, e.g. BTCUSDT
default: All trading pairs
For settled Futures, it only supports default
Response Example
{
"event": "subscribe",
"arg": {
"instType": "USDT-FUTURES",
"channel": "orders",
"instId": "default"
}
}

Response Parameters
Parameter	Type	Description
event	String	Event
arg	Object	Subscribed channels
> channel	String	Channel name: orders
> instType	String	Product type
USDT-FUTURES USDT-M Futures
COIN-FUTURES Coin-M Futures
USDC-FUTURES USDC-M Futures
> instId	String	Product ID
code	String	Error code
msg	String	Error message
Push Data
{
"data": [
{
"leverage": "12",
"orderType": "limit",
"presetStopLossType": "fill_price",
"tradeSide": "open",
"orderId": "13333333333333333333",
"presetStopSurplusExecutePrice": "3200",
"feeDetail": [
{
"feeCoin": "USDT",
"fee": "0.00000000"
}
],
"cTime": "1760461517274",
"posMode": "hedge_mode",
"marginMode": "crossed",
"presetStopLossExecutePrice": "2800",
"posSide": "long",
"price": "3000",
"enterPointSource": "API",
"cancelReason": "",
"accBaseVolume": "0",
"stpMode": "none",
"side": "buy",
"totalProfits": "0",
"marginCoin": "USDT",
"notionalUsd": "1200",
"instId": "ETHUSDT",
"presetStopSurplusPrice": "3200",
"size": "0.4",
"reduceOnly": "no",
"presetStopLossPrice": "2800",
"force": "gtc",
"uTime": "1760461517274",
"presetStopSurplusType": "fill_price",
"clientOid": "12354678990111",
"status": "live"
}
],
"arg": {
"instType": "USDT-FUTURES",
"instId": "default",
"channel": "orders"
},
"action": "snapshot",
"ts": 1760461517285
}

Push Parameters
Parameter	Type	Description
arg	Object	Channels with successful subscription
> channel	String	Channel name: orders
> instType	String	Product type
USDT-FUTURES USDT-M Futures
COIN-FUTURES Coin-M Futures
USDC-FUTURES USDC-M Futures
> instId	String	Product ID
delivery contract reference：https://www.bitget.com/api-doc/common/release-note
data	List<Object>	Subscription data
> orderId	String	Order ID
> clientOid	String	Customized order ID
> price	String	Order price
> size	String	Original order amount in coin
> posMode	String	Position Mode
one_way_mode：one-way mode
hedge-mode: hedge mode
> enterPointSource	String	Order source
WEB: Orders created on the website
API: Orders created on API
SYS: System managed orders, usually generated by forced liquidation logic
ANDROID: Orders created on the Android app
IOS: Orders created on the iOS app
> tradeSide	String	Direction
close: Close (open and close mode)
open: Open (open and close mode)
reduce_close_long: Liquidate partial long positions for hedge position mode
reduce_close_short：Liquidate partial short positions for hedge position mode
burst_close_long：Liquidate long positions for hedge position mode
burst_close_short：Liquidate short positions for hedge position mode
offset_close_long：Liquidate partial long positions for netting for hedge position mode
offset_close_short：Liquidate partial short positions for netting for hedge position mode
delivery_close_long：Delivery long positions for hedge position mode
delivery_close_short：Delivery short positions for hedge position mode
dte_sys_adl_close_long：ADL close long position for hedge position mode
dte_sys_adl_close_short：ADL close short position for hedge position mode
buy_single：Buy, one way postion mode
sell_single：Sell, one way postion mode
reduce_buy_single：Liquidate partial positions, buy, one way position mode
reduce_sell_single：Liquidate partial positions, sell, one way position mode
burst_buy_single：Liquidate short positions, buy, one way postion mode
burst_sell_single：Liquidate partial positions, sell, one way position mode
delivery_sell_single：Delivery sell, one way position mode
delivery_buy_single：Delivery buy, one way position mode
dte_sys_adl_buy_in_single_side_mode：ADL close position, buy, one way position mode
dte_sys_adl_sell_in_single_side_mode：ADL close position, sell, one way position mode
> notionalUsd	String	Estimated USD value of orders
> orderType	String	Order type
limit: limit order
market: market order
> force	String	Order validity period
> side	String	Order direction
> posSide	String	Position direction
long: hedge-mode, long position
short: hedge-mode, short position
net: one-way-mode position
> marginMode	String	Margin mode
crossed: crossed mode
isolated: isolated mode
> marginCoin	String	Margin coin
> fillPrice	String	Latest filled price
> tradeId	String	Latest transaction ID
> baseVolume	String	Number of latest filled orders
> fillTime	String	Latest transaction time. Unix millisecond timestamp, e.g. 1690196141868
> fillFee	String	Transaction fee of the latest transaction, negative value
> fillFeeCoin	String	Currency of transaction fee of the latest transaction
> tradeScope	String	The liquidity direction of the latest transaction T: taker M maker
> accBaseVolume	String	Total filled quantity
> fillNotionalUsd	String	USD value of filled orders
> priceAvg	String	Average filled price
If the filled size is 0, the field is 0; if the order is not filled, the field is also 0; This field will not be pushed if the order is cancelled
> status	String	Order status
live: New order, waiting for a match in orderbook
partially_filled: Partially filled
filled: All filled
canceled: the order is cancelled
> cancelReason	String	Cancel reason
normal_cancel Normal cancel
stp_cancel Cancelled by STP
Detailed enumerations can be obtained on the Enumeration page.
> leverage	String	Leverage
> feeDetail	List<Object>	Transaction fee of the order
>> feeCoin	String	The currency of the transaction fee. The margin is charged.
>> fee	String	Order transaction fee, the transaction fee charged by the platform from the user.
> pnl	String	Profit
> uTime	String	Order update time, Milliseconds format of updated data timestamp Unix, e.g. 1597026383085
> cTime	String	Order creation time, milliseconds format of Unix timestamp, e.g.1597026383085
> reduceOnly	String	Reduce-only
yes: Yes
no: No
> presetStopSurplusPrice	String	Set TP price
> presetStopLossPrice	String	Set SL price
> stpMode	String	STP Mode
none not setting STP
cancel_taker cancel taker order
cancel_maker cancel maker order
cancel_both cancel both of taker and maker orders
> totalProfits	String	Total profits
> presetStopSurplusPrice	String	Take-profit value
> presetStopLossPrice	String	Stop-loss value
> presetStopSurplusExecutePrice	String	Preset stop - profit execution price
> presetStopLossExecutePrice	String	Preset stop-loss execution price
이벤트 구조
json{
"action": "snapshot",
"arg": {
"instType": "USDT-FUTURES",
"channel": "orders",
"instId": "default"
},
"data": [
{
"orderId": "13333333333333333333",
"clientOid": "12354678990111",
"instId": "ETHUSDT",              // Symbol
"side": "buy",                    // buy / sell
"tradeSide": "open",             // "open" / "close" ← positionEffect 역할
"posSide": "long",               // long / short
"posMode": "hedge_mode",         // hedge_mode / one_way_mode
"orderType": "limit",            // limit / market
"price": "3000",                 // 주문 가격
"size": "0.4",                   // 주문 수량
"accBaseVolume": "0",            // 누적 체결 수량
"leverage": "12",               // 레버리지
"marginMode": "crossed",
"marginCoin": "USDT",
"notionalUsd": "1200",
"status": "live",                // live / partially_filled / filled / cancelled
"totalProfits": "0",            // 실현 손익
"reduceOnly": "no",             // yes / no
"feeDetail": [                   // 수수료 상세 (누적)
{
"feeCoin": "USDT",
"fee": "0.00000000"
}
],
"presetStopSurplusPrice": "3200",   // TP 가격
"presetStopLossPrice": "2800",      // SL 가격
"enterPointSource": "API",
"force": "gtc",
"cTime": "1760461517274",
"uTime": "1760461517274"
}
],
"ts": 1760461517285
}

leverage, totalFee가 WS에 다 포함 — Binance처럼 REST 추가 호출 불필요
tradeSide 필드 — "open" / "close"로 진입/청산을 명시적으로 알려줘서 reduceOnly로 간접 판단하는 Bybit/Binance보다 정확함
수수료가 feeDetail 배열로 누적 제공 — Binance처럼 직접 합산할 필요 없음

### 주의사항
snapshot: 첫 구독 시 + 재연결 시 현재 전체 상태를 보내줌
update: 이후 변경분만 보내줌

javaif ("snapshot".equals(action)) {
// 현재 열린 포지션 전체 동기화 (DB와 비교)
// → 재연결 시 갭 복구 역할도 겸함
}

if ("update".equals(action)) {
// 변경된 포지션/오더만 처리
// → 기존 Bybit onPositionUpdate/onPositionClosed 로직과 동일
}
실질적으로 update에서 오는 데이터 구조는 snapshot과 동일하니까, 둘 다 같은 핸들러로 처리하되 snapshot일 때 전체 동기화 로직을 추가하면 됨.
오히려 Bitget의 snapshot이 장점인 게, 재연결 시 자동으로 현재 상태 snapshot을 다시 보내줘서 Bybit/Binance의 onReconnected() 갭 복구 로직이 일부 간소화될 수 있음. 단, 오더는 snapshot에 열린 주문만 오니까 WS 끊긴 사이에 체결+완료된 주문은 REST 보완이 여전히 필요함.