# MediManage UML Diagrams

This document contains all UML diagrams for the MediManage project in Mermaid format. You can:
1. **View these directly** in any Markdown viewer that supports Mermaid (GitHub, VS Code with Mermaid extension)
2. **Convert to images** using online tools like https://mermaid.live or https://mermaid.ink
3. **Copy to PowerPoint** by taking screenshots or exporting as PNG/SVG

---

## 1. Use Case Diagram

```mermaid
graph TB
    %% Actors
    Admin([Admin])
    Manager([Manager])
    Pharmacist([Pharmacist])
    Cashier([Cashier])
    Customer([Customer])
    
    %% Authentication Use Cases
    subgraph Authentication
        UC1(Login to System)
        UC2(Manage User Accounts)
        UC3(Change Password)
    end
    
    %% Billing Use Cases
    subgraph "Billing & Sales"
        UC4(Search Medicine)
        UC5(Add Items to Bill)
        UC6(Apply Discounts)
        UC7(Select Payment Mode)
        UC8(Generate Invoice)
        UC9(Print Thermal Receipt)
        UC10(Print PDF Invoice)
    end
    
    %% Inventory Use Cases
    subgraph "Inventory Management"
        UC11(Add New Medicine)
        UC12(Update Medicine Details)
        UC13(Delete Medicine)
        UC14(View Stock Levels)
        UC15(Set Low Stock Alerts)
        UC16(Track Expiry Dates)
        UC17(Export to Excel)
    end
    
    %% Customer Use Cases
    subgraph "Customer Management"
        UC18(Add New Customer)
        UC19(Search Customer)
        UC20(View Customer Details)
        UC21(Track Credit Balance)
    end
    
    %% Financial Use Cases
    subgraph "Financial Management"
        UC22(Add Expense)
        UC23(View Expenses)
        UC24(Calculate Net Profit)
        UC25(View Sales Reports)
    end
    
    %% Reporting Use Cases
    subgraph Reporting
        UC26(Generate Sales Report)
        UC27(View Bill History)
        UC28(Reprint Invoice)
    end
    
    %% Admin connections
    Admin --> UC1
    Admin --> UC2
    Admin --> UC3
    Admin --> UC22
    Admin --> UC23
    Admin --> UC24
    Admin --> UC25
    Admin --> UC26
    
    %% Manager connections
    Manager --> UC1
    Manager --> UC3
    Manager --> UC11
    Manager --> UC12
    Manager --> UC13
    Manager --> UC14
    Manager --> UC15
    Manager --> UC16
    Manager --> UC17
    Manager --> UC22
    Manager --> UC23
    Manager --> UC24
    Manager --> UC25
    Manager --> UC26
    Manager --> UC4
    Manager --> UC5
    Manager --> UC8
    
    %% Pharmacist connections
    Pharmacist --> UC1
    Pharmacist --> UC3
    Pharmacist --> UC11
    Pharmacist --> UC12
    Pharmacist --> UC14
    Pharmacist --> UC16
    Pharmacist --> UC4
    Pharmacist --> UC5
    Pharmacist --> UC8
    
    %% Cashier connections
    Cashier --> UC1
    Cashier --> UC3
    Cashier --> UC4
    Cashier --> UC5
    Cashier --> UC6
    Cashier --> UC7
    Cashier --> UC8
    Cashier --> UC9
    Cashier --> UC10
    Cashier --> UC18
    Cashier --> UC19
    Cashier --> UC20
    Cashier --> UC21
    Cashier --> UC27
    Cashier --> UC28
    
    %% Customer connections
    Customer -.-> UC10
    Customer -.-> UC21
    
    %% Include/Extend relationships
    UC8 -.-> UC9
    UC8 --> UC5

    style Admin fill:#ff6b6b
    style Manager fill:#4ecdc4
    style Pharmacist fill:#45b7d1
    style Cashier fill:#96ceb4
    style Customer fill:#dfe6e9
```

---

## 2. Class Diagram - Main Model Classes

