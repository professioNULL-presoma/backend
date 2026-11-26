package com.professionull.presoma.auth;

import com.professionull.presoma.auth.AuthController.ConsentDocument;
import com.professionull.presoma.auth.AuthController.LoginRequest;
import com.professionull.presoma.auth.AuthController.LoginResult;
import com.professionull.presoma.auth.AuthController.Member;
import com.professionull.presoma.auth.AuthController.SignupRequest;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("postgres")
public class AuthService {
    private static final String SIGNUP_PRIVACY = "SIGNUP_PRIVACY";
    private static final Set<String> CONSENT_TYPES = Set.of(
            SIGNUP_PRIVACY, "CONTRACT_PROCESSING", "EXPERT_SHARING", "RESEARCH_USE");
    private static final Pattern EMAIL = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");
    private static final Pattern LETTER = Pattern.compile("[A-Za-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final int MAX_PASSWORD_BYTES = 72;

    private final JdbcClient db;
    private final JwtAccessTokens tokens;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
    private final String missingUserHash = passwords.encode(UUID.randomUUID().toString());

    AuthService(JdbcClient db, JwtAccessTokens tokens) {
        this.db = db;
        this.tokens = tokens;
    }

    @Transactional
    public UUID signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        String nickname = request.nickname() == null ? "" : request.nickname().strip();
        String password = request.password();
        int nicknameLength = nickname.codePointCount(0, nickname.length());
        if (nicknameLength < 2 || nicknameLength > 20 || !validSignupPassword(password)) {
            throw AuthException.invalidInput();
        }
        ConsentDocument document = consentDocument(SIGNUP_PRIVACY);
        if (!List.of(document.id()).equals(request.agreedConsentDocumentIds())) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_CONSENT",
                    "현재 시행 중인 가입 동의 문서를 확인해 주세요.");
        }

        UUID userId = db.sql("""
                INSERT INTO users (email, password_hash, nickname) VALUES (:email, :password, :nickname)
                ON CONFLICT (email) DO NOTHING RETURNING id
                """)
                .param("email", email)
                .param("password", passwords.encode(password))
                .param("nickname", nickname)
                .query(UUID.class).optional()
                .orElseThrow(() -> new AuthException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "이미 가입된 이메일이에요."));

        db.sql("""
                INSERT INTO user_consents (user_id, consent_document_id, consent_status, agreed_at)
                VALUES (:userId, :documentId, 'AGREED', CURRENT_TIMESTAMP)
                """)
                .param("userId", userId)
                .param("documentId", document.id())
                .update();
        return userId;
    }

    @Transactional
    public LoginResult login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        if (!validPasswordLength(request.password())) {
            throw AuthException.invalidCredentials();
        }
        Account account = db.sql("""
                SELECT id, nickname, password_hash FROM users
                WHERE email = :email AND status = 'ACTIVE' AND deleted_at IS NULL
                """)
                .param("email", email)
                .query((row, index) -> new Account(
                        row.getObject("id", UUID.class), row.getString("nickname"), row.getString("password_hash")))
                .optional().orElse(null);

        String hash = account == null ? missingUserHash : account.passwordHash();
        if (!passwords.matches(request.password(), hash) || account == null) {
            throw AuthException.invalidCredentials();
        }
        int updated = db.sql("""
                UPDATE users SET last_login_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id AND status = 'ACTIVE' AND deleted_at IS NULL
                """)
                .param("id", account.id()).update();
        if (updated != 1) {
            throw AuthException.invalidCredentials();
        }
        return new LoginResult(tokens.issue(account.id()), new Member(account.id(), account.nickname()));
    }

    public Member currentMember(String authorization) {
        return db.sql("SELECT id, nickname FROM users WHERE id = :id AND status = 'ACTIVE' AND deleted_at IS NULL")
                .param("id", tokens.requireUserId(authorization))
                .query((row, index) -> new Member(row.getObject("id", UUID.class), row.getString("nickname")))
                .optional().orElseThrow(AuthException::invalidToken);
    }

    public ConsentDocument consentDocument(String type) {
        if (!CONSENT_TYPES.contains(type)) {
            throw AuthException.invalidInput();
        }
        return db.sql("""
                SELECT id, document_type, version, title, content, is_required, effective_at
                FROM consent_documents
                WHERE document_type = :type AND status = 'ACTIVE'
                  AND deleted_at IS NULL AND effective_at <= CURRENT_TIMESTAMP
                ORDER BY effective_at DESC, created_at DESC, id DESC
                LIMIT 1
                """)
                .param("type", type)
                .query((row, index) -> new ConsentDocument(row.getObject("id", UUID.class),
                        row.getString("document_type"), row.getString("version"), row.getString("title"),
                        row.getString("content"), row.getBoolean("is_required"),
                        row.getObject("effective_at", OffsetDateTime.class)))
                .optional()
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "CONSENT_DOCUMENT_NOT_FOUND",
                        "현재 시행 중인 동의 문서가 없어요."));
    }

    private static String normalizeEmail(String input) {
        String email = input == null ? "" : input.strip().toLowerCase(Locale.ROOT);
        if (email.length() > 255 || !EMAIL.matcher(email).matches()) {
            throw AuthException.invalidInput();
        }
        return email;
    }

    private static boolean validSignupPassword(String password) {
        return validPasswordLength(password) && password.length() >= 8
                && LETTER.matcher(password).find() && DIGIT.matcher(password).find();
    }

    private static boolean validPasswordLength(String password) {
        return password != null && !password.isEmpty() && password.length() <= MAX_PASSWORD_BYTES
                && password.getBytes(StandardCharsets.UTF_8).length <= MAX_PASSWORD_BYTES;
    }

    private record Account(UUID id, String nickname, String passwordHash) {}
}
