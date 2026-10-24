package vn.edu.uit.edulens.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

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
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        int codeNumber = secureRandom.nextInt(900000) + 100000;
        String otpCode = String.valueOf(codeNumber);

        user.setOtpCode(otpCode);
        user.setOtpExpiresAt(Instant.now().plus(Duration.ofMinutes(5)));
        userRepository.save(user);

        emailService.sendOtpEmail(user.getEmail(), otpCode);
        return "Mã OTP gồm 6 chữ số đã được gửi tới email của bạn";
    }

    @Transactional(readOnly = true)
    public boolean verifyOtp(VerifyOtpRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        validateOtp(user, request.getOtpCode());
        return true;
    }

    @Transactional
    public String resetPassword(ResetPasswordRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        validateOtp(user, request.getOtpCode());

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setOtpCode(null);
        user.setOtpExpiresAt(null);
        userRepository.save(user);

        log.info("Password reset successfully for user: {}", user.getEmail());
        return "Đặt lại mật khẩu thành công";
    }

    private void validateOtp(User user, String inputOtp) {
        if (user.getOtpCode() == null || user.getOtpExpiresAt() == null) {
            throw new AppException(ErrorCode.OTP_NOT_FOUND);
        }

        if (Instant.now().isAfter(user.getOtpExpiresAt())) {
            throw new AppException(ErrorCode.OTP_EXPIRED);
        }

        if (!user.getOtpCode().equals(inputOtp.trim())) {
            throw new AppException(ErrorCode.INVALID_OTP);
        }
    }
}
