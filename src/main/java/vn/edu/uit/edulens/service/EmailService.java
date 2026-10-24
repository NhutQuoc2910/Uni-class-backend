package vn.edu.uit.edulens.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Async
    public void sendOtpEmail(String toEmail, String otpCode) {
        // Luôn ghi log OTP ra Console để thuận tiện kiểm thử trên máy local/dev
        log.info("==================================================");
        log.info(">> [OTP EMAIL SERVICE] Gửi mã xác thực tới: {}", toEmail);
        log.info(">> [OTP CODE]: {}", otpCode);
        log.info(">> Thời hạn mã: 5 phút");
        log.info("==================================================");

        if (fromEmail == null || fromEmail.isBlank()) {
            log.warn("Chưa cấu hình spring.mail.username. Email thật sẽ không được gửi (dùng mã OTP ở log trên để test).");
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, "Uni-Class EduLens");
            helper.setTo(toEmail);
            helper.setSubject("[EduLens] Mã xác thực OTP đặt lại mật khẩu");

            String htmlContent = """
                    <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 8px;">
                        <h2 style="color: #1976D2; text-align: center;">Uni-Class EduLens</h2>
                        <p>Xin chào,</p>
                        <p>Bạn đã gửi yêu cầu đặt lại mật khẩu cho tài khoản tại hệ thống EduLens. Dưới đây là mã xác thực OTP của bạn:</p>
                        <div style="background-color: #f1f6fd; border: 1px dashed #1976D2; border-radius: 6px; padding: 15px; text-align: center; margin: 20px 0;">
                            <span style="font-size: 32px; font-weight: bold; letter-spacing: 6px; color: #1976D2;">%s</span>
                        </div>
                        <p style="color: #666; font-size: 13px;">Mã này có hiệu lực trong vòng <strong>5 phút</strong>. Vui lòng không chia sẻ mã này cho bất kỳ ai.</p>
                        <hr style="border: none; border-top: 1px solid #eee; margin: 20px 0;" />
                        <p style="font-size: 12px; color: #999; text-align: center;">Đây là email tự động từ hệ thống Uni-Class EduLens, vui lòng không phản hồi.</p>
                    </div>
                    """.formatted(otpCode);

            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Đã gửi email OTP thành công tới: {}", toEmail);
        } catch (Exception e) {
            log.error("Không thể gửi email OTP tới {}: {}", toEmail, e.getMessage());
        }
    }
}