```mermaid
classDiagram
    %% Model Classes
    class Medicine {
        -String id
        -String name
        -String genericName
        -String company
        -Double price
        -LocalDate expiryDate
        -String barcode
        -String category
        -Integer stock
        +getName() String
        +setPrice(Double) void
        +isExpired() boolean
        +getStock() Integer
    }
    
    class Customer {
        -String id
        -String name
        -String phone
        -String email
        -String address
        -Double currentBalance
        -LocalDateTime createdDate
        +addCredit(Double) void
        +deductCredit(Double) void
        +getPhone() String
        +getCurrentBalance() Double
    }
    
    class BillItem {
        -Integer id
        -String billId
        -String medicineId
        -String medicineName
        -Integer quantity
        -Double price
        -Double total
        +calculateTotal() Double
    }
    
    class Bill {
        -String id
        -LocalDateTime date
        -String customerId
        -String userId
        -Double subtotal
        -Double discount
        -Double total
        -String paymentMode
        +addItem(BillItem) void
        +calculateTotal() Double
    }
    
    class User {
        -Integer id
        -String username
        -String password
        -UserRole role
        -LocalDateTime createdAt
        +authenticate(String) boolean
        +getRole() UserRole
    }
    
    class UserRole {
        <<enumeration>>
        ADMIN
        MANAGER
        PHARMACIST
        CASHIER
    }
    
    class Expense {
        -Integer id
        -String category
        -Double amount
        -String description
        -LocalDate date
        +getAmount() Double
        +getCategory() String
    }
    
    %% Relationships
    Bill "1" *-- "many" BillItem : contains
    Bill --> Customer : references
    Bill --> User : created by
    User --> UserRole : has role
    BillItem --> Medicine : references
```

---

## 3. Class Diagram - DAO and Service Layer

```mermaid
classDiagram
    %% DAO Classes
    class MedicineDAO {
        +getAllMedicines() List~Medicine~
        +searchByName(String) List~Medicine~
        +searchByGeneric(String) List~Medicine~
        +searchByBarcode(String) Medicine
        +addMedicine(Medicine) void
        +updateMedicine(Medicine) void
        +deleteMedicine(String) void
        +getLowStockItems() List~Medicine~
        +getExpiringItems(int) List~Medicine~
    }
    
    class CustomerDAO {
        +getAllCustomers() List~Customer~
        +searchByPhone(String) Customer
        +addCustomer(Customer) void
        +updateCustomer(Customer) void
        +updateBalance(String, Double) void
    }
    
    class BillDAO {
        +generateInvoice(Bill, List~BillItem~) String
        +getBillHistory() List~Bill~
        +getBillById(String) Bill
        +getDailySales(LocalDate) Double
        +getMonthlySales(int, int) Double
    }
    
    class UserDAO {
        +authenticate(String, String) User
        +getAllUsers() List~User~
        +addUser(User) void
        +updateUser(User) void
        +deleteUser(Integer) void
    }
    
    class ExpenseDAO {
        +addExpense(Expense) void
        +getAllExpenses() List~Expense~
        +getExpensesByMonth(int, int) List~Expense~
        +getTotalExpenses(LocalDate, LocalDate) Double
    }
    
    %% Service Classes
    class AuthService {
        +login(String, String) User
        +logout() void
        +getCurrentUser() User
    }
    
    class ReportService {
        +generatePDFInvoice(String) void
        +generateSalesReport(LocalDate, LocalDate) void
        +printThermalReceipt(Bill) void
    }
    
    class DatabaseService {
        +initializeDatabase() void
        +backupDatabase() void
    }
    
    %% Configuration
    class DatabaseConfig {
        +getConnection() Connection
        +getConnectionString() String
        +isDevMode() boolean
    }
    
    %% Controllers
    class DashboardController {
        -MedicineDAO medicineDAO
        -BillDAO billDAO
        -ExpenseDAO expenseDAO
        +loadKPIs() void
        +loadExpiryAlerts() void
        +calculateNetProfit() Double
    }
    
    class BillingController {
        -MedicineDAO medicineDAO
        -CustomerDAO customerDAO
        -BillDAO billDAO
        -ReportService reportService
        +searchMedicine(String) void
        +addItemToBill(Medicine) void
        +handleCheckout() void
        +processPayment(String) void
    }
    
    class InventoryController {
        -MedicineDAO medicineDAO
        +loadInventory() void
        +addMedicine() void
        +updateMedicine() void
        +deleteMedicine() void
        +exportToExcel() void
    }
    
    %% Dependencies
    DashboardController ..> MedicineDAO : uses
    DashboardController ..> BillDAO : uses
    DashboardController ..> ExpenseDAO : uses
    
    BillingController ..> MedicineDAO : uses
    BillingController ..> CustomerDAO : uses
    BillingController ..> BillDAO : uses
    BillingController ..> ReportService : uses
    
    InventoryController ..> MedicineDAO : uses
    
    AuthService ..> UserDAO : uses
    ReportService ..> BillDAO : uses
    
    MedicineDAO ..> DatabaseConfig : uses
    CustomerDAO ..> DatabaseConfig : uses
    BillDAO ..> DatabaseConfig : uses
    UserDAO ..> DatabaseConfig : uses
    ExpenseDAO ..> DatabaseConfig : uses
```

