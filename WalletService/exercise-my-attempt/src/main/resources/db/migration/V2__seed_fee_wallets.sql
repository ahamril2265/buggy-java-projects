-- One system wallet per supported currency collects transfer fees.
insert into wallets (id, owner_id, currency, balance_minor, status, version, created_at, updated_at)
values ('00000000-0000-0000-0000-0000000000f1', 'SYSTEM_FEES', 'USD', 0, 'ACTIVE', 0, current_timestamp, current_timestamp);

insert into wallets (id, owner_id, currency, balance_minor, status, version, created_at, updated_at)
values ('00000000-0000-0000-0000-0000000000f2', 'SYSTEM_FEES', 'EUR', 0, 'ACTIVE', 0, current_timestamp, current_timestamp);

insert into wallets (id, owner_id, currency, balance_minor, status, version, created_at, updated_at)
values ('00000000-0000-0000-0000-0000000000f3', 'SYSTEM_FEES', 'GBP', 0, 'ACTIVE', 0, current_timestamp, current_timestamp);
