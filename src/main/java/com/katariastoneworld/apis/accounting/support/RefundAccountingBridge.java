package com.katariastoneworld.apis.accounting.support;

import com.katariastoneworld.apis.accounting.api.AccountingTransactionService;
import com.katariastoneworld.apis.accounting.command.PostMoneyCommand;
import com.katariastoneworld.apis.accounting.command.VoidByReferenceCommand;
import com.katariastoneworld.apis.accounting.command.VoidByRequestIdCommand;
import com.katariastoneworld.apis.accounting.config.AccountingFeatureFlags;
import com.katariastoneworld.apis.accounting.dto.AccountingReference;
import com.katariastoneworld.apis.accounting.domain.AccountingEventType;
import com.katariastoneworld.apis.constants.MoneyLedgerCategories;
import com.katariastoneworld.apis.dto.BillReturnRefundMode;
import com.katariastoneworld.apis.entity.BillPaymentMode;
import com.katariastoneworld.apis.entity.Customer;
import com.katariastoneworld.apis.entity.LedgerPaymentMode;
import com.katariastoneworld.apis.entity.LedgerSources;
import com.katariastoneworld.apis.entity.LedgerTransactionType;
import com.katariastoneworld.apis.entity.MoneyCategory;
import com.katariastoneworld.apis.entity.MoneyDirection;
import com.katariastoneworld.apis.entity.MoneyPaymentMode;
import com.katariastoneworld.apis.entity.MoneyReferenceType;
import com.katariastoneworld.apis.entity.MoneyTransaction;
import com.katariastoneworld.apis.entity.MoneyTxnStatus;
import com.katariastoneworld.apis.repository.MoneyTransactionRepository;
import com.katariastoneworld.apis.service.MoneyTransactionLegacySync;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * All customer refund / revenue-reversal OUT flows in {@code transactions} (stock return payout,
 * advance refund, wallet mirrors, bill adjustment refunds). Bill-payment reversals use
 * {@link BillPaymentAccountingBridge}.
 */
@Component
public class RefundAccountingBridge {

    @Autowired
    private AccountingFeatureFlags accountingFeatureFlags;

    @Autowired
    private AccountingTransactionService accountingTransactionService;

    @Autowired
    private MoneyTransactionRepository moneyTransactionRepository;

    @Autowired
    private MoneyTransactionLegacySync moneyTransactionLegacySync;

