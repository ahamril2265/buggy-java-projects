package com.example.wallet.web.dto;

import com.example.wallet.domain.TransferStatus;
import com.example.wallet.service.TransferService.RefundResult;

import java.util.UUID;

public record RefundResponse(UUID transactionId, UUID transferId, long refundedNowMinor, long totalRefundedMinor,
                             long refundableMinor, TransferStatus status) {

    public static RefundResponse from(RefundResult result) {
        return new RefundResponse(result.transactionId(), result.transfer().getId(), result.refundedNowMinor(),
                result.transfer().getRefundedMinor(), result.transfer().refundableMinor(),
                result.transfer().getStatus());
    }
}
