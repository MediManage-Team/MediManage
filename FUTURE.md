# MediManage — Future Implementation Roadmap

> This document tracks planned improvements, known bugs, and feature ideas for future development.

---

## 🔴 Known Bugs

### `BillItem.setTotal()` Missing
- `handleRedeemPoints()` in `BillingController` calls `item.setTotal()`, but `BillItem` computes total from `qty × price`
- **Fix**: Apply loyalty discount to item price instead, or add a `discountPercent` property to `BillItem`

### MCP `get_low_stock` Hardcodes Threshold
- Doesn't use the per-medicine `reorder_threshold` column
- **Fix**: Change SQL to `WHERE COALESCE(s.quantity, 0) < COALESCE(m.reorder_threshold, 10)`

---

## 🟡 Code Simplification

### Split Large Files
| File | Lines | Split Into |
|------|-------|------------|
| `MedicineDAO.java` | 1100+ | `MedicineDAO` (CRUD) + `InventoryInsightsDAO` (BI queries) |
| `DashboardController.java` | 740+ | `DashboardController` (Overview) + `HistoryTabController` + `BiTabController` |
| `BillingController.java` | 800+ | `BillingController` (core) + `HeldOrderManager` + `CareProtocolManager` |

### Medicine Factory Method
- 6+ identical `new Medicine(rs.getInt(...), ...)` patterns in `MedicineDAO`
- Extract `private static Medicine fromResultSet(ResultSet rs)` helper

### Consolidate Utilities
- Move `showAlert()` from 4+ controllers → shared `AlertHelper` utility
- Replace `e.printStackTrace()` (~20 occurrences) → `java.util.logging.Logger`

### Remove Dead Subscription DDL
- 9 `CREATE TABLE` statements in `schema.sql` for unused subscription tables
- Safe to delete: `subscription_plans`, `subscription_enrollments`, `enrollment_items`, etc.

---

## 🟢 New Features

### MCP Server Enhancements
```python
# Add these tools to mcp_server.py:
get_customer_loyalty(customer_id)   # Query loyalty points
get_profit_margins(limit)           # Top/bottom margin medicines
get_reorder_needed()                # Medicines below reorder threshold
```

### Profit Margin Heatmap
- Color-code the Margin % column on the dashboard: red (<15%), yellow (15–30%), green (>30%)

### Loyalty Points History
- New `loyalty_history` table: `customer_id, points, type (EARN/REDEEM), bill_id, timestamp`
- Display history in a customer details dialog

### Purchase Price Bulk Import
- CSV upload to set `purchase_price` for all medicines at once
- Useful for initial data population

### Dashboard Reorder Alert Card
- Dedicated card listing top 5 medicines below threshold
- "Create PO" button to pre-fill a purchase order

### CSV Export on Dashboard
- Add "Export CSV" button alongside existing "Export Excel"

### Patient Care Assistance Insights
- Greatly expand the AI `salesSummaryPrompt` to generate detailed, multi-paragraph Care Protocols, Dietary Advice, and Pharmacist Alerts based on recent sales.
- Enlarge the `txtAISummary` UI container in `reports-view.fxml` so the generated clinic reports are fully readable without excessive scrolling.

#### Implementation Steps:
1. **Modify `AIPromptCatalog.java`**: Update the `salesSummaryPrompt` template string. Instruct the LLM to output at least 3-4 comprehensive sentences per section, covering "Public Health Trend", "Care Assistance Advice", and "Inventory Recommendations". Emphasize clinical accuracy.
2. **Update `reports-view.fxml`**: Increase the `minHeight` setting of the `txtAISummary` TextArea from `200` to `400` pixels.
3. **Validate**: Run `mvn clean compile javafx:run`, navigate to the Reports tab, and generate a new summary to verify the enlarged UI and the rich text response.

### Digital Invoices & Instructions
- Add functionality to send digital invoices via WhatsApp and Email directly from the billing screen.
- Include instructions on the usage of medicines (dosage, timing, care protocols) directly within the WhatsApp/Email messages.

#### Implementation Steps:
1. **Email Integration**: Add `javax.mail` dependency to `pom.xml`. Create `EmailService.java` to format the invoice details into an HTML email template, attaching a PDF generated from JasperReports.
2. **WhatsApp Integration**: Use a cloud API (Twilio or WhatsApp Business API) in a new `WhatsAppService.java` to send text-based invoice summaries and a download link.
3. **Usage Instructions**: Query the `AIAssistantService` for a concise patient care protocol based on the `billItems` being sold, and append this block of instructions into the message body.
4. **UI Updates (`billing-view.fxml`)**: Add "Email Invoice" and "WhatsApp Invoice" buttons in the checkout/success modal.
5. **Controller Logic**: Bind the buttons to their respective service methods, prompting the user for an email address or phone number if not already present in the customer record.

---

## 🔵 Architecture Ideas

### Service Layer for All DAOs
- Wrap remaining raw DAO calls through service classes (like `LoyaltyService`, `DashboardKpiService`)
- Enables easier unit testing and transaction management

### Feature Toggle for Loyalty
- Add `LOYALTY_PROGRAM` to `FeatureFlag.java`
- Hide/show loyalty UI based on flag

### Database Connection Pooling
- Replace single-connection model with HikariCP
- Better performance for concurrent dashboard + billing operations
