package com.professionull.presoma.auth;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("postgres")
@RequestMapping("/api")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/auth/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public Envelope<SignupResult> signup(@RequestBody SignupRequest request) {
        return Envelope.ok(new SignupResult(auth.signup(request)));
    }

    @PostMapping("/auth/login")
    public Envelope<LoginResult> login(@RequestBody LoginRequest request) {
        return Envelope.ok(auth.login(request));
    }

    @GetMapping("/auth/me")
    public Envelope<Member> me(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
                               String authorization) {
        return Envelope.ok(auth.currentMember(authorization));
    }

    @GetMapping("/consent-documents/{type}")
    public Envelope<ConsentDocument> consentDocument(@PathVariable("type") String type) {
        return Envelope.ok(auth.consentDocument(type));
    }

    @ExceptionHandler(AuthException.class)
    ResponseEntity<Envelope<Void>> handleAuthError(AuthException exception) {
        return ResponseEntity.status(exception.status())
                .body(new Envelope<>(false, null, new ErrorBody(exception.code(), exception.getMessage())));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Envelope<Void>> handleUnreadableRequest() {
        return handleAuthError(AuthException.invalidInput());
    }

    public record SignupRequest(String email, String password, String nickname, List<UUID> agreedConsentDocumentIds) {}
    public record LoginRequest(String email, String password) {}
    public record SignupResult(UUID userId) {}
    public record Member(UUID id, String nickname) {}
    public record LoginResult(String accessToken, Member user) {}
    public record ConsentDocument(UUID id, String documentType, String version, String title,
                                  String content, boolean isRequired, OffsetDateTime effectiveAt) {}
    public record ErrorBody(String code, String message) {}
    public record Envelope<T>(boolean success, T data, ErrorBody error) {
        public static <T> Envelope<T> ok(T data) {
            return new Envelope<>(true, data, null);
        }
    }
}
