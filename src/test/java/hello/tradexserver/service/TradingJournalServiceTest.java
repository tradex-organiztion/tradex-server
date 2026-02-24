package hello.tradexserver.service;

import hello.tradexserver.domain.*;
import hello.tradexserver.domain.enums.PositionSide;
import hello.tradexserver.domain.enums.PositionStatus;
import hello.tradexserver.dto.request.JournalRequest;
import hello.tradexserver.dto.request.PrincipleCheckRequest;
import hello.tradexserver.dto.response.JournalDetailResponse;
import hello.tradexserver.dto.response.PrincipleCheckResponse;
import hello.tradexserver.exception.BusinessException;
import hello.tradexserver.exception.ErrorCode;
import hello.tradexserver.repository.OrderRepository;
import hello.tradexserver.repository.TradingJournalRepository;
import hello.tradexserver.repository.TradingPrincipleCheckRepository;
import hello.tradexserver.repository.TradingPrincipleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TradingJournalService 단위 테스트")
class TradingJournalServiceTest {

    @Mock private TradingJournalRepository tradingJournalRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private TradingPrincipleRepository tradingPrincipleRepository;
    @Mock private TradingPrincipleCheckRepository tradingPrincipleCheckRepository;

    @InjectMocks private TradingJournalService tradingJournalService;

    private static final Long USER_ID = 1L;
    private static final Long JOURNAL_ID = 10L;
    private static final Long POSITION_ID = 100L;

    private User testUser;
    private Position testPosition;
    private TradingJournal testJournal;

    @BeforeEach
    void setUp() {
        testUser = User.builder().email("test@test.com").username("testUser").build();
        ReflectionTestUtils.setField(testUser, "id", USER_ID);

        testPosition = Position.builder()
                .user(testUser).symbol("BTCUSDT").side(PositionSide.LONG)
                .avgEntryPrice(new BigDecimal("40000")).currentSize(BigDecimal.ZERO)
                .entryTime(LocalDateTime.now().minusHours(2)).exitTime(LocalDateTime.now())
                .status(PositionStatus.CLOSED).build();
        ReflectionTestUtils.setField(testPosition, "id", POSITION_ID);

        testJournal = TradingJournal.builder()
                .user(testUser).position(testPosition)
                .entryReason("지지선 근처 롱 진입")
                .targetPrice(new BigDecimal("45000"))
                .stopLoss(new BigDecimal("38000"))
                .build();
        ReflectionTestUtils.setField(testJournal, "id", JOURNAL_ID);
    }

    // ─── 헬퍼 메서드 ──────────────────────────────────────────────────────────

