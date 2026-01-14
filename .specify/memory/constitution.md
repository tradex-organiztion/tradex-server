# Tradex Constitution
트레이딩 분석 플랫폼 백엔드 개발 헌장

## 📋 프로젝트 개요

### 프로젝트명
**Tradex** - AI 기반 트레이딩 분석 및 매매 일지 관리 플랫폼

### 기술 스택
- **Framework**: Spring Boot 4.0.1
- **Language**: Java 17
- **Architecture**: Modular Monolithic Architecture

---

## 🎯 핵심 원칙 (Core Principles)

### 1. 데이터 무결성 우선 (Data Integrity First)
- 모든 거래 데이터는 **Source of Truth**로 관리
- 트랜잭션 일관성 보장 (ACID)
- 거래소 API 데이터와 내부 데이터의 동기화 무결성 유지
- 금융 데이터 특성상 데이터 손실 절대 불가

### 2. 보안 중심 설계 (Security by Design)
- 거래소 API Key는 암호화하여 저장
- 사용자 민감 정보 보호 (개인정보, 거래 내역)
- 모든 API 엔드포인트에 인증/인가 적용
- 금융 데이터 접근 시 감사 로그 필수

### 3. 모듈화된 모놀리틱 설계 (Modular Monolithic Design)
- 명확한 모듈 경계로 기능 분리
- 각 모듈은 독립적인 패키지로 구성
- 다중 거래소 지원을 위한 추상화 계층 구현
- 모듈 간 인터페이스 기반 통신
- 향후 필요 시 모듈을 서비스로 분리 가능한 구조

### 4. AI 친화적 데이터 구조 (AI-Ready Data Structure)
- 매매 일지 데이터의 구조화 및 정규화
- AI 분석을 위한 시계열 데이터 최적화
- 메타데이터 태깅 시스템 구현
- 학습 데이터 품질 보장

### 5. 명확한 책임 분리 (Clear Separation of Concerns)
- Layer 기반 아키텍처 준수
- Domain-Driven Design 원칙 적용
- 각 도메인의 독립성 보장

---

## 🏗️ 아키텍처 원칙

### Modular Monolithic Architecture

#### 핵심 개념
- **하나의 애플리케이션**으로 배포
- **레이어 기반** 패키지 구조
- 도메인은 Entity 단위로 구분
- 명확한 레이어 간 의존성 규칙

#### 아키텍처 다이어그램
```
┌────────────────────────────────────────────────────────────┐
│                   Tradex Application                       │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │         API Layer (Controllers + DTOs)               │  │
│  │  UserController, PositionController, JournalController│  │
│  └──────────────────────────────────────────────────────┘  │
│                           ↓                                │
│  ┌──────────────────────────────────────────────────────┐  │
│  │         Application Layer (Services)                 │  │
│  │  UserService, PositionService, JournalService...     │  │
│  └──────────────────────────────────────────────────────┘  │
│                           ↓                                │
│  ┌──────────────────────────────────────────────────────┐  │
│  │         Domain Layer (Entities + Repositories)       │  │
│  │  User, Position, Journal, Order, Trade...            │  │
│  └──────────────────────────────────────────────────────┘  │
│                           ↓                                │
│  ┌──────────────────────────────────────────────────────┐  │
│  │    Infrastructure Layer (Data + External APIs)       │  │
│  │  JPA Repositories, Exchange Clients, Cache...        │  │
│  └──────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────┘
```

### Layered Architecture
```
┌─────────────────────────────────┐
│   API Layer                     │ ← Controllers, DTOs
├─────────────────────────────────┤
│   Application Layer             │ ← Services, Use Cases
├─────────────────────────────────┤
│   Domain Layer                  │ ← Entities, VOs, Repo Interface
├─────────────────────────────────┤
│   Infrastructure Layer          │ ← Repo Impl, External APIs
└─────────────────────────────────┘
```

