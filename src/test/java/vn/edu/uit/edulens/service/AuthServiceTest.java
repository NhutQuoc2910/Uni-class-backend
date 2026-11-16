package vn.edu.uit.edulens.service;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import vn.edu.uit.edulens.dto.request.LoginRequest;
import vn.edu.uit.edulens.dto.request.ResetPasswordRequest;
import vn.edu.uit.edulens.dto.request.SendOtpRequest;
import vn.edu.uit.edulens.entity.User;
import vn.edu.uit.edulens.entity.enums.OtpPurpose;
import vn.edu.uit.edulens.entity.enums.Role;
import vn.edu.uit.edulens.entity.enums.UserStatus;
import vn.edu.uit.edulens.exception.AppException;
import vn.edu.uit.edulens.exception.ErrorCode;
import vn.edu.uit.edulens.repository.PendingRegistrationRepository;
import vn.edu.uit.edulens.repository.UserRepository;
import vn.edu.uit.edulens.security.JwtTokenProvider;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PendingRegistrationRepository pendingRegistrationRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthService authService;

    private Validator validator;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "otpHmacSecret", "UniClassDefaultHmacSecretKey2026Fixed");
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("sendOtp ném AppException(INVALID_INPUT) khi thiếu dữ liệu bắt buộc")
    void sendOtp_missingFields_throwsAppExceptionInvalidInput() {
        SendOtpRequest req = SendOtpRequest.builder()
                .email("newuser@uit.edu.vn")
                .purpose(OtpPurpose.REGISTER)
                .fullName(" ")
                .password("123456")
                .role(Role.ROLE_STUDENT)
                .build();

        when(userRepository.existsByEmail("newuser@uit.edu.vn")).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () -> authService.sendOtp(req));
        assertEquals(ErrorCode.INVALID_INPUT, ex.getErrorCode());
    }

    @Test
    @DisplayName("SendOtpRequest từ chối SĐT có dấu + ở giữa chuỗi hoặc ngắn hơn 9 số")
    void sendOtpRequest_invalidPhoneFormat_failsValidation() {
        SendOtpRequest req = SendOtpRequest.builder()
                .email("student@uit.edu.vn")
                .purpose(OtpPurpose.REGISTER)
                .phone("090+12345")
                .build();

        Set<ConstraintViolation<SendOtpRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
    }

    @Test
    @DisplayName("resetPassword ném SAME_AS_OLD_PASSWORD khi mật khẩu mới trùng mật khẩu cũ")
    void resetPassword_sameAsOldPassword_throwsAppException() throws Exception {
        String email = "student@uit.edu.vn";
        String otp = "123456";
        String hmac = computeHmac(email, OtpPurpose.FORGOT_PASSWORD, otp);

        User user = User.builder()
                .email(email)
                .passwordHash("hashed_old")
                .otpCode(hmac)
                .otpPurpose(OtpPurpose.FORGOT_PASSWORD)
                .otpExpiresAt(Instant.now().plusSeconds(120))
                .otpFailedAttempts(0)
                .build();

        ResetPasswordRequest req = ResetPasswordRequest.builder()
                .email(email)
                .otpCode(otp)
                .newPassword("OldPass@123")
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPass@123", "hashed_old")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> authService.resetPassword(req));
        assertEquals(ErrorCode.SAME_AS_OLD_PASSWORD, ex.getErrorCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("login từ chối khi trạng thái tài khoản khác ACTIVE")
    void login_nonActiveUser_throwsAccountLocked() {
        User user = User.builder()
                .email("locked@uit.edu.vn")
                .passwordHash("hashed")
                .status(UserStatus.LOCKED)
                .build();

        when(userRepository.findByEmailOrPhone("locked@uit.edu.vn", "locked@uit.edu.vn"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Pass@123", "hashed")).thenReturn(true);

        AppException ex = assertThrows(AppException.class,
                () -> authService.login(new LoginRequest("locked@uit.edu.vn", "Pass@123")));
        assertEquals(ErrorCode.ACCOUNT_LOCKED, ex.getErrorCode());
    }

    private String computeHmac(String email, OtpPurpose purpose, String otp) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("UniClassDefaultHmacSecretKey2026Fixed".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal((email + "|" + purpose.name() + "|" + otp).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
