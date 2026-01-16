# MediManage - Professional Pharmacy Management System

A robust, enterprise-grade Desktop application for managing pharmacy inventory, billing, expenses, and customer records. Built with **JavaFX** and **SQLite**, optimized for performance, reliability, and ease of use.

![MediManage](https://via.placeholder.com/800x400?text=MediManage+Dashboard) *Note: Add actual screenshot here*

## 🚀 Key Features

### 🛒 Point of Sale (POS) & Billing
- **Fast Billing**: Keyboard-optimized checkout flow for high-volume environments.
- **Barcode Scanner Integration**: Auto-focus search (`ZXing`), "Scan to Add", and smart quantity increments.
- **Payment Modes**: Support for **Cash**, **Credit (Udhar)**, and UPI logging.
- **Thermal Printing**: Automatic 58mm/80mm receipt printing.
- **Substitute Search**: Find medicines by **Generic Name** (e.g., search "Paracetamol" to find "Calpol").

### 📦 Inventory & Stock
- **Real-time Tracking**: Low stock alerts and KPI dashboard.
- **Expiry Management**: Dedicated "Expiry Alerts" tab for medicines expiring in < 30 days.
- **Excel Export**: Export full inventory to `.xlsx` using **Apache POI**.

### 💰 Financial Management
- **Expense Manager**: Track operating costs (Rent, Salaries, Utilities).
- **Net Profit Calculation**: Real-time simple P&L (Gross Profit - Expenses).
- **Customer Credit**: Track "Udhar" balances and customer debt.

### 📅 Reporting & Invoices
- **PDF Invoices**: Generate A4 invoices with **JasperReports**.
- **Bill History**: View, reprint, or share past invoices.

---

## 🛠️ Technology Stack

- **Language**: Java 21 LTS
- **UI Framework**: JavaFX 21 + [AtlantaFX](https://github.com/mkpaz/atlantafx) (PrimerLight Theme)
- **Database**: SQLite (Embedded, Zero-configuration) with `sqlite-jdbc`
- **Build Tool**: Maven
- **Reporting**: JasperReports 6.21
- **Utilities**:
    - **Apache POI**: Excel Export
    - **ZXing**: Barcode Scanning
    - **iText**: PDF Generation

---

## 📂 Project Structure & Architecture

The application follows a standard **MVC (Model-View-Controller)** architecture with a dedicated **DAO (Data Access Object)** layer for database interactions.

### Directory Layout
```text
src/main/java/org/example/MediManage/
├── config/           # Configuration classes (DatabaseConfig)
├── dao/              # Data Access Objects (SQL Logic)
├── model/            # POJOs / Data Transfer Objects
├── service/          # Business Logic Services (Reports, Print)
├── util/             # Helpers (PDFUtil, UserSession)
├── *Controller.java  # JavaFX UI Controllers
└── Launcher.java     # Application Entry Components
```

### Key Components

#### 1. Configuration (`config/`)
- **`DatabaseConfig.java`**: Manages SQLite connection reuse and file location.
    - *Dev Mode Check*: Automatically detects if running in IDE (uses local `medimanage.db`) vs. Installed (uses `%APPDATA%/MediManage/medimanage.db`) to prevent permission errors in "Program Files".

#### 2. Models (`model/`)
- **`Medicine`**, **`Customer`**, **`Bill`**, **`User`**, **`Expense`**: represent DB entities with JavaFX Properties for UI binding.

#### 3. DAOs (`dao/`) - The Data Layer
- **`BillDAO`**: Handles Invoice generation (Transactional), Daily Sales queries.
- **`MedicineDAO`**: Manages Inventory, Stock updates, and Generic Search.
- **`CustomerDAO`**: Manages Customer profiles and Credit Balances.
- **`ExpenseDAO`**: Logs expenses and calculates monthly totals.

#### 4. Controllers (`*Controller.java`)
- **`DashboardController`**: Main Hub. Loads KPIs, Expiry Alerts, and navigation.
- **`BillingController`**: Handles the POS flow, Scanner input payment dialogs.
- **`InventoryController`**: CRUD operations for medicines.

---

## 🗄️ Database Schema (SQLite)

The application uses a relational SQLite database initialized programmatically via `DBUtil.java` and `schema.sql`.

### Tables
1.  **`users`**: Auth credentials (`username`, `password`, `role`).
2.  **`medicines`**: Product catalog (`name`, `generic_name`, `company`, `price`, `expiry_date`).
3.  **`stock`**: Quantity tracking (`medicine_id`, `quantity`).
4.  **`customers`**: Profiles (`name`, `phone`, `current_balance`, `details...`).
5.  **`bills`**: Invoice headers (`total`, `date`, `customer_id`, `payment_mode`).
6.  **`bill_items`**: Invoice line items (`qty`, `price`, `total`).
7.  **`expenses`**: Operating costs (`category`, `amount`, `date`).

---

## 💻 Development Setup

### Prerequisites
1.  **Java Development Kit (JDK) 21**
2.  **Maven** (3.8+)
3.  **IDE**: IntelliJ IDEA (Recommended) or Eclipse

### How to Run Locally
1.  **Clone the Repository**:
    ```bash
    git clone https://github.com/your-repo/MediManage.git
    cd MediManage
    ```
2.  **Build**:
    ```bash
    mvn clean install
    ```
3.  **Run**:
    ```bash
    mvn javafx:run
    ```
    *Note*: In Dev mode, the app creates/uses `medimanage.db` in the project root.

### Building the Installer (.exe)
To create a distributable Windows installer with bundled JRE:
1. Ensure **Inno Setup** is installed and allowed in your PATH or update the path in `build_full_installer.bat`.
2. Run the build script:
    ```powershell
    ./build_full_installer.bat
    ```
    *This script will:*
    1. Clean and Build the project with Maven.
    2. Use `jpackage` to create a bundled runtime image (JDK 21).
    3. Use `Inno Setup` to compile the final `MediManage_Setup_x.x.x.exe`.
    
    **Output Location**: `Output/MediManage_Setup_0.1.5.exe`

---

## 🔄 Data Flow Example: Generating a Bill (Transaction Logic)

1.  **UI**: User scans a barcode in `BillingController` or searches by **Generic Name**.
2.  **Add**: Item added to `TableView` (`ObservableList`).
3.  **Checkout**: User clicks "Checkout", selects Payment Mode (Cash/Credit/UPI).
4.  **Transaction**: `BillDAO.generateInvoice()` starts a Atomic DB Transaction:
    - **Validates** User ID (Fallback to Admin if session stale).
    - Insert into `bills`.
    - Insert into `bill_items` (Batch Insert).
    - Update `stock` (Decrement Quantity).
    - **Credit Logic**: If Mode is 'Credit', executes SQL Update on `customers` balance within the **same connection** to prevent locking.
    - `commit()` transaction.
5.  **Output**: PDF generated via `ReportService` and Thermal Receipt printed.

---

## 🧩 Layouts & UI

- **`main-shell-view.fxml`**: The sidebar navigation container.
- **`dashboard-view.fxml`**: High-level KPIs and Tabs (Expenses, Expiry Alerts).
- **`billing-view.fxml`**: Split-pane design (Search/Table on left, Totals/Actions on right).
- **Login UI**: Role-based access (Admin, Manager, Pharmacist, Cashier). *Staff role hidden by default.*

---

## 📝 License
MIT License. See `LICENSE.txt` for details.
