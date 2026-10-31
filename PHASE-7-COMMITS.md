# Phase 7 Commits — Knowledge Base (RAG)

Branch: `feature/phase-7-knowledge-base`  
Author: Rutikyadav71

---

## What Phase 7 contains

Retrieval-Augmented Generation (RAG) knowledge base:

- **Ingestion** — upload PDF/Word/plain-text documents, extract text (Apache POI + PDFBox), chunk by sliding window, embed via Gemini `text-embedding-004` (or a disabled stub), store chunks + vectors in `kb_chunk`.
- **Vector search** — `pgvector` extension (`<->` cosine operator) with an automatic fallback to in-memory cosine-scan so the app works on plain PostgreSQL and H2 (tests).
- **Grounded answers** — retrieve top-K chunks for a question, feed them as context to `AiGateway`, return answer + source citations.
- **Audience control** — documents tagged `MANAGEMENT` are visible only to Owner/Admin/HR; `ALL` is visible to everyone.
- **Embedding quota** — embedding calls are logged as `AiFeature.EMBEDDING` so they don't count against the chat quota.
- **Frontend** — Knowledge Base management page (upload, list, delete) and Ask-Documents page (Q&A with citations).
- **AI off** — when `ai.provider=none` the KB answer endpoint returns 503 (same as other AI features).

---

## Path aliases

| Alias | Expanded path |
|-------|--------------|
| `B`   | `backend/src/main/java/com/tenemsa/platform` |
| `R`   | `backend/src/main/resources` |
| `T`   | `backend/src/test/java/com/tenemsa/platform` |
| `F`   | `frontend/src` |

---

## Commit log (12 commits, in order)

### Commit 1 — `build: PDF/Word extraction libraries and knowledge-base settings`
**Files:**
- `backend/pom.xml`
- `R/application.properties`
- `backend/.env.example`
- `backend/src/test/resources/application-test.properties`

- `pom.xml`: added Apache PDFBox (`pdfbox`), Apache POI (`poi-ooxml`) for DOCX, and `postgresql` JDBC extension for pgvector.
- `application.properties`: added `kb.*` block — `kb.embedding-provider`, `kb.embedding-model`, `kb.chunk-size`, `kb.chunk-overlap`, `kb.top-k`, `kb.vector-store`.
- `.env.example`: added `KB_EMBEDDING_PROVIDER`, `KB_VECTOR_STORE` with comments.
- `application-test.properties`: `kb.embedding-provider=none`, `kb.vector-store=scan` (no pgvector needed).

---

### Commit 2 — `feat(db): knowledge base tables, optional pgvector column`
**Files:**
- `R/db/migration/postgresql/V7__knowledge_base.sql`
- `R/db/migration/mysql/V7__knowledge_base.sql`

PostgreSQL V7:
- `kb_document` — `id`, `company_id`, `title`, `source_filename`, `mime_type`, `audience` (ALL/MANAGEMENT), `status` (PENDING/READY/FAILED), `uploaded_by`, `created_at`.
- `kb_chunk` — `id`, `document_id`, `chunk_index`, `content TEXT`, `embedding vector(768)` (nullable, requires pgvector; guarded by `DO $$ ... IF EXISTS $$` so it degrades cleanly on plain PG).
- Indexes: `idx_kb_chunk_document`, `idx_kb_chunk_embedding` (IVFFlat cosine, created only when pgvector available).

MySQL V7: same tables without the vector column (scan fallback always used with MySQL).

---

### Commit 3 — `feat(ai): embedding usage logging that does not eat the chat quota`
**Files:**
- `B/ai/model/AiFeature.java`
- `B/ai/repository/AiRequestLogRepository.java`
- `B/ai/service/AiGateway.java`
- `B/ai/service/AiUsageService.java`

- `AiFeature`: added `EMBEDDING` variant.
- `AiRequestLogRepository`: added `countByCompanyAndFeatureAndCreatedAtAfter` so embedding calls are counted separately.
- `AiGateway`: quota check now accepts a `feature` parameter; `EMBEDDING` bypasses the chat per-user-per-hour counter.
- `AiUsageService`: usage response breaks down tokens by feature (`chat` vs `embedding`).

---

### Commit 4 — `feat(security): kb:ask, kb:manage, kb:read:management`
**Files:**
- `B/audit/model/AuditAction.java`
- `B/security/rbac/Permission.java`
- `B/security/rbac/RolePermissionRegistry.java`

New permissions:
- `KB_ASK("kb:ask")` — ask questions against the knowledge base.
- `KB_MANAGE("kb:manage")` — upload, delete documents.
- `KB_READ_MANAGEMENT("kb:read:management")` — read MANAGEMENT-audience docs.

New audit actions: `KB_DOCUMENT_UPLOADED`, `KB_DOCUMENT_DELETED`, `KB_QUESTION_ASKED`.

