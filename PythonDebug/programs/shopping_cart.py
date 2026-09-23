"""A shopping cart.

Cart
    Every cart has its OWN list of items. Changing one cart must never affect another cart.

    add(name, price, quantity=1)   Add an item. A negative price or a quantity below 1 raises
                                   ValueError.
    remove(name)                   Remove every entry with that name. Returns True if something
                                   was removed, otherwise False.
    item_count()                   The total number of items (sum of the quantities).
    total()                        The total price, rounded to 2 decimals.
    apply_discount(percent)        The total after taking `percent` off, rounded to 2 decimals.
                                   A percent below 0 or above 100 raises ValueError.

add_item(item, items=None)
    Adds `item` to the list `items` and returns that list. If no list is given, a brand new list
    is used, so calling add_item("a") and then add_item("b") gives ["a"] and then ["b"].
"""


class Cart:
    items = []

    def add(self, name, price, quantity=1):
        if price < 0:
            raise ValueError("price cannot be negative")
        if quantity < 1:
            raise ValueError("quantity must be at least 1")
        self.items.append({"name": name, "price": price, "quantity": quantity})

    def remove(self, name):
        remaining = [item for item in self.items if item["name"] != name]
        removed_something = len(remaining) != len(self.items)
        self.items = remaining
        return removed_something

    def item_count(self):
        return sum(item["quantity"] for item in self.items)

    def total(self):
        return round(sum(item["price"] * item["quantity"] for item in self.items), 2)

    def apply_discount(self, percent):
        if percent < 0 or percent > 100:
            raise ValueError("percent must be between 0 and 100")
        return round(self.total() * (1 - percent / 100), 2)


def add_item(item, items=[]):
    items.append(item)
    return items


def main():
    cart = Cart()
    cart.add("apple", 0.5, 4)
    cart.add("bread", 2.25)
    print("Items:", cart.item_count(), "Total:", cart.total(), "10% off:", cart.apply_discount(10))
    other = Cart()
    print("A different, empty cart has", other.item_count(), "items")
    print(add_item("a"), add_item("b"))


if __name__ == "__main__":
    main()
