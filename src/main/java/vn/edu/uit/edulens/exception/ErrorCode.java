package vn.edu.uit.edulens.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    USER_NOT_FOUND(40401, "Không tìm thấy người dùng", HttpStatus.NOT_FOUND),
    USER_ALREADY_EXISTS(40001, "Email đã được đăng ký trên hệ thống", HttpStatus.BAD_REQUEST),
    PHONE_ALREADY_EXISTS(40002, "Số điện thoại đã được đăng ký trên hệ thống", HttpStatus.BAD_REQUEST),
    INVALID_CREDENTIALS(40101, "Email/Số điện thoại hoặc mật khẩu không chính xác", HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED(40301, "Tài khoản của bạn đã bị khóa bởi quản trị viên", HttpStatus.FORBIDDEN),
    INVALID_OTP(40003, "Mã OTP không chính xác", HttpStatus.BAD_REQUEST),
    OTP_EXPIRED(40004, "Mã OTP đã hết hạn, vui lòng yêu cầu mã mới", HttpStatus.BAD_REQUEST),
    OTP_NOT_FOUND(40005, "Chưa có yêu cầu OTP cho email này", HttpStatus.BAD_REQUEST),
    WRONG_CURRENT_PASSWORD(40006, "Mật khẩu hiện tại không chính xác", HttpStatus.BAD_REQUEST),
    SAME_AS_OLD_PASSWORD(40007, "Mật khẩu mới không được trùng với mật khẩu hiện tại", HttpStatus.BAD_REQUEST),
    CLASS_NOT_FOUND(40402, "Không tìm thấy lớp học với mã này", HttpStatus.NOT_FOUND),
    ALREADY_ENROLLED(40008, "Bạn đã tham gia lớp học này rồi", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(40100, "Bạn chưa đăng nhập hoặc phiên đăng nhập đã hết hạn", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(40300, "Bạn không có quyền thực hiện thao tác này", HttpStatus.FORBIDDEN),
    INVALID_INPUT(40000, "Dữ liệu gửi lên không hợp lệ", HttpStatus.BAD_REQUEST),
    INTERNAL_SERVER_ERROR(50000, "Hệ thống gặp sự cố, vui lòng thử lại sau", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
