# UML Guide for MediManage Project

## What is UML?
**UML (Unified Modeling Language)** is a standard way to visualize the design of a software system. It helps developers, stakeholders, and team members understand the structure and behavior of an application without diving into code.

Think of UML diagrams as **blueprints** for your software, just like architectural blueprints for a building.

---

## Overview of MediManage System

**MediManage** is a Professional Pharmacy Management System with the following key features:
- **Point of Sale (POS) & Billing**: Fast checkout with barcode scanning and payment processing
- **Inventory Management**: Track medicines, stock levels, and expiry dates
- **Customer Management**: Maintain customer profiles and credit balances
- **Financial Management**: Track expenses and calculate profit/loss
- **Reporting**: Generate PDF invoices and sales reports
- **User Authentication**: Role-based access control (Admin, Manager, Pharmacist, Cashier)

**Technology Stack**: Java 21, JavaFX, SQLite Database, Maven

---

## 1. Use Case Diagram

### What is it?
A **Use Case Diagram** shows **WHO** uses the system and **WHAT** they can do with it. It focuses on the **functionality** from a user's perspective.

### Key Elements:
- **Actors**: People or systems that interact with your application (shown as stick figures)
- **Use Cases**: Actions or functions the actors can perform (shown as ovals)
- **Relationships**: Lines showing how actors interact with use cases

### MediManage Use Case Diagram Description:

#### Actors:
1. **Admin** - Full system access, manages users and configuration
2. **Manager** - Manages inventory, views reports, handles expenses
3. **Pharmacist** - Handles inventory and billing
4. **Cashier** - Performs billing operations only
5. **Customer** - Indirect actor (receives invoices, credit tracking)

#### Primary Use Cases:

**Authentication & Authorization**
- Login to System
- Manage User Accounts (Admin only)
- Change Password

**Billing & Sales**
- Search Medicine (by name/barcode/generic name)
- Add Items to Bill
- Apply Discounts
- Select Payment Mode (Cash/Credit/UPI)
- Generate Invoice
- Print Thermal Receipt
- Print PDF Invoice

**Inventory Management**
- Add New Medicine
- Update Medicine Details
- Delete Medicine
- View Stock Levels
- Set Low Stock Alerts
- Track Expiry Dates
- Export Inventory to Excel

**Customer Management**
- Add New Customer
- Search Customer by Phone
- View Customer Details
- Track Credit Balance (Udhar)
- Update Customer Information

**Financial Management**
- Add Expense
- View Expenses by Category
- Calculate Net Profit
- View Daily/Monthly/Yearly Sales

**Reporting**
- Generate Sales Report
- View Bill History
- Reprint Old Invoice
- Export Reports to PDF

#### Relationships:
- **Include**: Some use cases include others (e.g., "Generate Invoice" includes "Update Stock")
- **Extend**: Optional behaviors (e.g., "Print Thermal Receipt" extends "Generate Invoice")
- **Generalization**: Pharmacist and Manager can do everything Cashier can do

---

## 2. Class Diagram

### What is it?
A **Class Diagram** shows the **structure** of your system - the different classes, their attributes (data), methods (functions), and how they relate to each other. It's like showing the **skeleton** of your code.

### Key Elements:
- **Classes**: Represented as rectangles divided into 3 sections:
  - Class Name (top)
  - Attributes/Properties (middle)
  - Methods/Operations (bottom)
- **Relationships**:
  - **Association**: Simple connection between classes
  - **Aggregation**: "Has-a" relationship (weak ownership)
  - **Composition**: "Part-of" relationship (strong ownership)
  - **Inheritance**: "Is-a" relationship (parent-child)
  - **Dependency**: Uses another class temporarily

### MediManage Class Diagram Description:

#### Model Classes (Entity/Data Classes):

**Medicine**
- Attributes:
  - `id: String`
  - `name: String`
  - `genericName: String`
  - `company: String`
  - `price: Double`
  - `expiryDate: LocalDate`
  - `barcode: String`
  - `category: String`
  - `stock: Integer`
