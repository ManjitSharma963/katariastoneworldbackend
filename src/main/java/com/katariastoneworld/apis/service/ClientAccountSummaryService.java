package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.ClientAccountSummaryDTO;
import com.katariastoneworld.apis.dto.ClientChannelBalanceDTO;
import com.katariastoneworld.apis.dto.ClientPurchaseResponseDTO;
import com.katariastoneworld.apis.dto.ClientTransactionResponseDTO;
import com.katariastoneworld.apis.entity.ClientAccountChannel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ClientAccountSummaryService {

    @Autowired
    private ClientPurchaseService clientPurchaseService;

    @Autowired
    private ClientTransactionService clientTransactionService;

    public ClientAccountSummaryDTO summarize(String location, String clientName) {
        if (location == null || location.isBlank() || clientName == null || clientName.isBlank()) {
            return ClientAccountSummaryDTO.builder().clientName(clientName).build();
        }
        String keyLower = clientName.trim().toLowerCase(java.util.Locale.ROOT);
        List<ClientPurchaseResponseDTO> purchases = clientPurchaseService.getAllClientPurchases(location.trim()).stream()
                .filter(p -> p.getClientName() != null
                        && p.getClientName().trim().toLowerCase(java.util.Locale.ROOT).equals(keyLower))
                .toList();

        ClientChannelBalanceDTO gst = buildChannelBalance(
                location, clientName, ClientAccountChannel.GST, purchases);
        ClientChannelBalanceDTO nonGst = buildChannelBalance(
                location, clientName, ClientAccountChannel.NON_GST, purchases);
        ClientChannelBalanceDTO combined = combine(gst, nonGst);

        return ClientAccountSummaryDTO.builder()
                .clientName(clientName.trim())
                .combined(combined)
                .gst(gst)
                .nonGst(nonGst)
                .build();
    }

    private ClientChannelBalanceDTO buildChannelBalance(
            String location,
            String clientName,
            ClientAccountChannel channel,
            List<ClientPurchaseResponseDTO> allPurchases) {
        List<ClientPurchaseResponseDTO> channelPurchases = allPurchases.stream()
                .filter(p -> channel.name().equalsIgnoreCase(
                        p.getAccountChannel() != null ? p.getAccountChannel() : ClientAccountChannel.NON_GST.name()))
                .toList();

        BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal paid = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal pending = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (ClientPurchaseResponseDTO p : channelPurchases) {
            total = total.add(nullToZero(p.getTotalAmount()));
            paid = paid.add(nullToZero(p.getAmountPaid()));
            pending = pending.add(nullToZero(p.getAmountOutstanding()));
        }

        List<ClientTransactionResponseDTO> ledger = clientTransactionService.runningLedgerForClient(
                location, clientName, channel.name());
        BigDecimal running = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (!ledger.isEmpty()) {
            ClientTransactionResponseDTO last = ledger.get(ledger.size() - 1);
            if (last.getRunningBalanceAfter() != null) {
                running = last.getRunningBalanceAfter().setScale(2, RoundingMode.HALF_UP);
            }
        }

        return ClientChannelBalanceDTO.builder()
                .accountChannel(channel.name())
                .accountLabel(channel.displayLabel())
                .purchaseCount(channelPurchases.size())
                .totalAmount(total)
                .paidAmount(paid)
                .pendingAmount(pending)
                .runningBalance(running)
                .build();
    }

    private static ClientChannelBalanceDTO combine(ClientChannelBalanceDTO gst, ClientChannelBalanceDTO nonGst) {
        return ClientChannelBalanceDTO.builder()
                .accountChannel("COMBINED")
                .accountLabel("Combined")
                .purchaseCount(gst.getPurchaseCount() + nonGst.getPurchaseCount())
                .totalAmount(gst.getTotalAmount().add(nonGst.getTotalAmount()))
                .paidAmount(gst.getPaidAmount().add(nonGst.getPaidAmount()))
                .pendingAmount(gst.getPendingAmount().add(nonGst.getPendingAmount()))
                .runningBalance(null)
                .build();
    }

    private static BigDecimal nullToZero(BigDecimal v) {
        return v != null ? v.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
