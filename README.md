# Lingzhou Agent

[中文文档](./README_zh.md)

Lingzhou Agent is an open-source AI application stack built with Spring Boot, Spring AI, Vue 3, and Docker Compose.
It combines chat, skill-aware tool orchestration, knowledge-base RAG, dataset management, and integration-oriented backend capabilities in a single repository.

The repository currently contains:

- A reusable Java `core` module for skill-aware AI extensions
- A Spring Boot `backend` application for chat, datasets, RAG, tools, and integrations
- A Vue 3 `frontend` workspace for the web UI
- Docker-based deployment assets under `deploy/`

## Highlights

- Skill-aware chat built on top of Spring AI
- Filesystem-based skill loading and runtime tool injection
- Knowledge-base ingestion, document chunking, retrieval, and QA flows
- Dataset management and dataset-to-tool publishing capabilities
- MCP and external integration extension points
- Docker quick-start for local demos and single-machine deployment

## Repository Layout

```text
.
├── backend/                # Spring Boot application
├── core/                   # Reusable skill-aware extension module
├── frontend/               # pnpm workspace
│   ├── packages/core/      # Shared frontend utilities
│   └── packages/web/       # Vue 3 + Vite web app
├── deploy/                 # Docker Compose, images, env templates, helper scripts
├── docs/                   # Design notes and API-related documents
├── pom.xml                 # Maven parent project
├── README.md
└── README_zh.md
```

## Tech Stack

- Backend: Java 17, Spring Boot 3.4, Spring AI, MyBatis-Plus, Redis, MinIO, Elasticsearch
- Frontend: Vue 3, Vite, pnpm workspace, Tailwind CSS
- Runtime and deployment: Docker Compose

## Quick Start

### Option A: Quick experience with Docker Compose

This is the easiest way to bring up the full stack.

1. Copy the quick deployment environment template:

```bash
cp deploy/compose-quick/.env.example deploy/compose-quick/.env
```

2. Edit `deploy/compose-quick/.env` and set the required model credentials:

- `APP_CHAT_QWEN_ONLINE_API_KEY`
- `APP_EMBEDDING_API_KEY`
- `APP_RAG_RERANK_API_KEY`

3. Start the stack:

```bash
./deploy/manage.sh quick-up
```

By default, the frontend is exposed on port `80` and the backend serves APIs under `/api`.

Related files:

- `deploy/README.md`
- `deploy/compose-quick/README.md`

### Option B: Local development

Use this flow if you want to run the backend and frontend locally while keeping middleware in Docker.

1. Start the development middleware stack:

```bash
cp deploy/.env.dev.example deploy/.env.dev
./deploy/manage.sh dev-up
```

This prepares and starts:

- MySQL on `3306`
- Redis on `16379`
- MinIO on `19000` and `19001`
- Elasticsearch on `9200`

2. Start the backend from the repository root:

```bash
mvn -f backend/pom.xml spring-boot:run
```

Backend defaults:

- Base URL: `http://localhost:5050`
- API context path: `/api`

3. Start the frontend:

```bash
cd frontend
pnpm install
pnpm dev
```

Frontend dev server defaults to `http://localhost:5173` and proxies `/api` to the backend.

For more details, see `deploy/README.dev.md`.

## Key Functional Areas

### 1. Chat and agent interaction

- Conversational APIs
- Streaming responses
- Chat session persistence hooks
- File upload support for chat-related flows

### 2. Skill-aware runtime

- Filesystem-backed skill packages
- On-demand skill content loading
- Runtime tool registration and activation
- Extension hooks for MCP and custom tools

### 3. Knowledge base and RAG

- Knowledge base management
- Document upload and parsing
- Chunking and indexing
- Retrieval, rerank, and QA orchestration

### 4. Dataset and integration management

- Integration dataset entities and bindings
- Dataset publishing as callable tools
- Low-code platform browsing and integration support
- Platform/system configuration endpoints

## Configuration Notes

- The backend default profile is `qwen`.
- Most runtime settings can be overridden with environment variables.
- Community deployments should use sanitized configuration values and replace all placeholders before production use.
- If you provide your own skill packages, place them under `./skills` or mount that directory at runtime.

## Documentation

- RAG-related design notes: [`docs/rag/`](./docs/rag/)
- SSO token exchange API notes: [`docs/sso-exchange-token-api.md`](./docs/sso-exchange-token-api.md)
- Deployment guides: [`deploy/README.md`](./deploy/README.md) and [`deploy/README.dev.md`](./deploy/README.dev.md)

## Open-Source Notes

The community release is intentionally trimmed for public distribution.
Depending on how the release package is generated, internal workflow metadata, private configuration, and non-public skill resources may be excluded.

If you are running a public/community package:

- Prepare your own model credentials and environment files
- Mount or create your own `skills` directory if you want filesystem skills
- Review deployment defaults before exposing the system publicly

## Security

- Do not commit real API keys, passwords, tokens, or private endpoints
- Review all Docker and application configuration before public deployment
- Treat the included deployment files as templates, not production-hardening guidance

## Contributing

Issues and pull requests are welcome.
When contributing, please keep changes focused, document behavior changes clearly, and update relevant docs when configuration or runtime behavior changes.

## License

See [`LICENSE`](./LICENSE).
