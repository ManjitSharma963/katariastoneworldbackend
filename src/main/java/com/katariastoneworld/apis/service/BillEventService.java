package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.entity.BillEvent;
import com.katariastoneworld.apis.entity.BillEventType;
import com.katariastoneworld.apis.entity.BillKind;
import com.katariastoneworld.apis.repository.BillEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BillEventService {

    @Autowired
    private BillEventRepository billEventRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            BillKind billKind,
            Long billId,
            BillEventType eventType,
            Long billVersionId,
            String linkedGroupId,
            Long actorUserId,
            String payloadJson) {
        if (billKind == null || billId == null || eventType == null) {
            return;
        }
        BillEvent e = new BillEvent();
        e.setBillKind(billKind);
        e.setBillId(billId);
        e.setEventType(eventType);
        e.setBillVersionId(billVersionId);
        e.setLinkedGroupId(linkedGroupId);
        e.setPayloadJson(payloadJson);
        e.setCreatedBy(actorUserId);
        billEventRepository.save(e);
    }

    @Transactional(readOnly = true)
    public List<BillEvent> listEvents(BillKind billKind, Long billId) {
        return billEventRepository.findByBillKindAndBillIdOrderByCreatedAtDescIdDesc(billKind, billId);
    }
}
