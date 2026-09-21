package com.example.wallet.web;

import com.example.wallet.service.IdempotentExecutor;
import com.example.wallet.service.IdempotentResult;
import com.example.wallet.service.TransferService;
import com.example.wallet.web.dto.RefundRequest;
import com.example.wallet.web.dto.RefundResponse;
import com.example.wallet.web.dto.TransferRequest;
import com.example.wallet.web.dto.TransferResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferService transfers;
    private final IdempotentExecutor idempotent;

    public TransferController(TransferService transfers, IdempotentExecutor idempotent) {
        this.transfers = transfers;
        this.idempotent = idempotent;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> transfer(
            @RequestHeader(WalletController.IDEMPOTENCY_KEY) @NotBlank @Size(max = 64) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        IdempotentResult<TransferResponse> result = idempotent.execute(
                idempotencyKey, "transfer", request, TransferResponse.class, HttpStatus.CREATED.value(),
                () -> TransferResponse.from(transfers.transfer(request.sourceWalletId(), request.targetWalletId(),
                        request.amountMinor(), request.currency())));
        return WalletController.respond(result);
    }

    @GetMapping("/{transferId}")
    public TransferResponse get(@PathVariable UUID transferId) {
        return TransferResponse.from(transfers.getTransfer(transferId));
    }

    @PostMapping("/{transferId}/refunds")
    public ResponseEntity<RefundResponse> refund(
            @PathVariable UUID transferId,
            @RequestHeader(WalletController.IDEMPOTENCY_KEY) @NotBlank @Size(max = 64) String idempotencyKey,
            @Valid @RequestBody RefundRequest request) {
        record Command(UUID transferId, long amountMinor) {
        }
        IdempotentResult<RefundResponse> result = idempotent.execute(
                idempotencyKey, "refund", new Command(transferId, request.amountMinor()), RefundResponse.class,
                HttpStatus.CREATED.value(),
                () -> RefundResponse.from(transfers.refund(transferId, request.amountMinor())));
        return WalletController.respond(result);
    }
}