### 패키지 구조 원칙
```
com.tradex
├── api (모든 Controllers, DTOs)
│   ├── controller
│   │   ├── UserController
│   │   ├── PositionController
│   │   ├── JournalController
│   │   ├── StrategyController
│   │   └── ...
│   └── dto
│       ├── request
│       └── response
│
├── application (모든 Services, UseCases)
│   ├── UserService
│   ├── PositionService
│   ├── JournalService
│   ├── StrategyService
│   ├── RiskAnalysisService
│   └── ...
│
├── domain (모든 Entities, Value Objects, Repository Interfaces)
│   ├── entity
│   │   ├── User
│   │   ├── Position
│   │   ├── TradingJournal
│   │   ├── Order
│   │   └── ...
│   ├── vo (Value Objects)
│   └── repository (Interfaces)
│       ├── UserRepository
│       ├── PositionRepository
│       └── ...
│
├── infrastructure (Repository 구현, External APIs)
│   ├── repository (JPA 구현체)
│   ├── exchange (거래소 API 클라이언트)
│   │   ├── ExchangeClient (Interface)
│   │   ├── BinanceClient
│   │   ├── BybitClient
│   │   └── ...
│   └── external (기타 외부 연동)
│
├── security (인증/인가)
│   ├── jwt
│   ├── oauth
│   └── filter
│
└── config (설정)
    ├── SecurityConfig
    ├── JpaConfig
    ├── CacheConfig
    └── ...
```

### 레이어별 책임

#### API Layer
- HTTP 요청/응답 처리
- DTO 변환
- 입력 검증
- 예외 처리

#### Application Layer
- 비즈니스 로직 오케스트레이션
- 트랜잭션 관리
- 도메인 객체 조합
- 서비스 간 협업

#### Domain Layer
- 핵심 비즈니스 규칙
- 엔티티 및 값 객체
- Repository 인터페이스 (구현체는 Infrastructure)
- 도메인 이벤트

#### Infrastructure Layer
- 데이터 영속성 (JPA 구현)
- 외부 시스템 연동
- 기술적 세부사항

### 레이어 간 의존성 규칙

#### ✅ 허용되는 의존성
```java
// 1. 상위 레이어 → 하위 레이어 (단방향)
api.controller → application.service
application.service → domain.entity
application.service → domain.repository (Interface)
infrastructure.repository → domain.repository (구현)

// 2. 같은 레이어 내 의존
application.JournalService → application.PositionService
domain.Position → domain.Order
```

#### ❌ 금지되는 의존성
```java
// 1. 하위 → 상위 레이어 (역방향)
domain.entity → application.service (X)
application.service → api.controller (X)

// 2. 레이어 건너뛰기
api.controller → domain.repository (X)
api.controller → infrastructure.repository (X)

// 3. Infrastructure → Application (X)
infrastructure.BinanceClient → application.PositionService (X)
// 대신 Interface를 domain에 두고 의존성 역전
```

### 의존성 역전 원칙 (DIP) 적용
```java
// domain 패키지에 Interface 정의
package com.tradex.domain.repository;
public interface PositionRepository {
    Position save(Position position);
    Optional<Position> findById(Long id);
}

// infrastructure 패키지에서 구현
package com.tradex.infrastructure.repository;
@Repository
public class PositionRepositoryImpl implements PositionRepository {
    // JPA 구현
}

// application에서 Interface에 의존
package com.tradex.application;
@Service
public class PositionService {
    private final PositionRepository positionRepository; // Interface
}
```

---

## 📊 도메인 모델 핵심 개념

### 1. 거래 계층 구조
```
Position (포지션)
  └── Order (주문)
       └── Trade/Fill (체결)
```

### 2. 핵심 도메인 엔티티