    public void postStockReturnCashRefund(
            Long billId,
            Long stockReturnId,
            BillReturnRefundMode mode,
            BigDecimal settlementAmount,
            String legacyRefundPaymentModeRaw,
            String billNumber,
            String location,
            Customer customer,
            Long actorUserId,
            LocalDate billBusinessDate,
            String adjustmentGroupId) {
        if (billId == null || stockReturnId == null || settlementAmount == null
                || settlementAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String txnType = "STOCK_RETURN_" + stockReturnId;
        if (moneyTransactionRepository.existsByReferenceTypeAndReferenceIdAndTxnTypeAndIsDeletedFalse(
                MoneyReferenceType.bill, billId, txnType)) {
            return;
        }

        MoneyPaymentMode paymentMode = resolveStockReturnPaymentMode(mode, legacyRefundPaymentModeRaw);
        String partyName = resolvePartyName(customer);
        String notes = "Stock return settlement | BillNo: " + (billNumber != null ? billNumber : ("#" + billId))
                + " | returnId=" + stockReturnId + " | mode=" + mode;

        PostMoneyCommand command = PostMoneyCommand.builder()
                .location(location != null ? location.trim() : "")
                .transactionDate(billBusinessDate != null ? billBusinessDate : LocalDate.now())
                .amount(settlementAmount.setScale(2, RoundingMode.HALF_UP))
                .direction(MoneyDirection.OUT)
                .category(MoneyCategory.BILL_RETURN)
                .subCategory(MoneyLedgerCategories.SUB_CUSTOMER_REFUND)
                .referenceType(MoneyReferenceType.bill)
                .referenceId(billId)
                .paymentMode(paymentMode)
                .partyId(customer != null ? customer.getId() : null)
                .partyName(partyName)
                .requestId("REFUND:STOCK_RETURN:" + stockReturnId)
                .ledgerTxnType(txnType)
                .adjustmentGroupId(adjustmentGroupId)
                .linkedGroupId(adjustmentGroupId)
                .ownerUserId(actorUserId)
                .notes(notes)
                .eventType(AccountingEventType.REFUND_STOCK_RETURN)
                .build();

        postRefundOut(command);
    }

    public void postAdvanceRefund(
            String location,
            Long customerId,
            Long walletTxnId,
            BillPaymentMode mode,
            BigDecimal amount,
            LocalDate eventDate,
            String description) {
        if (location == null || location.isBlank() || walletTxnId == null || mode == null || amount == null) {
            return;
        }
        BigDecimal amt = amount.setScale(2, RoundingMode.HALF_UP);
        if (amt.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (accountingFeatureFlags.isRefundEnabled()) {
            if (moneyTransactionRepository.existsByReferenceTypeAndReferenceIdAndTxnTypeAndIsDeletedFalse(
                    MoneyReferenceType.other, walletTxnId, LedgerSources.ADVANCE_REFUND)) {
                return;
            }
            PostMoneyCommand command = PostMoneyCommand.builder()
                    .location(location.trim())
                    .transactionDate(eventDate != null ? eventDate : LocalDate.now())
                    .amount(amt)
                    .direction(MoneyDirection.OUT)
                    .category(MoneyCategory.ADVANCE)
                    .subCategory(LedgerSources.ADVANCE_REFUND)
                    .referenceType(MoneyReferenceType.other)
                    .referenceId(walletTxnId)
                    .paymentMode(mapBillPaymentMode(mode))
                    .partyId(customerId)
                    .partyName(customerId != null ? ("Customer_" + customerId) : "Customer_Unknown")
                    .requestId("REFUND:ADVANCE:" + walletTxnId)
                    .ledgerTxnType(LedgerSources.ADVANCE_REFUND)
                    .notes(description != null ? description : ("Customer advance refund customerId=" + customerId))
                    .eventType(AccountingEventType.REFUND_ADVANCE)
                    .build();
            accountingTransactionService.postOut(command);
            return;
        }
        moneyTransactionLegacySync.syncFromUnified(
                location.trim(),
                eventDate != null ? eventDate : LocalDate.now(),
                amt,
                LedgerTransactionType.DEBIT,
                LedgerPaymentMode.fromBillPaymentMode(mode),
                LedgerSources.ADVANCE_REFUND,
                walletTxnId,
                "Customer advance refund customerId=" + customerId);
    }

    public void postBillReturnWalletCredit(
            String location,
            Long customerId,
            Long walletTxnId,
            BigDecimal amount,
            LocalDate eventDate) {
        if (location == null || location.isBlank() || walletTxnId == null || amount == null) {
            return;
        }
        BigDecimal amt = amount.setScale(2, RoundingMode.HALF_UP);
        if (amt.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (accountingFeatureFlags.isRefundEnabled()) {
            String txnType = LedgerSources.BILL_RETURN_WALLET_CREDIT;
            if (moneyTransactionRepository.existsByReferenceTypeAndReferenceIdAndTxnTypeAndIsDeletedFalse(
                    MoneyReferenceType.other, walletTxnId, txnType)) {
                return;
            }
            PostMoneyCommand command = PostMoneyCommand.builder()
                    .location(location.trim())
                    .transactionDate(eventDate != null ? eventDate : LocalDate.now())
                    .amount(amt)
                    .direction(MoneyDirection.OUT)
                    .category(MoneyCategory.OTHER)
                    .subCategory(txnType)
                    .referenceType(MoneyReferenceType.other)
                    .referenceId(walletTxnId)
                    .paymentMode(MoneyPaymentMode.CASH)
                    .partyId(customerId)
                    .partyName(customerId != null ? ("Customer_" + customerId) : "Customer_Unknown")
                    .requestId("REFUND:BILL_RETURN_WALLET:" + walletTxnId)
                    .ledgerTxnType(txnType)
                    .notes("Bill return → wallet credit customerId=" + customerId)
                    .eventType(AccountingEventType.REFUND_BILL_RETURN_WALLET)
                    .build();
            accountingTransactionService.postOut(command);
            return;
        }
        moneyTransactionLegacySync.syncFromUnified(
                location.trim(),
                eventDate != null ? eventDate : LocalDate.now(),
                amt,
                LedgerTransactionType.DEBIT,
                LedgerPaymentMode.CASH,
                LedgerSources.BILL_RETURN_WALLET_CREDIT,
                walletTxnId,
                "Bill return → wallet credit customerId=" + customerId);
    }

    public void postBillEditStoreCredit(
            String location,
            Long customerId,
            Long walletTxnId,
            BigDecimal amount,
            LocalDate eventDate) {
        if (location == null || location.isBlank() || walletTxnId == null || amount == null) {
            return;
        }
        BigDecimal amt = amount.setScale(2, RoundingMode.HALF_UP);
        if (amt.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (accountingFeatureFlags.isRefundEnabled()) {
            String txnType = LedgerSources.BILL_EDIT_ADJUSTMENT;
            if (moneyTransactionRepository.existsByReferenceTypeAndReferenceIdAndTxnTypeAndIsDeletedFalse(
                    MoneyReferenceType.other, walletTxnId, txnType)) {
                return;
            }
            PostMoneyCommand command = PostMoneyCommand.builder()
                    .location(location.trim())
                    .transactionDate(eventDate != null ? eventDate : LocalDate.now())
                    .amount(amt)
                    .direction(MoneyDirection.OUT)
                    .category(MoneyCategory.ADVANCE)
                    .subCategory(txnType)
                    .referenceType(MoneyReferenceType.other)
                    .referenceId(walletTxnId)
                    .paymentMode(MoneyPaymentMode.CASH)
                    .partyId(customerId)
                    .partyName(customerId != null ? ("Customer_" + customerId) : "Customer_Unknown")
                    .requestId("REFUND:BILL_EDIT:" + walletTxnId)
                    .ledgerTxnType(txnType)
                    .notes("Bill edit excess → store credit customerId=" + customerId)
                    .eventType(AccountingEventType.REFUND_BILL_EDIT_CREDIT)
                    .build();
            accountingTransactionService.postOut(command);
            return;
        }
        moneyTransactionLegacySync.syncFromUnified(
                location.trim(),
                eventDate != null ? eventDate : LocalDate.now(),
                amt,
                LedgerTransactionType.DEBIT,
                LedgerPaymentMode.CASH,
                LedgerSources.BILL_EDIT_ADJUSTMENT,
                walletTxnId,
                "Bill edit excess → store credit customerId=" + customerId);
    }

    public void postAdjustmentRefund(
            Long billId,
            String groupId,
            BigDecimal amount,
            MoneyPaymentMode paymentMode,
            LocalDate transactionDate,
            String reference,
            String location,
            Long customerId,
            String partyName,
            Long actorUserId) {
        if (billId == null || groupId == null || groupId.isBlank() || amount == null) {
            return;
        }
        String txnType = "ADJ_SETTLE_REFUND_" + groupId;
        if (moneyTransactionRepository.existsByReferenceTypeAndReferenceIdAndTxnTypeAndIsDeletedFalse(
                MoneyReferenceType.bill, billId, txnType)) {
            return;
        }

        PostMoneyCommand command = PostMoneyCommand.builder()
                .location(location != null ? location.trim() : "")
                .transactionDate(transactionDate != null ? transactionDate : LocalDate.now())
                .amount(amount.setScale(2, RoundingMode.HALF_UP))
                .direction(MoneyDirection.OUT)
                .category(MoneyCategory.BILL_RETURN)
                .subCategory(MoneyLedgerCategories.SUB_ADJUSTMENT_REFUND)
                .referenceType(MoneyReferenceType.bill)
                .referenceId(billId)
                .paymentMode(paymentMode)
                .partyId(customerId)
                .partyName(partyName)
                .requestId("REFUND:ADJ:" + groupId)
                .ledgerTxnType(txnType)
                .adjustmentGroupId(groupId)
                .linkedGroupId(groupId)
                .ownerUserId(actorUserId)
                .notes(reference != null && !reference.isBlank() ? reference : ("Adjustment settlement " + groupId))
                .eventType(AccountingEventType.REFUND_ADJUSTMENT)
                .build();

        postRefundOut(command);
    }

    /**
     * Routes {@link com.katariastoneworld.apis.service.FinancialLedgerService#removeTransaction} for refund ledger sources.
     *
     * @return true if this bridge handled the void (engine or legacy refund path)
     */
    public boolean tryVoidByLedgerSource(String location, String source, Long referenceId, String reason) {
        if (location == null || location.isBlank() || source == null || source.isBlank() || referenceId == null) {
            return false;
        }
        String s = source.trim().toUpperCase(Locale.ROOT);
        return switch (s) {
            case "ADVANCE_REFUND" -> {
                voidAdvanceRefundLedger(location, referenceId, reason);
                yield true;
            }
            case "BILL_EDIT_ADJUSTMENT" -> {
                voidBillEditStoreCreditLedger(location, referenceId, reason);
                yield true;
            }
            case "BILL_RETURN_WALLET_CREDIT" -> {
                voidBillReturnWalletCreditLedger(location, referenceId, reason);
                yield true;
            }
            default -> false;
        };
    }

    public void voidStockReturnCashRefund(String location, Long billId, Long stockReturnId, String reason) {
        if (billId == null || stockReturnId == null) {
            return;
        }
        String txnType = "STOCK_RETURN_" + stockReturnId;
        voidRefundLine(
                location,
                MoneyReferenceType.bill,
                billId,
                MoneyCategory.BILL_RETURN,
                txnType,
                "REFUND:STOCK_RETURN:" + stockReturnId,
                reason);
    }

    public void voidAdvanceRefundLedger(String location, Long walletTxnId, String reason) {
        if (walletTxnId == null) {
            return;
        }
        voidRefundLine(
                location,
                MoneyReferenceType.other,
                walletTxnId,
                MoneyCategory.ADVANCE,
                LedgerSources.ADVANCE_REFUND,
                "REFUND:ADVANCE:" + walletTxnId,
                reason);
    }

    public void voidBillReturnWalletCreditLedger(String location, Long walletTxnId, String reason) {
        if (walletTxnId == null) {
            return;
        }
        voidRefundLine(
                location,
                MoneyReferenceType.other,
                walletTxnId,
                MoneyCategory.OTHER,
                LedgerSources.BILL_RETURN_WALLET_CREDIT,
                "REFUND:BILL_RETURN_WALLET:" + walletTxnId,
                reason);
    }

    public void voidBillEditStoreCreditLedger(String location, Long walletTxnId, String reason) {
        if (walletTxnId == null) {
            return;
        }
        voidRefundLine(
                location,
                MoneyReferenceType.other,
                walletTxnId,
                MoneyCategory.ADVANCE,
                LedgerSources.BILL_EDIT_ADJUSTMENT,
                "REFUND:BILL_EDIT:" + walletTxnId,
                reason);
    }

    public void voidAdjustmentRefund(String location, Long billId, String groupId, String reason) {
        if (billId == null || groupId == null || groupId.isBlank()) {
            return;
        }
        String txnType = "ADJ_SETTLE_REFUND_" + groupId.trim();
        voidRefundLine(
                location,
                MoneyReferenceType.bill,
                billId,
                MoneyCategory.BILL_RETURN,
                txnType,
                "REFUND:ADJ:" + groupId.trim(),
                reason);
    }

    private void voidRefundLine(
            String location,
            MoneyReferenceType referenceType,
            Long referenceId,
            MoneyCategory category,
            String ledgerTxnType,
            String requestId,
            String reason) {
        String loc = location != null ? location.trim() : "";
        if (loc.isEmpty() || referenceId == null) {
            return;
        }
        String voidReason = reason != null && !reason.isBlank() ? reason : "refund voided";

        if (accountingFeatureFlags.isRefundEnabled()) {
            if (requestId != null && !requestId.isBlank()) {
                accountingTransactionService.voidByRequestId(
                        VoidByRequestIdCommand.of(loc, requestId, voidReason));
            }
            if (ledgerTxnType != null && !ledgerTxnType.isBlank()) {
                accountingTransactionService.voidByReference(VoidByReferenceCommand.of(
                        loc,
                        AccountingReference.of(referenceType, referenceId, category, ledgerTxnType),
                        voidReason));
            }
            return;
        }
        voidLegacyRefundLine(loc, referenceType, referenceId, ledgerTxnType, requestId, voidReason);
    }

    private void voidLegacyRefundLine(
            String location,
            MoneyReferenceType referenceType,
            Long referenceId,
            String ledgerTxnType,
            String requestId,
            String reason) {
        if (requestId != null && !requestId.isBlank()) {
            moneyTransactionRepository.findByLocationAndRequestId(location, requestId.trim())
                    .filter(row -> row.getStatus() == MoneyTxnStatus.ACTIVE && !Boolean.TRUE.equals(row.getIsDeleted()))
                    .ifPresent(row -> cancelLegacy(row, reason));
        }
        if (ledgerTxnType != null && !ledgerTxnType.isBlank()) {
            moneyTransactionRepository
                    .findFirstByReferenceTypeAndReferenceIdAndTxnTypeAndStatusAndIsDeletedFalseOrderByIdAsc(
                            referenceType, referenceId, ledgerTxnType, MoneyTxnStatus.ACTIVE)
                    .ifPresent(row -> cancelLegacy(row, reason));
        }
    }

    private void cancelLegacy(MoneyTransaction row, String reason) {
        row.setStatus(MoneyTxnStatus.CANCELLED);
        row.setIsDeleted(true);
        row.setVoidReason(trimReason(reason));
        moneyTransactionRepository.save(row);
    }

    private static String trimReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "VOID";
        }
        String t = reason.trim();
        return t.length() > 500 ? t.substring(0, 500) : t;
    }

    private void postRefundOut(PostMoneyCommand command) {
        if (accountingFeatureFlags.isRefundEnabled()) {
            accountingTransactionService.postOut(command);
            return;
        }
        saveLegacyRefundRow(command);
    }

    private void saveLegacyRefundRow(PostMoneyCommand command) {
        MoneyTransaction tx = new MoneyTransaction();
        LocalDate d = command.transactionDate() != null ? command.transactionDate() : LocalDate.now();
        tx.setLocation(command.location());
        tx.setTransactionDate(d);
        tx.setDateTime(LocalDateTime.now());
        tx.setAmount(command.amount().setScale(2, RoundingMode.HALF_UP));
        tx.setDirection(command.direction());
        tx.setCategory(command.category());
        tx.setSubCategory(command.subCategory());
        tx.setTxnType(command.ledgerTxnType());
        tx.setReferenceType(command.referenceType());
        tx.setReferenceId(command.referenceId());
        tx.setPaymentMode(command.paymentMode());
        tx.setPartyId(command.partyId());
        tx.setPartyName(command.partyName());
        tx.setNotes(command.notes());
        tx.setOwnerUserId(command.ownerUserId());
        tx.setAdjustmentGroupId(command.adjustmentGroupId());
        tx.setLinkedGroupId(command.linkedGroupId());
        tx.setStatus(MoneyTxnStatus.ACTIVE);
        tx.setIsDeleted(false);
        moneyTransactionRepository.save(tx);
    }

    private static MoneyPaymentMode resolveStockReturnPaymentMode(
            BillReturnRefundMode mode, String legacyRefundPaymentModeRaw) {
        if (mode == BillReturnRefundMode.BANK_REFUND) {
            return MoneyPaymentMode.BANK;
        }
        String rawRail = legacyRefundPaymentModeRaw != null && !legacyRefundPaymentModeRaw.isBlank()
                ? legacyRefundPaymentModeRaw.trim()
                : "CASH";
        BillPaymentMode billMode = BillPaymentMode.parseFlexible(rawRail);
        if (billMode == BillPaymentMode.BANK_TRANSFER || billMode == BillPaymentMode.CHEQUE) {
            return MoneyPaymentMode.BANK;
        }
        return mapBillPaymentMode(billMode);
    }

    private static MoneyPaymentMode mapBillPaymentMode(BillPaymentMode mode) {
        if (mode == null) {
            return MoneyPaymentMode.CASH;
        }
        String m = mode.name().trim().toUpperCase(Locale.ROOT);
        return switch (m) {
            case "CASH" -> MoneyPaymentMode.CASH;
            case "UPI", "WALLET" -> MoneyPaymentMode.UPI;
            case "BANK_TRANSFER", "CHEQUE", "OTHER", "BANK" -> MoneyPaymentMode.BANK;
            default -> MoneyPaymentMode.BANK;
        };
    }

    private static String resolvePartyName(Customer customer) {
        if (customer != null && customer.getCustomerName() != null && !customer.getCustomerName().isBlank()) {
            return customer.getCustomerName().trim();
        }
        if (customer != null && customer.getId() != null) {
            return "Customer_" + customer.getId();
        }
        return "Customer_Unknown";
    }
}
