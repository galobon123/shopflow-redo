package com.Proyect.Ecommerce.model;

import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;

import com.Proyect.Ecommerce.search.ProductSearchIndex;

/** Tells the search index that the products changed, so the next search sees the new names. */
public class ProductChangesListener {

    @PostPersist
    @PostUpdate
    @PostRemove
    void changed(Product product) {
        ProductSearchIndex.productsChanged();
    }
}
