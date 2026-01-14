# MediManage - Medical Shop Management System

A modern, robust Desktop application for managing pharmacy inventory, billing, and customer records. Built with JavaFX and optimized for performance and usability.

## 🚀 Key Features

### 🛒 Point of Sale (POS)
- **Fast Billing**: Keyboard-optimized checkout flow.
- **Barcode Scanner Support**: Auto-focus search, "Scan to Add", and auto-increment quantity logic using **ZXing** integration.
- **Thermal Printing**: Automatic 58mm receipt printing support.
- **Smart Search**: Search medicines by name or company.

### 📦 Inventory Management
- **Excel Export**: Export your entire inventory to `.xlsx` format using **Apache POI**.
- **Stock Tracking**: Low stock alerts and KPI dashboard.
- **Expiry Management**: Track medicine expiry dates.

### 📄 Reporting & Invoices
- **PDF Invoices**: Generate pixel-perfect A4 invoices with **JasperReports** and **iText**.
- **Bill History**: View and reprint past invoices.

### 👥 Customer Management
- **CRM Features**: Track customer purchase history and disease profiles.
- **Smart Flow**: Auto-redirect to "Add Customer" with pre-filled data if a search fails.

### 🎨 Modern UI/UX
- **AtlantaFX**: styled with the `PrimerLight` theme for a clean, modern look.
- **Responsive Design**: Adaptive layouts using JavaFX `BorderPane` and `SplitPane`.

---

## 🛠️ Technology Stack

- **Language**: Java 21
- **UI Framework**: JavaFX 17+
- **Styling**: [AtlantaFX](https://github.com/mkpaz/atlantafx) (PrimerLight Theme)
- **Database**: MySQL (Connector/J 9.5.0)
- **Reporting**: 
  - [JasperReports](https://community.jaspersoft.com/) (v6.21.0)
  - [iText](https://github.com/itext/itext) (v2.1.7)
- **Data Export**: [Apache POI](https://poi.apache.org/) (v5.2.5)
- **Barcode/Scanning**: [ZXing](https://github.com/zxing/zxing) (v3.5.3)
- **Build Tool**: Maven

---

## ⚙️ Setup & Installation

1.  **Prerequisites**:
    - Java 21 SDK
    - Maven
    - MySQL Server

2.  **Database Configuration**:
    - Ensure MySQL is running.
    - Update `src/main/resources/db_config.properties` with your credentials:
        ```properties
        db.url=jdbc:mysql://localhost:3306/medimanage_db
        db.user=your_username
        db.password=your_password
        ```

3.  **Build & Run**:
    ```bash
    mvn clean compile javafx:run
    ```

## 📝 License
This project is for educational and commercial management use.