---

## 4. Sequence Diagram - User Login Flow

```mermaid
sequenceDiagram
    actor User
    participant LC as LoginController
    participant AS as AuthService
    participant UD as UserDAO
    participant DC as DatabaseConfig
    participant DB as Database
    
    User->>LC: Enter credentials & click Login
    activate LC
    LC->>AS: login(username, password)
    activate AS
    AS->>UD: authenticate(username, password)
    activate UD
    UD->>DC: getConnection()
    activate DC
    DC-->>UD: Connection
    deactivate DC
    UD->>DB: SELECT * FROM users WHERE username=?
    activate DB
    DB-->>UD: User record
    deactivate DB
    UD->>UD: Validate password
    UD-->>AS: User object
    deactivate UD
    AS->>AS: Store in UserSession
    AS-->>LC: User object
    deactivate AS
    LC->>LC: Navigate to Dashboard
    LC-->>User: Dashboard View
    deactivate LC
```

---

## 5. Sequence Diagram - Generate Invoice (Billing)

```mermaid
sequenceDiagram
    actor Cashier
    participant BC as BillingController
    participant MD as MedicineDAO
    participant CD as CustomerDAO
    participant BD as BillDAO
    participant DB as Database
    participant RS as ReportService
    participant Printer
    
    Cashier->>BC: Scan barcode / Search medicine
    activate BC
    BC->>MD: searchByBarcode(barcode)
    activate MD
    MD->>DB: SELECT from medicines
    activate DB
    DB-->>MD: Medicine data
    deactivate DB
    MD-->>BC: Medicine object
    deactivate MD
    BC->>BC: Add to bill table
    
    Cashier->>BC: Enter customer phone
    BC->>CD: searchByPhone(phone)
    activate CD
    CD->>DB: SELECT from customers
    activate DB
    DB-->>CD: Customer data
    deactivate DB
    CD-->>BC: Customer object
    deactivate CD
    
    Cashier->>BC: Click Checkout
    BC->>BD: generateInvoice(bill, items)
    activate BD
    BD->>DB: BEGIN TRANSACTION
    activate DB
    BD->>DB: INSERT INTO bills
    BD->>DB: INSERT INTO bill_items
    BD->>DB: UPDATE stock (decrement)
    alt Payment = Credit
        BD->>DB: UPDATE customer balance
    end
    BD->>DB: COMMIT
    DB-->>BD: Success
    deactivate DB
    BD-->>BC: billId
    deactivate BD
    
    BC->>RS: generatePDFInvoice(billId)
    activate RS
    RS->>RS: Create PDF
    deactivate RS
    
    BC->>RS: printThermalReceipt(bill)
    activate RS
    RS->>Printer: Send print job
    activate Printer
    Printer-->>RS: Printed
    deactivate Printer
    deactivate RS
    
    BC-->>Cashier: Invoice complete
    deactivate BC
```

---

## 6. Sequence Diagram - View Dashboard KPIs