    private TradingPrinciple principle(Long id, String content) {
        TradingPrinciple p = TradingPrinciple.builder().user(testUser).content(content).build();
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private TradingPrincipleCheck check(TradingJournal journal, TradingPrinciple principle, boolean isChecked) {
        TradingPrincipleCheck c = TradingPrincipleCheck.builder()
                .tradingJournal(journal).tradingPrinciple(principle).isChecked(isChecked).build();
        ReflectionTestUtils.setField(c, "id", principle.getId() * 100);
        return c;
    }

    // ─── getDetail 테스트 ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("getDetail - 매매일지 상세 조회")
    class GetDetail {

        @Test
        @DisplayName("체크 기록 없는 원칙은 isChecked=false로 반환")
        void 체크기록_없으면_false_기본값() {
            // given
            TradingPrinciple p1 = principle(1L, "원칙1");
            TradingPrinciple p2 = principle(2L, "원칙2");

            given(tradingJournalRepository.findByIdAndUserId(JOURNAL_ID, USER_ID))
                    .willReturn(Optional.of(testJournal));
            given(orderRepository.findByPositionId(POSITION_ID)).willReturn(List.of());
            given(tradingPrincipleRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
                    .willReturn(List.of(p1, p2));
            given(tradingPrincipleCheckRepository.findByTradingJournalId(JOURNAL_ID))
                    .willReturn(List.of());

            // when
            JournalDetailResponse response = tradingJournalService.getDetail(USER_ID, JOURNAL_ID);

            // then
            List<PrincipleCheckResponse> checks = response.getPrincipleChecks();
            assertThat(checks).hasSize(2);
            assertThat(checks).allMatch(pc -> !pc.isChecked());
            assertThat(checks.get(0).getContent()).isEqualTo("원칙1");
            assertThat(checks.get(1).getContent()).isEqualTo("원칙2");
        }

        @Test
        @DisplayName("체크 기록 있으면 isChecked 값 반영, 기록 없는 원칙은 false")
        void 체크기록_있으면_isChecked_반영() {
            // given: p1=체크, p2=미체크, p3=기록없음
            TradingPrinciple p1 = principle(1L, "손절 준수");
            TradingPrinciple p2 = principle(2L, "3연속 손실 중단");
            TradingPrinciple p3 = principle(3L, "계획 외 진입 금지");

            given(tradingJournalRepository.findByIdAndUserId(JOURNAL_ID, USER_ID))
                    .willReturn(Optional.of(testJournal));
            given(orderRepository.findByPositionId(POSITION_ID)).willReturn(List.of());
            given(tradingPrincipleRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
                    .willReturn(List.of(p1, p2, p3));
            given(tradingPrincipleCheckRepository.findByTradingJournalId(JOURNAL_ID))
                    .willReturn(List.of(
                            check(testJournal, p1, true),
                            check(testJournal, p2, false)
                    ));

            // when
            JournalDetailResponse response = tradingJournalService.getDetail(USER_ID, JOURNAL_ID);

            // then
            List<PrincipleCheckResponse> checks = response.getPrincipleChecks();
            assertThat(checks).hasSize(3);
            assertThat(checks.get(0).isChecked()).isTrue();   // p1 체크됨
            assertThat(checks.get(1).isChecked()).isFalse();  // p2 미체크
            assertThat(checks.get(2).isChecked()).isFalse();  // p3 기록 없음 → false
        }

        @Test
        @DisplayName("매매원칙이 없으면 빈 목록 반환")
        void 매매원칙_없으면_빈목록() {
            // given
            given(tradingJournalRepository.findByIdAndUserId(JOURNAL_ID, USER_ID))
                    .willReturn(Optional.of(testJournal));
            given(orderRepository.findByPositionId(POSITION_ID)).willReturn(List.of());
            given(tradingPrincipleRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
                    .willReturn(List.of());
            given(tradingPrincipleCheckRepository.findByTradingJournalId(JOURNAL_ID))
                    .willReturn(List.of());

            // when
            JournalDetailResponse response = tradingJournalService.getDetail(USER_ID, JOURNAL_ID);

            // then
            assertThat(response.getPrincipleChecks()).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 매매일지 조회 시 JOURNAL_NOT_FOUND 예외")
        void 존재하지않는_일지_예외() {
            // given
            given(tradingJournalRepository.findByIdAndUserId(JOURNAL_ID, USER_ID))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> tradingJournalService.getDetail(USER_ID, JOURNAL_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.JOURNAL_NOT_FOUND);
        }
    }

    // ─── update 테스트 ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("update - 매매일지 수정")
    class Update {

        @BeforeEach
        void mockCommonStubs() {
            lenient().when(tradingJournalRepository.save(any())).thenReturn(testJournal);
            lenient().when(orderRepository.findByPositionId(POSITION_ID)).thenReturn(List.of());
            lenient().when(tradingPrincipleRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
                    .thenReturn(List.of());
            lenient().when(tradingPrincipleCheckRepository.findByTradingJournalId(JOURNAL_ID))
                    .thenReturn(List.of());
        }

        @Test
        @DisplayName("새 필드 수정 - targetPrice, stopLoss, entryReason, reviewContent 반영")
        void 새필드_정상_수정() {
            // given
            JournalRequest request = new JournalRequest(
                    null, null, null,
                    new BigDecimal("46000"), new BigDecimal("37000"),
                    "볼린저밴드 하단 지지 확인 후 진입",
                    "95k 돌파 시 분할 익절",
                    null, "손절 기준을 잘 지킴",
                    null, null
            );
            given(tradingJournalRepository.findByIdAndUserId(JOURNAL_ID, USER_ID))
                    .willReturn(Optional.of(testJournal));

            // when
            JournalDetailResponse response = tradingJournalService.update(USER_ID, JOURNAL_ID, request);

            // then: journal.update()가 호출되어 필드가 변경됨
            assertThat(testJournal.getTargetPrice()).isEqualByComparingTo(new BigDecimal("46000"));
            assertThat(testJournal.getStopLoss()).isEqualByComparingTo(new BigDecimal("37000"));
            assertThat(testJournal.getEntryReason()).isEqualTo("볼린저밴드 하단 지지 확인 후 진입");
            assertThat(testJournal.getReviewContent()).isEqualTo("손절 기준을 잘 지킴");
        }

        @Test
        @DisplayName("principleChecks 있으면 기존 삭제 후 새로 저장")
        void principleChecks_upsert_기존삭제후_신규저장() {
            // given
            TradingPrinciple p1 = principle(1L, "원칙1");
            TradingPrinciple p2 = principle(2L, "원칙2");

            JournalRequest request = new JournalRequest(
                    null, null, null, null, null, null, null, null, null,
                    List.of(
                            new PrincipleCheckRequest(1L, true),
                            new PrincipleCheckRequest(2L, false)
                    ),
                    null
            );

            given(tradingJournalRepository.findByIdAndUserId(JOURNAL_ID, USER_ID))
                    .willReturn(Optional.of(testJournal));
            given(tradingPrincipleRepository.getReferenceById(1L)).willReturn(p1);
            given(tradingPrincipleRepository.getReferenceById(2L)).willReturn(p2);

            // when
            tradingJournalService.update(USER_ID, JOURNAL_ID, request);

            // then
            verify(tradingPrincipleCheckRepository).deleteByTradingJournalId(JOURNAL_ID);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<TradingPrincipleCheck>> captor = ArgumentCaptor.forClass(List.class);
            verify(tradingPrincipleCheckRepository).saveAll(captor.capture());

            List<TradingPrincipleCheck> saved = captor.getValue();
            assertThat(saved).hasSize(2);
            assertThat(saved.get(0).isChecked()).isTrue();
            assertThat(saved.get(1).isChecked()).isFalse();
        }

        @Test
        @DisplayName("principleChecks null이면 체크 upsert 스킵")
        void principleChecks_null이면_upsert_스킵() {
            // given
            JournalRequest request = new JournalRequest(
                    null, null, null, null, null, "진입 근거 수정", null, null, null,
                    null, // principleChecks = null
                    null
            );
            given(tradingJournalRepository.findByIdAndUserId(JOURNAL_ID, USER_ID))
                    .willReturn(Optional.of(testJournal));

            // when
            tradingJournalService.update(USER_ID, JOURNAL_ID, request);

            // then: 체크 관련 메서드 미호출
            verify(tradingPrincipleCheckRepository, never()).deleteByTradingJournalId(anyLong());
            verify(tradingPrincipleCheckRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("존재하지 않는 매매일지 수정 시 JOURNAL_NOT_FOUND 예외")
        void 존재하지않는_일지_수정_예외() {
            // given
            given(tradingJournalRepository.findByIdAndUserId(JOURNAL_ID, USER_ID))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> tradingJournalService.update(USER_ID, JOURNAL_ID, new JournalRequest()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.JOURNAL_NOT_FOUND);
        }
    }

    // ─── delete 테스트 ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("delete - 매매일지 삭제")
    class Delete {

        @Test
        @DisplayName("정상 삭제 - repository.delete 호출됨")
        void 정상_삭제() {
            // given
            given(tradingJournalRepository.findByIdAndUserId(JOURNAL_ID, USER_ID))
                    .willReturn(Optional.of(testJournal));

            // when
            tradingJournalService.delete(USER_ID, JOURNAL_ID);

            // then
            verify(tradingJournalRepository).delete(testJournal);
        }

        @Test
        @DisplayName("존재하지 않는 매매일지 삭제 시 JOURNAL_NOT_FOUND 예외")
        void 존재하지않는_일지_삭제_예외() {
            // given
            given(tradingJournalRepository.findByIdAndUserId(JOURNAL_ID, USER_ID))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> tradingJournalService.delete(USER_ID, JOURNAL_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.JOURNAL_NOT_FOUND);
        }
    }
}