- Methods:
  - `getName(): String`
  - `setPrice(price: Double): void`
  - `isExpired(): boolean`

**Customer**
- Attributes:
  - `id: String`
  - `name: String`
  - `phone: String`
  - `email: String`
  - `address: String`
  - `currentBalance: Double`
  - `createdDate: LocalDateTime`
- Methods:
  - `addCredit(amount: Double): void`
  - `deductCredit(amount: Double): void`
  - `getPhone(): String`

**BillItem**
- Attributes:
  - `id: Integer`
  - `billId: String`
  - `medicineId: String`
  - `medicineName: String`
  - `quantity: Integer`
  - `price: Double`
  - `total: Double`
- Methods:
  - `calculateTotal(): Double`

**Bill** (Not shown in your codebase, but implied)
- Attributes:
  - `id: String`
  - `date: LocalDateTime`
  - `customerId: String`
  - `userId: String`
  - `subtotal: Double`
  - `discount: Double`
  - `total: Double`
  - `paymentMode: String`
- Methods:
  - `addItem(item: BillItem): void`
  - `calculateTotal(): Double`

**User**
- Attributes:
  - `id: Integer`
  - `username: String`
  - `password: String`
  - `role: UserRole`
  - `createdAt: LocalDateTime`
- Methods:
  - `authenticate(password: String): boolean`

**UserRole** (Enum)
- Values: `ADMIN`, `MANAGER`, `PHARMACIST`, `CASHIER`

**Expense**
- Attributes:
  - `id: Integer`
  - `category: String`
  - `amount: Double`
  - `description: String`
  - `date: LocalDate`
- Methods:
  - `getAmount(): Double`

#### DAO Classes (Data Access Layer):

**MedicineDAO**
- Methods:
  - `getAllMedicines(): List<Medicine>`
  - `searchByName(name: String): List<Medicine>`
  - `searchByGeneric(generic: String): List<Medicine>`
  - `searchByBarcode(barcode: String): Medicine`
  - `addMedicine(medicine: Medicine): void`
  - `updateMedicine(medicine: Medicine): void`
  - `deleteMedicine(id: String): void`
  - `getLowStockItems(): List<Medicine>`
  - `getExpiringItems(days: int): List<Medicine>`

**CustomerDAO**
- Methods:
  - `getAllCustomers(): List<Customer>`
  - `searchByPhone(phone: String): Customer`
  - `addCustomer(customer: Customer): void`
  - `updateCustomer(customer: Customer): void`
  - `updateBalance(customerId: String, amount: Double): void`

**BillDAO**
- Methods:
  - `generateInvoice(bill: Bill, items: List<BillItem>): String`
  - `getBillHistory(): List<Bill>`
  - `getBillById(id: String): Bill`
  - `getDailySales(date: LocalDate): Double`
  - `getMonthlySales(month: int, year: int): Double`

**UserDAO**
- Methods:
  - `authenticate(username: String, password: String): User`
  - `getAllUsers(): List<User>`
  - `addUser(user: User): void`
  - `updateUser(user: User): void`
  - `deleteUser(id: Integer): void`

**ExpenseDAO**
- Methods:
  - `addExpense(expense: Expense): void`
  - `getAllExpenses(): List<Expense>`
  - `getExpensesByMonth(month: int, year: int): List<Expense>`
  - `getTotalExpenses(startDate: LocalDate, endDate: LocalDate): Double`

#### Service Classes (Business Logic):

**AuthService**
- Methods:
  - `login(username: String, password: String): User`
  - `logout(): void`
  - `getCurrentUser(): User`

**DatabaseService**
- Methods:
  - `initializeDatabase(): void`
  - `backupDatabase(): void`

**ReportService**
- Methods:
  - `generatePDFInvoice(billId: String): void`
  - `generateSalesReport(startDate: LocalDate, endDate: LocalDate): void`
  - `printThermalReceipt(bill: Bill): void`

#### Controller Classes (UI Layer):

**LoginController**
- Dependencies: AuthService, UserDAO
- Methods:
  - `handleLogin(): void`
  - `validateCredentials(): boolean`

