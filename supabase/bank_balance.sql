-- Wallet: the bank balance the user typed, and when. One row per user.
-- Lira adds every income and removes every expense logged after set_at to show the live balance.
create table if not exists public.bank_balance (
  user_id   uuid primary key default auth.uid() references auth.users(id) on delete cascade,
  amount    numeric not null,
  currency  text not null,
  set_at    timestamptz not null default now()
);

alter table public.bank_balance enable row level security;

create policy "bank_balance: read own"   on public.bank_balance for select using (auth.uid() = user_id);
create policy "bank_balance: insert own" on public.bank_balance for insert with check (auth.uid() = user_id);
create policy "bank_balance: update own" on public.bank_balance for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "bank_balance: delete own" on public.bank_balance for delete using (auth.uid() = user_id);
