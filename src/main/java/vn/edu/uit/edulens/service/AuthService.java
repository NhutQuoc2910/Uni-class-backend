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
    @Value("${OTP_HMAC_SECRET}")
    private String otpHmacSecret;
    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.USER_ALREADY_EXISTS);
        }

        if (request.getPhone() != null && !request.getPhone().isBlank() && userRepository.existsByPhone(request.getPhone())) {
            throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);
        }

        User user = User.builder()
                .email(request.getEmail().trim().toLowerCase())
                .phone(request.getPhone() != null ? request.getPhone().trim() : null)
                .fullName(request.getFullName().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .status(UserStatus.ACTIVE)
                .build();

        User savedUser = userRepository.save(user);
        log.info("Registered new user with email: {} and role: {}", savedUser.getEmail(), savedUser.getRole());
        return UserResponse.fromUser(savedUser);
    }

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

        // Tạo OTP 6 chữ số
        String otpCode = String.format(
                "%06d",
                secureRandom.nextInt(1_000_000)
        );

        // Lưu HMAC thay vì OTP gốc
        user.setOtpCode(hashOtp(otpCode));
        user.setOtpPurpose(OtpPurpose.FORGOT_PASSWORD);
        user.setOtpExpiresAt(
                Instant.now().plus(Duration.ofMinutes(5))
        );

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
            if (!matchesOtp(inputOtp, pending.getOtpHash())) {
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
    @Transactional
    public String resetPassword(ResetPasswordRequest request) {

        String email = request.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new AppException(ErrorCode.USER_NOT_FOUND)
                );

        // Chỉ chấp nhận OTP dùng cho quên mật khẩu
        validateOtp(
                user,
                request.getOtpCode(),
                OtpPurpose.FORGOT_PASSWORD
        );

        // Cập nhật mật khẩu mới
        user.setPasswordHash(
                passwordEncoder.encode(request.getNewPassword())
        );

        // Xóa OTP sau khi đặt lại mật khẩu thành công
        user.setOtpCode(null);
        user.setOtpExpiresAt(null);
        user.setOtpPurpose(null);

        userRepository.save(user);

        log.info("Password reset successfully for user: {}", email);

        return "Đặt lại mật khẩu thành công";
    }

    private void validateOtp(
            User user,
            String inputOtp,
            OtpPurpose expectedPurpose
    ) {
        if (user.getOtpCode() == null
                || user.getOtpExpiresAt() == null
                || user.getOtpPurpose() == null) {
            throw new AppException(ErrorCode.OTP_NOT_FOUND);
        }

        // Kiểm tra OTP có đúng mục đích không
        if (user.getOtpPurpose() != expectedPurpose) {
            throw new AppException(ErrorCode.INVALID_OTP_PURPOSE);
        }

        // Kiểm tra hết hạn
        if (!Instant.now().isBefore(user.getOtpExpiresAt())) {
            throw new AppException(ErrorCode.OTP_EXPIRED);
        }

        // So sánh HMAC
        if (!matchesOtp(inputOtp, user.getOtpCode())) {
            throw new AppException(ErrorCode.INVALID_OTP);
        }
    }
    private String hashOtp(String otpCode) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");

            SecretKeySpec secretKey = new SecretKeySpec(
                    otpHmacSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );

            mac.init(secretKey);

            byte[] hashBytes = mac.doFinal(
                    otpCode.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hashBytes);

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Không thể tạo HMAC cho OTP", e
            );
        }
    }
    private boolean matchesOtp(String inputOtp, String storedHash) {
        String inputHash = hashOtp(inputOtp.trim());

        byte[] inputBytes = HexFormat.of().parseHex(inputHash);
        byte[] storedBytes = HexFormat.of().parseHex(storedHash);

        return MessageDigest.isEqual(inputBytes, storedBytes);
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

        // Kiểm tra thời gian gửi lại OTP: tối thiểu 60 giây
        PendingRegistration pending = pendingRegistrationRepository
                .findByEmail(email)
                .orElse(null);

        if (pending != null && pending.getLastSentAt() != null) {
            Instant resendAllowedAt = pending.getLastSentAt()
                    .plusSeconds(60);

            if (Instant.now().isBefore(resendAllowedAt)) {
                throw new AppException(ErrorCode.OTP_RESEND_TOO_SOON);
            }
        }

        // Tạo OTP 6 chữ số
        String otpCode = String.format(
                "%06d",
                secureRandom.nextInt(1_000_000)
        );

        // Tạo dữ liệu đăng ký tạm
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
        pending.setOtpHash(hashOtp(otpCode));
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