**DashboardController**
- Dependencies: MedicineDAO, BillDAO, ExpenseDAO
- Methods:
  - `loadKPIs(): void`
  - `loadExpiryAlerts(): void`
  - `calculateNetProfit(): Double`

**BillingController**
- Dependencies: MedicineDAO, CustomerDAO, BillDAO, ReportService
- Methods:
  - `searchMedicine(query: String): void`
  - `addItemToBill(medicine: Medicine): void`
  - `handleCheckout(): void`
  - `processPayment(mode: String): void`

**InventoryController**
- Dependencies: MedicineDAO
- Methods:
  - `loadInventory(): void`
  - `addMedicine(): void`
  - `updateMedicine(): void`
  - `deleteMedicine(): void`
  - `exportToExcel(): void`

**CustomersController**
- Dependencies: CustomerDAO
- Methods:
  - `loadCustomers(): void`
  - `addCustomer(): void`
  - `searchCustomer(phone: String): void`

#### Configuration Classes:

**DatabaseConfig**
- Methods:
  - `getConnection(): Connection`
  - `getConnectionString(): String`
  - `isDevMode(): boolean`

#### Relationships:

1. **Controllers → Services** (Dependency - uses)
2. **Services → DAOs** (Dependency - uses)
3. **DAOs → Models** (Creates and manipulates)
4. **DAOs → DatabaseConfig** (Dependency - uses for connections)
5. **Bill → BillItem** (Composition - bill contains items)
6. **User → UserRole** (Association - has a role)

---

## 3. Sequence Diagram

### What is it?
A **Sequence Diagram** shows **HOW** objects interact with each other over **TIME**. It shows the **order** of method calls and messages between different components.

### Key Elements:
- **Actors/Objects**: Shown at the top (boxes)
- **Lifelines**: Vertical dashed lines showing object existence
- **Messages**: Arrows showing method calls between objects
- **Activation Boxes**: Rectangles on lifelines showing when object is active
- **Return Messages**: Dashed arrows showing return values

### MediManage Sequence Diagrams Description:

We'll create sequences for the most important workflows:

#### Sequence 1: User Login Flow

**Participants**: User → LoginController → AuthService → UserDAO → DatabaseConfig → Database

**Flow**:
1. User enters username and password
2. User clicks "Login" button
3. LoginController calls `handleLogin()`
4. LoginController calls `AuthService.login(username, password)`
5. AuthService calls `UserDAO.authenticate(username, password)`
6. UserDAO calls `DatabaseConfig.getConnection()`
7. DatabaseConfig returns Connection
8. UserDAO executes SQL query to find user
9. Database returns user record
10. UserDAO validates password
11. UserDAO returns User object (or null)
12. AuthService stores user in session
13. AuthService returns User to Controller
14. LoginController navigates to Dashboard
15. Dashboard view is displayed to User

#### Sequence 2: Generate Invoice (Billing Flow)

**Participants**: Cashier → BillingController → MedicineDAO → CustomerDAO → BillDAO → DatabaseConfig → Database → ReportService

**Flow**:
1. Cashier scans barcode/searches medicine
2. BillingController calls `searchMedicine(query)`
3. BillingController calls `MedicineDAO.searchByBarcode(barcode)`
4. MedicineDAO queries Database
5. Database returns Medicine object
6. BillingController adds medicine to bill table
7. Cashier enters customer phone
8. BillingController calls `CustomerDAO.searchByPhone(phone)`
9. CustomerDAO queries Database
10. Database returns Customer (or null)
11. Cashier selects payment mode and clicks "Checkout"
12. BillingController calls `BillDAO.generateInvoice(bill, items)`
13. BillDAO begins database transaction
14. BillDAO inserts into `bills` table
15. BillDAO inserts all items into `bill_items` table
16. BillDAO updates `stock` table (decrements quantity)
17. If payment mode is "Credit":
    - BillDAO updates customer balance
