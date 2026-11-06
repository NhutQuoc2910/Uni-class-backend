package vn.edu.uit.edulens.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.uit.edulens.dto.request.*;
import vn.edu.uit.edulens.dto.response.AuthResponse;
import vn.edu.uit.edulens.dto.response.UserResponse;
import vn.edu.uit.edulens.entity.User;
import vn.edu.uit.edulens.entity.enums.UserStatus;
import vn.edu.uit.edulens.exception.AppException;
import vn.edu.uit.edulens.exception.ErrorCode;
import vn.edu.uit.edulens.repository.UserRepository;
import vn.edu.uit.edulens.security.JwtTokenProvider;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import vn.edu.uit.edulens.entity.PendingRegistration;
import vn.edu.uit.edulens.entity.enums.OtpPurpose;
import vn.edu.uit.edulens.entity.enums.Role;
import vn.edu.uit.edulens.repository.PendingRegistrationRepository;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PendingRegistrationRepository pendingRegistrationRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final SecureRandom secureRandom = new SecureRandom();
    @Value("${jwt.otp-hmac-secret:${OTP_HMAC_SECRET:UniClassDefaultHmacSecretKey2026Fixed}}")
    private String otpHmacSecret;
   
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String identifier = request.getIdentifier().trim();
        User user = userRepository.findByEmailOrPhone(identifier.toLowerCase(), identifier)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (user.getStatus() == UserStatus.LOCKED) {
            throw new AppException(ErrorCode.ACCOUNT_LOCKED);
        }

        String token = jwtTokenProvider.generateToken(user);
        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .user(UserResponse.fromUser(user))
                .build();
    }

    @Transactional
    public String forgotPassword(ForgotPasswordRequest request) {

        String email = request.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new AppException(ErrorCode.USER_NOT_FOUND)
                );

        Instant now = Instant.now();

        // Giới hạn thời gian gửi lại OTP: tối thiểu 60 giây
        if (user.getOtpLastSentAt() != null &&
                now.isBefore(user.getOtpLastSentAt().plusSeconds(60))) {

            throw new AppException(ErrorCode.OTP_RESEND_TOO_SOON);
        }

        // Tạo OTP ngẫu nhiên gồm 6 chữ số
        String otpCode = String.format(
                "%06d",
                secureRandom.nextInt(1_000_000)
        );

        // Lưu HMAC thay vì OTP gốc
        user.setOtpCode(
                hashOtp(email, OtpPurpose.FORGOT_PASSWORD, otpCode)
        );

        // Thiết lập mục đích và thời hạn OTP
        user.setOtpPurpose(OtpPurpose.FORGOT_PASSWORD);
        user.setOtpExpiresAt(
                now.plus(Duration.ofMinutes(5))
        );

        // Reset số lần nhập OTP sai khi cấp mã mới
        user.setOtpFailedAttempts(0);

        // Lưu thời điểm gửi OTP để giới hạn gửi lại
        user.setOtpLastSentAt(now);

        userRepository.save(user);

        // Gửi OTP qua email
        emailService.sendOtpEmail(
                email,
                otpCode,
                OtpPurpose.FORGOT_PASSWORD
        );

        return "Mã OTP gồm 6 chữ số đã được gửi tới email của bạn";
    }
    @Transactional(noRollbackFor = AppException.class)
    public boolean verifyOtp(VerifyOtpRequest request) {

        String email = request.getEmail().trim().toLowerCase();
        String inputOtp = request.getOtpCode().trim();

        // LUỒNG 1: XÁC THỰC ĐĂNG KÝ
        if (request.getPurpose() == OtpPurpose.REGISTER) {

            PendingRegistration pending = pendingRegistrationRepository
                    .findByEmail(email)
                    .orElseThrow(() ->
                            new AppException(ErrorCode.REGISTRATION_DATA_NOT_FOUND)
                    );

            // Kiểm tra OTP đã tồn tại
            if (pending.getOtpHash() == null ||
                    pending.getOtpExpiresAt() == null) {
                throw new AppException(ErrorCode.OTP_NOT_FOUND);
            }

            // Kiểm tra hết hạn
            if (!Instant.now().isBefore(pending.getOtpExpiresAt())) {
                pendingRegistrationRepository.delete(pending);
                throw new AppException(ErrorCode.OTP_EXPIRED);
            }

            // Giới hạn 5 lần nhập sai
            if (pending.getFailedAttempts() >= 5) {
                pendingRegistrationRepository.delete(pending);
                throw new AppException(ErrorCode.OTP_MAX_ATTEMPTS_EXCEEDED);
            }

            // Kiểm tra OTP
            if (!matchesOtp(
                    email,
                    OtpPurpose.REGISTER,
                    inputOtp,
                    pending.getOtpHash()
            )) {
                pending.setFailedAttempts(
                        pending.getFailedAttempts() + 1
                );

                if (pending.getFailedAttempts() >= 5) {
                    pendingRegistrationRepository.delete(pending);
                    throw new AppException(
                            ErrorCode.OTP_MAX_ATTEMPTS_EXCEEDED
                    );
                }

                pendingRegistrationRepository.save(pending);
                throw new AppException(ErrorCode.INVALID_OTP);
            }

            // Kiểm tra lại email và số điện thoại trước khi tạo User
            if (userRepository.existsByEmail(email)) {
                pendingRegistrationRepository.delete(pending);
                throw new AppException(ErrorCode.USER_ALREADY_EXISTS);
            }

            if (pending.getPhone() != null &&
                    !pending.getPhone().isBlank() &&
                    userRepository.existsByPhone(pending.getPhone())) {
                pendingRegistrationRepository.delete(pending);
                throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);
            }

            // Chỉ tạo tài khoản sau khi OTP hợp lệ
            User user = User.builder()
                    .email(pending.getEmail())
                    .phone(pending.getPhone())
                    .fullName(pending.getFullName())
                    .passwordHash(pending.getPasswordHash())
                    .role(pending.getRole())
                    .status(UserStatus.ACTIVE)
                    .build();

            userRepository.save(user);

            // Xóa dữ liệu đăng ký tạm
            pendingRegistrationRepository.delete(pending);

            log.info("Registered verified user: {}", email);

            return true;
        }

        // LUỒNG 2: QUÊN MẬT KHẨU (tạm giữ logic cũ)
        if (request.getPurpose() == OtpPurpose.FORGOT_PASSWORD) {

            User user = userRepository.findByEmail(email)
                    .orElseThrow(() ->
                            new AppException(ErrorCode.USER_NOT_FOUND)
                    );

            validateOtp(
                user,
                inputOtp,
                OtpPurpose.FORGOT_PASSWORD
            );

            return true;
        }

        throw new AppException(ErrorCode.INVALID_OTP_PURPOSE);
    }
    @Transactional(noRollbackFor = AppException.class)
    public String resetPassword(ResetPasswordRequest request) {

        String email = request.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new AppException(ErrorCode.USER_NOT_FOUND)
                );

        // Xác thực OTP dành riêng cho chức năng quên mật khẩu
        validateOtp(
                user,
                request.getOtpCode().trim(),
                OtpPurpose.FORGOT_PASSWORD
        );

        // Cập nhật mật khẩu mới bằng BCrypt
        user.setPasswordHash(
                passwordEncoder.encode(request.getNewPassword())
        );

        // Xóa thông tin OTP sau khi đặt lại mật khẩu thành công
        user.setOtpCode(null);
        user.setOtpExpiresAt(null);
        user.setOtpPurpose(null);

        // Reset số lần nhập OTP sai
        user.setOtpFailedAttempts(0);

        // Lưu thay đổi
        userRepository.save(user);

        log.info("Password reset successfully for user: {}", email);

        return "Đặt lại mật khẩu thành công";
    }
    private boolean validateOtp(User user, String inputOtp, OtpPurpose expectedPurpose) {

        // 1. Kiểm tra OTP có tồn tại và đủ thông tin không
        if (user.getOtpCode() == null
                || user.getOtpExpiresAt() == null
                || user.getOtpPurpose() == null) {
            throw new AppException(ErrorCode.OTP_NOT_FOUND);
        }

        // 2. Kiểm tra OTP có đúng mục đích không
        if (user.getOtpPurpose() != expectedPurpose) {
            throw new AppException(ErrorCode.INVALID_OTP_PURPOSE);
        }

        // 3. Kiểm tra OTP đã hết hạn chưa
        if (!Instant.now().isBefore(user.getOtpExpiresAt())) {

            // Xóa OTP hết hạn
            user.setOtpCode(null);
            user.setOtpExpiresAt(null);
            user.setOtpPurpose(null);
            user.setOtpFailedAttempts(0);

            throw new AppException(ErrorCode.OTP_EXPIRED);
        }

        // 4. Kiểm tra số lần nhập sai đã đạt giới hạn chưa
        if (user.getOtpFailedAttempts() >= 5) {

            // Vô hiệu hóa OTP
            user.setOtpCode(null);
            user.setOtpExpiresAt(null);
            user.setOtpPurpose(null);
            user.setOtpFailedAttempts(0);

            throw new AppException(ErrorCode.OTP_MAX_ATTEMPTS_EXCEEDED);
        }

        // 5. So sánh OTP nhập vào với HMAC đã lưu trong database
       if (!matchesOtp(
            user.getEmail(),
            expectedPurpose,
            inputOtp,
            user.getOtpCode()
        )) {

            // Tăng số lần nhập sai
            int failedAttempts = user.getOtpFailedAttempts() + 1;
            user.setOtpFailedAttempts(failedAttempts);

            // Nếu đã sai đủ 5 lần thì vô hiệu hóa OTP
            if (failedAttempts >= 5) {
                user.setOtpCode(null);
                user.setOtpExpiresAt(null);
                user.setOtpPurpose(null);
                user.setOtpFailedAttempts(0);

                throw new AppException(ErrorCode.OTP_MAX_ATTEMPTS_EXCEEDED);
            }

            throw new AppException(ErrorCode.INVALID_OTP);
        }

        // 6. OTP hợp lệ
        return true;
    }
    private String hashOtp(
            String email,
            OtpPurpose purpose,
            String otpCode
    ) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");

            SecretKeySpec secretKey = new SecretKeySpec(
                    otpHmacSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );

            mac.init(secretKey);

            String data = email.trim().toLowerCase()
                    + "|" + purpose.name()
                    + "|" + otpCode.trim();

            byte[] hashBytes = mac.doFinal(
                    data.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hashBytes);

        } catch (Exception e) {
            throw new IllegalStateException("Không thể tạo HMAC OTP", e);
        }
    }
    private boolean matchesOtp(
            String email,
            OtpPurpose purpose,
            String inputOtp,
            String storedHash
    ) {
        if (storedHash == null || storedHash.length() != 64) {
            return false;
        }

        try {
            byte[] inputBytes = HexFormat.of().parseHex(
                    hashOtp(email, purpose, inputOtp)
            );

            byte[] storedBytes = HexFormat.of().parseHex(storedHash);

            return MessageDigest.isEqual(inputBytes, storedBytes);

        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    @Transactional
    public String sendOtp(SendOtpRequest request) {

        // Chỉ xử lý OTP đăng ký ở hàm này
        if (request.getPurpose() != OtpPurpose.REGISTER) {
            throw new AppException(ErrorCode.INVALID_OTP_PURPOSE);
        }

        String email = request.getEmail().trim().toLowerCase();

        // Kiểm tra email đã đăng ký chưa
        if (userRepository.existsByEmail(email)) {
            throw new AppException(ErrorCode.USER_ALREADY_EXISTS);
        }

        // Chuẩn hóa và kiểm tra số điện thoại
        String phone = request.getPhone();

        if (phone != null && !phone.isBlank()) {
            phone = phone.trim();

            if (userRepository.existsByPhone(phone)) {
                throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);
            }
        } else {
            phone = null;
        }

        // Kiểm tra thông tin đăng ký
        if (request.getFullName() == null || request.getFullName().isBlank()
                || request.getPassword() == null || request.getPassword().isBlank()
                || request.getPassword().length() < 6
                || request.getRole() == null) {
            throw new IllegalArgumentException("Thông tin đăng ký không hợp lệ");
        }

        // Không cho phép tự đăng ký ADMIN
        Role role = request.getRole();

        if (role != Role.ROLE_STUDENT && role != Role.ROLE_LECTURER) {
            throw new AppException(ErrorCode.INVALID_OTP_PURPOSE);
        }

        // Lấy hoặc tạo dữ liệu đăng ký tạm
        PendingRegistration pending = pendingRegistrationRepository
                .findByEmail(email)
                .orElse(null);

        if (pending == null) {
            pending = PendingRegistration.builder()
                    .email(email)
                    .build();
        }

        pending.setPhone(phone);
        pending.setFullName(request.getFullName().trim());
        pending.setPasswordHash(
                passwordEncoder.encode(request.getPassword())
        );
        pending.setRole(role);

        // Nếu đã gửi OTP trong vòng 60s và mã vẫn còn hiệu lực, cập nhật thông tin và tái sử dụng OTP để tránh spam email
        if (pending.getLastSentAt() != null
                && Instant.now().isBefore(pending.getLastSentAt().plusSeconds(60))
                && pending.getOtpHash() != null
                && pending.getOtpExpiresAt() != null
                && Instant.now().isBefore(pending.getOtpExpiresAt())) {
            pendingRegistrationRepository.save(pending);
            return "Mã OTP đã được gửi trước đó vào email của bạn. Vui lòng kiểm tra hộp thư.";
        }

        // Tạo OTP 6 chữ số mới
        String otpCode = String.format(
                "%06d",
                secureRandom.nextInt(1_000_000)
        );

        pending.setOtpHash(
                hashOtp(email, OtpPurpose.REGISTER, otpCode)
        );
        pending.setOtpExpiresAt(Instant.now().plus(Duration.ofMinutes(5)));
        pending.setFailedAttempts(0);
        pending.setLastSentAt(Instant.now());

        pendingRegistrationRepository.save(pending);

        // Gửi OTP qua email
        emailService.sendOtpEmail(
                email,
                otpCode,
                OtpPurpose.REGISTER
        );

        return "Mã OTP đăng ký đã được gửi tới email của bạn";
    }
}
