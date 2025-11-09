package com.katariastoneworld.apis.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

public class DoubleDeserializer extends JsonDeserializer<Double> {
    
    @Override
    public Double deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonToken token = p.getCurrentToken();
        if (token.isNumeric()) {
            return p.getDoubleValue();
        } else if (token == JsonToken.VALUE_STRING) {
            try {
                return Double.parseDouble(p.getText());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}

