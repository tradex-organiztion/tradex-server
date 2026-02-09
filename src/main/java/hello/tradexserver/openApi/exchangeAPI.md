# Tradex 거래소 API 연동 가이드 (Claude Code 전달용)

---

## 1. 거래소별 API 검증 결과

### 1-1. 바이낸스 (Binance USDT-M Futures)

| 용도 | API | 검증 결과 | api docs link                                                                                                             |
|------|-----|-----------|---------------------------------------------------------------------------------------------------------------------------|
| 포지션 WebSocket | `ACCOUNT_UPDATE` (User Data Stream) | ✅ 가능 | https://developers.binance.com/docs/derivatives/usds-margined-futures/user-data-streams/Event-Balance-and-Position-Update |
| 포지션 REST | `GET /fapi/v2/positionRisk` | ✅ 가능 | https://developers.binance.com/docs/derivatives/usds-margined-futures/trade/rest-api/Position-Information-V2 |
| 체결 정보 | `GET /fapi/v1/userTrades` | ✅ 가능 | https://developers.binance.com/docs/derivatives/usds-margined-futures/trade/rest-api/Account-Trade-List|

**ACCOUNT_UPDATE WebSocket 주요 필드:**
```json
{
  "e": "ACCOUNT_UPDATE",
  "a": {
    "m": "ORDER",  // 이벤트 사유: ORDER, FUNDING_FEE, LIQUIDATION 등
    "P": [{
      "s": "BTCUSDT",       // symbol
      "pa": "20",            // position amount (수량)
      "ep": "6563.66500",   // entry price
      "bep": "0",            // breakeven price
      "up": "2850.21200",   // unrealized PnL
      "mt": "isolated",     // margin type
      "iw": "13200.707",    // isolated wallet
      "ps": "LONG"          // position side (BOTH/LONG/SHORT)
    }]
  }
}
```

**positionRisk REST 보완 필드:** `leverage`, `liquidationPrice`, `markPrice`, `maxNotionalValue`, `isAutoAddMargin`, `marginType`

**⚠️ 주의사항:**
- `ACCOUNT_UPDATE`에는 `leverage` 필드 없음 → REST로 보완 필수
- User Data Stream은 listenKey 필요, 60분마다 갱신 (PUT)
- `userTrades` 최대 6개월 조회, startTime~endTime 간격 7일 제한
- `positionSide` 필드로 hedge/one-way 모드 구분 (BOTH=one-way, LONG/SHORT=hedge)

---

### 1-2. 바이비트 (Bybit V5)

| 용도 | API | 검증 결과 |
|------|-----|-----------|
| 포지션 WebSocket | `position` (private) | ✅ 가능 |
| 포지션 REST | `GET /v5/position/list` | ✅ 가능 |
| 체결 정보 | `GET /v5/execution/list` | ✅ 가능 |
| positionIdx 조회 | `GET /v5/order/history` (orderId로) | ✅ 가능 |
| realized PnL | `GET /v5/position/closed-pnl` (orderId로) | ✅ 가능 |

**position WebSocket 주요 필드:**
```json
{
  "topic": "position",
  "data": [{
    "positionIdx": 2,       // 0=one-way, 1=buy-side(hedge), 2=sell-side(hedge)
    "symbol": "BTCUSDT",
    "side": "",              // Buy/Sell (빈 문자열이면 포지션 없음)
    "size": "0",
    "entryPrice": "0",
    "leverage": "10",        // ✅ leverage 포함!
    "markPrice": "28184.5",
    "unrealisedPnl": "0",
    "takeProfit": "0",
    "stopLoss": "0",
    "trailingStop": "0",
    "liqPrice": "",
    "breakEvenPrice": "93556.73"
  }]
}
```

**⚠️ 주의사항:**
- Bybit position WS에는 `leverage` 포함 → 바이낸스와 다름
- `execution/list`에서 `orderId`는 반환되지만 `positionIdx`는 없음 → `order/history`로 보완 필요
- `closed-pnl`은 포지션 단위 조회이므로 orderId가 아닌 symbol 기준으로 조회하는 게 더 적합
- order/amend/cancel 시에도 position 메시지가 발생 (실제 변경 없어도)

---

### 1-3. 비트겟 (Bitget V2)

| 용도 | API | 검증 결과 | api docs link |
|------|-----|-----------| ----- |
| 포지션 WebSocket | `positions` (private channel) | ✅ 가능 | https://www.bitget.com/api-doc/contract/websocket/private/Positions-Channel |
| 포지션 REST | `GET /api/v2/mix/position/all-position` | ✅ 가능 | https://www.bitget.com/api-doc/contract/position/get-all-position |
| 체결 정보 | `GET /api/v2/mix/order/fill-history` | ✅ 가능 | https://www.bitget.com/api-doc/contract/trade/Get-Fill-History
| position side 조회 | `GET /api/v2/mix/order/orders-history` (orderId로) | ✅ 가능 | https://www.bitget.com/api-doc/contract/trade/Get-Orders-History |

**⚠️ 주의사항:**
- Bitget V2는 `side` + `tradeSide` 조합으로 포지션 방향 결정
    - one-way: `side=buy/sell` (tradeSide 불필요)
    - hedge: `side=buy/sell` + `tradeSide=open/close`
