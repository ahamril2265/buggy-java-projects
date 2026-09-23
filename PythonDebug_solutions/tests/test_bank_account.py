import pytest

from programs.bank_account import BankAccount, InsufficientFundsError


def test_deposit_and_withdraw():
    account = BankAccount("Amy", 100)
    account.deposit(50)
    account.withdraw(30)
    assert account.balance == 120


def test_withdrawing_exactly_the_balance_is_allowed():
    account = BankAccount("Amy", 100)
    account.withdraw(100)
    assert account.balance == 0


def test_withdrawing_more_than_the_balance_fails():
    account = BankAccount("Amy", 100)
    with pytest.raises(InsufficientFundsError):
        account.withdraw(100.01)
    assert account.balance == 100


@pytest.mark.parametrize("amount", [0, -5])
def test_deposit_and_withdraw_need_a_positive_amount(amount):
    account = BankAccount("Amy", 100)
    with pytest.raises(ValueError):
        account.deposit(amount)
    with pytest.raises(ValueError):
        account.withdraw(amount)
    assert account.balance == 100


def test_negative_starting_balance_is_rejected():
    with pytest.raises(ValueError):
        BankAccount("Amy", -1)


def test_each_account_has_its_own_history():
    first = BankAccount("Amy")
    second = BankAccount("Bob")
    first.deposit(10)
    assert second.statement() == []


def test_statement():
    account = BankAccount("Amy", 100)
    account.deposit(50)
    account.withdraw(30)
    assert account.statement() == ["deposit: 50", "withdraw: 30"]


def test_transfer_moves_the_money():
    amy = BankAccount("Amy", 100)
    bob = BankAccount("Bob", 5)
    amy.transfer_to(bob, 40)
    assert amy.balance == 60
    assert bob.balance == 45
    assert amy.statement() == ["withdraw: 40"]
    assert bob.statement() == ["deposit: 40"]


def test_a_failed_transfer_changes_nothing():
    amy = BankAccount("Amy", 10)
    bob = BankAccount("Bob", 5)
    with pytest.raises(InsufficientFundsError):
        amy.transfer_to(bob, 50)
    assert amy.balance == 10
    assert bob.balance == 5
    assert amy.statement() == []
    assert bob.statement() == []


def test_transfer_of_the_whole_balance_is_allowed():
    amy = BankAccount("Amy", 25)
    bob = BankAccount("Bob")
    amy.transfer_to(bob, 25)
    assert amy.balance == 0
    assert bob.balance == 25
