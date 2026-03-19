# SME Omnichannel ERP & POS — Backend

## Tech Stack
- Java 21, Spring Boot 3.4.3, Maven
- PostgreSQL 15+ + pgvector | Redis | Spring Security (JWT)
- Spring AI (Vertex AI Gemini) | WebSocket (STOMP) | Spring Batch

## Cấu trúc (104 Java files)
```
src/main/java/sme/backend/
├── config/          7 files — Security, CORS, Redis, WebSocket, AI, Audit, AppProperties
├── security/        4 files — JWT Provider/Filter, UserPrincipal, UserDetailsService
├── entity/          17 files — Full domain model với Rich Business Methods
├── repository/      14 files — JPA repos với custom JPQL/Native queries
├── service/         10 files — AuthService, POSService, ShiftService, InventoryService,
│                               OrderService, PurchaseService, FinanceService,
│                               ProductService, TransferService, NotificationService
├── controller/      13 files — REST endpoints cho 9 modules
├── dto/             14 files — Request/Response DTOs + generic wrappers
├── ai/              1 file   — RAG Service (Tika + pgvector + Gemini)
├── batch/           2 files  — Spring Batch inventory snapshot job
└── exception/       4 files  — Global handler + custom exceptions

src/main/resources/
├── application.yml                  — Full configuration
└── db/migration/
    ├── V1__init_schema.sql          — 24 tables, indexes, HNSW vector index
    └── V2__seed_data.sql            — Admin account + seed data
```

## Chạy nhanh

### 1. Prerequisites
```bash
# PostgreSQL 15+
createdb sme_erp_pos
psql sme_erp_pos -c "CREATE EXTENSION IF NOT EXISTS vector;"

# Redis
redis-server
```

### 2. Environment Variables
```bash
export GCP_PROJECT_ID=your-gcp-project-id   # Cho Vertex AI Gemini
export JWT_SECRET=your-256-bit-secret        # Tùy chọn, có default
```

### 3. Run
```bash
mvn spring-boot:run
# Hoặc
mvn package && java -jar target/backend-0.0.1-SNAPSHOT.jar
```

### 4. Test Login
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123"}'
```

## API Endpoints

| Module | Base Path | Roles |
|--------|-----------|-------|
| Auth | `/api/auth` | Public + Admin |
| POS | `/api/pos` | Cashier+ |
| Products | `/api/products` | Cashier+ |
| Inventory | `/api/inventory` | Cashier+ |
| Orders | `/api/orders` | Manager+ |
| Purchase | `/api/purchase-orders` | Manager+ |
| Transfers | `/api/transfers` | Manager+ |
| Finance | `/api/finance` | Manager+ |
| Customers | `/api/customers` | Cashier+ |
| Reports | `/api/reports` | Manager+ |
| Warehouses | `/api/warehouses` | Admin |
| Suppliers | `/api/suppliers` | Manager+ |
| AI Co-pilot | `/api/ai` | Manager+ |

## WebSocket
- Connect: `ws://localhost:8080/api/ws` (SockJS)
- Topics: `/topic/warehouse/{id}/low-stock`, `/topic/warehouse/{id}/new-order`, `/topic/warehouse/{id}/shift-alert`

## Tài khoản mặc định
- Username: `admin` | Password: `Admin@123` | Role: ROLE_ADMIN
