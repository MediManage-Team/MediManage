# MediManage Development Documentation

Welcome to the MediManage development documentation! This directory contains all the technical documentation needed for development, maintenance, and understanding the system architecture.

## 📂 Directory Structure

```
docs/
├── uml/                    # UML Diagrams and Guides
│   ├── UML_GUIDE.md       # Comprehensive UML explanation for beginners
│   └── UML_DIAGRAMS.md    # Mermaid diagram source code
├── images/                 # Generated UML diagram images
│   ├── uploaded_image_0_*.png  # Component Diagram
│   ├── uploaded_image_1_*.png  # Login Sequence Diagram
│   ├── uploaded_image_2_*.png  # Dashboard Sequence Diagram
│   ├── uploaded_image_3_*.png  # Comprehensive Class Diagram
│   └── uploaded_image_4_*.png  # Domain Model Class Diagram
├── ARCHITECTURE.md         # System architecture overview
├── DATABASE.md            # Database schema and design
└── README.md              # This file
```

## 📖 Documentation Index

### For New Developers

Start here to understand the project:

1. **[UML_GUIDE.md](./uml/UML_GUIDE.md)** - Learn UML basics and understand MediManage diagrams
   - What is UML?
   - Use Case Diagram - Features and user roles
   - Class Diagram - Code structure
   - Sequence Diagrams - Workflow explanations
   - Component Diagram - Module organization
   - Deployment Diagram - Physical architecture

2. **[UML_DIAGRAMS.md](./uml/UML_DIAGRAMS.md)** - Interactive Mermaid diagrams
   - View in VS Code with Mermaid extension
   - Render on GitHub automatically
   - Convert to images at https://mermaid.live

3. **[ARCHITECTURE.md](./ARCHITECTURE.md)** - System architecture deep dive
   - Design patterns used
   - Layer responsibilities
   - Data flow
   - Best practices

4. **[DATABASE.md](./DATABASE.md)** - Database design and schema
   - Table structures
   - Relationships
   - Indexes
   - Migration strategy

### For Project Reviews & Presentations

Use these resources for academic reviews or stakeholder presentations:

- **UML Diagrams** (`images/` folder) - Ready-to-use images for PowerPoint
- **UML_GUIDE.md** - Comprehensive explanations to accompany diagrams
- **README.md** (project root) - High-level project overview

## 🏗️ System Architecture Overview

MediManage follows a **layered MVC architecture** with clear separation of concerns:

```
┌─────────────────────────────────────┐
│         UI Layer (JavaFX)           │
│         Controllers                 │
└─────────────┬───────────────────────┘
              │
┌─────────────▼───────────────────────┐
│       Service Layer                 │
│  Business Logic & Orchestration     │
└─────────────┬───────────────────────┘
              │
┌─────────────▼───────────────────────┐
│         DAO Layer                   │
│     Data Access Objects             │
└─────────────┬───────────────────────┘
              │
┌─────────────▼───────────────────────┐
│       Model Layer                   │
│      Domain Entities                │
└─────────────────────────────────────┘
              │
┌─────────────▼───────────────────────┐
│      SQLite Database                │
│      (Embedded)                     │
└─────────────────────────────────────┘
```

## 🔑 Key Components

### Controllers (`src/main/java/.../`)
- `LoginController` - User authentication
- `DashboardController` - Main hub with KPIs
- `BillingController` - POS and invoice generation
- `InventoryController` - Medicine stock management
- `CustomersController` - Customer profile management

### Services (`service/`)
- `AuthService` - Authentication and session management
- `ReportService` - PDF generation and printing
- `DatabaseService` - Database initialization

### DAOs (`dao/`)
- `MedicineDAO` - Medicine CRUD and search
- `CustomerDAO` - Customer management
- `BillDAO` - Invoice generation and sales queries
- `UserDAO` - User authentication
- `ExpenseDAO` - Expense tracking

### Models (`model/`)
- `Medicine`, `Customer`, `Bill`, `BillItem`, `User`, `Expense`
- JavaFX properties for UI binding

## 🛠️ Technology Stack

- **Java 21** - LTS version with modern features
- **JavaFX 21** - Rich desktop UI framework
- **SQLite** - Embedded zero-configuration database
- **Maven** - Dependency management and build tool
- **AtlantaFX** - Modern UI theme
- **JasperReports** - Professional report generation
- **Apache POI** - Excel export functionality
- **ZXing** - Barcode scanning support

## 📊 Database Schema

See **[DATABASE.md](./DATABASE.md)** for detailed schema documentation.

Key tables:
- `users` - Authentication and roles
- `medicines` - Product catalog
- `stock` - Inventory tracking
- `customers` - Customer profiles
- `bills` - Invoice headers
- `bill_items` - Invoice line items
- `expenses` - Operating costs

## 🚀 Getting Started

### Prerequisites
- JDK 21
- Maven 3.8+
- Git

### Clone and Run
```bash
git clone https://github.com/your-repo/MediManage.git
cd MediManage
mvn clean install
mvn javafx:run
```

### Build Installer
```powershell
./build_full_installer.bat
```

## 📝 Development Workflow

1. **Planning** - Read UML diagrams and architecture docs
2. **Development** - Follow MVC pattern and existing code structure
3. **Testing** - Test both UI and database operations
4. **Documentation** - Update docs when adding features
5. **Commit** - Use meaningful commit messages

## 🤝 Contributing

When contributing to this project:

1. **Understand the Architecture** - Review UML diagrams first
2. **Follow Patterns** - Maintain MVC structure
3. **Update Documentation** - Keep docs in sync with code
4. **Test Thoroughly** - Especially database transactions
5. **Code Review** - Get feedback before merging

## 📚 Additional Resources

- [JavaFX Documentation](https://openjfx.io/)
- [SQLite Documentation](https://www.sqlite.org/docs.html)
- [AtlantaFX Theme Guide](https://github.com/mkpaz/atlantafx)
- [UML Modeling Best Practices](https://www.uml-diagrams.org/)

## 📧 Contact

For questions or issues, please create a GitHub issue or contact the development team.

---

**Last Updated:** January 2026  
**Version:** 0.1.5
