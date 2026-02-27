# Tradex Server 개발 진행 현황

> 마지막 업데이트: 2026-02-27

---

## Week 1: 프로젝트 기반 구축

- [x] Spring Boot 프로젝트 초기 설정 (Gradle, Java 17)
- [x] 데이터베이스 스키마 설계 (엔티티 23개)
- [x] PostgreSQL 설정 및 JPA 엔티티 구성
- [x] Docker Compose 환경 구축 (`docker-compose.yml`, `docker-compose.local.yml`)
- [x] CI/CD 파이프라인 기본 설정 (GitHub Actions - `ci.yml`, `deploy.yml`)
- [x] API 문서화 도구 세팅 (SpringDoc OpenAPI 2.7.0 / Swagger)
- [x] 공통 응답 포맷 및 예외 처리 구조 (`ApiResponse`, `GlobalExceptionHandler`, `ErrorCode`)

---

## Week 2: 인증/인가 시스템

- [x] Spring Security + JWT 기반 인증 구현 (`JwtTokenProvider`, `JwtAuthenticationFilter`)
- [x] 회원가입/로그인 API (`AuthController`)
- [x] OAuth 2.0 소셜 로그인 (Google ✅ / Kakao ✅ / Apple ❌ 미구현)
- [x] 리프레시 토큰 관리 (Redis - `RefreshTokenRepository`)
- [x] 이메일 인증 시스템 (`EmailService`, `VerificationCode`)
- [x] 사용자 프로필 CRUD API (조회/완성/비밀번호 재설정)
- [x] 권한 기반 접근 제어 (`SecurityConfig`)

---

## Week 3: 거래소 API 연동 + 외부 데이터 수집

- [x] REST API 클라이언트 구현 (Bybit ✅ / Binance ✅ / Bitget ✅)
- [x] WebSocket 실시간 시세 수신 구조 (`ExchangeWebSocketManager`, `PositionListener`, `OrderListener`)
- [x] 거래소 API 키 암호화 저장 (`EncryptedStringConverter`, AES 암호화)
- [ ] 거래소별 API Rate Limiting 처리 (기본 구조만 존재, 세부 구현 필요)
- [x] 사용자별 거래소 연동 관리 API (`ExchangeApiKeyService`)

---

## Week 4: 매매일지 및 매매 원칙 관리

- [x] 매매일지 CRUD API (`TradingJournalController`)
- [x] 매매 원칙 등록/관리 API (`TradingPrincipleController`)
- [x] 다중 조건 필터링 (`TradingJournalRepositoryCustomImpl`)
- [x] 매매일지 통계 집계 API (`DailyStatsService`, `DailyStatsAggregationService`)
- [x] 태그/카테고리 관리 (`JournalStatsOptionsResponse`)
- [x] 페이지네이션 및 정렬 최적화

---

## Week 5: AI 연동 인프라

- [x] OpenAI API 클라이언트 구현 (Spring AI, gpt-4o-mini)
- [x] AI 채팅 API SSE 스트리밍 (`ChatController` - `SseEmitter`)
- [x] 프롬프트 엔지니어링 및 템플릿 관리 (`ChatContextService`)
- [x] 매매 원칙 AI 추천 API (`RiskAnalysisService`)
- [x] 차트 분석 AI 연동 (`StrategyAnalysisService`)
- [ ] AI 응답 캐싱 전략
- [ ] 토큰 사용량 모니터링 및 제한

---

## Week 6: 차트 데이터 + 분석 시스템

- [x] OHLCV 캔들 데이터 조회 API (`ChartController` - `/bars`)
- [ ] 기술적 지표 계산 엔진 (MA, RSI, MACD 등 - 현재 지표명 저장만 가능)
- [ ] Trading System 트리거 설정 API
- [x] 전략 분석 API (`StrategyAnalysisController`)
- [x] 리스크 매핑 데이터 생성 (`RiskPattern`, `RiskAnalysisController`)
- [x] 차트 레이아웃 저장 기능 (`ChartLayoutController`)
- [ ] 배치 작업 - 시세 데이터 정기 수집 (일별 통계 배치는 있으나 시세 수집 배치 없음)

---

## Week 7: 포트폴리오 + 수익 관리

- [x] 자산 현황 조회 API (`PortfolioController` - `/summary`)
- [x] P&L 계산 로직 (`PositionCalculationService`)
- [x] 포트폴리오 수익률 집계 (누적/일일 수익, 자산 내역)
- [x] 거래 내역 동기화 (거래소 API 연동)
- [x] 통계 대시보드 데이터 API (`HomeController`)
- [x] 알림 시스템 (`NotificationService`, `NotificationRepository`)
- [x] 수신함 CRUD API (`Notification` 엔티티)
- [ ] FCM 연동 - 앱 알림

---

## Week 8: 결제 + 최적화 + 배포

- [x] 구독 플랜 관리 시스템 (`Subscription`, `SubscriptionService`)
- [x] 결제 연동 - Toss Payments (`TossPaymentService` - 빌링키 발급, 자동결제)
- [x] 웹훅 처리 (`SubscriptionController` - 빌링키/플랜변경/취소)
- [ ] 쿼리 성능 최적화 (인덱싱, N+1 해결)
- [ ] API 응답 속도 개선 (캐싱, 비동기 처리)
- [ ] 로깅 및 모니터링 (ELK/Prometheus)
- [ ] 부하 테스트 (k6/JMeter)
- [x] 프로덕션 배포 및 헬스체크 (`deploy.yml`)

---

## 진행률 요약

| Week | 기능 | 완료 / 전체 | 완성도 |
|------|------|------------|--------|
| 1 | 기반 구축 | 7 / 7 | 100% |
| 2 | 인증/인가 | 6 / 7 | 86% (Apple 로그인 미구현) |
| 3 | 거래소 API | 4 / 5 | 80% (Rate Limiting 미완) |
| 4 | 매매일지 | 6 / 6 | 100% |
| 5 | AI 연동 | 5 / 7 | 71% (캐싱/모니터링 미구현) |
| 6 | 차트/분석 | 4 / 7 | 57% (지표 엔진/트리거/배치 미구현) |
| 7 | 포트폴리오 | 7 / 7 | 100% |
| 8 | 결제/배포 | 4 / 8 | 50% (최적화/모니터링 미구현) |
