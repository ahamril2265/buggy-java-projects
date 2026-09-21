package com.example.wallet.repository;

import com.example.wallet.domain.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    Page<LedgerEntry> findByWalletId(UUID walletId, Pageable pageable);

    List<LedgerEntry> findByTransactionId(UUID transactionId);

    List<LedgerEntry> findByTransferIdOrderByIdAsc(UUID transferId);

    /** Total withdrawn (as a positive number) from the wallet by entries created strictly after {@code since}. */
    @Query("""
            select coalesce(sum(-e.amountMinor), 0) from LedgerEntry e
            where e.walletId = :walletId
              and e.type = com.example.wallet.domain.EntryType.WITHDRAWAL
              and e.createdAt > :since
            """)
    long sumWithdrawnSince(@Param("walletId") UUID walletId, @Param("since") Instant since);

    @Query("select coalesce(sum(e.amountMinor), 0) from LedgerEntry e where e.walletId = :walletId")
    long sumByWalletId(@Param("walletId") UUID walletId);
}