- `fill-history`에 `marginCoin` 필드 추가됨 (최근 업데이트)
- WebSocket 구독 시 `instType: "USDT-FUTURES"` 지정
- 인증에 `ACCESS-PASSPHRASE` 필요 (Binance/Bybit와 다름)

---

## 2. 공통 규칙 (Trade → Position 매핑) 검토 및 보완

### 현재 규칙
```
- position 구분 기준: symbol, position side, 진입 시간
- position WS가 들어오면 DB 업데이트 후 trade REST API 호출
- 진입 판단: 새로운 symbol + position side
- 청산 판단: size가 0이 되는 시점
```

### 🔴 보완 필요 사항

#### (1) 진입 시간 기준의 모호함
- "진입 시간"은 포지션 첫 진입 시점을 의미하는데, **WS에서 신규 포지션이 감지된 시점의 timestamp를 기록**해야 함
- 바이낸스: `ACCOUNT_UPDATE`의 `T` (Transaction Time) 사용
- 바이비트: `creationTime` 사용
- 비트겟: WS push의 `cTime` (create time) 사용
- **제안:** `positionOpenTime` 필드를 별도 관리하고, 최초 진입 trade의 시간으로 설정

#### (2) 부분 청산(분할 익절/손절) 처리
- size가 줄어들지만 0이 아닌 경우 = 부분 청산
- 이 경우 포지션은 계속 유지되며, **해당 trade는 "청산 trade"로 구분해서 저장**해야 함
- `trade.side`와 `position.side` 비교로 진입/청산 구분:
    - 롱 포지션 + Buy trade = 추가 진입
    - 롱 포지션 + Sell trade = 부분/전체 청산

#### (3) 포지션 모드 (One-way vs Hedge) 처리
- **One-way 모드:** 같은 symbol에 하나의 포지션만 존재
    - 바이낸스: `positionSide = "BOTH"`
    - 바이비트: `positionIdx = 0`
    - 비트겟: `holdMode = "one_way_mode"`
- **Hedge 모드:** 같은 symbol에 Long/Short 동시 존재 가능
    - 바이낸스: `positionSide = "LONG"/"SHORT"`
    - 바이비트: `positionIdx = 1(Buy)/2(Sell)`
    - 비트겟: `holdMode = "double_hold"`, `holdSide = "long"/"short"`
- **포지션 구분 키:** `(exchangeId, symbol, positionSide)` → 유저의 포지션 모드를 먼저 확인하는 로직 필요

#### (5) 청산(Liquidation) 이벤트 처리
- 강제청산은 일반 청산과 다르게 처리해야 함
- 바이낸스: `ACCOUNT_UPDATE`의 `m: "LIQUIDATION"` 또는 `forceOrders` API
- 바이비트: position WS에서 `bustPrice` 필드, `adlRankIndicator` 확인
- 비트겟: WS position channel에서 감지
- **제안:** `position.closeReason` enum 추가 (MANUAL, LIQUIDATION, ADL, TP, SL)

#### (6) WS 연결 끊김 시 데이터 정합성
- WS 재연결 시 **REST API로 현재 포지션 스냅샷 동기화** 필수
- 끊김 사이에 발생한 trade 누락 방지:
    - 마지막으로 수신한 trade의 timestamp 이후부터 REST로 보충 조회

#### (7) 평균 진입가/청산가 계산
- "포지션 닫히기 전에는 WS로 받은 entryPrice 사용, 종료 후 trade들로 재계산"이라고 했는데:
    - WS의 `entryPrice`는 **거래소가 계산한 가중평균**이므로 그대로 사용 가능
    - 종료 후 trade 기반 재계산 시: `avgEntryPrice = Σ(entryTrade.price × entryTrade.qty) / Σ(entryTrade.qty)`
    - 평균 청산가도 동일 방식: 청산 trade들의 가중평균
- **주의:** 부분 청산 후 추가 진입 시 거래소의 entryPrice도 변경됨

---

## 3. 구현 순서 제안

```
1. Exchange WebSocket 연결 관리 (listenKey 관리, 재연결 로직)
2. Position WS 이벤트 핸들러 (신규 포지션 감지 / 업데이트 / 종료 감지)
3. Trade REST API 호출 및 Position-Trade 매핑 로직
4. Position REST API로 보완 데이터 조회 (leverage 등)
5. 평균가/PnL 계산 로직
6. WS 재연결 시 동기화 로직
```

---

## 4. 거래소별 인증 방식 요약

| 거래소 | 인증 방식 | WS 접속 |
|--------|----------|---------|
| 바이낸스 | HMAC-SHA256, `X-MBX-APIKEY` 헤더 | listenKey 발급 후 `wss://fstream.binance.com/ws/{listenKey}` |
| 바이비트 | HMAC-SHA256, `X-BAPI-*` 헤더 | `wss://stream.bybit.com/v5/private` + auth 메시지 |
| 비트겟 | HMAC-SHA256 + Base64, `ACCESS-*` 헤더 + **PASSPHRASE** | `wss://ws.bitget.com/v2/ws/private` + login 메시지 |