| 엔티티 | 주요 책임 |
|--------|----------|
| User | 사용자 계정, 인증, 프로필 관리 |
| ExchangeConnection | 거래소 API 연동 정보 (API Key 등) |
| Position | 포지션 생성/조회/종료, 실시간 데이터 |
| Order | 주문 정보 (진입, 손절, 익절) |
| Trade | 체결 내역 |
| TradingJournal | 매매 일지 (사전 시나리오 + 사후 복기) |
| Strategy | 전략 정의 및 성과 분석 데이터 |
| RiskPattern | 리스크 패턴 식별 결과 |
| Notification | 알림 데이터 |
| AIInsight | AI 인사이트 기록 |

### 3. 데이터 흐름
```
Exchange API (외부)
    ↓
ExchangeClient (Infrastructure)
    ↓
Position (Domain Entity)
    ↓
TradingJournal (Domain Entity)
    ↓
┌──────────────────┬─────────────────┐
↓                  ↓                 ↓
Strategy        RiskPattern      AIInsight
(분석)            (분석)           (추천)
    ↓
Notification (알림)
```

---

## 🔐 보안 정책

### API Key 관리
- AES-256 암호화 저장
- 환경 변수로 암호화 키 관리
- API Key는 절대 로그에 노출 금지

### 인증/인가
- JWT 기반 인증
- Role-based Access Control (RBAC)
- Refresh Token 전략 적용

### 데이터 접근
- 사용자는 자신의 데이터만 접근 가능
- 관리자 권한 분리
- API Rate Limiting 적용

---

## 🧪 테스트 원칙

### 1. 테스트 커버리지 목표
- Unit Test: 80% 이상
- Integration Test: 핵심 비즈니스 로직 100%
- E2E Test: 주요 사용자 시나리오

### 2. 테스트 작성 규칙
- Given-When-Then 패턴 사용
- 테스트는 독립적이어야 함
- 외부 의존성은 Mocking
- 실제 거래소 API는 테스트에서 제외 (Stub 사용)

### 3. TDD 권장 사항
- 핵심 비즈니스 로직은 TDD로 개발
- 복잡한 계산 로직 (손익비, 승률 등) 우선 적용

---

## 📝 코딩 컨벤션

### Java 코딩 스타일
- Google Java Style Guide 준수
- Checkstyle 설정 적용
- 변수명은 명확하고 의미 있게
- 매직 넘버 사용 금지 (상수로 정의)

### 네이밍 규칙
- Entity: 명사형 (예: `Position`, `TradingJournal`)
- Service: 동사형 메서드 (예: `createPosition()`, `analyzeTradingPattern()`)
- Repository: 표준 CRUD + 도메인별 쿼리 (예: `findByUserIdAndStatus()`)
- DTO: 용도 명시 (예: `PositionCreateRequest`, `PositionResponse`)

### 주석 작성
- Public API는 JavaDoc 필수
- 복잡한 비즈니스 로직은 주석으로 설명
- TODO, FIXME 태그 활용

---

## 🔄 API 설계 원칙

### RESTful API 가이드라인
- HTTP Method 적절히 사용 (GET, POST, PUT, PATCH, DELETE)
- 리소스 중심 URL 설계
- 버전 관리: `/api/v1/...`
- 일관된 응답 형식

### 응답 형식
```json
{
  "success": true,
  "data": { },
  "error": null,
  "timestamp": "2026-01-13T10:00:00Z"
}
```

### 에러 처리
- HTTP 상태 코드 적절히 사용
- 에러 메시지는 사용자 친화적으로
- 개발자를 위한 에러 코드 제공
- Stack Trace는 로그에만 기록

---

## 🔗 서비스 간 통신 전략

### 1. 직접 메서드 호출 (기본)
```java
@Service
@RequiredArgsConstructor
public class RiskAnalysisService {
    private final JournalService journalService;  // 같은 레이어의 다른 Service
    
    public RiskReport analyzeRisk(Long userId) {
        // 직접 호출 - 간단하고 빠름
        List<Journal> journals = journalService.getJournalsByUserId(userId);
        return calculateRisk(journals);
    }
}
```

