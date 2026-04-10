# Lingzhou Agent

[中文文档](./README_zh.md)

Lingzhou Agent is an open-source AI agent engineering stack built for enterprise scenarios with Spring Boot, Spring AI, Vue 3, and Docker Compose.

It is designed not just as a chat app, but as an extensible agent platform focused on three practical directions:

- Skill-extended Agentic RAG
- Intelligent data-querying agents
- Intelligent business form-filling agents

The repository combines chat, skill runtime, knowledge-base RAG, dataset capabilities, frontend rendering, and integration-oriented backend services in one codebase for local development, private deployment, and domain-specific extension.

## Why Lingzhou Agent

Many open-source AI projects solve only one slice of the problem:

- Some are strong at chat, but weak at business execution
- Some support RAG, but stop before data, APIs, and frontend interaction
- Some generate SQL, but lack business context, schema understanding, and workflow closure

Lingzhou Agent is built for a fuller enterprise agent path:

- Start from natural-language interaction
- Let skills decide how the agent retrieves, queries, and uses tools
- Render structured cards, rankings, or form drafts when needed
- Connect the final result to business APIs for real workflow execution

If you want an extensible business agent platform rather than a one-off demo, this project is designed for that direction.

## Project Highlights

- Skill-driven architecture: agent behavior is packaged as reusable skills instead of scattered prompts
- Agentic RAG: retrieval can work together with datasets, tools, and frontend rendering
- Intelligent data querying: not just text-to-SQL, but summary understanding, schema inspection, querying, and explanation
- Intelligent form filling: schema-driven extraction, draft generation, and confirmation flows
- Frontend-friendly outputs: cards, ranked lists, and form drafts instead of plain text only
- Private-deployment ready: backend, frontend, middleware, and deployment assets live in one repository

## At a Glance

```text
Natural language input
    ↓
Chat entry / Agent runtime
    ↓
Skill decides the strategy
    ↓
Knowledge base / Datasets / API tools / Frontend rendering tools
    ↓
Answer / Ranked list / Form draft / Business execution result
```

## What This Project Is Good At

Lingzhou Agent is a strong fit if you want to build:

- An internal agent platform that can grow through reusable skills
- An enterprise RAG system that goes beyond plain retrieval-and-answer
- A data agent that can understand datasets, inspect schema, run SQL, and explain business metrics
- A form-filling assistant that turns natural language into structured API-ready drafts
- A business agent that can talk, retrieve, analyze, render cards, and eventually trigger business actions

## Core Capabilities

### 1. Skill-Extended Agentic RAG

This project treats RAG as part of a broader agent workflow instead of an isolated retrieval module.

In practice:

- Skills define task goals, orchestration rules, and output constraints
- Knowledge bases provide retrievable business facts and documents
- Datasets, APIs, and rendering tools can participate in the same execution flow
- The agent can move from answering into analysis, confirmation, rendering, and execution

Already available in the project:

- Knowledge-base document upload, parsing, chunking, and indexing
- Retrieval, rerank, and QA orchestration
- Filesystem-based skill loading
- Runtime tool injection
- Structured frontend rendering for cards and forms

This makes it suitable for business workflows such as:

- Consult a policy knowledge base
- Inspect data structure
- Query or aggregate business data
- Return an explainable answer or a structured UI artifact

Typical use cases include:

- Policy and compliance copilots
- Product and project knowledge assistants
- Task-oriented flows that require retrieval, tools, rendering, and follow-up actions

### 2. Intelligent Data Agent

The project includes a dataset-oriented querying flow rather than a simple text-to-SQL demo.

Current capabilities include:

- Dataset management
- Dataset summaries
- Dataset structure inspection
- Publishing datasets as tools
- SQL querying
- Combining data answers with knowledge-base explanations

This allows a data agent to follow a safer and more useful workflow:

1. Understand what a dataset is meant to answer
2. Inspect objects, fields, and relations
3. Generate and run SQL for filtering, aggregation, ranking, or trend analysis
4. Add business-rule or policy explanations when needed

The existing repository already reflects this direction through domain-oriented skills such as expense-related querying assistants.

For business teams, this means:

- They get results with business context instead of isolated SQL
- They do not need to fully understand database structure before asking questions
- The querying capability is easier to package as a production-facing agent

### 3. Intelligent Business Form Filling

This is one of the most distinctive parts of the project.

Instead of hardcoding a single business form, the project supports a generic pattern:

- Read the input schema of the currently bound API tool
- Extract structured fields from natural language
- Build a confirmable frontend form draft
- Let the user confirm and then decide whether to call the target API

This fits scenarios such as:

- ERP / CRM / OA form entry
- Expense, registration, application, and record-creation flows
- Conversational prefill before structured submission

Key advantages:

- Field structure comes from API schema, not from hardcoded skill text
- The same form-filling skill can be reused across different business APIs
- The frontend can render a confirmable draft instead of plain JSON
- The agent can distinguish between “draft first” and “execute now”

This makes it a good fit for:

- ERP / CRM / OA data entry workflows
- Form-prefill and business record creation
- Conversational collection before structured submission

## Where It Fits Best

- Enterprise AI portals
- Knowledge and policy assistants
- Business analytics and intelligent data-querying systems
- OA, ERP, CRM, and low-code platforms with guided data entry
- Composite business agents that need chat, retrieval, querying, form generation, and execution in one experience

## Typical Scenarios

### Agentic RAG

Useful for:

- Enterprise policy Q&A
- Product knowledge assistants
- Project documentation copilots
- Retrieval flows that need tools, rendering, and execution together

### Intelligent Data Querying

Useful for:

- Sales analytics
- Operations analytics
- Financial querying
- Domain datasets around orders, reimbursement, customers, or projects

### Intelligent Form Filling

Useful for:

- Business document creation
- Draft generation and prefilling
- Approval preparation
- Structured submission to internal or external APIs

## Repository Layout

```text
.
├── backend/                # Spring Boot application for chat, RAG, datasets, tools, and integrations
├── core/                   # Reusable skill-aware extension core
├── frontend/               # Frontend workspace
│   ├── packages/core/      # Shared frontend capabilities
│   └── packages/web/       # Vue 3 + Vite web app
├── skills/                 # Filesystem skills, extensible by business scenarios
├── deploy/                 # Docker Compose assets, env templates, deployment scripts
├── docs/                   # Design docs and capability notes
├── pom.xml                 # Maven parent project
├── README.md
└── README_zh.md
```

## Tech Stack

- Backend: Java 17, Spring Boot 3.4, Spring AI, MyBatis-Plus, Redis, MinIO, Elasticsearch
- Frontend: Vue 3, Vite, pnpm workspace, Tailwind CSS
- Deployment: Docker Compose

## Quick Start

### Option A: Quick Docker Compose experience

1. Copy the quick environment file:

```bash
cp deploy/compose-quick/.env.example deploy/compose-quick/.env
```

2. Edit `deploy/compose-quick/.env` as needed

3. Start the full stack:

```bash
./deploy/manage.sh quick-up
```

By default:

- Frontend is exposed on port `80`
- Backend APIs are served under `/api`

See [deploy/README.md](./deploy/README.md) for deployment details.

### Option B: Local development

1. Start middleware:

```bash
cp deploy/.env.dev.example deploy/.env.dev
./deploy/manage.sh dev-up
```

2. Start backend:

```bash
mvn -f backend/pom.xml spring-boot:run
```

3. Start frontend:

```bash
cd frontend
pnpm install
pnpm dev
```

See [deploy/README.dev.md](./deploy/README.dev.md) for more details.

## Model Configuration

Model configuration is currently split between database records and application configuration:

- The model table maintains `baseUrl`, `path`, and `modelName`
- Configuration files maintain vendor runtime parameters such as `model.qwen.*` and `model.vllm.*`

In other words:

- Which model is selected and which endpoint/path is used comes from model records
- Vendor-specific runtime behavior remains in configuration and environment variables

## Skill Examples

The repository already includes skill examples such as:

- `skills/expense-assistant/`
- `skills/intelligent-form-fill/`

These illustrate how the project can evolve from a generic chat interface into a business-facing agent entry point.

## Screenshots

This section is a good place for the screenshots you mentioned. The most valuable visuals would be:

### 1. Agentic RAG

- Conversation flow
- Retrieved evidence or tool execution traces
- Final structured answer or card

### 2. Intelligent Data Agent

- Natural-language business question
- Ranked list, chart, or metric card
- Business interpretation alongside raw numbers

### 3. Intelligent Form Filling

- Natural-language input
- Auto-generated form draft
- Missing-field prompts and confirmation actions

Current example screenshots:

![Agentic RAG Example](./docs/images/agentic-rag-example.png)

![Intelligent Data Agent Example](./docs/images/data-agent-example.png)

![Intelligent Form Filling Example](./docs/images/form-fill-example.png)

## Documentation

- RAG design notes: [`docs/rag/`](./docs/rag/)
- SSO token exchange notes: [`docs/sso-exchange-token-api.md`](./docs/sso-exchange-token-api.md)
- Deployment guides: [`deploy/README.md`](./deploy/README.md), [`deploy/README.dev.md`](./deploy/README.dev.md)

## Open-Source Notes

Community releases may exclude internal collaboration metadata, private configuration, and non-public skills.

If you are using a public/community package:

- Prepare your own model configuration and environment variables
- Mount or create your own `skills` directory if you need filesystem skills
- Review deployment defaults and security boundaries before exposing the system

## Security

- Do not commit real API keys, passwords, tokens, or private endpoints
- Treat included Docker and application configs as templates, not production-hardening guidance
- Add authentication, authorization, key management, and audit controls before public exposure

## Contributing

Issues and pull requests are welcome.

If you are extending this repository with more skills, RAG workflows, data agents, or business agents, this codebase is intended to be a practical foundation for that work.

## License

See [`LICENSE`](./LICENSE).
