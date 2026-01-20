package hello.tradexserver.service;

import hello.tradexserver.config.SolapiProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.nurigo.sdk.NurigoApp;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.request.SingleMessageSendingRequest;
import net.nurigo.sdk.message.response.SingleMessageSentResponse;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmsService {

    private final SolapiProperties solapiProperties;
    private DefaultMessageService messageService;

    @PostConstruct
    public void init() {
        this.messageService = NurigoApp.INSTANCE.initialize(
                solapiProperties.getApiKey(),
                solapiProperties.getApiSecret(),
                "https://api.solapi.com"
        );
    }

    public void sendVerificationCode(String phoneNumber, String code) {
        Message message = new Message();
        message.setFrom(solapiProperties.getSenderNumber());
        message.setTo(phoneNumber);
        message.setText("[Tradex] 인증번호는 [" + code + "]입니다. 5분 내로 입력해주세요.");

        try {
            SingleMessageSentResponse response = messageService.sendOne(new SingleMessageSendingRequest(message));
            log.info("SMS sent successfully. MessageId: {}", response.getMessageId());
        } catch (Exception e) {
            log.error("Failed to send SMS to {}: {}", phoneNumber, e.getMessage());
            throw new RuntimeException("SMS 발송에 실패했습니다.", e);
        }
    }
}
