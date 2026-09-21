package com.example.wallet.repository;

import com.example.wallet.domain.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByOwnerIdAndCurrency(String ownerId, String currency);

    /**
     * Returns only the id, not the entity. Loading a wallet entity here would put it in the
     * persistence context <em>before</em> its row is locked, and a later locking query would then
     * hand back that stale managed copy instead of the freshly locked state.
     */
    @Query("select w.id from Wallet w where w.ownerId = :ownerId and w.currency = :currency")
    Optional<UUID> findIdByOwnerIdAndCurrency(@Param("ownerId") String ownerId, @Param("currency") String currency);

    /**
     * Locks the wallets in ascending id order. Every code path that locks more than one wallet
     * goes through this method so that concurrent operations always acquire locks in the same
     * order and cannot deadlock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.id in :ids order by w.id")
    List<Wallet> lockAllByIdOrdered(@Param("ids") Collection<UUID> ids);
}
