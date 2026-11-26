package com.professionull.presoma.auth;

import org.springframework.http.HttpStatus;

public final class AuthException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    AuthException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    static AuthException invalidInput() {
        return new AuthException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "입력값을 확인해 주세요.");
    }

    static AuthException invalidCredentials() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "이메일 또는 비밀번호가 맞지 않아요.");
    }

    static AuthException invalidToken() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_ACCESS_TOKEN", "다시 로그인해 주세요.");
    }
}
