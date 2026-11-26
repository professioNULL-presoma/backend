# 집사 ERD와 마이그레이션 범위

2026-11-26 멘토링 반영 ERD와 계약서 접수용 첫 PostgreSQL 마이그레이션을 기준으로 비교했다.

| 범위 | 반영 위치 | 내용 |
|---|---|---|
| 가입·동의 | V1 | `users`, `consent_documents`, `user_consents`. 회원가입과 버전별 동의 저장에 사용한다. |
| 계약서 접수 | V1 | `contracts`, `uploads`, `contract_files`, `ocr_results`. 비공개 S3의 객체 키, 파일 순서, OCR 시도를 저장한다. |
| OCR 페이지·재촬영 | V2 | `pages`, `retake_pages`, `parent_ocr_result_id`와 계약서·OCR의 `RETAKE_REQUIRED` 상태를 추가한다. AI 호출이나 재촬영 작업 스케줄링은 별도 구현 범위다. |
| 계약 조건·공개 분석 결과 | V2 | `contract_details`, `contract_reviews`, `contracts.published_review_id`를 추가한다. 추출본과 리뷰에 `contract_id`를 함께 저장해 복합 외래 키로 같은 계약서에 속하는 관계를 보장한다. |
| 질문 목록 | 후속 | `contract_questions`와 분석 실행 후 질문을 저장하는 흐름이 필요하다. |
| 전문가 기준 답변·평가 | 후속 | `expert_answers`, `evaluation_results`는 연구용이다. 현재 ERD는 전문가 가입·배정·승인 흐름을 추후 범위로 둔다. |
| 꿀팁·혜택·유료 기능 | 기획 확인 후 | `housing_tips`, `housing_benefits`, `paid_features`, `user_entitlements`는 후보 범위다. |

V1과 V2는 데이터 저장 구조를 제공한다. 실제 계약서 등록과 업로드 소비, OCR·추출·분석 실행, 결과 공개 시점에 리뷰 ID 교체, S3 파일 삭제는 각 쓰기 작업에서 구현해야 한다.

분석 조회는 로그인한 회원의 계약서만 반환한다. 재분석이 실패해도 이미 공개된 성공 리뷰 ID를 가리키면 이전 결과를 읽을 수 있다. `processingStatus`는 현재 계약서의 처리 상태다.
