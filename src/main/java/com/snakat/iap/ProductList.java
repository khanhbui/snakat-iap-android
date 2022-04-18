package com.snakat.iap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;

public class ProductList extends ArrayList<Product> {

    @Nullable
    public Product get(@NonNull String sku) {
        for (Product product : this) {
            if (sku.equals(product.getSku())) {
                return product;
            }
        }
        return null;
    }

    public boolean contains(@NonNull String sku) {
        for (Product product : this) {
            if (sku.equals(product.getSku())) {
                return true;
            }
        }
        return false;
    }
}
