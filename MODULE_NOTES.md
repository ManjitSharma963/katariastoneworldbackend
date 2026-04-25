# Module Notes (Short)

Quick overview of backend modules and responsibilities.

## Auth (`controller/AuthController`, `service/AuthService`)
- Handles register, login, and `/me`.
- JWT token issuance/validation.
- Role-aware access (`admin`, `user`).

## Billing (`controller/BillController`, `service/BillService`)
- GST and Non-GST bill create/read/list/delete.
- Bill payments (add/update/delete).
- PDF generation and bill email integration.
- Inventory side effects:
  - reserve -> deduct -> consume on create
  - return/release on cancel
  - partial stock return support for old bills

## Inventory (`controller/ProductController`, `service/ProductService`)
- Product CRUD and stock operations.
- Ledger-first stock changes in `inventory_transactions`.
- `products.quantity` used as cached on-hand value.
- Reservation support in `inventory_reservations`.

## Customer & Wallet Advance (`CustomerController`, `CustomerAdvanceController`)
- Customer CRUD and lookup by phone.
- Customer wallet ledger (`customer_wallet_transactions`):
  - deposit credit
  - bill usage debit
  - refund credit on bill cancellation

## Financial Ledger / Balance (`FinancialLedgerService`, `BalanceController`)
- Canonical money movement logs.
- In-hand and bank summary endpoints.
- Reconciliation support for budget/report screens.

## Daily Budget & Reports (`DailyBudgetController`, `ReportController`)
- Daily budget set/get/history.
- Daily closing and payment-mode summaries.
- In-hand reconciliation endpoint.

## Expenses / Employees / Payroll
- `ExpenseController`: expense CRUD.
- `EmployeeController`: employee CRUD.
- `PayrollController`: advance + salary settlement + employee ledger/summary.

## Client Purchases (Supplier side)
- Client purchase CRUD and payments.
- Supplier account settings and alerts (due/credit limit).
- Running ledger endpoints.

## Website Content Modules
- `HeroController`: homepage banners.
- `CategoryController`: categories.
- `WebsiteProductController`: website product catalogue.

## Layering Rule (must follow)
- Controller = HTTP + DTO only
- Service = business logic only
- Repository = DB access only
- Dependency direction: `controller -> service -> repository`

