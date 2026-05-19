package com.katariastoneworld.apis.accounting.support;

import com.katariastoneworld.apis.accounting.api.AccountingTransactionService;
import com.katariastoneworld.apis.accounting.command.PostMoneyCommand;
import com.katariastoneworld.apis.accounting.command.VoidByReferenceCommand;
import com.katariastoneworld.apis.accounting.config.AccountingFeatureFlags;
import com.katariastoneworld.apis.accounting.domain.AccountingEventType;
import com.katariastoneworld.apis.accounting.dto.AccountingReference;
import com.katariastoneworld.apis.entity.Expense;
import com.katariastoneworld.apis.entity.LedgerPaymentMode;
import com.katariastoneworld.apis.entity.LedgerSources;
import com.katariastoneworld.apis.entity.LedgerTransactionType;
import com.katariastoneworld.apis.entity.MoneyCategory;
import com.katariastoneworld.apis.entity.MoneyDirection;
import com.katariastoneworld.apis.entity.MoneyPaymentMode;
import com.katariastoneworld.apis.entity.MoneyReferenceType;
import com.katariastoneworld.apis.entity.ReferenceType;
import com.katariastoneworld.apis.service.FinancialLedgerService;
import com.katariastoneworld.apis.service.LoanLedgerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Routes expense money lines through the accounting engine when enabled; otherwise legacy ledger sync.
 */
@Component
public class ExpenseAccountingBridge {

    @Autowired
    private AccountingFeatureFlags accountingFeatureFlags;

    @Autowired
    private AccountingTransactionService accountingTransactionService;

    @Autowired
    private FinancialLedgerService financialLedgerService;

    @Autowired
    private LoanLedgerService loanLedgerService;

    public void syncExpenseLedger(Expense expense) {
        if (expense == null || expense.getId() == null || expense.getAmount() == null) {
            return;
        }
        if (expense.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            voidExpenseMoneyLines(expense, "expense amount cleared");
            return;
        }
        if (expense.getReferenceType() == ReferenceType.PAYROLL) {
            financialLedgerService.removeLegacyFinancialTransaction("EXPENSE_DEBIT", String.valueOf(expense.getId()));
            return;
        }
        if (loanLedgerService.isSyncedLoanRepaymentExpense(expense)
                && loanLedgerService.hasRepaymentLedgerRowForExpense(expense.getId())) {
            voidExpenseCategoryOnly(expense, "loan repayment expense; EXPENSE line suppressed");
            financialLedgerService.removeLegacyFinancialTransaction("EXPENSE_DEBIT", String.valueOf(expense.getId()));
            return;
        }
        if (accountingFeatureFlags.isExpenseEnabled()) {
            accountingTransactionService.postOut(buildExpensePost(expense));
            return;
        }
        financialLedgerService.recordTransaction(
                expense.getLocation(),
                expense.getDate(),
                expense.getAmount(),
                LedgerTransactionType.DEBIT,
                LedgerPaymentMode.fromLegacyPaymentMethod(expense.getPaymentMethod()),
                LedgerSources.EXPENSE,
                expense.getId(),
                expense.getDescription());
    }

    public void voidExpenseMoneyLines(Expense expense, String reason) {
        if (expense == null || expense.getId() == null || expense.getLocation() == null) {
            return;
        }
        if (accountingFeatureFlags.isExpenseEnabled()) {
            voidByEngine(expense, reason);
        } else {
            financialLedgerService.removeTransaction(expense.getLocation(), LedgerSources.EXPENSE, expense.getId());
            financialLedgerService.removeTransaction(expense.getLocation(), LedgerSources.LOAN_REPAY, expense.getId());
        }
        financialLedgerService.removeLegacyFinancialTransaction("EXPENSE_DEBIT", String.valueOf(expense.getId()));
    }

    private void voidExpenseCategoryOnly(Expense expense, String reason) {
        if (accountingFeatureFlags.isExpenseEnabled()) {
            accountingTransactionService.voidByReference(VoidByReferenceCommand.of(
                    expense.getLocation(),
                    AccountingReference.of(MoneyReferenceType.expense, expense.getId(), MoneyCategory.EXPENSE),
                    reason));
        } else {
            financialLedgerService.removeTransaction(expense.getLocation(), LedgerSources.EXPENSE, expense.getId());
        }
    }

    private void voidByEngine(Expense expense, String reason) {
        accountingTransactionService.voidByReference(VoidByReferenceCommand.of(
                expense.getLocation(),
                AccountingReference.of(MoneyReferenceType.expense, expense.getId(), MoneyCategory.EXPENSE),
                reason));
        accountingTransactionService.voidByReference(VoidByReferenceCommand.of(
                expense.getLocation(),
                AccountingReference.of(MoneyReferenceType.loan, expense.getId(), MoneyCategory.LOAN),
                reason));
    }

    private static PostMoneyCommand buildExpensePost(Expense expense) {
        return PostMoneyCommand.builder()
                .location(expense.getLocation())
                .transactionDate(expense.getDate())
                .amount(expense.getAmount())
                .direction(MoneyDirection.OUT)
                .category(MoneyCategory.EXPENSE)
                .subCategory("EXPENSE")
                .referenceType(MoneyReferenceType.expense)
                .referenceId(expense.getId())
                .paymentMode(toMoneyPaymentMode(LedgerPaymentMode.fromLegacyPaymentMethod(expense.getPaymentMethod())))
                .requestId("EXPENSE:OUT:" + expense.getId())
                .notes(expense.getDescription())
                .eventType(AccountingEventType.EXPENSE_OUT)
                .build();
    }

    private static MoneyPaymentMode toMoneyPaymentMode(LedgerPaymentMode mode) {
        if (mode == null) {
            return MoneyPaymentMode.CASH;
        }
        return switch (mode) {
            case CASH -> MoneyPaymentMode.CASH;
            case UPI -> MoneyPaymentMode.UPI;
            case BANK, CARD, CHEQUE -> MoneyPaymentMode.BANK;
        };
    }
}