Role assignments:
- Owner/Admin: `KB_ASK`, `KB_MANAGE`, `KB_READ_MANAGEMENT`.
- HR: `KB_ASK`, `KB_MANAGE`, `KB_READ_MANAGEMENT`.
- Manager: `KB_ASK`, `KB_READ_MANAGEMENT`.
- Employee/Intern: `KB_ASK` (ALL-audience docs only).

---

### Commit 5 — `feat(kb): embedding client, Gemini over HTTP, gateway`
**Files:**
- `B/knowledge/config/KbConfig.java`
- `B/knowledge/config/KbProperties.java`
- `B/knowledge/embedding/EmbeddingClient.java`
- `B/knowledge/embedding/GeminiEmbeddingClient.java`
- `B/knowledge/embedding/DisabledEmbeddingClient.java`
- `B/knowledge/embedding/EmbeddingGateway.java`

- `KbProperties`: `@ConfigurationProperties("kb")` — all `kb.*` settings.
- `KbConfig`: `@Bean` picks `GeminiEmbeddingClient` when `kb.embedding-provider=gemini`, else `DisabledEmbeddingClient`.
- `EmbeddingClient` interface: `embed(text)` → `float[]`.
- `GeminiEmbeddingClient`: HTTP POST to Gemini Embeddings API (`models/text-embedding-004:embedContent`), using `GEMINI_API_KEY`; returns 768-dim float array.
- `DisabledEmbeddingClient`: throws `AiUnavailableException`.
- `EmbeddingGateway`: rate-limit + quota wrap around `EmbeddingClient`; logs to `AiRequestLog` with `AiFeature.EMBEDDING`.

---

### Commit 6 — `feat(kb): vector store with pgvector and scan fallback`
**Files:**
- `B/knowledge/vector/VectorStore.java`
- `B/knowledge/vector/PgVectorStore.java`
- `B/knowledge/vector/ScanVectorStore.java`
- `B/knowledge/vector/VectorStoreRouter.java`

- `VectorStore` interface: `upsert(chunkId, embedding)`, `findNearest(query, k)` → `List<UUID>`.
- `PgVectorStore`: JDBC `SELECT chunk_id FROM kb_chunk ORDER BY embedding <-> ? LIMIT ?` using pgvector's cosine operator; updates the `embedding` column.
- `ScanVectorStore`: loads all `(chunk_id, embedding)` from `kb_chunk`, computes cosine similarity in-memory, returns top-K.
- `VectorStoreRouter`: `@Bean` picks `PgVectorStore` when `kb.vector-store=pgvector`, else `ScanVectorStore`.

---

### Commit 7 — `feat(kb): documents, chunks, chunking and text extraction`
**Files:**
- `B/knowledge/model/KbDocument.java`
- `B/knowledge/model/KbChunk.java`
- `B/knowledge/model/DocumentStatus.java`
- `B/knowledge/model/DocumentAudience.java`
- `B/knowledge/repository/KbDocumentRepository.java`
- `B/knowledge/repository/KbChunkRepository.java`
- `B/knowledge/service/TextChunker.java`
- `B/knowledge/service/TextExtractor.java`

- `KbDocument` / `KbChunk`: JPA entities matching V7 schema.
- `DocumentStatus`: `PENDING`, `READY`, `FAILED`.
- `DocumentAudience`: `ALL`, `MANAGEMENT`.
- Repositories: standard JPA + custom queries (`findByDocumentIdOrderByChunkIndex`, `findIdsByDocumentId`).
- `TextExtractor`: extracts plain text from `application/pdf` (PDFBox), `application/vnd.openxmlformats-officedocument.wordprocessingml.document` (POI XWPF), or `text/plain`. Throws `UnsupportedMediaTypeException` for others.
- `TextChunker`: sliding-window chunker — chunk size = `kb.chunk-size` chars (default 800), overlap = `kb.chunk-overlap` chars (default 200); splits on sentence boundaries where possible.

---

### Commit 8 — `feat(kb): ingestion, grounded answers with citations, API`
**Files:**
- `B/knowledge/service/KnowledgeIngestionService.java`
- `B/knowledge/service/KnowledgeAnswerService.java`
- `B/knowledge/dto/KbAskRequest.java`
- `B/knowledge/dto/KbAnswerResponse.java`
- `B/knowledge/dto/KbAiOutput.java`
- `B/knowledge/dto/KbDocumentResponse.java`
- `B/knowledge/dto/KbStatusResponse.java`
- `B/knowledge/dto/KbTextRequest.java`
- `B/knowledge/controller/KnowledgeController.java`
- `B/ai/service/PromptCatalog.java`
- `R/prompts/kb-answer.v1.txt`

`KnowledgeIngestionService`:
- `ingest(file, audience, uploadedBy)` — extract text → chunk → embed each chunk via `EmbeddingGateway` → upsert vectors → set status `READY`.
- `ingestText(text, title, audience, uploadedBy)` — same flow for plain-text input.
- `delete(documentId)` — deletes chunks + document.