18. BillDAO commits transaction
19. BillDAO returns bill ID
20. BillingController calls `ReportService.generatePDFInvoice(billId)`
21. ReportService generates PDF
22. BillingController calls `ReportService.printThermalReceipt(bill)`
23. ReportService sends to printer
24. Invoice and receipt are delivered to Cashier

#### Sequence 3: Add Medicine to Inventory

**Participants**: Manager → InventoryController → MedicineDAO → Database

**Flow**:
1. Manager fills medicine form (name, price, expiry, etc.)
2. Manager clicks "Add Medicine"
3. InventoryController validates input
4. InventoryController creates Medicine object
5. InventoryController calls `MedicineDAO.addMedicine(medicine)`
6. MedicineDAO calls `DatabaseConfig.getConnection()`
7. DatabaseConfig returns Connection
8. MedicineDAO inserts into `medicines` table
9. MedicineDAO inserts into `stock` table (initial quantity)
10. Database confirms insertion
11. MedicineDAO returns success
12. InventoryController refreshes inventory table
13. Success message shown to Manager

#### Sequence 4: View Dashboard KPIs

**Participants**: User → DashboardController → BillDAO → ExpenseDAO → MedicineDAO → Database

**Flow**:
1. User navigates to Dashboard
2. DashboardController calls `loadKPIs()`
3. DashboardController calls `BillDAO.getDailySales(today)`
4. BillDAO queries Database
5. Database returns total sales
6. DashboardController calls `BillDAO.getMonthlySales(month, year)`
7. Database returns monthly sales
8. DashboardController calls `ExpenseDAO.getTotalExpenses(startDate, endDate)`
9. Database returns total expenses
10. DashboardController calculates Net Profit (Sales - Expenses)
11. DashboardController calls `MedicineDAO.getLowStockItems()`
12. Database returns low stock medicines
13. DashboardController calls `MedicineDAO.getExpiringItems(30)`
14. Database returns expiring medicines
15. DashboardController updates UI with all KPIs
16. Dashboard displayed to User

---

## 4. Collaboration Diagram (Communication Diagram)

### What is it?
A **Collaboration Diagram** (also called Communication Diagram) shows the **same information** as a Sequence Diagram but focuses on the **relationships** between objects rather than the time sequence. It emphasizes the **structural organization**.

### Key Elements:
- **Objects**: Shown as rectangles
- **Links**: Lines showing relationships between objects
- **Messages**: Numbered arrows on links showing sequence
- **Message Numbers**: 1, 2, 3... or 1.1, 1.2... for nested calls

### MediManage Collaboration Diagram Description:

We'll create collaboration diagrams for the same key workflows:

#### Collaboration 1: Generate Invoice Workflow

**Objects and Links**:
```
[Cashier] ←→ [BillingController]
[BillingController] ←→ [MedicineDAO]
[BillingController] ←→ [CustomerDAO]
[BillingController] ←→ [BillDAO]
[BillingController] ←→ [ReportService]
[MedicineDAO] ←→ [Database]
[CustomerDAO] ←→ [Database]
[BillDAO] ←→ [Database]
[ReportService] ←→ [Printer]
```

**Messages** (numbered in sequence):
1. `searchMedicine(barcode)` - Cashier → BillingController
2. `searchByBarcode(barcode)` - BillingController → MedicineDAO
3. `executeQuery()` - MedicineDAO → Database
4. `Medicine object` - Database → MedicineDAO
5. `Medicine object` - MedicineDAO → BillingController
6. `addItemToTable()` - BillingController → BillingController
7. `searchCustomer(phone)` - Cashier → BillingController
8. `searchByPhone(phone)` - BillingController → CustomerDAO
9. `executeQuery()` - CustomerDAO → Database
10. `Customer object` - Database → CustomerDAO
11. `Customer object` - CustomerDAO → BillingController
12. `checkout(paymentMode)` - Cashier → BillingController
13. `generateInvoice(bill, items)` - BillingController → BillDAO
14. `beginTransaction()` - BillDAO → Database
15. `insertBill()` - BillDAO → Database
16. `insertBillItems()` - BillDAO → Database
17. `updateStock()` - BillDAO → Database
18. `commit()` - BillDAO → Database
19. `billId` - BillDAO → BillingController
20. `generatePDFInvoice(billId)` - BillingController → ReportService
21. `printThermalReceipt(bill)` - BillingController → ReportService
22. `print()` - ReportService → Printer