```mermaid
sequenceDiagram
    actor User
    participant DC as DashboardController
    participant BD as BillDAO
    participant ED as ExpenseDAO
    participant MD as MedicineDAO
    participant DB as Database
    
    User->>DC: Navigate to Dashboard
    activate DC
    DC->>DC: loadKPIs()
    
    DC->>BD: getDailySales(today)
    activate BD
    BD->>DB: Query daily sales
    activate DB
    DB-->>BD: Total sales
    deactivate DB
    BD-->>DC: Daily sales amount
    deactivate BD
    
    DC->>BD: getMonthlySales(month, year)
    activate BD
    BD->>DB: Query monthly sales
    activate DB
    DB-->>BD: Monthly total
    deactivate DB
    BD-->>DC: Monthly sales
    deactivate BD
    
    DC->>ED: getTotalExpenses(startDate, endDate)
    activate ED
    ED->>DB: Query expenses
    activate DB
    DB-->>ED: Expense total
    deactivate DB
    ED-->>DC: Total expenses
    deactivate ED
    
    DC->>DC: Calculate Net Profit<br/>(Sales - Expenses)
    
    DC->>MD: getLowStockItems()
    activate MD
    MD->>DB: Query low stock
    activate DB
    DB-->>MD: Low stock list
    deactivate DB
    MD-->>DC: Medicine list
    deactivate MD
    
    DC->>MD: getExpiringItems(30)
    activate MD
    MD->>DB: Query expiring soon
    activate DB
    DB-->>MD: Expiring medicines
    deactivate DB
    MD-->>DC: Expiring list
    deactivate MD
    
    DC->>DC: Update UI with KPIs
    DC-->>User: Display Dashboard
    deactivate DC
```

---

## 7. Component Diagram

```mermaid
graph TB
    subgraph "UI Layer"
        Controllers["📦 controllers<br/>LoginController<br/>DashboardController<br/>BillingController<br/>InventoryController<br/>CustomersController"]
    end
    
    subgraph "Service Layer"
        Services["📦 service<br/>AuthService<br/>ReportService<br/>DatabaseService"]
    end
    
    subgraph "Data Access Layer"
        DAOs["📦 dao<br/>MedicineDAO<br/>CustomerDAO<br/>BillDAO<br/>UserDAO<br/>ExpenseDAO"]
    end
    
    subgraph "Domain Model Layer"
        Models["📦 model<br/>Medicine<br/>Customer<br/>Bill<br/>BillItem<br/>User<br/>Expense"]
    end
    
    subgraph "Configuration Layer"
        Config["📦 config<br/>DatabaseConfig"]
    end
    
    subgraph "Utility Layer"
        Utils["📦 util<br/>UserSession<br/>PDFUtil<br/>DatabaseUtil"]
    end
    
    subgraph "External Libraries"
        ExtLibs["📚 Dependencies<br/>JavaFX 21<br/>SQLite JDBC<br/>JasperReports<br/>OpenPDF<br/>Apache POI<br/>ZXing<br/>AtlantaFX"]
    end
    
    %% Dependencies
    Controllers -->|uses| Services
    Controllers -->|uses| DAOs
    Controllers -->|uses| Models
    Services -->|uses| DAOs
    Services -->|uses| Models
    DAOs -->|uses| Config
    DAOs -->|uses| Models
    Config -->|uses| ExtLibs
    Services -->|uses| ExtLibs
    Controllers -->|uses| Utils
    
    style Controllers fill:#e3f2fd
    style Services fill:#fff3e0
    style DAOs fill:#f3e5f5
    style Models fill:#e8f5e9
    style Config fill:#fce4ec
    style Utils fill:#e0f2f1
    style ExtLibs fill:#f5f5f5
```

---

## 8. Deployment Diagram

