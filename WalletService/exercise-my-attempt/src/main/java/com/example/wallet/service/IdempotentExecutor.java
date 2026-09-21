package com.example.wallet.service;

import com.example.wallet.domain.DomainException;
import com.example.wallet.domain.ErrorCode;
import com.example.wallet.domain.IdempotencyRecord;
import com.example.wallet.repository.IdempotencyRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.util.concurrent.ThreadLocalRandom;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Executes an operation at most once per idempotency key.
 *
 * <p>The key is reserved (inserted) <em>inside the same transaction</em> as the operation itself,
 * before the operation runs. Two concurrent requests with the same key therefore collide on the
 * primary key: the loser blocks until the winner commits, fails with a constraint violation, and is
 * retried, at which point it finds the winner's stored response and replays it. If the operation
 * fails, the whole transaction (including the reservation) is rolled back, so failures are never
 * cached and the client may retry with the same key.
 */
@Component
public class IdempotentExecutor {

    private static final int MAX_ATTEMPTS = 4;

    private final IdempotencyRepository records;
    private final TransactionTemplate transactions;
    private final ObjectMapper mapper;
    private final Clock clock;

    public IdempotentExecutor(IdempotencyRepository records, TransactionTemplate transactions,
                              ObjectMapper mapper, Clock clock) {
        this.records = records;
        this.transactions = transactions;
        this.mapper = mapper;
        this.clock = clock;
    }

    public <T> IdempotentResult<T> execute(String key, String operation, Object request, Class<T> responseType,
                                           int successStatus, Supplier<T> action) {
        String requestHash = hash(operation, request);
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return transactions.execute(status ->
                        executeInTransaction(key, operation, requestHash, responseType, successStatus, action));
            } catch (DataIntegrityViolationException | PessimisticLockingFailureException | ObjectOptimisticLockingFailureException lostRace) {
                // Another request with the same key won the race; loop to replay its result.
                pause(attempt);
            }
        }
        throw new DomainException(ErrorCode.REQUEST_IN_PROGRESS,
                "A request with this Idempotency-Key is still being processed");
    }

    private <T> IdempotentResult<T> executeInTransaction(String key, String operation, String requestHash,
                                                         Class<T> responseType, int successStatus,
                                                         Supplier<T> action) {
        Optional<IdempotencyRecord> existing = records.findById(key);
        if (existing.isPresent()) {
            return replay(existing.get(), operation, requestHash, responseType);
        }

        IdempotencyRecord reservation = records.saveAndFlush(
                IdempotencyRecord.reserve(key, operation, requestHash, clock.instant()));
        T body = action.get();
        reservation.complete(successStatus, mapper.writeValueAsString(body));
        return new IdempotentResult<>(successStatus, body, false);
    }

    private <T> IdempotentResult<T> replay(IdempotencyRecord record, String operation, String requestHash,
                                           Class<T> responseType) {
        if (!record.getOperation().equals(operation) || !record.getRequestHash().equals(requestHash)) {
            throw new DomainException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "This Idempotency-Key was already used with a different request");
        }
        T body = mapper.readValue(record.getResponseBody(), responseType);
        return new IdempotentResult<>(200, body, true);
    }

    private String hash(String operation, Object request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = (operation + "\n" + request.getClass().getName()).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private void pause(int attempt) {
        long baseMillis = Math.min(50L * (1L << Math.min(attempt, 6)), 500L); // cap growth
        long jitter = ThreadLocalRandom.current().nextLong(baseMillis / 2, baseMillis + 1);
        try {
            Thread.sleep(jitter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DomainException(ErrorCode.REQUEST_IN_PROGRESS, "Interrupted while retrying");
        }
    }
}
