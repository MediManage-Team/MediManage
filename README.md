# MediManage — Pharmacy Management System

A professional desktop application for managing pharmacy operations — inventory, billing, expenses, customers, prescriptions, and supplier management. Built with **JavaFX 21** and **SQLite**, designed for Indian pharmacies.

> **Version 6.5.0** · Java 21 LTS · Windows

---

## How The App Works

MediManage follows a **layered MVC architecture**. The JavaFX desktop app (frontend) talks to service classes (middle layer) which talk to DAOs (backend/database). Two external microservices run alongside — a **Python AI engine** for LLM inference and a **Node.js WhatsApp bridge** for messaging.

```
┌───────────────────────────────────────────────────────────┐
│                    FRONTEND (JavaFX)                      │
│  FXML Views  ←→  Controllers  ←→  Utility Classes        │
└────────────────────────┬──────────────────────────────────┘
                         │
┌────────────────────────▼──────────────────────────────────┐
│                  MIDDLE LAYER (Services)                  │
│  BillingService, ReportService, LoyaltyService,           │
│  EmailService, WhatsAppService, DashboardKpiService       │
│  CustomerService, InventoryService, BarcodeService        │
│  AIOrchestrator → LocalAIService / Cloud APIs             │
└──────────┬─────────────────────┬──────────────────────────┘
           │                     │
┌──────────▼──────────┐  ┌───────▼────────────────────────┐
│  BACKEND (DAOs)     │  │  EXTERNAL SERVICES              │
│  SQLite Database    │  │  Python Flask (AI Engine :5000) │
│  12 DAO classes     │  │  Node.js Express (WhatsApp :3001│
└─────────────────────┘  └────────────────────────────────┘
```

### Startup Flow

1. `Launcher.main()` → Sets up structured logging → calls `MediManageApplication.main()`
2. `MediManageApplication` → Initializes database schema via `DatabaseUtil` → auto-starts the Python AI engine via `PythonEnvironmentManager` → shows Login screen
3. User logs in via `LoginController` → credentials checked by `UserDAO` + `PasswordHasher` → session stored in `UserSession`
4. `MainShellController` loads the sidebar navigation → `ViewSwitcher` loads the selected module

### Billing Flow (Main Use Case)

1. `BillingController` → user scans barcode or searches medicine
2. `BillingService.addMedicineToBill()` → validates stock via `MedicineDAO`
3. User clicks Checkout → `BillingService.completeCheckout()`:
   - `BillDAO.generateInvoice()` — atomic DB transaction (insert bill + bill_items, decrement stock, update credit if needed)
   - `ReportService.generateInvoicePDF()` — generates A4 PDF via JasperReports
   - AI care protocol saved to DB via `BillDAO.saveAICareProtocol()`
4. Optional: Send invoice via `EmailService` (SMTP) or `WhatsAppService` (local bridge HTTP)

### AI Flow

1. `AIController` → user sends chat message
2. `AIOrchestrator` routes to `LocalAIService` (local Python engine) or cloud provider (Gemini/OpenAI)
3. `LocalAIService` calls the Flask server at `localhost:5000` which uses `llama-cpp-python` for inference
4. Fallback: `BillingCareProtocolFallbackRulesEngine` provides rule-based care protocols when AI is unavailable

---

## Tech Stack

