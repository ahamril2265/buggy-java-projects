package com.example.wallet.web;

import com.example.wallet.service.IdempotentExecutor;
import com.example.wallet.service.IdempotentResult;
import com.example.wallet.service.WalletService;
import com.example.wallet.web.dto.CreateWalletRequest;
import com.example.wallet.web.dto.MoneyRequest;
import com.example.wallet.web.dto.PageResponse;
import com.example.wallet.web.dto.TransactionResponse;
import com.example.wallet.web.dto.UpdateStatusRequest;
import com.example.wallet.web.dto.WalletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets")
public class WalletController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private final WalletService wallets;
    private final IdempotentExecutor idempotent;

    public WalletController(WalletService wallets, IdempotentExecutor idempotent) {
        this.wallets = wallets;
        this.idempotent = idempotent;
    }

    @PostMapping
    public ResponseEntity<WalletResponse> create(@Valid @RequestBody CreateWalletRequest request) {
        WalletResponse created = WalletResponse.from(wallets.createWallet(request.ownerId(), request.currency()));
        return ResponseEntity.created(URI.create("/api/v1/wallets/" + created.id())).body(created);
    }

    @GetMapping("/{walletId}")
    public WalletResponse get(@PathVariable UUID walletId) {
        return WalletResponse.from(wallets.getWallet(walletId));
    }

    @PatchMapping("/{walletId}/status")
    public WalletResponse updateStatus(@PathVariable UUID walletId, @Valid @RequestBody UpdateStatusRequest request) {
        return WalletResponse.from(wallets.changeStatus(walletId, request.status()));
    }

    @PostMapping("/{walletId}/deposits")
    public ResponseEntity<TransactionResponse> deposit(
            @PathVariable UUID walletId,
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank @Size(max = 64) String idempotencyKey,
            @Valid @RequestBody MoneyRequest request) {
        record Command(UUID walletId, long amountMinor, String currency) {
        }
        IdempotentResult<TransactionResponse> result = idempotent.execute(
                idempotencyKey, "deposit", new Command(walletId, request.amountMinor(), request.currency()),
                TransactionResponse.class, HttpStatus.CREATED.value(),
                () -> TransactionResponse.from(wallets.deposit(walletId, request.amountMinor(), request.currency())));
        return respond(result);
    }

    @PostMapping("/{walletId}/withdrawals")
    public ResponseEntity<TransactionResponse> withdraw(
            @PathVariable UUID walletId,
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank @Size(max = 64) String idempotencyKey,
            @Valid @RequestBody MoneyRequest request) {
        record Command(UUID walletId, long amountMinor, String currency) {
        }
        IdempotentResult<TransactionResponse> result = idempotent.execute(
                idempotencyKey, "withdrawal", new Command(walletId, request.amountMinor(), request.currency()),
                TransactionResponse.class, HttpStatus.CREATED.value(),
                () -> TransactionResponse.from(wallets.withdraw(walletId, request.amountMinor(), request.currency())));
        return respond(result);
    }

    @GetMapping("/{walletId}/transactions")
    public PageResponse<TransactionResponse> transactions(
            @PathVariable UUID walletId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size) {
        return PageResponse.from(wallets.transactions(walletId, page, size), TransactionResponse::from);
    }

    static <T> ResponseEntity<T> respond(IdempotentResult<T> result) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(result.status());
        if (result.replayed()) {
            builder.header(REPLAYED_HEADER, "true");
        }
        return builder.body(result.body());
    }
}
