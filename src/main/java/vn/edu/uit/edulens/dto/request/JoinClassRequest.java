package vn.edu.uit.edulens.dto.request;

import jakarta.validation.constraints.NotBlank;

public class JoinClassRequest {

    @NotBlank(message = "Vui lòng nhập mã lớp")
    private String inviteCode;

    public JoinClassRequest() {}

    public JoinClassRequest(String inviteCode) {
        this.inviteCode = inviteCode;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public void setInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }
}
