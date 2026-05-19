package com.katariastoneworld.apis.accounting.support;

import com.katariastoneworld.apis.accounting.command.PostMoneyCommand;
import com.katariastoneworld.apis.accounting.config.AccountingFeatureFlags;
import com.katariastoneworld.apis.accounting.domain.AccountingEventType;
import com.katariastoneworld.apis.entity.BillPaymentMode;
import com.katariastoneworld.apis.entity.LedgerPaymentMode;
import com.katariastoneworld.apis.entity.LedgerSources;
import com.katariastoneworld.apis.entity.LedgerTransactionType;
import com.katariastoneworld.apis.entity.MoneyCategory;
import com.katariastoneworld.apis.entity.MoneyDirection;
import com.katariastoneworld.apis.entity.MoneyPaymentMode;
import com.katariastoneworld.apis.entity.MoneyReferenceType;
import com.katariastoneworld.apis.service.MoneyTransactionLegacySync;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/** Salary advance and salary cash payment lines (payroll ledger entry ids). */
@Component
public class PayrollAccountingBridge {

    private static final String MODULE = "PAYROLL";

    private final AccountingFeatureFlags flags;
    private final AccountingPostingSupport posting;
    private final MoneyTransactionLegacySync legacySync;

    public PayrollAccountingBridge(
            AccountingFeatureFlags flags,
            AccountingPostingSupport posting,
            MoneyTransactionLegacySync legacySync) {
        this.flags = flags;
        this.posting = posting;
        this.legacySync = legacySync;
    }

    public void postSalaryAdvance(
            String location,
            Long payrollEntryId,
            Long employeeId,
            String employeeName,
            BigDecimal amount,
            BillPaymentMode mode,
            LocalDate eventDate,
            String notes) {
        postSalaryOut(
                location,
                payrollEntryId,
                employeeId,
                employeeName,
                amount,
                mode,
                eventDate,
                notes,
                LedgerSources.SALARY_ADVANCE,
                "SALARY_ADVANCE",
                AccountingEventType.GENERIC_OUT,
                "EMPLOYEE_ADVANCE",
                "CASH");
    }

    public void postSalaryPayment(
            String location,
            Long payrollEntryId,
            Long employeeId,
            String employeeName,
            BigDecimal amount,
            BillPaymentMode mode,
            LocalDate eventDate,
            String notes) {
        postSalaryOut(
                location,
                payrollEntryId,
                employeeId,
                employeeName,
                amount,
                mode,
                eventDate,
                notes,
                LedgerSources.SALARY_PAY,
                "SALARY_PAY",
                AccountingEventType.GENERIC_OUT,
                "SALARY_EXPENSE",
                "CASH");
    }

    private void postSalaryOut(
            String location,
            Long payrollEntryId,
            Long employeeId,
            String employeeName,
            BigDecimal amount,
            BillPaymentMode mode,
            LocalDate eventDate,
            String notes,
            String ledgerSource,
            String subCategory,
            AccountingEventType eventType,
            String debitAccount,
            String creditAccount) {
        if (location == null || payrollEntryId == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal amt = amount.setScale(2, RoundingMode.HALF_UP);
        if (flags.isPayrollEnabled()) {
            PostMoneyCommand command = PostMoneyCommand.builder()
                    .location(location.trim())
                    .transactionDate(eventDate != null ? eventDate : LocalDate.now())
                    .amount(amt)
                    .direction(MoneyDirection.OUT)
                    .category(MoneyCategory.SALARY)
                    .subCategory(subCategory)
                    .referenceType(MoneyReferenceType.salary)
                    .referenceId(payrollEntryId)
                    .paymentMode(mapPaymentMode(mode))
                    .partyId(employeeId)
                    .partyName(employeeName)
                    .requestId("PAYROLL:" + subCategory + ":" + payrollEntryId)
                    .ledgerTxnType(ledgerSource)
                    .notes(notes)
                    .eventType(eventType)
                    .metadataJson(AccountingPostingSupport.balancedEntryMetadata(debitAccount, creditAccount))
                    .build();
            posting.postOut(command, MODULE);
            return;
        }
        legacySync.syncFromUnified(
                location.trim(),
                eventDate != null ? eventDate : LocalDate.now(),
                amt,
                LedgerTransactionType.DEBIT,
                LedgerPaymentMode.fromBillPaymentMode(mode),
                ledgerSource,
                payrollEntryId,
                notes);
    }

    public void voidPayrollLine(String location, Long payrollEntryId, String ledgerSource, String reason) {
        if (!flags.isPayrollEnabled() || payrollEntryId == null) {
            return;
        }
        posting.voidByRequestId(location, "PAYROLL:SALARY_ADVANCE:" + payrollEntryId, reason, MODULE);
        posting.voidByRequestId(location, "PAYROLL:SALARY_PAY:" + payrollEntryId, reason, MODULE);
        posting.voidByReference(
                location,
                MoneyReferenceType.salary,
                payrollEntryId,
                MoneyCategory.SALARY,
                ledgerSource,
                reason,
                MODULE);
    }

    public boolean tryVoidByLedgerSource(String location, String source, Long referenceId, String reason) {
        if (!flags.isPayrollEnabled() || referenceId == null || source == null) {
            return false;
        }
        String s = source.trim().toUpperCase();
        if (LedgerSources.SALARY_ADVANCE.equals(s) || LedgerSources.SALARY_PAY.equals(s)) {
            voidPayrollLine(location, referenceId, s, reason);
            return true;
        }
        return false;
    }

    private static MoneyPaymentMode mapPaymentMode(BillPaymentMode mode) {
        if (mode == null) {
            return MoneyPaymentMode.CASH;
        }
        return switch (mode) {
            case CASH -> MoneyPaymentMode.CASH;
            case UPI, WALLET -> MoneyPaymentMode.UPI;
            case BANK_TRANSFER, CHEQUE, OTHER -> MoneyPaymentMode.BANK;
        };
    }
}