#### Collaboration 2: Login Authentication

**Objects and Links**:
```
[User] ←→ [LoginController]
[LoginController] ←→ [AuthService]
[AuthService] ←→ [UserDAO]
[UserDAO] ←→ [DatabaseConfig]
[DatabaseConfig] ←→ [Database]
```

**Messages**:
1. `login(username, password)` - User → LoginController
2. `authenticate(username, password)` - LoginController → AuthService
3. `findUser(username, password)` - AuthService → UserDAO
4. `getConnection()` - UserDAO → DatabaseConfig
5. `Connection` - DatabaseConfig → UserDAO
6. `executeQuery()` - UserDAO → Database
7. `User record` - Database → UserDAO
8. `User object` - UserDAO → AuthService
9. `storeSession(user)` - AuthService → AuthService
10. `User object` - AuthService → LoginController
11. `navigateToDashboard()` - LoginController → LoginController

---

## 5. Deployment Diagram

### What is it?
A **Deployment Diagram** shows the **physical architecture** of your system - how the software is deployed on hardware. It shows servers, devices, and how components are distributed.

### Key Elements:
- **Nodes**: Physical devices or execution environments (shown as 3D boxes)
- **Artifacts**: Software components deployed on nodes (shown as documents)
- **Communication Paths**: Connections between nodes

### MediManage Deployment Diagram Description:

#### Nodes:

**1. Client Workstation (Windows PC)**
- **Hardware**: Standard Desktop/Laptop PC
- **OS**: Windows 10/11
- **Components Deployed**:
  - **MediManage.exe** - JavaFX Application
  - **Bundled JRE 21** - Java Runtime Environment
  - **SQLite Database** - `medimanage.db` file
  - **Configuration Files** - Application settings
  - **PDF Generator** - JasperReports library
  - **Barcode Scanner Driver** - USB/Serial driver
  
- **Installation Location**:
  - Program: `C:\Program Files\MediManage\`
  - Database: `C:\Program Files\MediManage\runtime\db\medimanage.db`
  - User Data: `%APPDATA%\MediManage\`

**2. Thermal Printer (Hardware Device)**
- **Type**: POS-58 Thermal Printer
- **Connection**: USB / Network (Ethernet/WiFi)
- **Protocol**: ESC/POS commands
- **Role**: Print receipts

**3. Barcode Scanner (Hardware Device)**
- **Type**: USB Barcode Scanner
- **Connection**: USB (acts as keyboard input)
- **Role**: Scan medicine barcodes

#### Deployment Architecture:

```
┌─────────────────────────────────────────┐
│   Windows PC (Client Workstation)      │
│  ┌───────────────────────────────────┐ │
│  │  MediManage Application           │ │
│  │  ┌─────────────────────────────┐  │ │
│  │  │  JavaFX UI Layer             │  │ │
│  │  │  - Login Screen               │  │ │
│  │  │  - Dashboard                  │  │ │
│  │  │  - Billing Module             │  │ │
│  │  │  - Inventory Module           │  │ │
│  │  └─────────────────────────────┘  │ │
│  │  ┌─────────────────────────────┐  │ │
│  │  │  Business Logic Layer        │  │ │
│  │  │  - Controllers                │  │ │
│  │  │  - Services                   │  │ │
│  │  │  - DAOs                       │  │ │
│  │  └─────────────────────────────┘  │ │
│  │  ┌─────────────────────────────┐  │ │
│  │  │  Data Layer                  │  │ │
│  │  │  - SQLite Database Engine    │  │ │
│  │  │  - medimanage.db             │  │ │
│  │  └─────────────────────────────┘  │ │
│  └───────────────────────────────────┘ │
│  ┌───────────────────────────────────┐ │
│  │  Bundled JRE 21                   │ │
│  │  - Java Runtime Environment       │ │
│  └───────────────────────────────────┘ │
└─────────┬───────────────┬─────────────┘
          │               │
  USB     │               │  USB/Network
          │               │
    ┌─────▼─────┐   ┌────▼──────┐
    │  Barcode  │   │  Thermal  │
    │  Scanner  │   │  Printer  │
    └───────────┘   └───────────┘
