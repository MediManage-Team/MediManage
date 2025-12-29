# 🏥 MediManage: Smart Medical Billing & Inventory System

**MediManage** is a comprehensive pharmaceutical management solution designed to digitize medical stores. It combines robust billing features with AI-driven insights to optimize stock management and improve patient service.

---

## 🚀 Core Features

### 🛒 Intelligent Billing & POS
* **Automated Invoicing:** Generates GST-compliant digital and printed bills.
* **Batch Tracking:** Automatically selects medicines from the oldest batch first (FIFO) to minimize waste.
* **Expiry Alerts:** Real-time dashboard notifications for medicines nearing their expiry date.

### 🤖 AI-Powered Capabilities
* **Smart Substitute Engine:** Suggests alternative medicines based on salt composition/generic name when a specific brand is out of stock.
* **Predictive Inventory:** Analyzes sales patterns to forecast stock requirements for the upcoming month.
* **NLP Search:** Search for medicines using fuzzy logic to handle common misspellings by staff.

### 📦 Inventory & Supplier Management
* **Stock Monitoring:** Real-time tracking of thousands of SKUs across multiple categories.
* **Supplier Portal:** Manage vendor contacts, purchase orders, and payment history.

---

## 🛠️ Technical Stack

| Component | Technology |
| :--- | :--- |
| **Language** | Java (JDK 17+) |
| **UI Framework** | JavaFX / Scene Builder |
| **Database** | MySQL / SQLite |
| **AI Modules** | [e.g., Python scripts / OpenAI API / Custom ML Model] |
| **Build Tool** | Maven |

---

## 📁 Project Structure
```text
├── src/main/java         # Backend Logic & Controllers
├── src/main/resources    # FXML files and CSS Styles
├── database/             # SQL scripts for DB initialization
├── ai_modules/           # Python/AI scripts for forecasting
└── README.md
