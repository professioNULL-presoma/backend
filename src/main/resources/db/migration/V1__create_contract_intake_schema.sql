-- 회원 동의부터 계약서 등록과 OCR 접수까지 필요한 구조를 만든다.
-- contracts.published_review_id는 분석 결과 테이블과 함께 추가한다.

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'DORMANT', 'WITHDRAWN')),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    role VARCHAR(30) NOT NULL DEFAULT 'MEMBER'
        CHECK (role IN ('MEMBER', 'ADMIN')),
    last_login_at TIMESTAMPTZ,
    password_changed_at TIMESTAMPTZ,
    withdrawn_at TIMESTAMPTZ
);

CREATE TABLE consent_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED')),
    document_type VARCHAR(50) NOT NULL
        CHECK (document_type IN ('SIGNUP_PRIVACY', 'CONTRACT_PROCESSING', 'EXPERT_SHARING', 'RESEARCH_USE')),
    version VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    is_required BOOLEAN NOT NULL,
    effective_at TIMESTAMPTZ NOT NULL,
    UNIQUE (document_type, version)
);

CREATE TABLE contracts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'PRIVATE', 'DELETED')),
    user_id UUID NOT NULL REFERENCES users (id),
    title VARCHAR(255),
    processing_status VARCHAR(30) NOT NULL DEFAULT 'UPLOADED'
        CHECK (processing_status IN ('UPLOADED', 'OCR_PROCESSING', 'EXTRACTING', 'REVIEWING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX contracts_user_created_at_id_idx
    ON contracts (user_id, created_at DESC, id DESC);

CREATE TABLE user_consents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'DELETED')),
    user_id UUID NOT NULL REFERENCES users (id),
    consent_document_id UUID NOT NULL REFERENCES consent_documents (id),
    contract_id UUID REFERENCES contracts (id),
    consent_status VARCHAR(30) NOT NULL
        CHECK (consent_status IN ('AGREED', 'WITHDRAWN')),
    agreed_at TIMESTAMPTZ NOT NULL,
    withdrawn_at TIMESTAMPTZ
);

CREATE TABLE uploads (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL DEFAULT 'ISSUED'
        CHECK (status IN ('ISSUED', 'CONSUMED', 'EXPIRED')),
    user_id UUID NOT NULL REFERENCES users (id),
    consent_document_id UUID NOT NULL REFERENCES consent_documents (id),
    agreed_at TIMESTAMPTZ NOT NULL,
    object_key TEXT NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT NOT NULL CHECK (file_size_bytes > 0),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE ocr_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'DELETED')),
    contract_id UUID NOT NULL REFERENCES contracts (id),
    attempt_no INTEGER NOT NULL CHECK (attempt_no > 0),
    ocr_engine VARCHAR(100),
    engine_version VARCHAR(50),
    masked_text TEXT,
    processing_status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        CHECK (processing_status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    error_code VARCHAR(50),
    UNIQUE (contract_id, attempt_no),
    UNIQUE (contract_id, id)
);

CREATE TABLE contract_files (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'DELETED')),
    contract_id UUID NOT NULL REFERENCES contracts (id),
    object_key TEXT NOT NULL,
    ocr_result_id UUID NOT NULL,
    upload_id UUID NOT NULL UNIQUE REFERENCES uploads (id),
    original_file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT NOT NULL CHECK (file_size_bytes > 0),
    upload_order INTEGER NOT NULL CHECK (upload_order > 0),
    file_deleted_at TIMESTAMPTZ,
    FOREIGN KEY (contract_id, ocr_result_id) REFERENCES ocr_results (contract_id, id),
    UNIQUE (ocr_result_id, upload_order)
);
