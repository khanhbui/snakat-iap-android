package com.snakat.iap;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.SkuDetails;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.reactivex.Completable;
import io.reactivex.CompletableEmitter;
import io.reactivex.CompletableOnSubscribe;
import io.reactivex.CompletableSource;
import io.reactivex.Maybe;
import io.reactivex.MaybeSource;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.SingleSource;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.BiFunction;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;

public final class Purchaser extends PurchaserInternal {

    private static Purchaser mInstance;

    public static Purchaser getInstance() {
        return mInstance;
    }

    public static void createInstance(@NonNull Context context) {
        createInstance(context, null, false);
    }

    public static void createInstance(@NonNull Context context, boolean logEnabled) {
        createInstance(context, null, logEnabled);
    }

    public static void createInstance(@NonNull Context context, @Nullable ProductList products) {
        createInstance(context, products, false);
    }

    public static void createInstance(@NonNull Context context, @Nullable ProductList products, boolean logEnabled) {
        if (mInstance == null) {
            synchronized (Purchaser.class) {
                mInstance = new Purchaser(context, products, logEnabled);
            }
        }
    }

    public static void destroyInstance() {
        mInstance.endConnection();
        mInstance = null;
    }

    private Purchaser(@NonNull Context context, @Nullable ProductList products, boolean logEnabled) {
        super(context, products, logEnabled);
    }

    public void addProducts(@NonNull Product product, Product... others) {
        addProduct(product);
        if (others != null) {
            addProducts(others);
        }
    }

    public void addProducts(@NonNull Product[] products) {
        for (Product product : products) {
            addProduct(product);
        }
    }

    @NonNull
    public Single<ProductList> getProducts(@NonNull String sku, String... others) {
        List<String> skuList = new ArrayList<>(Arrays.asList(others));
        skuList.add(0, sku);
        return getProducts(skuList);
    }

    @NonNull
    public Single<ProductList> getProducts(@NonNull List<String> skuList) {
        Single<ProductList> single = checkAllExist(skuList)
                .andThen(startConnection())
                .andThen(Observable.zip(
                        querySkuDetailsAsync(skuList).toObservable(),
                        queryPurchasesAsync().toObservable(),
                        new BiFunction<List<SkuDetails>, List<Purchase>, ProductList>() {
                            @Override
                            public ProductList apply(List<SkuDetails> skuDetailsList, List<Purchase> purchaseList) throws Exception {
                                updateSkuDetails(skuDetailsList);
                                updatePurchases(purchaseList);

                                return filterProducts(skuList);
                            }
                        }
                ).last(new ProductList()));

        if (LOG_ENABLED) {
            single = addLog("getProducts", single);
        }

        return single;
    }

    @NonNull
    public Maybe<Product> purchase(@NonNull Activity activity, @NonNull String sku) {
        return purchase(activity, sku, true);
    }

    @NonNull
    public Maybe<Product> purchase(@NonNull Activity activity, @NonNull String sku, boolean autoAcknowledge) {
        Maybe<Product> maybe = startConnection()
                .andThen(getSkuDetails(sku))
                .flatMapMaybe(new Function<SkuDetails, MaybeSource<? extends Purchase>>() {
                    @Override
                    public MaybeSource<? extends Purchase> apply(SkuDetails skuDetails) throws Exception {
                        return launchBillingFlow(activity, skuDetails)
                                .subscribeOn(AndroidSchedulers.mainThread());
                    }
                })
                .flatMapSingleElement(new Function<Purchase, SingleSource<? extends Product>>() {
                    @Override
                    public SingleSource<? extends Product> apply(Purchase purchase) throws Exception {
                        if (autoAcknowledge && !purchase.isAcknowledged()) {
                            return acknowledge(sku);
                        }
                        return getProduct(sku);
                    }
                });

        if (LOG_ENABLED) {
            maybe = addLog("purchase", maybe);
        }

        return maybe;
    }

    @NonNull
    public Single<Product> acknowledge(@NonNull String sku) {
        Single<Product> single = startConnection()
                .andThen(getPurchaseToken(sku))
                .flatMapCompletable(new Function<String, CompletableSource>() {
                    @Override
                    public CompletableSource apply(String purchaseToken) throws Exception {
                        return acknowledgePurchase(purchaseToken);
                    }
                })
                .andThen(queryPurchasesAsync())
                .flatMap(new Function<List<Purchase>, SingleSource<Product>>() {
                    @Override
                    public SingleSource<Product> apply(List<Purchase> purchases) throws Exception {
                        updatePurchases(purchases);
                        return getProduct(sku);
                    }
                });

        if (LOG_ENABLED) {
            single = addLog("acknowledge", single);
        }

        return single;
    }

    @NonNull
    public Completable consume(@NonNull String sku) {
        Completable completable = isConsumable(sku)
                .andThen(startConnection())
                .andThen(getPurchaseToken(sku))
                .flatMapCompletable(new Function<String, CompletableSource>() {
                    @Override
                    public CompletableSource apply(String purchaseToken) throws Exception {
                        return consumeAsync(purchaseToken);
                    }
                })
                .andThen(queryPurchasesAsync())
                .doOnSuccess(new Consumer<List<Purchase>>() {
                    @Override
                    public void accept(List<Purchase> purchases) throws Exception {
                        updatePurchases(purchases);
                    }
                })
                .ignoreElement();

        if (LOG_ENABLED) {
            completable = addLog("consume", completable);
        }

        return completable;
    }

    @NonNull
    public Maybe<Product> restorePurchases() {
        return restorePurchases(true);
    }
    @NonNull
    public Maybe<Product> restorePurchases(boolean autoAcknowledge) {
        Maybe<Product> maybe = startConnection()
                .andThen(queryPurchasesAsync())
                .flatMapMaybe(new Function<List<Purchase>, MaybeSource<? extends Product>>() {
                    @Override
                    public MaybeSource<? extends Product> apply(List<Purchase> purchaseList) throws Exception {
                        updatePurchases(purchaseList);

                        for (Product product : mProducts.values()) {
                            if (product.getPurchaseState() == Product.PurchaseState.PURCHASED &&
                                    (product.isConsumable() || !product.isAcknowledged())) {
                                return Maybe.just(product);
                            }
                        }
                        return Maybe.empty();
                    }
                })
                .flatMapSingleElement(new Function<Product, SingleSource<? extends Product>>() {
                    @Override
                    public SingleSource<? extends Product> apply(Product product) throws Exception {
                        if (autoAcknowledge && !product.isAcknowledged()) {
                            return acknowledge(product.getSku());
                        }
                        return Single.just(product);
                    }
                });

        if (LOG_ENABLED) {
            maybe = addLog("restorePurchase", maybe);
        }

        return maybe;
    }
}
