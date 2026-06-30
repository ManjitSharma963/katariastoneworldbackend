package com.katariastoneworld.apis.accounting.internal;

import com.katariastoneworld.apis.accounting.command.PostMoneyCommand;
import com.katariastoneworld.apis.accounting.dto.PostedTransactionResult;
import com.katariastoneworld.apis.entity.MoneyTransaction;
import com.katariastoneworld.apis.entity.MoneyTxnStatus;
import com.katariastoneworld.apis.repository.MoneyTransactionRepository;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Component
class AccountingTransactionWriter {

    private static final int NOTES_MAX = 2000;

    private final MoneyTransactionRepository moneyTransactionRepository;

    AccountingTransactionWriter(MoneyTransactionRepository moneyTransactionRepository) {
        this.moneyTransactionRepository = moneyTransactionRepository;
    }

    PostedTransactionResult post(PostMoneyCommand command) {
        String location = trimLocation(command.location());
        if (command.requestId() != null && !command.requestId().isBlank()) {
            var byRequest = moneyTransactionRepository.findByLocationAndRequestId(location, command.requestId().trim());
            if (byRequest.isPresent()) {
                MoneyTransaction existing = byRequest.get();
                if (existing.getStatus() == MoneyTxnStatus.ACTIVE && !Boolean.TRUE.equals(existing.getIsDeleted())) {
                    return PostedTransactionResult.from(existing, true);
                }
                apply(command, location, existing);
                existing.setStatus(MoneyTxnStatus.ACTIVE);
                existing.setIsDeleted(false);
                existing.setVoidReason(null);
                return PostedTransactionResult.from(moneyTransactionRepository.save(existing), false);
            }
        }

        if (isImmutableLedgerTxnKey(command)) {
            var existing = findExistingActive(location, command);
            if (existing.isPresent()) {
                return PostedTransactionResult.from(existing.get(), true);
            }
        }

        MoneyTransaction row = findExistingActive(location, command).orElseGet(MoneyTransaction::new);
        apply(command, location, row);
        row.setStatus(MoneyTxnStatus.ACTIVE);
        row.setIsDeleted(false);
        row.setVoidReason(null);
        return PostedTransactionResult.from(moneyTransactionRepository.save(row), false);
    }

    private Optional<MoneyTransaction> findExistingActive(String location, PostMoneyCommand command) {
        if (command.billPaymentId() != null) {
            return moneyTransactionRepository.findFirstByBillPaymentIdAndStatusAndIsDeletedFalseOrderByIdAsc(
                    command.billPaymentId(), MoneyTxnStatus.ACTIVE);
        }
        if (command.ledgerTxnType() != null
                && !command.ledgerTxnType().isBlank()
                && command.referenceType() != null
                && command.referenceId() != null) {
            return moneyTransactionRepository.findFirstByReferenceTypeAndReferenceIdAndTxnTypeAndStatusAndIsDeletedFalseOrderByIdAsc(
                    command.referenceType(),
                    command.referenceId(),
                    command.ledgerTxnType().trim(),
                    MoneyTxnStatus.ACTIVE);
        }
        var active = moneyTransactionRepository.findByLocationAndReferenceTypeAndReferenceIdAndCategoryAndStatusAndIsDeletedFalse(
                location,
                command.referenceType(),
                command.referenceId(),
                command.category(),
                MoneyTxnStatus.ACTIVE);
        if (!active.isEmpty()) {
            return Optional.of(active.get(0));
        }
        return moneyTransactionRepository
                .findByReferenceIdAndReferenceTypeAndCategory(
                        command.referenceId(), command.referenceType(), command.category())
                .filter(row -> location.equals(trimLocation(row.getLocation()))
                        && row.getStatus() == MoneyTxnStatus.ACTIVE
                        && !Boolean.TRUE.equals(row.getIsDeleted()));
    }

    private void apply(PostMoneyCommand command, String location, MoneyTransaction row) {
        LocalDate d = command.transactionDate() != null ? command.transactionDate() : LocalDate.now();
        row.setLocation(location);
        row.setTransactionDate(d);
        row.setDateTime(command.billPaymentId() != null ? LocalDateTime.now() : d.atStartOfDay());
        row.setAmount(command.amount().setScale(2, RoundingMode.HALF_UP));
        row.setDirection(command.direction());
        row.setCategory(command.category());
        row.setSubCategory(command.subCategory());
        row.setReferenceType(command.referenceType());
        row.setReferenceId(command.referenceId());
        row.setPaymentMode(command.paymentMode());
        row.setPartyId(command.partyId());
        row.setPartyName(trimParty(command.partyName()));
        row.setRequestId(trimRequestId(command.requestId()));
        row.setNotes(notesMax(command.notes()));
        row.setBillPaymentId(command.billPaymentId());
        row.setBillVersionId(command.billVersionId());
        row.setLinkedGroupId(trimLinkedGroup(command.linkedGroupId()));
        row.setAdjustmentGroupId(trimLinkedGroup(command.adjustmentGroupId()));
        row.setOwnerUserId(command.ownerUserId());
        if (command.ledgerTxnType() != null && !command.ledgerTxnType().isBlank()) {
            row.setTxnType(command.ledgerTxnType().trim());
        } else if (command.eventType() != null) {
            row.setTxnType(command.eventType().name());
        }
        if (command.reversalOfBillPaymentId() != null) {
            moneyTransactionRepository
                    .findFirstByBillPaymentIdAndIsDeletedFalseOrderByIdAsc(command.reversalOfBillPaymentId())
                    .ifPresent(orig -> row.setReversalOfId(orig.getId()));
        }
        if (command.metadataJson() != null && !command.metadataJson().isBlank()) {
            row.setMetadataJson(command.metadataJson());
        }
    }

    /** Refunds and keyed reversals: one active row per {@code ledgerTxnType} + reference (no upsert). */
    private static boolean isImmutableLedgerTxnKey(PostMoneyCommand command) {
        if (command.billPaymentId() != null) {
            return false;
        }
        return command.ledgerTxnType() != null && !command.ledgerTxnType().isBlank();
    }

    private static String trimLocation(String location) {
        if (location == null) {
            return "";
        }
        String t = location.trim();
        return t.length() > 64 ? t.substring(0, 64) : t;
    }

    private static String trimRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return null;
        }
        String t = requestId.trim();
        return t.length() > 64 ? t.substring(0, 64) : t;
    }

    private static String trimLinkedGroup(String linkedGroupId) {
        if (linkedGroupId == null || linkedGroupId.isBlank()) {
            return null;
        }
        String t = linkedGroupId.trim();
        return t.length() > 64 ? t.substring(0, 64) : t;
    }

    private static String trimParty(String partyName) {
        if (partyName == null || partyName.isBlank()) {
            return null;
        }
        String t = partyName.trim();
        return t.length() > 150 ? t.substring(0, 150) : t;
    }

    private static String notesMax(String s) {
        if (s == null) {
            return null;
        }
        if (s.length() <= NOTES_MAX) {
            return s;
        }
        return s.substring(0, NOTES_MAX - 3) + "...";
    }
}