**장점**: 간단, 빠름, 트랜잭션 보장  
**사용 시기**: 대부분의 경우 (기본 전략)

---

### 2. 이벤트 기반 통신 (선택적)
```java
// PositionService
@Service
public class PositionService {
    private final ApplicationEventPublisher eventPublisher;
    
    public void closePosition(Long positionId) {
        Position position = close(positionId);
        
        // 이벤트 발행 (비동기)
        eventPublisher.publishEvent(new PositionClosedEvent(position));
    }
}

// JournalService에서 이벤트 리스닝
@Component
public class JournalEventListener {
    
    @EventListener
    @Async
    public void onPositionClosed(PositionClosedEvent event) {
        journalService.createPostTradeJournal(event.getPosition());
    }
}

// NotificationService에서도 이벤트 리스닝
@Component
public class NotificationEventListener {
    
    @EventListener
    @Async
    public void onPositionClosed(PositionClosedEvent event) {
        notificationService.sendPositionClosedNotification(event);
    }
}
```

**장점**: 완전한 디커플링, 확장 용이  
**단점**: 디버깅 어려움, 트랜잭션 관리 복잡  
**사용 시기**:
- 비동기 처리가 필요한 경우 (알림, 로깅)
- 한 액션에 여러 서비스가 반응해야 하는 경우

---

### 서비스 간 통신 가이드라인

| 상황 | 권장 방법 | 예시 |
|------|----------|------|
| 같은 트랜잭션 필요 | 직접 호출 | Position 종료 + Journal 생성 |
| 데이터 조회만 필요 | 직접 호출 | Strategy가 Journal 데이터 조회 |
| 비동기 처리 | 이벤트 | Position 종료 → 알림 발송 |
| 여러 서비스가 반응 | 이벤트 | Position 종료 → Journal, Notification, Analytics |

---

## 🗄️ 데이터베이스 원칙

### 데이터베이스 전략
- **단일 데이터베이스** 사용 (PostgreSQL 권장)
- 단일 스키마 구조 (public)
- ACID 트랜잭션 완전 지원

### 테이블 네이밍 규칙
```sql
-- 도메인별 테이블 prefix 사용 (선택)
users
exchange_connections
positions
orders
trades
trading_journals
strategies
risk_patterns
notifications
ai_insights
```

### 스키마 설계
- 정규화 우선, 필요 시 비정규화
- 인덱스 전략 수립 (특히 시계열 데이터)
- Soft Delete 적용 (중요 데이터)
- 감사 컬럼 필수 (created_at, updated_at, created_by)

### 트랜잭션 관리
```java
// ✅ 단일 트랜잭션으로 여러 엔티티 처리
@Transactional
public void closePositionWithJournal(Long positionId) {
    // 1. Position 종료
    Position position = positionService.closePosition(positionId);
    
    // 2. Journal 자동 생성
    journalService.createPostTradeJournal(position);
    
    // 3. Strategy 데이터 업데이트
    strategyService.updateAnalytics(position);
    
    // 하나라도 실패하면 전체 롤백 - 간단명료!
}
```

### 성능 최적화
- N+1 문제 방지 (Fetch Join 활용)
- Pagination 필수 (대량 데이터 조회 시)
- 읽기 전용 쿼리는 @Transactional(readOnly = true)
- 배치 작업은 청크 단위 처리
- 모듈 간 데이터 조회 시 적절한 인덱스 활용

---

## 🔌 외부 연동 원칙

### 거래소 API 연동
- Circuit Breaker 패턴 적용
- Retry 전략 구현 (Exponential Backoff)
- Rate Limiting 준수
- 연결 실패 시 Graceful Degradation

### 추상화 레이어
```java
public interface ExchangeClient {
    List<Position> fetchPositions(String userId);
    List<Order> fetchOrders(String userId);
    // 거래소별 구현체: BinanceClient, BybitClient, etc.
}
```

