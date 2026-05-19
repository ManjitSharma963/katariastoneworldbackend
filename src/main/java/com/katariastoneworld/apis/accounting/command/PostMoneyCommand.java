package com.katariastoneworld.apis.accounting.command;

import com.katariastoneworld.apis.accounting.domain.AccountingEventType;
import com.katariastoneworld.apis.entity.MoneyCategory;
import com.katariastoneworld.apis.entity.MoneyDirection;
import com.katariastoneworld.apis.entity.MoneyPaymentMode;
import com.katariastoneworld.apis.entity.MoneyReferenceType;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Immutable post instruction for the accounting engine. */
public record PostMoneyCommand(
        String location,
        LocalDate transactionDate,
        BigDecimal amount,
        MoneyDirection direction,
        MoneyCategory category,
        String subCategory,
        MoneyReferenceType referenceType,
        Long referenceId,
        MoneyPaymentMode paymentMode,
        Long partyId,
        String partyName,
        String requestId,
        String notes,
        AccountingEventType eventType,
        Long billPaymentId,
        Long billVersionId,
        String linkedGroupId,
        Long reversalOfBillPaymentId,
        Long ownerUserId,
        String ledgerTxnType,
        String adjustmentGroupId,
        String metadataJson) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String location;
        private LocalDate transactionDate;
        private BigDecimal amount;
        private MoneyDirection direction;
        private MoneyCategory category;
        private String subCategory;
        private MoneyReferenceType referenceType;
        private Long referenceId;
        private MoneyPaymentMode paymentMode;
        private Long partyId;
        private String partyName;
        private String requestId;
        private String notes;
        private AccountingEventType eventType;
        private Long billPaymentId;
        private Long billVersionId;
        private String linkedGroupId;
        private Long reversalOfBillPaymentId;
        private Long ownerUserId;
        private String ledgerTxnType;
        private String adjustmentGroupId;
        private String metadataJson;

        public Builder location(String location) {
            this.location = location;
            return this;
        }

        public Builder transactionDate(LocalDate transactionDate) {
            this.transactionDate = transactionDate;
            return this;
        }

        public Builder amount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public Builder direction(MoneyDirection direction) {
            this.direction = direction;
            return this;
        }

        public Builder category(MoneyCategory category) {
            this.category = category;
            return this;
        }

        public Builder subCategory(String subCategory) {
            this.subCategory = subCategory;
            return this;
        }

        public Builder referenceType(MoneyReferenceType referenceType) {
            this.referenceType = referenceType;
            return this;
        }

        public Builder referenceId(Long referenceId) {
            this.referenceId = referenceId;
            return this;
        }

        public Builder paymentMode(MoneyPaymentMode paymentMode) {
            this.paymentMode = paymentMode;
            return this;
        }

        public Builder partyId(Long partyId) {
            this.partyId = partyId;
            return this;
        }

        public Builder partyName(String partyName) {
            this.partyName = partyName;
            return this;
        }

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public Builder eventType(AccountingEventType eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder billPaymentId(Long billPaymentId) {
            this.billPaymentId = billPaymentId;
            return this;
        }

        public Builder billVersionId(Long billVersionId) {
            this.billVersionId = billVersionId;
            return this;
        }

        public Builder linkedGroupId(String linkedGroupId) {
            this.linkedGroupId = linkedGroupId;
            return this;
        }

        public Builder reversalOfBillPaymentId(Long reversalOfBillPaymentId) {
            this.reversalOfBillPaymentId = reversalOfBillPaymentId;
            return this;
        }

        public Builder ownerUserId(Long ownerUserId) {
            this.ownerUserId = ownerUserId;
            return this;
        }

        public Builder ledgerTxnType(String ledgerTxnType) {
            this.ledgerTxnType = ledgerTxnType;
            return this;
        }

        public Builder adjustmentGroupId(String adjustmentGroupId) {
            this.adjustmentGroupId = adjustmentGroupId;
            return this;
        }

        public Builder metadataJson(String metadataJson) {
            this.metadataJson = metadataJson;
            return this;
        }

        public PostMoneyCommand build() {
            return new PostMoneyCommand(
                    location,
                    transactionDate,
                    amount,
                    direction,
                    category,
                    subCategory,
                    referenceType,
                    referenceId,
                    paymentMode,
                    partyId,
                    partyName,
                    requestId,
                    notes,
                    eventType,
                    billPaymentId,
                    billVersionId,
                    linkedGroupId,
                    reversalOfBillPaymentId,
                    ownerUserId,
                    ledgerTxnType,
                    adjustmentGroupId,
                    metadataJson);
        }
    }
}
