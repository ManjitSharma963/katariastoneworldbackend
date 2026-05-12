package com.katariastoneworld.apis.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class MoneyDirectionConverter implements AttributeConverter<MoneyDirection, String> {

    @Override
    public String convertToDatabaseColumn(MoneyDirection attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute == MoneyDirection.IN ? "IN" : "OUT";
    }

    @Override
    public MoneyDirection convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        if ("in".equalsIgnoreCase(dbData.trim())) {
            return MoneyDirection.IN;
        }
        return MoneyDirection.OUT;
    }
}
