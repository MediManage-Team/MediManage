# MediManage Development Documentation

Welcome to the MediManage development documentation! This directory contains all the technical documentation needed for development, maintenance, and understanding the system architecture.

## 📂 Directory Structure

```
docs/
├── adr/                    # Architecture Decision Records
│   └── 0001-sqlite-vs-hybrid-backend.md
├── uml/                    # UML Diagrams and Guides
│   ├── UML_GUIDE.md       # Comprehensive UML explanation for beginners
│   └── UML_DIAGRAMS.md    # Mermaid diagram source code
├── images/                 # Generated UML diagram images
│   ├── uploaded_image_0_*.png  # Component Diagram
│   ├── uploaded_image_1_*.png  # Login Sequence Diagram
│   ├── uploaded_image_2_*.png  # Dashboard Sequence Diagram
│   ├── uploaded_image_3_*.png  # Comprehensive Class Diagram
│   └── uploaded_image_4_*.png  # Domain Model Class Diagram
├── pilot-logs/             # Daily pilot execution run sheets
│   └── 2026-02-23-subscription-pilot-day-1.md
├── ARCHITECTURE.md         # System architecture overview
├── DATABASE.md            # Database schema and design
├── IMPLEMENTATION_ROADMAP.md # Subscription commerce TODO backlog
├── PHASE_0_FOUNDATION_GOVERNANCE.md # Active governance baseline and acceptance criteria
├── SUBSCRIPTION_GOVERNANCE_SPEC.md # Permission matrix and approval workflow baseline
├── SUBSCRIPTION_PLAN_RULES_BASELINE.md # Plan duration/price/renewal/grace/cancellation-refund baseline
├── SUBSCRIPTION_ELIGIBILITY_RULES_BASELINE.md # Include/exclude + cap + margin-floor eligibility baseline
├── SUBSCRIPTION_AI_PLAN_RECOMMENDATION_BASELINE.md # AI recommendation baseline for plan fit/savings prediction
├── SUBSCRIPTION_AI_RENEWAL_PROPENSITY_BASELINE.md # AI renewal churn-risk scoring baseline
├── SUBSCRIPTION_AI_DISCOUNT_ABUSE_DETECTION_BASELINE.md # AI suspicious discount behavior detection baseline
├── SUBSCRIPTION_AI_OVERRIDE_RISK_SCORING_BASELINE.md # AI override decision-risk scoring baseline
├── SUBSCRIPTION_AI_DYNAMIC_OFFER_SUGGESTIONS_BASELINE.md # AI guardrailed dynamic offer suggestion baseline
├── SUBSCRIPTION_AI_CONVERSATIONAL_ASSISTANT_BASELINE.md # AI staff assistant for discount decision explanations
├── SUBSCRIPTION_AI_MULTILINGUAL_EXPLANATION_SNIPPETS_BASELINE.md # AI multilingual savings explanations for checkout/invoice
├── SUBSCRIPTION_AI_MODEL_MONITORING_DASHBOARD_BASELINE.md # AI model monitoring baseline for abuse precision/recall and recommendation acceptance
├── SUBSCRIPTION_AI_FALLBACK_RULES_ENGINE_BASELINE.md # AI fallback rules engine baseline for checkout continuity
├── SUBSCRIPTION_AI_INPUT_SAFETY_PII_MASKING_BASELINE.md # AI input allowlist + PII masking baseline for subscription AI
├── SUBSCRIPTION_AI_PROMPT_VERSION_REGISTRY_BASELINE.md # AI prompt version registry with change tracking and rollback baseline
├── SUBSCRIPTION_AI_DECISION_LOGGING_BASELINE.md # AI decision logging baseline for reason-coded audit and post-incident review
├── SUBSCRIPTION_AI_FAIRNESS_BIAS_TEST_SET_BASELINE.md # AI fairness/bias cross-group test-set baseline
├── SUBSCRIPTION_AI_OFFLINE_EVALUATION_BENCHMARKS_BASELINE.md # AI offline benchmark gate baseline before production enablement
├── SUBSCRIPTION_AI_INCIDENT_RUNBOOK_BASELINE.md # AI incident response baseline for misclassification, drift, and outage
├── SUBSCRIPTION_SCHEMA_MIGRATION_PLAN.md # Subscription DB schema/migration notes
├── SUBSCRIPTION_ROLLOUT_ROLLBACK_RUNBOOK.md # Subscription rollout/rollback operations runbook
├── SUBSCRIPTION_ALL_STORES_ROLLOUT_CYCLE_1_REPORT.md # All-stores rollout cycle 1 execution baseline
├── SUBSCRIPTION_PILOT_MONITORING_CHECKLIST.md # Pilot-day monitoring + sign-off template
├── SUBSCRIPTION_PILOT_MONITORING_CYCLE_1_REPORT.md # Pilot monitoring cycle 1 execution baseline
├── SUBSCRIPTION_UAT_CHECKLIST.md # Pharmacy operations UAT checklist and sign-off sheet
├── SUBSCRIPTION_PILOT_QA_CYCLE_1_REPORT.md # Pilot QA cycle 1 execution results
├── MIGRATION_ROLLBACK_RUNBOOK.md  # Backend migration/rollback steps
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

5. **[adr/0001-sqlite-vs-hybrid-backend.md](./adr/0001-sqlite-vs-hybrid-backend.md)** - Storage strategy decision
   - Why SQLite remains default
   - Hybrid backend evolution path
   - Tradeoffs and follow-up steps

6. **[MIGRATION_ROLLBACK_RUNBOOK.md](./MIGRATION_ROLLBACK_RUNBOOK.md)** - Operational migration guide
   - Backend switch controls
   - Cutover checklist
   - Rollback procedure

7. **[PHASE_0_FOUNDATION_GOVERNANCE.md](./PHASE_0_FOUNDATION_GOVERNANCE.md)** - Active roadmap governance baseline
   - Phase 0 requirements and acceptance criteria
   - Central RBAC policy matrix
   - Feature flag catalog and rollout controls
   - Exit criteria for foundation completion

8. **[IMPLEMENTATION_ROADMAP.md](./IMPLEMENTATION_ROADMAP.md)** - Subscription Commerce delivery TODO
   - Phase 1 functional backlog and governance gates
   - AI extension backlog for subscription intelligence
   - Weekly operations analytics panel implementation checklist

9. **[SUBSCRIPTION_GOVERNANCE_SPEC.md](./SUBSCRIPTION_GOVERNANCE_SPEC.md)** - Governance baseline for Phase 1
   - Role permission matrix for subscription operations
   - Approval flows for policy changes, overrides, backdated enrollment
   - Stakeholder confirmation record inputs for change-control meeting

10. **[SUBSCRIPTION_SCHEMA_MIGRATION_PLAN.md](./SUBSCRIPTION_SCHEMA_MIGRATION_PLAN.md)** - DB implementation baseline for subscription module
   - New tables and extended billing columns
   - Migration mechanics for existing installs
   - Rollback approach and integrity notes

11. **[SUBSCRIPTION_PLAN_RULES_BASELINE.md](./SUBSCRIPTION_PLAN_RULES_BASELINE.md)** - Finalized subscription plan policy baseline
   - Price, duration, grace-period limits
   - Renewal behavior contract for current version
   - Cancellation and refund policy guardrails

12. **[SUBSCRIPTION_ELIGIBILITY_RULES_BASELINE.md](./SUBSCRIPTION_ELIGIBILITY_RULES_BASELINE.md)** - Frozen eligibility and discount guardrails baseline
   - Medicine/category include/exclude rule precedence
   - Max discount cap and margin-floor enforcement contract
   - Rule-level restrictions against undercutting plan guardrails

13. **[SUBSCRIPTION_AI_PLAN_RECOMMENDATION_BASELINE.md](./SUBSCRIPTION_AI_PLAN_RECOMMENDATION_BASELINE.md)** - AI recommendation baseline for plan selection
   - Customer history/refill behavior features used for scoring
   - Expected savings, cost, and net-benefit ranking model
   - Fallback and permission guard behavior

14. **[SUBSCRIPTION_AI_RENEWAL_PROPENSITY_BASELINE.md](./SUBSCRIPTION_AI_RENEWAL_PROPENSITY_BASELINE.md)** - AI churn-risk baseline for renewals
   - Renewal-due candidate extraction window and behavior features
   - Churn probability/risk band output contract
   - Advisory action guidance and permission constraints

15. **[SUBSCRIPTION_AI_DISCOUNT_ABUSE_DETECTION_BASELINE.md](./SUBSCRIPTION_AI_DISCOUNT_ABUSE_DETECTION_BASELINE.md)** - AI abuse-detection baseline for discount operations
   - Suspicious enrollment, override, and billing integrity signal inputs
   - Cross-signal risk scoring and ranked finding output contract
   - Access constraints and manual-governance safety boundary

16. **[SUBSCRIPTION_AI_OVERRIDE_RISK_SCORING_BASELINE.md](./SUBSCRIPTION_AI_OVERRIDE_RISK_SCORING_BASELINE.md)** - AI override decision-risk baseline
   - Per-request risk scoring inputs and output contract
   - Pre-approval advisor behavior for Manager/Admin
   - Escalation recommendation and safety constraints

17. **[SUBSCRIPTION_AI_DYNAMIC_OFFER_SUGGESTIONS_BASELINE.md](./SUBSCRIPTION_AI_DYNAMIC_OFFER_SUGGESTIONS_BASELINE.md)** - AI dynamic offer suggestion baseline
   - Offer recommendation inputs from plan fit + renewal risk + candidate policies
   - Guardrail clipping contract (plan cap + minimum margin floor)
   - Safe advisory output for enrollment operators

18. **[SUBSCRIPTION_AI_CONVERSATIONAL_ASSISTANT_BASELINE.md](./SUBSCRIPTION_AI_CONVERSATIONAL_ASSISTANT_BASELINE.md)** - AI conversational explanation assistant baseline
   - Deterministic explanation for discount-applied and discount-rejected outcomes
   - Reason-code mapping to staff-friendly customer communication points
   - Action guidance for eligibility failures and policy-rule outcomes

19. **[SUBSCRIPTION_AI_MULTILINGUAL_EXPLANATION_SNIPPETS_BASELINE.md](./SUBSCRIPTION_AI_MULTILINGUAL_EXPLANATION_SNIPPETS_BASELINE.md)** - AI multilingual explanation snippets baseline
   - Localized subscription savings explanation snippets for checkout and invoice
   - AI-first translation path with deterministic fallback behavior
   - Language metadata and safe rendering contract

20. **[SUBSCRIPTION_AI_MODEL_MONITORING_DASHBOARD_BASELINE.md](./SUBSCRIPTION_AI_MODEL_MONITORING_DASHBOARD_BASELINE.md)** - AI model monitoring dashboard baseline
   - Abuse detection precision/recall definition and subject mapping
   - Recommendation acceptance-rate definition over enrollment decisions
   - Baseline constraints for monitorable ground-truth coverage

21. **[SUBSCRIPTION_AI_FALLBACK_RULES_ENGINE_BASELINE.md](./SUBSCRIPTION_AI_FALLBACK_RULES_ENGINE_BASELINE.md)** - AI fallback rules engine baseline
   - AI-first care protocol generation with deterministic fallback path
   - Checkout continuity contract during AI outages
   - Safety boundaries for rule-based advisory output

22. **[SUBSCRIPTION_AI_INPUT_SAFETY_PII_MASKING_BASELINE.md](./SUBSCRIPTION_AI_INPUT_SAFETY_PII_MASKING_BASELINE.md)** - AI input safety and PII masking baseline
   - Approved-field allowlist for subscription-adjacent AI prompts
   - Centralized masking/tokenization policy before inference
   - Validation coverage for prompt hygiene controls

23. **[SUBSCRIPTION_AI_PROMPT_VERSION_REGISTRY_BASELINE.md](./SUBSCRIPTION_AI_PROMPT_VERSION_REGISTRY_BASELINE.md)** - AI prompt version registry baseline
    - Central prompt-key versioning and active template resolution contract
    - Update/rollback behavior with immutable version history
    - Runtime fallback guarantees when registry access is unavailable

24. **[SUBSCRIPTION_AI_DECISION_LOGGING_BASELINE.md](./SUBSCRIPTION_AI_DECISION_LOGGING_BASELINE.md)** - AI decision logging baseline
    - Reason-coded AI decision capture across subscription intelligence workflows
    - Subject-level and reason-level indexing for post-incident review
    - Best-effort logging behavior that does not block checkout/approval flows

25. **[SUBSCRIPTION_AI_FAIRNESS_BIAS_TEST_SET_BASELINE.md](./SUBSCRIPTION_AI_FAIRNESS_BIAS_TEST_SET_BASELINE.md)** - AI fairness and bias test-set baseline
    - Cross-group offline dataset contract for subscription AI decisions
    - Required feature and customer-group coverage for fairness checks
    - Reference dataset location used by automated benchmark tests

26. **[SUBSCRIPTION_AI_OFFLINE_EVALUATION_BENCHMARKS_BASELINE.md](./SUBSCRIPTION_AI_OFFLINE_EVALUATION_BENCHMARKS_BASELINE.md)** - AI offline evaluation benchmark baseline
    - Feature-level precision/recall/fairness gate thresholds
    - Pre-enablement benchmark pass/fail policy
    - Command-level validation path before production rollout

27. **[SUBSCRIPTION_AI_INCIDENT_RUNBOOK_BASELINE.md](./SUBSCRIPTION_AI_INCIDENT_RUNBOOK_BASELINE.md)** - AI incident runbook baseline
    - Response playbook for misclassification, drift, and outage scenarios
    - 30-minute containment actions and recovery checks
    - Post-incident evidence and governance update checklist

28. **[SUBSCRIPTION_ROLLOUT_ROLLBACK_RUNBOOK.md](./SUBSCRIPTION_ROLLOUT_ROLLBACK_RUNBOOK.md)** - Subscription rollout/rollback operations guide
    - Wave-based rollout plan from pilot to all stores
    - Feature flag controls and rollout entry criteria
    - Rollback triggers and rapid disable procedure

29. **[SUBSCRIPTION_PILOT_MONITORING_CHECKLIST.md](./SUBSCRIPTION_PILOT_MONITORING_CHECKLIST.md)** - Pilot monitoring execution checklist
    - Daily monitoring cadence (morning/midday/end-of-day)
    - Stop/hold conditions and pilot exit criteria
    - Daily sign-off and pilot closeout templates

30. **[SUBSCRIPTION_PILOT_QA_CYCLE_1_REPORT.md](./SUBSCRIPTION_PILOT_QA_CYCLE_1_REPORT.md)** - QA cycle 1 execution baseline
    - Command-level automated QA evidence
    - Result summary and residual risks
    - Pilot readiness decision record

31. **[SUBSCRIPTION_UAT_CHECKLIST.md](./SUBSCRIPTION_UAT_CHECKLIST.md)** - Pharmacy operations UAT checklist
    - End-to-end role-based UAT scenarios
    - Analytics/export/dispatch acceptance checks
    - Formal sign-off section for go/hold decision

32. **[SUBSCRIPTION_PILOT_MONITORING_CYCLE_1_REPORT.md](./SUBSCRIPTION_PILOT_MONITORING_CYCLE_1_REPORT.md)** - Pilot monitoring cycle 1 execution baseline
    - Command-level monitoring run artifact for pilot day window
    - PASS/HOLD gate decision evidence from pricing/override/feedback monitors
    - Inputs for rollout go/no-go governance

33. **[SUBSCRIPTION_ALL_STORES_ROLLOUT_CYCLE_1_REPORT.md](./SUBSCRIPTION_ALL_STORES_ROLLOUT_CYCLE_1_REPORT.md)** - All-stores rollout cycle 1 execution baseline
    - 7-day entry gate execution and evidence
    - Regression gate evidence and final rollout decision
    - Full rollout and rollback flag posture

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
- **JavaFX 21 LTS** - Rich desktop UI framework (with AtlantaFX PrimerDark theme)
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
