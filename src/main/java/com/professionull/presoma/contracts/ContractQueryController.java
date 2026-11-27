package com.professionull.presoma.contracts;

import com.professionull.presoma.auth.AuthController.Envelope;
import com.professionull.presoma.auth.AuthController.ErrorBody;
import com.professionull.presoma.auth.AuthException;
import com.professionull.presoma.auth.AuthService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@Profile("postgres")
public class ContractQueryController {
    private static final Set<String> ANALYSIS_STATES = Set.of("UPLOADED", "OCR_PROCESSING", "EXTRACTING",
            "REVIEWING", "RETAKE_REQUIRED", "COMPLETED", "FAILED");
    private static final String PUBLISHED_RESULT = """
                FROM contracts c
                LEFT JOIN (
                    contract_reviews r
                    JOIN contract_details d ON d.id = r.contract_detail_id AND d.contract_id = r.contract_id
                     AND d.status = 'ACTIVE' AND d.deleted_at IS NULL AND d.extraction_status = 'COMPLETED'
                )
                  ON r.id = c.published_review_id AND r.contract_id = c.id
                 AND r.status = 'ACTIVE' AND r.deleted_at IS NULL AND r.result_status = 'COMPLETED'
            """;

    private final AuthService auth;
    private final JdbcClient db;
    private final ObjectMapper json;

    public ContractQueryController(AuthService auth, JdbcClient db, ObjectMapper json) {
        this.auth = auth;
        this.db = db;
        this.json = json;
    }

    @GetMapping("/api/contracts")
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

    @GetMapping("/api/v1/contracts")
    public ResponseEntity<Envelope<ContractPage>> page(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        UUID userId = auth.currentMember(authorization).id();
        if (page < 0 || size < 1 || size > 100 || (status != null && !ANALYSIS_STATES.contains(status))) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "조회 조건을 확인해 주세요.");
        }
        var rows = db.sql("""
                SELECT c.id, c.title, c.processing_status, c.created_at, r.id AS review_id, r.completed_at,
                       d.lease_type, d.deposit_amount, d.monthly_rent_amount, d.maintenance_fee_amount
                """ + PUBLISHED_RESULT + """
                WHERE c.user_id = :userId AND c.deleted_at IS NULL AND c.status IN ('ACTIVE', 'PRIVATE')
                  AND (CAST(:status AS text) IS NULL OR c.processing_status = :status)
                ORDER BY c.created_at DESC, c.id DESC
                LIMIT :limit OFFSET :offset
                """)
                .param("userId", userId).param("status", status, java.sql.Types.VARCHAR)
                .param("limit", size + 1).param("offset", (long) page * size)
                .query((row, index) -> {
                    boolean hasResult = row.getObject("review_id") != null;
                    var summary = hasResult ? new LeaseSummary(row.getString("lease_type"),
                            row.getObject("deposit_amount", Long.class), row.getObject("monthly_rent_amount", Long.class),
                            row.getObject("maintenance_fee_amount", Long.class)) : null;
                    return new ContractItem(row.getObject("id", UUID.class), row.getString("title"),
                            row.getString("processing_status"), hasResult, row.getObject("created_at", OffsetDateTime.class),
                            row.getObject("completed_at", OffsetDateTime.class), summary);
                }).list();
        boolean hasNext = rows.size() > size;
        var items = hasNext ? rows.subList(0, size) : rows;
        return ResponseEntity.ok(Envelope.ok(new ContractPage(items, page, size, hasNext)));
    }

    public record ContractPage(List<ContractItem> items, int page, int size, boolean hasNext) {}
    public record ContractItem(UUID contractId, String title, String analysisStatus, boolean hasResult,
                               OffsetDateTime createdAt, OffsetDateTime analyzedAt, LeaseSummary summary) {}
    public record LeaseSummary(String leaseType, Long deposit, Long monthlyRent, Long maintenanceFee) {}

    @GetMapping("/api/contracts/{id}/analysis")
    public ResponseEntity<Envelope<Analysis>> analysis(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable("id") UUID contractId) {
        UUID userId = auth.currentMember(authorization).id();
        var result = db.sql("""
                SELECT c.title, c.processing_status, r.id AS review_id, r.summary, r.findings, r.completed_at,
                       d.property_type, d.property_address_masked, d.deposit_amount,
                       d.monthly_rent_amount, d.maintenance_fee_amount
                """ + PUBLISHED_RESULT + """
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
    ResponseEntity<Envelope<Void>> handleInvalidParameter() {
        return error(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "요청 값을 확인해 주세요.");
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
