package com.katariastoneworld.apis.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Locale;

/**
 * Maps {@link MoneyCategory} to {@code transactions.category} (VARCHAR or legacy ENUM values).
 */
@Converter(autoApply = true)
public class MoneyCategoryConverter implements AttributeConverter<MoneyCategory, String> {

    @Override
    public String convertToDatabaseColumn(MoneyCategory attribute) {
        if (attribute == null) {
            return MoneyCategory.OTHER.name();
        }
        return attribute.name();
    }

    @Override
    public MoneyCategory convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return MoneyCategory.OTHER;
        }
        String key = dbData.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "bill_payment", "bill" -> MoneyCategory.BILL;
            case "advance" -> MoneyCategory.ADVANCE;
            case "expense" -> MoneyCategory.EXPENSE;
            case "salary" -> MoneyCategory.SALARY;
            case "loan_taken", "loan_given", "loan_repayment", "loan" -> MoneyCategory.LOAN;
            case "client_payment" -> MoneyCategory.CLIENT_PAYMENT;
            case "other" -> MoneyCategory.OTHER;
            default -> {
                String normalized = dbData.trim().toUpperCase(Locale.ROOT);
                try {
                    yield MoneyCategory.valueOf(normalized);
                } catch (IllegalArgumentException ex) {
                    yield MoneyCategory.OTHER;
                }
            }
        };
    }
}
