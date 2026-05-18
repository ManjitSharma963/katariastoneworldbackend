-- STOCK_RETURN_RECORDED (21 chars) exceeds legacy VARCHAR(20) on bill_events.event_type.
ALTER TABLE bill_events
    MODIFY COLUMN event_type VARCHAR(64) NOT NULL;
