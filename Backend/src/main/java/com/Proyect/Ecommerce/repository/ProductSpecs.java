package com.Proyect.Ecommerce.repository;

import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

import com.Proyect.Ecommerce.model.Product;

/** HU-10 · Catalog filters, combined only when the caller sends them. */
public final class ProductSpecs {

    private static final char ESCAPE = '\\';

    private ProductSpecs() {
    }

    public static Specification<Product> active() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }

    /** Category by name, any capitalization ("remeras" finds "Remeras"). */
    public static Specification<Product> inCategory(String category) {
        return (root, query, cb) -> cb.equal(cb.lower(root.get("category").get("name")), category.toLowerCase(Locale.ROOT));
    }

    /** Text in the name or the description, case-insensitive. % and _ are searched literally. */
    public static Specification<Product> matching(String text) {
        String pattern = "%" + escapeLike(text.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), pattern, ESCAPE),
                cb.like(cb.lower(root.get("description")), pattern, ESCAPE));
    }

    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