### Frontend
| Component | Technology |
|-----------|-----------|
| Language | Java 21 LTS |
| UI Framework | JavaFX 21.0.2 + [AtlantaFX](https://github.com/mkpaz/atlantafx) PrimerDark theme |
| Icons | Ikonli (Material2 + FontAwesome5) |
| Markdown | Flexmark (renders AI chat responses) |
| Animations | Custom `AnimationUtils` |

### Backend
| Component | Technology |
|-----------|-----------|
| Database | SQLite 3.46 (embedded, zero-config) via `sqlite-jdbc` |
| Reports | JasperReports 6.21 + OpenPDF 1.3.40 (replaces iText) |
| Excel Export | Apache POI 5.2.5 |
| Barcode | ZXing 3.5.3 |
| Email | JavaMail (`javax.mail` 1.6.2) via SMTP |
| Password Hashing | jBCrypt 0.4 |
| OS Integration | JNA Platform 5.14 |
| JSON | org.json 20231013 + Gson 2.10.1 |

### External Microservices
| Service | Technology | Port |
|---------|-----------|------|
| AI Engine | Python · Flask · llama-cpp-python · Hugging Face | `:5000` |
| MCP Server | Python · mcp[cli] | stdio |
| WhatsApp Bridge | Node.js · Express · [whatsapp-web.js](https://github.com/pedroslopez/whatsapp-web.js) | `:3001` |

### AI Hardware Acceleration
| Backend | Hardware | Notes |
|---------|----------|-------|
| CUDA | NVIDIA GPUs (RTX/GTX) | Fastest — requires CUDA toolkit |
| DirectML | AMD GPUs (Radeon RX) | Windows — via onnxruntime-directml |
| CPU | Any x86_64 processor | Fallback, always available |

---

## Features

- **POS & Billing** — barcode scanning, generic name search, held orders, Cash/Credit/UPI payments, AI care protocols at checkout
- **Inventory** — real-time stock tracking, low-stock alerts, expiry management, Excel export
- **Customers** — credit (Udhar) balance tracking, loyalty points (earn/redeem)
- **Prescriptions** — digital prescription management
- **Purchases & Suppliers** — purchase order entry, supplier profiles
- **PDF Invoices** — A4 format via JasperReports with care protocol appendix
- **Email Invoice** — send PDF directly via SMTP
- **WhatsApp Invoice** — send PDF via local WhatsApp bridge
- **AI Assistant** — local LLM chat, cloud fallback (Gemini/OpenAI), MCP server with 18 tools
- **Model Store** — download LLMs (Phi-3, TinyLlama, Qwen) from Hugging Face
- **Reports** — daily/weekly/monthly sales, expenses, net profit
- **Administration** — role-based access (Admin, Manager, Pharmacist, Cashier, Staff), attendance tracking
- **Windows Installer** — bundled JRE, no Java install needed on client

---

## File-by-File Documentation

### Entry Points

| File | Purpose | Connects To |
|------|---------|-------------|
| `Launcher.java` | App entry point. Sets up structured logging (`StructuredLogFormatter` + `LogContext`), then launches `MediManageApplication` | → `MediManageApplication` |
| `MediManageApplication.java` | JavaFX `Application`. Initializes DB schema, starts AI engine, loads Login screen. On exit, shuts down AI engine | → `DatabaseUtil`, `PythonEnvironmentManager`, `LoginController` |
| `JasperTester.java` | Dev-only utility to test JasperReports PDF generation | → `ReportService` |

---

### Controllers (Frontend — `controller/`)

Each controller is tied to an `.fxml` view file and handles user interaction for its module.

| File | Purpose | Connects To |
|------|---------|-------------|
| `LoginController` | User authentication. Validates credentials, sets `UserSession` | → `UserDAO`, `PasswordHasher`, `UserSession` |
| `MainShellController` | Sidebar navigation shell. Loads child views via `ViewSwitcher` | → `SidebarManager`, `ViewSwitcher`, all child controllers |
| `DashboardController` | Main hub. Shows KPIs (today's sales, low stock, expiry alerts), bill history tab with reprint/email/WhatsApp. Embeds `ExpensesController` and `ReportsController` | → `DashboardKpiService`, `BillDAO`, `MedicineDAO`, `ReportService`, `EmailService`, `WhatsAppService` |
| `BillingController` | POS checkout screen. Barcode scanning, medicine search, cart management, held orders, payment mode selection, AI care protocol generation | → `BillingService`, `BarcodeService`, `HeldOrderDAO`, `LoyaltyService`, `CustomerService` |
| `InventoryController` | Medicine CRUD. Add/edit/delete medicines, stock management | → `MedicineDAO`, `InventoryService` |
| `CustomersController` | Customer profile management. View/edit profiles, credit balances | → `CustomerDAO`, `CustomerService` |
| `PrescriptionsController` | Digital prescription management | → `PrescriptionDAO` |
| `PurchasesController` | Purchase order entry for restocking | → `PurchaseOrderDAO`, `SupplierDAO`, `MedicineDAO` |
| `SupplierController` | Supplier profile management | → `SupplierDAO` |
| `ExpensesController` | Expense tracking (rent, salaries, utilities) | → `ExpenseDAO` |
| `ReportsController` | Sales reports, AI-generated summaries | → `BillDAO`, `MedicineDAO`, `AIOrchestrator` |
| `AIController` | AI chat interface with Markdown rendering | → `AIOrchestrator`, `InventoryAIService`, `LocalAIService` |
| `ModelStoreController` | Download/manage LLMs from Hugging Face | → `LocalAIService`, `PythonEnvironmentManager` |
| `SettingsController` | System configuration — SMTP, AI hardware, receipt settings, API keys, cloud provider | → `ReceiptSettingsDAO`, `SecureSecretStore`, `CloudApiKeyStore`, `PythonEnvironmentManager` |
| `UsersController` | Staff account management (Admin only) | → `UserDAO`, `PasswordHasher`, `RbacPolicy` |
| `AttendanceController` | Staff attendance tracking | → `AttendanceDAO` |
| `MedicineSearchController` | Reusable medicine search popup (barcode + text) | → `MedicineDAO`, `BarcodeService` |
| `CustomerDisplayController` | Customer-facing display (secondary screen) | → `BillingController` (data binding) |
| `StartupProgressController` | Splash screen with loading progress during app startup | → `MediManageApplication` |

---

### Services (Middle Layer — `service/`)

Services contain business logic and orchestrate between controllers and DAOs.

| File | Purpose | Connects To |
|------|---------|-------------|
| `BillingService` | Core billing logic — add items to cart, validate stock, complete checkout (atomic transaction), generate PDF, trigger AI care protocol | → `BillDAO`, `MedicineDAO`, `CustomerDAO`, `ReportService`, `AIOrchestrator`, `BillingCareProtocolFallbackRulesEngine` |
| `ReportService` | PDF invoice generation via JasperReports. Fills report parameters, exports to PDF | → JasperReports engine, `.jrxml` templates |
| `EmailService` | Sends invoice emails with PDF attachment via SMTP. Uses `javax.mail` | → `SecureSecretStore` (SMTP credentials) |
| `WhatsAppService` | Sends invoice messages + PDF via HTTP to the local Node.js WhatsApp bridge | → WhatsApp bridge at `localhost:3001` |
| `CustomerService` | Customer profile business logic | → `CustomerDAO` |
| `LoyaltyService` | Loyalty points — award (1 pt per ₹100), redeem (100 pts = 5% discount) | → `DatabaseUtil` (direct SQL) |
| `InventoryService` | Inventory business logic, stock validation | → `MedicineDAO` |
| `DashboardKpiService` | Aggregates KPI metrics (sales, low stock, expiry counts) with caching | → `BillDAO`, `MedicineDAO`, `ExpenseDAO` |
| `BarcodeService` | Barcode generation using ZXing. Generates barcode images for medicines | → ZXing library |
| `DatabaseService` | Database initialization and schema setup | → `DatabaseUtil` |
| `AuthService` | Authentication interface | → `UserDAO` |
| `BillingCareProtocolFallbackRulesEngine` | Rule-based fallback when AI is unavailable. Generates generic dosage/care text | — standalone |

---

### AI Services (`service/ai/`)

| File | Purpose | Connects To |
|------|---------|-------------|
| `AIOrchestrator` | Central AI routing. Decides local vs cloud provider, handles retries and fallback | → `LocalAIService`, `AIServiceProvider`, cloud APIs |
| `LocalAIService` | Communicates with the Python Flask AI engine via HTTP (`localhost:5000`). Handles model loading, inference, download | → Flask server |
| `AIAssistantService` | High-level AI prompts — care protocols, inventory analysis, sales summaries | → `AIOrchestrator` |
| `InventoryAIService` | Inventory-specific AI queries — trend analysis, reorder suggestions | → `AIOrchestrator`, `MedicineDAO` |
| `AIService` | AI service interface/contract | — interface |
| `AIServiceProvider` | Singleton factory for AI services | → `AIOrchestrator`, `LocalAIService` |
| `AIInputSafetyGuard` | Sanitizes and validates AI prompt inputs, PII masking | — standalone |
| `PythonEnvironmentManager` | Auto-installs Python venv, installs dependencies, starts/stops the Flask server process | → `PythonDownloader`, Flask server |
| `PythonDownloader` | Downloads Python installer if not present | — standalone |

---

### DAOs (Backend — `dao/`)

DAOs handle all SQL operations against the SQLite database. Each corresponds to a domain entity.

| File | Purpose | Key Tables |
|------|---------|------------|
| `MedicineDAO` | Medicine CRUD, generic name search, stock queries, barcode lookup, expiry alerts, inventory insights | `medicines`, `stock` |
| `BillDAO` | Invoice generation (atomic transaction), sales queries, bill history, AI care protocol storage | `bills`, `bill_items` |
| `CustomerDAO` | Customer CRUD, credit balance management, search | `customers` |
| `ExpenseDAO` | Expense logging and monthly totals | `expenses` |
| `UserDAO` | User authentication, CRUD for staff accounts | `users` |
| `HeldOrderDAO` | Park/resume held orders (serialized as JSON via Gson) | `held_orders` |
| `PrescriptionDAO` | Prescription management | `prescriptions` |
| `PurchaseOrderDAO` | Purchase order entry for restocking | `purchase_orders`, `purchase_order_items` |
| `SupplierDAO` | Supplier profile management | `suppliers` |
| `AttendanceDAO` | Staff attendance logging | `attendance` |
| `LocationDAO` | Pharmacy location/branch management | `locations` |
| `ReceiptSettingsDAO` | Receipt/invoice display settings (header, footer, barcode on/off) | `receipt_settings` or Preferences |

---

### Models (`model/`)

Data classes with JavaFX `Property` fields for UI binding.

| File | Represents |
|------|-----------|
| `Medicine` | Product catalog entry (name, generic name, company, price, stock, expiry) |
| `BillItem` | Single line item in a bill (medicine, qty, price, GST, total) |
| `BillHistoryRecord` | Past bill summary for dashboard history tab |
| `Customer` | Customer profile (name, phone, email, credit balance, loyalty points) |
| `Expense` | Operating cost entry (category, amount, date) |
| `User` | Staff account (username, password hash, role) |
| `UserRole` | Enum: ADMIN, MANAGER, PHARMACIST, CASHIER, STAFF |
| `HeldOrder` | Parked bill (serialized cart items, customer, timestamp) |
| `Prescription` | Prescription record |
| `PurchaseOrder` | Purchase order header (supplier, date, status) |
| `PurchaseOrderItem` | Line item in a purchase order |
| `Supplier` | Supplier profile (name, contact, GST number) |
| `Location` | Pharmacy branch/location |
| `PaymentSplit` | Split payment record (Cash + UPI amounts) |
| `ReceiptSettings` | Receipt display configuration |

---

### Configuration (`config/`)

| File | Purpose |
|------|---------|
| `DatabaseConfig` | SQLite connection management. Dev mode (project root) vs installed mode (`%APPDATA%/MediManage/`) |
| `FeatureFlag` | Feature flag enum (currently: `AI_ASSISTANT`) |
| `FeatureFlags` | Runtime feature flag checks — reads from Preferences |

---

### Security (`security/`)

| File | Purpose |
|------|---------|
| `PasswordHasher` | Bcrypt password hashing (jBCrypt) |
| `RbacPolicy` | Role-based access control — maps `UserRole` to `Permission` sets |
| `Permission` | Permission enum for RBAC |
| `SecureSecretStore` | Encrypts/decrypts sensitive credentials (SMTP passwords, API keys) using Windows DPAPI via JNA |
| `CloudApiKeyStore` | Stores cloud AI provider API keys (Gemini, OpenAI) |
| `LocalAdminTokenManager` | Manages local admin authentication tokens |

---

### Utilities (`util/`)

| File | Purpose |
|------|---------|
| `DatabaseUtil` | Database connection factory + schema initialization from `schema.sql` |
| `UserSession` | Singleton holding current logged-in user ID and role |
| `ViewSwitcher` | Loads FXML views into the main content area |
| `SidebarManager` | Manages sidebar navigation state and active item highlighting |
| `ToastNotification` | In-app toast notification system (success/error/warning popups) |
| `AnimationUtils` | Fade, slide, scale animations for UI transitions |
| `InputValidator` | Input validation helpers (phone, email, numeric fields) |
| `AsyncUiFeedback` | Helpers for async operations with loading spinners on the UI thread |
| `AppExecutors` | Shared thread pool for background tasks |
| `ReportingWindowUtils` | Utility for opening report windows |
| `LogContext` | Correlation ID tracking for structured logging |
| `StructuredLogFormatter` | JSON-formatted log output |

---

### Storage (`storage/`)

| File | Purpose |
|------|---------|
| `StorageBackend` | Interface for data storage operations |
| `StorageBackendConfig` | Configuration for storage backend selection |
| `StorageFactory` | Factory that creates the appropriate storage backend |
| `customer/` | Customer storage implementation |
| `inventory/` | Inventory storage implementation |
| `inmemory/` | In-memory storage for testing |

---

### External: AI Engine (`ai_engine/`)

Python Flask microservice auto-started by the Java app.

| File | Purpose |
|------|---------|
| `server/server.py` | Flask server entry point (port 5000) |
| `server/mcp_server.py` | MCP server entry point — 18 tools for AI agents |
| `app/main.py` | Flask app factory |
| `app/core/hardware.py` | Auto-detects GPU (CUDA/DirectML) or falls back to CPU |
| `app/services/inference.py` | LLM inference via llama-cpp-python (GGUF models) |
| `app/services/download.py` | Model download from Hugging Face with progress tracking |
| `app/services/cloud.py` | Cloud AI provider integration |
| `app/services/prompts.py` | Prompt templates for care protocols, summaries, chat |
| `app/mcp/` | MCP tool definitions (inventory, customers, billing, prescriptions) |

---

### External: WhatsApp Bridge (`whatsapp-server/`)

Node.js Express server for sending WhatsApp messages.

| File | Purpose |
|------|---------|
| `index.js` | Express server — QR auth, `/status`, `/qr`, `/send-message`, `/send-pdf` endpoints |
| `package.json` | Dependencies: express, whatsapp-web.js, multer, cors |

---

## Development Setup

### Prerequisites
- **JDK 21**
- **Maven 3.8+**
- **Python 3.8+** (for AI engine)
- **Node.js 18+** (for WhatsApp bridge)

### Run Locally
```bash
git clone https://github.com/MediManage-Team/MediManage.git
cd MediManage
mvn clean install
mvn javafx:run
```

The app creates `medimanage.db` in the project root when running in dev mode.

### AI Engine
```bash
pip install -r ai_engine/requirements/requirements.txt
```
See [ai_engine/README.md](ai_engine/README.md) for GPU setup.

### WhatsApp Bridge
```bash
cd whatsapp-server
npm install
node index.js
```
Scan the QR code to authenticate.

### Build Windows Installer
```powershell
./build_full_installer.bat
```
Bundles JRE, database, and all dependencies into a single `.exe` installer using jpackage + Inno Setup.

---

## License

MIT License.
