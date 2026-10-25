package vn.edu.uit.edulens.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.edu.uit.edulens.dto.request.ChangePasswordRequest;
import vn.edu.uit.edulens.entity.User;
import vn.edu.uit.edulens.exception.AppException;
import vn.edu.uit.edulens.exception.ErrorCode;
import vn.edu.uit.edulens.repository.UserRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User sampleUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        sampleUser = User.builder()
                .id(userId)
                .email("student1@uit.edu.vn")
                .passwordHash("hashed_old_password")
                .build();
    }

    @Test
    @DisplayName("Đổi mật khẩu thành công khi thông tin hợp lệ")
    void changePassword_Success() {
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("OldPass@123")
                .newPassword("NewPass@456")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("OldPass@123", "hashed_old_password")).thenReturn(true);
        when(passwordEncoder.matches("NewPass@456", "hashed_old_password")).thenReturn(false);
        when(passwordEncoder.encode("NewPass@456")).thenReturn("hashed_new_password");

        assertDoesNotThrow(() -> userService.changePassword(userId, request));

        verify(userRepository, times(1)).save(sampleUser);
        assertEquals("hashed_new_password", sampleUser.getPasswordHash());
    }

    @Test
    @DisplayName("Ném ngoại lệ 400 khi mật khẩu hiện tại không khớp")
    void changePassword_WrongCurrentPassword() {
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("WrongPass@123")
                .newPassword("NewPass@456")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("WrongPass@123", "hashed_old_password")).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () -> userService.changePassword(userId, request));
        assertEquals(ErrorCode.WRONG_CURRENT_PASSWORD, ex.getErrorCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Ném ngoại lệ 400 khi mật khẩu mới trùng mật khẩu cũ")
    void changePassword_SameAsOldPassword() {
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("OldPass@123")
                .newPassword("OldPass@123")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("OldPass@123", "hashed_old_password")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> userService.changePassword(userId, request));
        assertEquals(ErrorCode.SAME_AS_OLD_PASSWORD, ex.getErrorCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Ném ngoại lệ 404 khi không tìm thấy user")
    void changePassword_UserNotFound() {
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("OldPass@123")
                .newPassword("NewPass@456")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> userService.changePassword(userId, request));
        assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
    }
}
