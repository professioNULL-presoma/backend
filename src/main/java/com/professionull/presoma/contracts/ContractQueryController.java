package com.professionull.presoma.contracts;

import com.professionull.presoma.auth.AuthController.Envelope;
import com.professionull.presoma.auth.AuthController.ErrorBody;
import com.professionull.presoma.auth.AuthException;
import com.professionull.presoma.auth.AuthService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@Profile("postgres")
@RequestMapping("/api/contracts")
public class ContractQueryController {
    private final AuthService auth;
    private final JdbcClient db;
    private final ObjectMapper json;

    public ContractQueryController(AuthService auth, JdbcClient db, ObjectMapper json) {
        this.auth = auth;
        this.db = db;
        this.json = json;
    }

    @GetMapping
    public Envelope<List<ContractSummary>> list(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        UUID userId = auth.currentMember(authorization).id();
        return Envelope.ok(db.sql("""
                SELECT id, title, processing_status, created_at
                FROM contracts
                WHERE user_id = :userId AND deleted_at IS NULL AND status IN ('ACTIVE', 'PRIVATE')
                ORDER BY created_at DESC, id DESC
                """)
                .param("userId", userId)
                .query((row, index) -> new ContractSummary(row.getObject("id", UUID.class),
                        row.getString("title"), row.getString("processing_status"),
                        row.getObject("created_at", OffsetDateTime.class)))
                .list());
    }

    @GetMapping("/{id}/analysis")
    public ResponseEntity<Envelope<Analysis>> analysis(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable("id") UUID contractId) {
        UUID userId = auth.currentMember(authorization).id();
        var result = db.sql("""
                SELECT c.title, c.processing_status, r.id AS review_id, r.summary, r.findings, r.completed_at,
                       d.property_type, d.property_address_masked, d.deposit_amount,
                       d.monthly_rent_amount, d.maintenance_fee_amount
                FROM contracts c
                LEFT JOIN (
                    contract_reviews r
                    JOIN contract_details d ON d.id = r.contract_detail_id AND d.contract_id = r.contract_id
                     AND d.status = 'ACTIVE' AND d.deleted_at IS NULL AND d.extraction_status = 'COMPLETED'
                )
                  ON r.id = c.published_review_id AND r.contract_id = c.id
                 AND r.status = 'ACTIVE' AND r.deleted_at IS NULL AND r.result_status = 'COMPLETED'
                WHERE c.id = :contractId AND c.user_id = :userId
                  AND c.deleted_at IS NULL AND c.status IN ('ACTIVE', 'PRIVATE')
                """)
                .param("contractId", contractId)
                .param("userId", userId)
                .query((row, index) -> new Analysis(contractId, row.getString("title"),
                        row.getString("processing_status"), row.getObject("review_id", UUID.class),
                        row.getString("summary"), readFindings(row.getString("findings")),
                        row.getObject("completed_at", OffsetDateTime.class),
                        new ContractConditions(row.getString("property_type"), row.getString("property_address_masked"),
                                row.getObject("deposit_amount", Long.class), row.getObject("monthly_rent_amount", Long.class),
                                row.getObject("maintenance_fee_amount", Long.class))))
                .optional();

        if (result.isEmpty()) {
            return error(HttpStatus.NOT_FOUND, "CONTRACT_NOT_FOUND", "계약서를 찾을 수 없어요.");
        }
        if (result.get().reviewId() == null) {
            return error(HttpStatus.CONFLICT, "ANALYSIS_NOT_READY", "공개된 분석 결과가 아직 없어요.");
        }
        return ResponseEntity.ok(Envelope.ok(result.get()));
    }

    @ExceptionHandler(AuthException.class)
    ResponseEntity<Envelope<Void>> handleAuthentication(AuthException exception) {
        return error(exception.status(), exception.code(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Envelope<Void>> handleInvalidId() {
        return error(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "계약서 ID를 확인해 주세요.");
    }

    private JsonNode readFindings(String text) {
        return text == null ? json.createArrayNode() : json.readTree(text);
    }

    private static <T> ResponseEntity<Envelope<T>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new Envelope<>(false, null, new ErrorBody(code, message)));
    }

    public record ContractSummary(UUID id, String title, String processingStatus, OffsetDateTime createdAt) {}
    public record ContractConditions(String propertyType, String propertyAddressMasked, Long depositAmount,
                                     Long monthlyRentAmount, Long maintenanceFeeAmount) {}
    public record Analysis(UUID contractId, String title, String processingStatus, UUID reviewId, String summary,
                           JsonNode findings, OffsetDateTime completedAt, ContractConditions conditions) {}
}
