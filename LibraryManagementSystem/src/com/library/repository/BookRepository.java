package com.library.repository;

import com.library.model.Book;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

public class BookRepository {
    private final Map<String, Book> booksByIsbn = new LinkedHashMap<>();

    public void save(Book book) {
        booksByIsbn.put(book.getIsbn(), book);
    }

    public Book findByIsbn(String isbn) {
        if (isbn == null) {
            return null;
        }
        return booksByIsbn.get(isbn);
    }

    public List<Book> findAll() {
        return new ArrayList<>(booksByIsbn.values());
    }

    public List<Book> searchByTitle(String query) {
        List<Book> results = new ArrayList<>();
        if (query == null || query.isEmpty()) {
            return results;
        }
        String needle = query.toLowerCase();
        for (Book book : booksByIsbn.values()) {
            if (book.getTitle().toLowerCase().contains(needle)) {
                results.add(book);
            }
        }
        return results;
    }
}