```

#### Deployment Characteristics:

**Standalone Desktop Application**:
- **No Server Required**: All components run on single workstation
- **Embedded Database**: SQLite database is file-based, no separate database server
- **Self-Contained**: Bundled JRE eliminates Java installation requirement

**Installation Package**:
- **Installer**: `MediManage_Setup_0.1.5.exe` (Inno Setup)
- **Size**: ~120 MB (includes JRE + Application + Pre-populated DB)
- **Distribution**: USB drive, Network share, or Download

**Hardware Requirements**:
- **Minimum**:
  - CPU: Dual-core 2.0 GHz
  - RAM: 4 GB
  - Storage: 500 MB free space
  - OS: Windows 10 or higher
- **Peripherals** (Optional):
  - Thermal Printer (POS-58)
  - USB Barcode Scanner

**Network Configuration**:
- **Offline Mode**: Fully functional without internet
- **Printer Network**: May use network for shared thermal printer
- **Future**: Could expand to client-server for multi-user

---

## 6. Component Diagram (Module Diagram)

### What is it?
A **Component Diagram** shows the **organization** and **dependencies** between software components/modules. It's about how your code is structured into packages and how they depend on each other.

### Key Elements:
- **Components**: Modules/packages (shown as rectangles with component icon)
- **Interfaces**: Contracts that components provide/require (lollipop notation)
- **Dependencies**: How components use each other (dashed arrows)

### MediManage Component Diagram Description:

#### Components (Packages):

**1. UI Layer (`controllers/`)**
- **Purpose**: JavaFX controllers for user interface
- **Components**:
  - `LoginController`
  - `DashboardController`
  - `BillingController`
  - `InventoryController`
  - `CustomersController`
  - `ReportsController`
  - `UsersController`
  - `SettingsController`
  - `MainShellController`
  - `MedicineSearchController`
- **Provides**: User Interface
- **Requires**: Services, DAOs
- **Dependencies**: 
  - Uses → Service Layer
  - Uses → DAO Layer
  - Uses → Model Layer

**2. Service Layer (`service/`)**
- **Purpose**: Business logic and cross-cutting concerns
- **Components**:
  - `AuthService` - Authentication and session management
  - `ReportService` - PDF generation and printing
  - `DatabaseService` - Database initialization and maintenance
- **Provides**: Business Operations
- **Requires**: DAOs, External Libraries
- **Dependencies**:
  - Uses → DAO Layer
  - Uses → Model Layer
  - Uses → External Libraries (JasperReports, OpenPDF)

**3. DAO Layer (`dao/`)**
- **Purpose**: Data access and persistence logic
- **Components**:
  - `MedicineDAO` - Medicine CRUD operations
  - `CustomerDAO` - Customer management
  - `BillDAO` - Invoice generation and sales queries
  - `UserDAO` - User authentication and management
  - `ExpenseDAO` - Expense tracking
- **Provides**: Data Access Interface
- **Requires**: Database Connection, Models
- **Dependencies**:
  - Uses → Configuration Layer
  - Uses → Model Layer
  - Uses → SQLite JDBC Driver

**4. Model Layer (`model/`)**
- **Purpose**: Data structures and domain entities
- **Components**:
  - `Medicine` - Medicine entity
  - `Customer` - Customer entity
  - `BillItem` - Bill line item
  - `User` - User account
  - `Expense` - Expense record
  - `UserRole` - Role enumeration
- **Provides**: Domain Objects
- **Requires**: None (Plain Java classes with JavaFX properties)
- **Dependencies**: None

**5. Configuration Layer (`config/`)**
- **Purpose**: System configuration and settings
- **Components**:
  - `DatabaseConfig` - Database connection management
- **Provides**: Configuration Services
- **Requires**: None
- **Dependencies**:
  - Uses → SQLite JDBC Driver

**6. Utility Layer (`util/`)**
- **Purpose**: Helper classes and common utilities
- **Components**:
  - `UserSession` - Session management
  - `PDFUtil` - PDF generation helpers
  - `SidebarManager` - Navigation helper
  - `DatabaseUtil` - Database initialization
- **Provides**: Utility Functions
- **Requires**: Various (depending on utility)

**7. External Libraries (Dependencies)**
- **JavaFX 21**: UI framework
- **SQLite JDBC**: Database driver
- **JasperReports**: Report generation
- **OpenPDF**: PDF generation
- **Apache POI**: Excel export
- **ZXing**: Barcode processing
- **AtlantaFX**: UI theming

#### Component Architecture Diagram:

```
┌─────────────────────────────────────────────────┐
│          UI Layer (controllers/)                │
│  LoginController, DashboardController, etc.     │
└──────────────┬──────────────────────────────────┘
               │ uses
               ▼
