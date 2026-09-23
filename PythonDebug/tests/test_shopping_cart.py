import pytest

from programs.shopping_cart import Cart, add_item


def test_new_cart_is_empty():
    cart = Cart()
    assert cart.item_count() == 0
    assert cart.total() == 0


def test_total_and_item_count():
    cart = Cart()
    cart.add("apple", 0.5, 4)
    cart.add("bread", 2.25)
    assert cart.item_count() == 5
    assert cart.total() == 4.25


def test_each_cart_has_its_own_items():
    first = Cart()
    second = Cart()
    first.add("apple", 1.0, 3)
    assert second.item_count() == 0
    assert second.total() == 0


def test_apply_discount():
    cart = Cart()
    cart.add("lamp", 100)
    assert cart.apply_discount(10) == 90.0
    assert cart.apply_discount(0) == 100.0
    assert cart.apply_discount(100) == 0.0


def test_discount_does_not_change_the_cart():
    cart = Cart()
    cart.add("lamp", 100)
    cart.apply_discount(50)
    assert cart.total() == 100


@pytest.mark.parametrize("percent", [-1, 101])
def test_discount_must_be_between_zero_and_a_hundred(percent):
    with pytest.raises(ValueError):
        Cart().apply_discount(percent)


def test_add_rejects_bad_values():
    cart = Cart()
    with pytest.raises(ValueError):
        cart.add("x", -1)
    with pytest.raises(ValueError):
        cart.add("x", 1, 0)


def test_remove_takes_out_every_entry_with_that_name():
    cart = Cart()
    cart.add("apple", 1, 2)
    cart.add("pear", 2)
    cart.add("apple", 1)
    assert cart.remove("apple") is True
    assert cart.item_count() == 1
    assert cart.remove("apple") is False


def test_add_item_uses_a_new_list_each_time():
    assert add_item("a") == ["a"]
    assert add_item("b") == ["b"]


def test_add_item_appends_to_a_given_list():
    mine = ["x"]
    assert add_item("y", mine) == ["x", "y"]
    assert mine == ["x", "y"]