---

## 📈 모니터링 및 로깅

### 로깅 전략
- 로그 레벨 적절히 사용 (ERROR, WARN, INFO, DEBUG)
- 구조화된 로깅 (JSON 형식 권장)
- 민감 정보 마스킹
- 요청/응답 로깅 (성능 영향 고려)

### 모니터링 지표
- API 응답 시간
- 거래소 API 호출 성공/실패율
- 데이터 동기화 지연 시간
- 에러 발생률

---

## 🚀 배포 및 운영

### 배포 전략
- **단일 JAR 파일** 배포
- Blue-Green 또는 Rolling 배포
- 헬스체크 엔드포인트 필수 구현

### 환경 분리
- Local, Dev, Staging, Production
- 환경별 설정 파일 분리 (application-{profile}.yml)
- 프로덕션 설정은 암호화

### 애플리케이션 구성
```yaml
# application.yml
spring:
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:local}
  
  # 모듈별 설정 그룹화
  datasource:
    url: ${DB_URL}
    hikari:
      maximum-pool-size: 20  # 모놀리틱이므로 단일 커넥션 풀
  
  jpa:
    properties:
      hibernate:
        default_schema: public
```

### CI/CD
- 자동화된 빌드 및 테스트
- 코드 품질 게이트 (SonarQube 등)
- 단일 파이프라인으로 전체 배포
- 롤백 전략 필수

### 데이터베이스 마이그레이션
- Flyway 또는 Liquibase 사용
- 롤백 가능한 마이그레이션 스크립트
- 프로덕션 적용 전 검증 필수
- 모듈별 스키마 마이그레이션 스크립트 분리

---

## 📚 문서화 정책

### 필수 문서
- API 명세서 (Swagger/OpenAPI)
- ERD (Entity Relationship Diagram)
- 아키텍처 다이어그램
- 배포 가이드

### 문서 관리
- README.md는 항상 최신 상태 유지
- 주요 의사결정은 ADR(Architecture Decision Record)로 기록
- 변경 사항은 CHANGELOG.md에 기록

---

## 🤝 협업 원칙

### Git 전략
- Git Flow 또는 GitHub Flow 사용
- 커밋 메시지 컨벤션 준수
    - feat: 새로운 기능
    - fix: 버그 수정
    - refactor: 리팩토링
    - docs: 문서 수정
    - test: 테스트 코드
- Pull Request 기반 코드 리뷰

### 코드 리뷰
- 모든 코드는 리뷰 후 머지
- 건설적이고 존중하는 피드백
- 리뷰 체크리스트 활용

---

## 🔧 성능 최적화 가이드

### 모놀리틱 아키텍처의 장점 활용
```java
// ✅ 로컬 메서드 호출 (네트워크 오버헤드 없음)
@Service
@RequiredArgsConstructor
public class StrategyAnalysisService {
    private final JournalService journalService;
    private final PositionService positionService;
    
    public StrategyReport analyzeStrategy(StrategyRequest request) {
        // 모두 같은 JVM 내에서 실행 - 매우 빠름!
        List<Journal> journals = journalService.getJournals(request);
        List<Position> positions = positionService.getPositions(request);
        return analyze(journals, positions);
    }
}
```

### 캐싱 전략
- Spring Cache Abstraction 활용
- 로컬 캐시 우선 (Caffeine)
- 필요 시 Redis 추가 (분산 환경 대비)
- 모듈 간 데이터 공유에 캐시 적극 활용

### 비동기 처리
- @Async 적절히 사용
- 대용량 데이터 처리는 배치 작업으로
- 필요 시 메시지 큐 활용 (RabbitMQ, Kafka)
- 모듈 간 이벤트 기반 통신 고려

