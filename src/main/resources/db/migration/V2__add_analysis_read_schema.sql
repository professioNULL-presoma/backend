ALTER TABLE contracts
    DROP CONSTRAINT contracts_processing_status_check,
    ADD CONSTRAINT contracts_processing_status_check CHECK (
        processing_status IN ('UPLOADED', 'OCR_PROCESSING', 'RETAKE_REQUIRED', 'EXTRACTING', 'REVIEWING', 'COMPLETED', 'FAILED')
    );

ALTER TABLE ocr_results
    ADD COLUMN pages JSONB,
    ADD COLUMN retake_pages JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN parent_ocr_result_id UUID,
    ADD CONSTRAINT ocr_results_parent_fk FOREIGN KEY (contract_id, parent_ocr_result_id)
        REFERENCES ocr_results (contract_id, id),
    DROP CONSTRAINT ocr_results_processing_status_check,
    ADD CONSTRAINT ocr_results_processing_status_check CHECK (
        processing_status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'RETAKE_REQUIRED', 'FAILED')
    );

CREATE TABLE contract_details (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DELETED')),
    contract_id UUID NOT NULL,
    ocr_result_id UUID NOT NULL,
    revision_no INTEGER NOT NULL CHECK (revision_no > 0),
    extraction_version INTEGER,
    extraction_status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        CHECK (extraction_status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    lease_type VARCHAR(50),
    contract_kind VARCHAR(50),
    property_type VARCHAR(50),
    property_address_masked TEXT,
    deposit_amount BIGINT,
    deposit_schedule JSONB,
    monthly_rent_amount BIGINT,
    rent_payment_day INTEGER,
    maintenance_fee_amount BIGINT,
    maintenance_fee_details JSONB,
    contract_signed_date DATE,
    handover_date DATE,
    lease_start_date DATE,
    lease_end_date DATE,
    special_terms JSONB,
    field_evidence JSONB,
    UNIQUE (ocr_result_id, revision_no),
    UNIQUE (contract_id, id),
    FOREIGN KEY (contract_id, ocr_result_id) REFERENCES ocr_results (contract_id, id)
);

CREATE TABLE contract_reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DELETED')),
    contract_id UUID NOT NULL,
    contract_detail_id UUID NOT NULL,
    revision_no INTEGER NOT NULL CHECK (revision_no > 0),
    model_name VARCHAR(100),
    model_version VARCHAR(50),
    prompt_version INTEGER,
    result_status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        CHECK (result_status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    summary TEXT,
    findings JSONB CHECK (findings IS NULL OR jsonb_typeof(findings) = 'array'),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    error_code VARCHAR(50),
    UNIQUE (contract_detail_id, revision_no),
    UNIQUE (contract_id, id),
    FOREIGN KEY (contract_id, contract_detail_id) REFERENCES contract_details (contract_id, id)
);

ALTER TABLE contracts
    ADD COLUMN published_review_id UUID,
    ADD CONSTRAINT contracts_published_review_fk FOREIGN KEY (id, published_review_id)
        REFERENCES contract_reviews (contract_id, id);