`KnowledgeAnswerService`:
- `answer(question, companyId, userId, audienceFilter)` — embed question → vector search top-K → fetch chunk content → build prompt with `PromptCatalog("kb-answer")` → call `AiGateway` → parse `KbAiOutput` (answer + citations) → return `KbAnswerResponse`.

`KnowledgeController` (`/api/kb`):
- `POST /api/kb/ingest` — multipart upload (requires `kb:manage`).
- `POST /api/kb/ingest-text` — plain-text ingestion (requires `kb:manage`).
- `GET /api/kb/documents` — list with audience filter.
- `DELETE /api/kb/documents/{id}` — delete (requires `kb:manage`).
- `POST /api/kb/ask` — ask question (requires `kb:ask`).
- `GET /api/kb/status` — returns `{ provider, vectorStore, documentsReady }`.

`R/prompts/kb-answer.v1.txt` — instructs the model to answer using ONLY the provided context chunks and return `{ "answer": "...", "citations": [{ "chunkId": "...", "excerpt": "..." }] }`.

---

### Commit 9 — `test: knowledge base, chunker, Gemini client, prompt contract`
**Files:**
- `T/knowledge/KnowledgeBaseTest.java`
- `T/knowledge/TextChunkerTest.java`
- `T/knowledge/GeminiEmbeddingClientTest.java`
- `T/PromptContractTest.java`

- **KnowledgeBaseTest** — `@SpringBootTest` + H2 + `kb.embedding-provider=none` + `kb.vector-store=scan`: ingest plain-text → ask → verify answer returned (stubbed AI); audience isolation (MANAGEMENT chunks not returned to Employee); delete document removes chunks.
- **TextChunkerTest** — unit test: empty input → empty list; text shorter than chunk size → single chunk; long text → correct overlap; chunk at sentence boundary.
- **GeminiEmbeddingClientTest** — `@WebMvcTest`-style mock: correct HTTP request shape, 768-dim response parsed, error → `AiProviderException`.
- **PromptContractTest** — updated to also assert `kb-answer.v1.txt` loads and contains `{{chunks}}` and `{{question}}`.

---

### Commit 10 — `feat(frontend): knowledge base and ask-documents pages`
**Files:**
- `F/shared/types/models.ts`
- `F/features/knowledge/services/knowledgeService.ts`
- `F/features/knowledge/pages/KnowledgePage.tsx`
- `F/features/knowledge/pages/AskKnowledgePage.tsx`

- `models.ts`: added `KbDocument`, `KbAnswerResponse`, `KbCitation`, `KbStatusResponse`.
- `knowledgeService.ts`: typed axios wrappers for all `/api/kb/*` endpoints; `uploadDocument(file, audience)` uses `FormData`.
- `KnowledgePage.tsx`: document list table (title, status, audience, uploaded-by, date), upload form (file picker + audience select), delete with confirmation. Requires `kb:manage`.
- `AskKnowledgePage.tsx`: question input, answer panel with markdown rendering, collapsible citations list. Requires `kb:ask`.

---

### Commit 11 — `feat(frontend): routes and menu items`
**Files:**
- `F/app/App.tsx`
- `F/layouts/admin/AdminSidebar.tsx`
- `F/layouts/user/UserSidebar.tsx`

- `App.tsx`: added routes `/admin/knowledge`, `/admin/knowledge/ask`, `/user/knowledge/ask`.
- `AdminSidebar.tsx`: "Knowledge Base" section with "Documents" and "Ask" links, gated on `kb:manage` / `kb:ask`.
- `UserSidebar.tsx`: "Ask Documents" link gated on `kb:ask`.

---

### Commit 12 — `docs: Phase 7`
**Files:**
- `docs/api.md`
- `docs/architecture.md`
- `docs/database.md`
- `docs/deployment.md`
- `docs/roadmap.md`
- `PHASE-7-COMMITS.md`

- `api.md`: new `/api/kb/*` endpoints with request/response shapes.
- `architecture.md`: RAG pipeline diagram (upload → extract → chunk → embed → store; question → embed → retrieve → generate → cite).
- `database.md`: V7 schema, pgvector setup, fallback behaviour.
- `deployment.md`: pgvector extension install, `KB_EMBEDDING_PROVIDER`, `KB_VECTOR_STORE` env vars.
- `roadmap.md`: Phase 7 marked complete; Phase 8 (predictive analytics) outlined.

---

## File count

| Layer | New files | Modified files |
|-------|-----------|---------------|
| Backend Java — knowledge module | 24 | 0 |
| Backend Java — ai module updates | 0 | 4 |
| Backend Java — security/audit | 0 | 3 |
| Backend resources | 2 | 4 |
| Tests | 4 | 1 |
| Frontend TypeScript/TSX | 3 | 4 |
| Docs / root | 6 | 0 |
| **Total** | **~39** | **~16** |

Grand total ≈ **55 files** across 12 commits.