### 데이터베이스 최적화
```java
// ✅ 한 번의 쿼리로 여러 엔티티 조인
@Query("""
    SELECT p, j, s 
    FROM Position p
    LEFT JOIN FETCH p.journal j
    LEFT JOIN FETCH p.strategyData s
    WHERE p.userId = :userId
""")
List<PositionAggregate> findPositionsWithDetails(@Param("userId") Long userId);
```

---

## ⚠️ 제약 사항 및 주의사항

### 거래소 연동
- 국내 거래소(업비트, 빗썸)는 선물 거래 미지원으로 제외
- 해외 거래소 중심 (Binance, Bybit, OKX 등)

### 데이터 정확성
- 포지션/주문/체결 데이터는 거래소 API가 Source of Truth
- 동기화 주기 및 실패 처리 전략 필수

### AI 분석 정확도
- 시장 상황 판단 로직의 한계 인지
- 사용자에게 판단 기준 명시

---

## 📅 마일스톤

### Phase 1: 기본 인프라 (우선순위 1-2)
- 로그인/회원가입
- 거래소 API 연동 기본 구조
- 홈 화면 데이터 제공
- Tradex AI 기본 연동

### Phase 2: 핵심 기능 (우선순위 3-4)
- 매매 일지 관리
- 차트 분석 연동
- 포지션 자동 수집 및 관리

### Phase 3: 분석 기능 (우선순위 5)
- 전략 분석
- 리스크 패턴 분석
- 수익 관리

### Phase 4: 부가 기능 (우선순위 6-8)
- 수신함
- 매매원칙
- 설정

---

## 🎓 학습 자료 및 참고 문서

### Spring Boot 4.0
- [Spring Boot 4.0 공식 문서](https://spring.io/projects/spring-boot)
- [Spring Boot Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)

### 트레이딩 도메인
- 선물 거래 용어 및 개념
- 거래소 API 문서 (Binance, Bybit)

### 아키텍처 패턴
- Domain-Driven Design (DDD)
- Clean Architecture
- Layered Architecture
- Event-Driven Architecture (서비스 간 통신)

---

## 🔮 향후 확장 전략

### 성능 최적화 우선

현재 구조에서는 마이크로서비스 전환보다 **성능 최적화**가 우선입니다.

#### 확장 시나리오
1. **수평 확장 (Scale-Out)**
    - 로드 밸런서 + 다중 인스턴스 배포
    - 세션 없는 Stateless 설계 유지

2. **캐싱 전략**
    - Redis 도입 (분산 환경 대비)
    - 자주 조회되는 데이터 캐싱

3. **읽기/쓰기 분리**
    - Read Replica 구성
    - CQRS 패턴 부분 적용 (필요 시)

4. **배치 최적화**
    - 대용량 데이터 처리 비동기화
    - 스케줄링 작업 최적화

### 유지해야 할 이유
- ✅ 빠른 개발 속도 (배포 파이프라인 1개)
- ✅ 간단한 디버깅 (로컬에서 전체 흐름 추적)
- ✅ 트랜잭션 관리 용이 (ACID 보장)
- ✅ 낮은 운영 복잡도
- ✅ 1인 개발자에게 최적

---

## ✅ Definition of Done

### 기능 완료 기준
- [ ] 단위 테스트 작성 및 통과
- [ ] 통합 테스트 작성 및 통과
- [ ] 코드 리뷰 완료
- [ ] API 문서 업데이트
- [ ] 성능 테스트 통과 (응답 시간 < 200ms)
- [ ] 보안 검토 완료
- [ ] 배포 가이드 업데이트

---

## 📞 연락 및 의사결정

### 기술적 의사결정
- 주요 아키텍처 변경은 팀 논의 필수
- ADR로 문서화

### 우선순위 조정
- 기획서의 우선순위 준수
- 변경 시 PM과 협의

---

**Last Updated**: 2026-01-13  
**Version**: 1.0.0  
**Maintained by**: Backend Development Team