package hello.tradexserver.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.password-reset.base-url}")
    private String passwordResetBaseUrl;

    public void sendPasswordResetEmail(String toEmail, String token) {
        String resetUrl = passwordResetBaseUrl + "?token=" + token;
        String subject = "[Tradex] 비밀번호 재설정 안내";
        String content = buildPasswordResetEmailContent(resetUrl);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(content, true);

            mailSender.send(message);
            log.info("Password reset email sent to: {}", toEmail);
        } catch (MessagingException e) {
            log.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("이메일 발송에 실패했습니다.", e);
        }
    }

    private String buildPasswordResetEmailContent(String resetUrl) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
            </head>
            <body style="font-family: 'Apple SD Gothic Neo', 'Malgun Gothic', sans-serif; line-height: 1.6; color: #333;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <h2 style="color: #2563eb;">Tradex 비밀번호 재설정</h2>
                    <p>안녕하세요,</p>
                    <p>비밀번호 재설정을 요청하셨습니다. 아래 버튼을 클릭하여 새 비밀번호를 설정해주세요.</p>
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="%s"
                           style="background-color: #2563eb; color: white; padding: 12px 30px;
                                  text-decoration: none; border-radius: 5px; display: inline-block;">
                            비밀번호 재설정
                        </a>
                    </div>
                    <p style="color: #666; font-size: 14px;">
                        이 링크는 1시간 동안 유효합니다.<br>
                        본인이 요청하지 않았다면 이 이메일을 무시해주세요.
                    </p>
                    <hr style="border: none; border-top: 1px solid #eee; margin: 20px 0;">
                    <p style="color: #999; font-size: 12px;">
                        본 메일은 발신 전용입니다.<br>
                        © Tradex. All rights reserved.
                    </p>
                </div>
            </body>
            </html>
            """.formatted(resetUrl);
    }
}
