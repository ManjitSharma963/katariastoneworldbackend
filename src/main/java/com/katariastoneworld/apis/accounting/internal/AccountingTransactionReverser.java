package com.katariastoneworld.apis.accounting.internal;

import com.katariastoneworld.apis.accounting.command.ReverseMoneyCommand;
import com.katariastoneworld.apis.accounting.domain.AccountingEventType;
import com.katariastoneworld.apis.accounting.dto.PostedTransactionResult;
import com.katariastoneworld.apis.entity.MoneyDirection;
import com.katariastoneworld.apis.entity.MoneyTransaction;
import com.katariastoneworld.apis.entity.MoneyTxnStatus;
import com.katariastoneworld.apis.repository.MoneyTransactionRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
class AccountingTransactionReverser {

    private final MoneyTransactionRepository moneyTransactionRepository;

    AccountingTransactionReverser(MoneyTransactionRepository moneyTransactionRepository) {
        this.moneyTransactionRepository = moneyTransactionRepository;
    }

    PostedTransactionResult reverse(ReverseMoneyCommand command) {
        if (command == null || command.transactionId() == null) {
            throw new IllegalArgumentException("transactionId is required");
        }
        MoneyTransaction original = moneyTransactionRepository.findById(command.transactionId())
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + command.transactionId()));
        if (command.location() != null && !command.location().trim().equals(original.getLocation())) {
            throw new IllegalArgumentException("Transaction location mismatch");
        }
        if (original.getStatus() == MoneyTxnStatus.CANCELLED || Boolean.TRUE.equals(original.getIsDeleted())) {
            return PostedTransactionResult.from(original, true);
        }

        if (command.requestId() != null && !command.requestId().isBlank()) {
            var existing = moneyTransactionRepository.findByLocationAndRequestId(
                    original.getLocation(), command.requestId().trim());
            if (existing.isPresent()) {
                return PostedTransactionResult.from(existing.get(), true);
            }
        }

        MoneyDirection offsetDirection = original.getDirection() == MoneyDirection.IN
                ? MoneyDirection.OUT
                : MoneyDirection.IN;

        MoneyTransaction offset = new MoneyTransaction();
        LocalDate d = original.getTransactionDate() != null ? original.getTransactionDate() : LocalDate.now();
        offset.setLocation(original.getLocation());
        offset.setTransactionDate(d);
        offset.setDateTime(original.getDateTime() != null ? original.getDateTime() : d.atStartOfDay());
        offset.setAmount(original.getAmount());
        offset.setDirection(offsetDirection);
        offset.setCategory(original.getCategory());
        offset.setSubCategory(original.getSubCategory());
        offset.setReferenceType(original.getReferenceType());
        offset.setReferenceId(original.getReferenceId());
        offset.setPaymentMode(original.getPaymentMode());
        offset.setPartyId(original.getPartyId());
        offset.setPartyName(original.getPartyName());
        offset.setRequestId(command.requestId());
        offset.setReversesTransactionId(original.getId());
        offset.setNotes(command.reason());
        offset.setTxnType(AccountingEventType.GENERIC_OUT.name());
        offset.setStatus(MoneyTxnStatus.ACTIVE);
        offset.setIsDeleted(false);
        MoneyTransaction savedOffset = moneyTransactionRepository.save(offset);

        original.setStatus(MoneyTxnStatus.CANCELLED);
        original.setVoidReason(trimReason(command.reason()));
        moneyTransactionRepository.save(original);

        return PostedTransactionResult.from(savedOffset, false);
    }

    private static String trimReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "REVERSED";
        }
        String t = reason.trim();
        return t.length() > 500 ? t.substring(0, 500) : t;
    }
}
