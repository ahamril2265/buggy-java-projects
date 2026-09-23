"""A simple bank account.

BankAccount(owner, balance=0)
    A negative starting balance raises ValueError. Every account keeps its OWN history.

    deposit(amount)     Add money. An amount of 0 or less raises ValueError.
    withdraw(amount)    Take money out. An amount of 0 or less raises ValueError. Taking out more
                        than the balance raises InsufficientFundsError. Taking out EXACTLY the
                        balance is allowed (the balance becomes 0).
    transfer_to(other, amount)
                        Move money to another account. If it fails (for example not enough money)
                        then NEITHER account may change, and neither history may get an entry.
    statement()         A list of strings, one per history entry, in order, for example
                        ["deposit: 100", "withdraw: 30"]. A transfer shows up as
                        "withdraw: 25" on the sending account and "deposit: 25" on the receiving one.
"""


class InsufficientFundsError(Exception):
    pass


class BankAccount:
    def __init__(self, owner, balance=0):
        if balance < 0:
            raise ValueError("starting balance cannot be negative")
        self.owner = owner
        self.balance = balance
        self.history = []

    def deposit(self, amount):
        if amount <= 0:
            raise ValueError("deposit must be positive")
        self.balance += amount
        self.history.append(("deposit", amount))

    def withdraw(self, amount):
        if amount <= 0:
            raise ValueError("withdrawal must be positive")
        if amount >= self.balance:
            raise InsufficientFundsError(f"balance {self.balance} is less than {amount}")
        self.balance -= amount
        self.history.append(("withdraw", amount))

    def transfer_to(self, other, amount):
        other.deposit(amount)
        self.withdraw(amount)

    def statement(self):
        return [f"{kind}: {amount}" for kind, amount in self.history]


def main():
    alice = BankAccount("Alice", 100)
    bob = BankAccount("Bob")
    alice.transfer_to(bob, 40)
    print("Alice:", alice.balance, "Bob:", bob.balance)
    try:
        bob.transfer_to(alice, 500)
    except InsufficientFundsError as error:
        print("Transfer failed:", error)
    print("Alice:", alice.balance, "Bob:", bob.balance)
    print(alice.statement(), bob.statement())


if __name__ == "__main__":
    main()