```mermaid
graph TB
    subgraph "Windows PC - Client Workstation"
        subgraph AppLayer["Application Components"]
            MediApp["MediManage.exe<br/>JavaFX Application"]
            JRE["Bundled JRE 21<br/>Java Runtime"]
        end
        
        subgraph UILayer["UI Layer"]
            UI["JavaFX Views<br/>Login | Dashboard<br/>Billing | Inventory"]
        end
        
        subgraph BizLayer["Business Logic"]
            BL["Controllers<br/>Services<br/>DAOs"]
        end
        
        subgraph DataLayer["Data Layer"]
            SQLite["SQLite Engine"]
            DBFile["medimanage.db<br/>C:\Program Files\MediManage\runtime\db\"]
        end
        
        subgraph ConfigLayer["Configuration"]
            Config["Config Files<br/>%APPDATA%\MediManage\"]
        end
    end
    
    subgraph "Hardware Peripherals"
        Scanner["🔲 Barcode Scanner<br/>USB Connection<br/>Acts as Keyboard Input"]
        Printer["🖨️ Thermal Printer<br/>POS-58<br/>USB/Network<br/>ESC/POS Protocol"]
    end
    
    subgraph "Installation Package"
        Installer["📦 MediManage_Setup_0.1.5.exe<br/>Size: ~120 MB<br/>Platform: Windows 10/11<br/>Includes: JRE + App + DB"]
    end
    
    MediApp --> JRE
    MediApp --> UI
    UI --> BL
    BL --> SQLite
    SQLite --> DBFile
    BL --> Config
    
    Scanner -.USB.-> MediApp
    Printer -.USB/Network.-> MediApp
    
    Installer -.Installs.-> MediApp
    
    style MediApp fill:#4CAF50
    style Scanner fill:#FFC107
    style Printer fill:#2196F3
    style Installer fill:#9C27B0
    style DBFile fill:#FF5722
```

---

## 9. Simplified Collaboration Diagram - Generate Invoice

```mermaid
graph LR
    Cashier([Cashier])
    BC[BillingController]
    MD[MedicineDAO]
    CD[CustomerDAO]
    BD[BillDAO]
    DB[(Database)]
    RS[ReportService]
    Printer[Printer]
    
    Cashier -->|1: searchMedicine| BC
    BC -->|2: searchByBarcode| MD
    MD -->|3: query| DB
    DB -->|4: Medicine| MD
    MD -->|5: Medicine| BC
    
    Cashier -->|6: searchCustomer| BC
    BC -->|7: searchByPhone| CD
    CD -->|8: query| DB
    DB -->|9: Customer| CD
    CD -->|10: Customer| BC
    
    Cashier -->|11: checkout| BC
    BC -->|12: generateInvoice| BD
    BD -->|13-17: transaction| DB
    DB -->|18: success| BD
    BD -->|19: billId| BC
    
    BC -->|20: generatePDF| RS
    BC -->|21: printReceipt| RS
    RS -->|22: print| Printer
    
    style Cashier fill:#ff6b6b
    style BC fill:#4ecdc4
    style DB fill:#95a5a6
    style RS fill:#f39c12
    style Printer fill:#3498db
```

---

## How to Use These Diagrams

### For Your PPT Presentation:

1. **Online Conversion** (Recommended):
   - Copy each Mermaid code block
   - Go to https://mermaid.live
   - Paste the code
   - Export as PNG or SVG (high quality)
   - Insert into PowerPoint

2. **Direct Screenshot**:
   - View this file in VS Code with Mermaid extension
   - Take screenshots of rendered diagrams
   - Insert into PowerPoint

3. **GitHub Rendering**:
   - Push this file to GitHub
   - GitHub automatically renders Mermaid
   - Take screenshots from GitHub

### Tips for Your Review:

- **Use Case Diagram**: Shows all features and who can access them
- **Class Diagrams**: Shows your code structure (models, DAOs, services, controllers)
- **Sequence Diagrams**: Shows how login, billing, and dashboard work step-by-step
- **Component Diagram**: Shows how your code is organized into packages
- **Deployment Diagram**: Shows the physical installation on Windows PC with peripherals

### Explanation Points for Your Review:

1. **Architecture**: "We use MVC pattern with DAO layer for clean separation"
2. **Technology**: "JavaFX for UI, SQLite for embedded database, no server needed"
3. **Deployment**: "Standalone Windows app with bundled Java - easy installation"
4. **Features**: "Complete POS system with billing, inventory, reports, and financial tracking"
5. **Security**: "Role-based access control with 4 user levels"
