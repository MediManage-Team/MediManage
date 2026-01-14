# 🏥 MediManage: Smart Medical Billing & Inventory System

**MediManage** is a modern, comprehensive pharmaceutical management solution designed to digitize medical stores. It combines robust billing features with AI-driven insights to optimize stock management and improve patient service, all wrapped in a premium, responsive JavaFX interface.

---

## 🚀 Core Features

### 🛒 Intelligent Billing & POS
* **Automated Invoicing:** Generates GST-compliant digital and printed bills.
* **Batch Tracking:** Automatically selects medicines from the oldest batch first (FIFO) to minimize waste.
* **Expiry Alerts:** Real-time dashboard notifications for medicines nearing their expiry date.

### 🎨 Modern UI/UX
* **Premium Design:** Sleek, dark-themed interface with glassmorphism effects.
* **Interactive Elements:** Smooth animations, hover effects, and responsive layout.
* **Dashboard:** Visual analytics for sales, stock levels, and critical alerts.

### 🤖 AI-Powered Capabilities
* **Smart Substitute Engine:** Suggests alternative medicines based on salt composition/generic name when a specific brand is out of stock.
* **Predictive Inventory:** Analyzes sales patterns to forecast stock requirements.
* **NLP Search:** Search for medicines using fuzzy logic to handle common misspellings.

### 📦 Inventory & Supplier Management
* **Stock Monitoring:** Real-time tracking of SKUs across multiple categories.
* **Supplier Portal:** Manage vendor contacts, purchase orders, and payment history.

---

## 🛠️ Technical Stack

| Component | Technology |
| :--- | :--- |
| **Language** | Java 21 |
| **UI Framework** | JavaFX / Scene Builder / CSS3 |
| **Database** | SQLite (Lightweight & Portable) |
| **Build Tool** | Maven |

---

## 💻 Setup & Installation

1.  **Clone the Repository**
    ```bash
    git clone https://github.com/MediManage-Team/MediManage.git
    cd MediManage
    ```

2.  **Build the Project**
    ```bash
    mvn clean install
    ```

3.  **Run the Application**
    ```bash
    mvn javafx:run
    ```

---

## 📁 Project Structure
```text
├── src/main/java         # Backend Logic, Controllers & DAO
├── src/main/resources    # FXML layouts, CSS Styles & Assets
├── database.db           # Local SQLite Database
├── pom.xml               # Maven Dependencies
└── README.md
```