┌─────────────────────────────────────────────────┐
│          Service Layer (service/)               │
│  AuthService, ReportService, DatabaseService    │
└──────────────┬──────────────────────────────────┘
               │ uses
               ▼
┌─────────────────────────────────────────────────┐
│             DAO Layer (dao/)                    │
│  MedicineDAO, BillDAO, CustomerDAO, etc.        │
└──────────────┬──────────────────────────────────┘
               │ uses
        ┌──────┴──────┐
        ▼             ▼
┌──────────────┐ ┌────────────────────────┐
│ Model Layer  │ │ Configuration Layer    │
│  (model/)    │ │    (config/)            │
│              │ │  DatabaseConfig         │
└──────────────┘ └────────────┬───────────┘
                              │ uses
                              ▼
                    ┌──────────────────┐
                    │ External Libs    │
                    │ - JavaFX          │
                    │ - SQLite JDBC     │
                    │ - JasperReports   │
                    │ - OpenPDF         │
                    │ - Apache POI      │
                    │ - ZXing           │
                    └──────────────────┘
```

#### Dependency Flow:
- **One-way Dependencies**: Higher layers depend on lower layers, never reverse
- **No Circular Dependencies**: Clean architecture with clear separation
- **Layered Architecture**: Presentation → Business → Data → Persistence

#### Module Responsibilities:

**Presentation Layer** (UI Controllers):
- Handle user input
- Display data to user
- Validate form inputs
- Navigate between views

**Business Logic Layer** (Services):
- Implement business rules
- Coordinate between DAOs
- Handle transactions
- Generate reports

**Data Access Layer** (DAOs):
- Execute SQL queries
- Map database records to objects
- Handle database transactions
- Manage connections

**Domain Model Layer** (Models):
- Represent business entities
- Encapsulate data
- Provide JavaFX properties for UI binding

**Infrastructure Layer** (Config + Utils):
- Manage system configuration
- Provide common utilities
- Handle database initialization

---

## Summary: When to Use Each Diagram

| Diagram Type | Purpose | Best For |
|-------------|---------|----------|
| **Use Case** | Show WHAT the system does | Understanding requirements, stakeholder communication |
| **Class** | Show system STRUCTURE | Understanding code organization, database design |
| **Sequence** | Show HOW things work over TIME | Understanding workflows, debugging logic |
| **Collaboration** | Show RELATIONSHIPS in workflows | Alternative view of interactions, simpler than sequence |
| **Deployment** | Show WHERE code runs | Understanding physical architecture, installation |
| **Component** | Show code ORGANIZATION | Understanding module structure, dependencies |

---

## Next Steps

Now that you have the descriptions, I can create visual diagrams for each of these using:
1. **Mermaid diagrams** (text-based, can be embedded in markdown)
2. **Image generation** (visual diagrams for your PPT)

Would you like me to create the actual visual diagrams now?
