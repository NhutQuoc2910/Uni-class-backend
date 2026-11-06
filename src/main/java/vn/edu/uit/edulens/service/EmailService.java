
package vn.edu.uit.edulens.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import vn.edu.uit.edulens.entity.enums.OtpPurpose;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    
    @Value("${OTP_LOG_CODE:false}")
    private boolean logOtpCode;

    @Async
    public void sendOtpEmail(
            String toEmail,
            String otpCode,
            OtpPurpose purpose
    ) {
        if (fromEmail == null || fromEmail.isBlank()) {
            log.warn("Chưa cấu hình spring.mail.username. [DEVELOPMENT ONLY] OTP ({}) của {}: {}",
                    purpose, toEmail, otpCode);
            return;
        }

        String subject;
        String description;

        switch (purpose) {
            case REGISTER -> {
                subject = String.format("%s là mã xác thực UniClass của bạn", otpCode);
                description = """
                        Bạn đã yêu cầu đăng ký tài khoản UniClass.
                        Hãy sử dụng mã OTP bên dưới để xác minh địa chỉ email.
                        """;
            }

            case FORGOT_PASSWORD -> {
                subject = String.format("%s là mã xác thực đặt lại mật khẩu UniClass của bạn", otpCode);
                description = """
                        Bạn đã yêu cầu đặt lại mật khẩu tài khoản UniClass.
                        Hãy sử dụng mã OTP bên dưới để tiếp tục.
                        """;
            }

            default -> throw new IllegalArgumentException(
                    "Mục đích OTP không hợp lệ"
            );
        }

        String preheaderText = String.format("%s là mã xác thực của bạn. Không chia sẻ mã này cho bất kỳ ai.", otpCode);

        String htmlContent = """
                <div style="display: none; max-height: 0px; overflow: hidden; mso-hide: all; font-size: 1px; line-height: 1px; max-width: 0px; opacity: 0;">
                    %s&#847; &zwnj;&nbsp;&#8199;&shy;&#847; &zwnj;&nbsp;&#8199;&shy;&#847; &zwnj;&nbsp;&#8199;&shy;&#847; &zwnj;&nbsp;&#8199;&shy;&#847; &zwnj;&nbsp;&#8199;&shy;
                </div>
                <div style="font-family: Arial, sans-serif;
                            max-width: 600px; margin: 0 auto;
                            padding: 20px; border: 1px solid #e0e0e0;
                            border-radius: 8px;">

                    <h2 style="color: #1976D2; text-align: center;">
                        UniClass
                    </h2>

                    <p>Xin chào,</p>

                    <p>%s</p>

                    <div style="background-color: #f1f6fd;
                                border: 1px dashed #1976D2;
                                border-radius: 6px; padding: 15px;
                                text-align: center; margin: 20px 0;">

                        <span style="font-size: 32px;
                                     font-weight: bold;
                                     letter-spacing: 6px;
                                     color: #1976D2;">
                            %s
                        </span>
                    </div>

                    <p style="color: #666; font-size: 13px;">
                        Mã OTP có hiệu lực trong vòng
                        <strong>5 phút</strong>.
                        Vui lòng không chia sẻ mã này cho bất kỳ ai.
                    </p>

                    <hr style="border: none;
                               border-top: 1px solid #eee;
                               margin: 20px 0;" />

                    <p style="font-size: 12px; color: #999;
                              text-align: center;">
                        Đây là email tự động từ UniClass.
                        Vui lòng không phản hồi.
                    </p>
                </div>
                """.formatted(preheaderText, description, otpCode);

        try {
            MimeMessage message = mailSender.createMimeMessage();

            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, "UniClass");
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);

            log.info("Đã gửi email OTP thành công tới: {}", toEmail);

            if (logOtpCode) {
                log.warn("[DEVELOPMENT ONLY] OTP của {}: {}",
                        toEmail, otpCode);
            }

        } catch (Exception e) {
            log.error("Không thể gửi email OTP tới {}: {}",
                    toEmail, e.getMessage());
            if (logOtpCode) {
                log.warn("[DEVELOPMENT ONLY - SMTP FALLBACK] OTP ({}) của {}: {}",
                        purpose, toEmail, otpCode);
            }
        }
    }
}
