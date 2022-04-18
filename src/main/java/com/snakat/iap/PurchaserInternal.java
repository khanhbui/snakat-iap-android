package com.snakat.iap;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;
import com.android.billingclient.api.SkuDetailsResponseListener;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import io.reactivex.Completable;
import io.reactivex.CompletableEmitter;
import io.reactivex.CompletableOnSubscribe;
import io.reactivex.CompletableSource;
import io.reactivex.Maybe;
import io.reactivex.MaybeEmitter;
import io.reactivex.MaybeOnSubscribe;
import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.SingleSource;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;

abstract class PurchaserInternal {

    static boolean LOG_ENABLED = true;

    protected static final String TAG = Purchaser.class.getName();

    protected final WeakReference<Context> mContext;
    protected final BillingClient mBillingClient;

    protected final Map<String, Product> mProducts = Collections.synchronizedMap(new HashMap<>());

    protected final List<CompletableEmitter> mConnectEmitters = Collections.synchronizedList(new ArrayList<>());
    protected final Map<String, MaybeEmitter<Purchase>> mPurchaseEmitters = Collections.synchronizedMap(new HashMap<>());

    protected PurchaserInternal(@NonNull Context context, @NonNull ProductList products, boolean logEnabled) {
        LOG_ENABLED = logEnabled;

        mContext = new WeakReference<>(context);
        mBillingClient = BillingClient.newBuilder(context)
                .setListener(new PurchasesUpdatedListener() {
                    @Override
                    public void onPurchasesUpdated(@NonNull BillingResult billingResult, @Nullable List<Purchase> purchases) {
                        PurchaserInternal.this.onPurchasesUpdated(billingResult, purchases);
                    }
                })
                .enablePendingPurchases()
                .build();

        for (Product product : products) {
            mProducts.put(product.getSku(), product);
        }
    }

    protected void onPurchasesUpdated(@NonNull BillingResult billingResult, @Nullable List<Purchase> purchases) {
        if (LOG_ENABLED) {
            Log.i(TAG, String.format("On Purchases Updated: [result=%s] [purchases=%s]", billingResult, purchases == null ? "-" : purchases.size()));
        }

        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
            updatePurchases(purchases);

            for (Purchase purchase : purchases) {
                boolean isPurchased = purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED;

                for (String sku : purchase.getSkus()) {
                    MaybeEmitter<Purchase> emitter = mPurchaseEmitters.remove(sku);
                    if (emitter == null) {
                        continue;
                    }
                    if (isPurchased) {
                        emitter.onSuccess(purchase);
                    } else {
                        emitter.onComplete();
                    }
                }
            }
        } else {
            for (MaybeEmitter<Purchase> emitter : mPurchaseEmitters.values()) {
                emitter.onError(new IapError(billingResult));
            }
            mPurchaseEmitters.clear();
        }
    }

    protected Completable startConnection() {
        return Completable.create(new CompletableOnSubscribe() {
            @Override
            public void subscribe(CompletableEmitter emitter) throws Exception {
                if (mBillingClient.isReady()) {
                    emitter.onComplete();
                    return;
                }

                mConnectEmitters.add(emitter);

                if (mBillingClient.getConnectionState() != BillingClient.ConnectionState.CONNECTING) {
                    mBillingClient.startConnection(new BillingClientStateListener() {
                        @Override
                        public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                                Log.i(TAG, "BillingClient connected.");
                                for (CompletableEmitter item : mConnectEmitters) {
                                    item.onComplete();
                                }
                            } else {
                                IapError iapError = new IapError(billingResult);
                                if (LOG_ENABLED) {
                                    Log.i(TAG, String.format("BillingClient failed to connect with error: %s", iapError));
                                }
                                for (CompletableEmitter item : mConnectEmitters) {
                                    item.onError(iapError);
                                }
                            }

                            mConnectEmitters.clear();
                        }

                        @Override
                        public void onBillingServiceDisconnected() {
                        }
                    });
                }
            }
        });
    }

    @NonNull
    protected Single<List<SkuDetails>> querySkuDetailsAsync(@NonNull List<String> skuList) {
        Single<List<SkuDetails>> single = Single.create(new SingleOnSubscribe<List<SkuDetails>>() {
            @Override
            public void subscribe(SingleEmitter<List<SkuDetails>> emitter) throws Exception {
                SkuDetailsParams params = SkuDetailsParams.newBuilder()
                        .setSkusList(skuList)
                        .setType(BillingClient.SkuType.INAPP)
                        .build();

                mBillingClient.querySkuDetailsAsync(params, new SkuDetailsResponseListener() {
                    @Override
                    public void onSkuDetailsResponse(@NonNull BillingResult billingResult, @Nullable List<SkuDetails> list) {
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                            emitter.onSuccess(list);
                        } else {
                            emitter.onError(new IapError(billingResult));
                        }
                    }
                });
            }
        });

        if (LOG_ENABLED) {
            single = single
                    .doOnSuccess(new Consumer<List<SkuDetails>>() {
                        @Override
                        public void accept(List<SkuDetails> skuDetails) throws Exception {
                            Log.i(TAG, String.format("Query SkuDetails done with %d item(s). ", skuDetails.size()));
                        }
                    })
                    .doOnError(new Consumer<Throwable>() {
                        @Override
                        public void accept(Throwable throwable) throws Exception {
                            Log.i(TAG, String.format("Query SkuDetails failed with error: %s", throwable.getLocalizedMessage()));
                            throwable.printStackTrace();
                        }
                    });
        }

        return single;
    }

    @NonNull
    protected Single<List<Purchase>> queryPurchasesAsync() {
        Single<List<Purchase>> single = Single.create(new SingleOnSubscribe<List<Purchase>>() {
            @Override
            public void subscribe(SingleEmitter<List<Purchase>> emitter) throws Exception {
                mBillingClient.queryPurchasesAsync(BillingClient.SkuType.INAPP, new PurchasesResponseListener() {
                    @Override
                    public void onQueryPurchasesResponse(@NonNull BillingResult billingResult, @NonNull List<Purchase> list) {
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                            emitter.onSuccess(list);
                        } else {
                            emitter.onError(new IapError(billingResult));
                        }
                    }
                });
            }
        });

        if (LOG_ENABLED) {
            single = single
                    .doOnSuccess(new Consumer<List<Purchase>>() {
                        @Override
                        public void accept(List<Purchase> purchases) throws Exception {
                            Log.i(TAG, String.format("Query Purchases done with %d item(s).", purchases.size()));
                        }
                    })
                    .doOnError(new Consumer<Throwable>() {
                        @Override
                        public void accept(Throwable throwable) throws Exception {
                            Log.i(TAG, String.format("Query Purchases failed with error: %s", throwable.getLocalizedMessage()));
                            throwable.printStackTrace();
                        }
                    });
        }

        return single;
    }

    @NonNull
    protected Maybe<Purchase> launchBillingFlow(@NonNull Activity activity, @NonNull SkuDetails skuDetails) {
        Maybe<Purchase> maybe = Maybe.create(new MaybeOnSubscribe<Purchase>() {
            @Override
            public void subscribe(MaybeEmitter<Purchase> emitter) throws Exception {
                BillingFlowParams params = BillingFlowParams.newBuilder()
                        .setSkuDetails(skuDetails)
                        .build();
                BillingResult billingResult = mBillingClient.launchBillingFlow(activity, params);
                if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                    emitter.onError(new IapError(billingResult));
                } else {
                    mPurchaseEmitters.put(skuDetails.getSku(), emitter);
                }
            }
        });

        if (LOG_ENABLED) {
            maybe = maybe
                    .doOnSuccess(new Consumer<Purchase>() {
                        @Override
                        public void accept(Purchase purchase) throws Exception {
                            Log.i(TAG, String.format("Purchase %s done with (%s, %b).",
                                    skuDetails.getSku(),
                                    purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED ? "PURCHASED" : (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING ? "PENDING" : "UNSPECIFIED_STATE"),
                                    purchase.isAcknowledged()));
                        }
                    })
                    .doOnError(new Consumer<Throwable>() {
                        @Override
                        public void accept(Throwable throwable) throws Exception {
                            Log.i(TAG, String.format("Purchase failed with error: %s", throwable.getLocalizedMessage()));
                            throwable.printStackTrace();
                        }
                    });
        }

        return maybe;
    }

    @NonNull
    protected Completable acknowledgePurchase(@NonNull String purchaseToken) {
        Completable completable = Completable.create(new CompletableOnSubscribe() {
            @Override
            public void subscribe(CompletableEmitter emitter) throws Exception {
                AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchaseToken)
                        .build();
                mBillingClient.acknowledgePurchase(params, new AcknowledgePurchaseResponseListener() {
                    @Override
                    public void onAcknowledgePurchaseResponse(@NonNull BillingResult billingResult) {
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                            emitter.onComplete();
                        } else {
                            emitter.onError(new IapError(billingResult));
                        }
                    }
                });
            }
        });

        if (LOG_ENABLED) {
            completable = completable.doOnComplete(new Action() {
                @Override
                public void run() throws Exception {
                    Log.i(TAG, "Acknowledge done.");
                }
            }).doOnError(new Consumer<Throwable>() {
                @Override
                public void accept(Throwable throwable) throws Exception {
                    Log.i(TAG, String.format("Acknowledge failed. %s", throwable.getLocalizedMessage()));
                }
            });
        }

        return completable;
    }

    @NonNull
    protected Completable consumeAsync(@NonNull String purchaseToken) {
        Completable completable = Completable.create(new CompletableOnSubscribe() {
            @Override
            public void subscribe(CompletableEmitter emitter) throws Exception {
                ConsumeParams params = ConsumeParams.newBuilder()
                        .setPurchaseToken(purchaseToken)
                        .build();
                mBillingClient.consumeAsync(params, new ConsumeResponseListener() {
                    @Override
                    public void onConsumeResponse(@NonNull BillingResult billingResult, @NonNull String purchaseToken) {
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                            emitter.onComplete();
                        } else {
                            emitter.onError(new IapError(billingResult));
                        }
                    }
                });
            }
        });

        if (LOG_ENABLED) {
            completable = completable.doOnComplete(new Action() {
                @Override
                public void run() throws Exception {
                    Log.i(TAG, "Consume done.");
                }
            }).doOnError(new Consumer<Throwable>() {
                @Override
                public void accept(Throwable throwable) throws Exception {
                    Log.i(TAG, String.format("Consume failed. %s", throwable.getLocalizedMessage()));
                }
            });
        }

        return completable;
    }

    protected void endConnection() {
        mBillingClient.endConnection();
        mContext.clear();
    }

    @NonNull
    protected ProductList filterProducts(@NonNull List<String> skuList) throws IapError {
        ProductList products = new ProductList();
        for (String sku : skuList) {
            Product product = mProducts.get(sku);
            if (product == null) {
                throw new IapError(IapError.Code.ITEM_UNAVAILABLE, mContext.get().getString(R.string.product_not_found, sku));
            }
            if (product.isLoaded()) {
                products.add(product);
            }
        }
        return products;
    }

    protected void updateSkuDetails(@NonNull List<SkuDetails> skuDetailsList) {
        for (SkuDetails skuDetails : skuDetailsList) {
            String sku = skuDetails.getSku();
            Product product = mProducts.get(sku);
            if (product == null) {
                continue;
            }

            product.setSkuDetails(skuDetails);
        }
    }

    protected void updatePurchases(@NonNull List<Purchase> purchaseList) {
        for (Product product : mProducts.values()) {
            product.setPurchaseToken(null);
            product.setAcknowledged(false);
            product.setPurchaseState(Purchase.PurchaseState.UNSPECIFIED_STATE);
        }

        for (Purchase purchase : purchaseList) {
            updatePurchase(purchase);
        }
    }

    protected void updatePurchase(@NonNull Purchase purchase) {
        for (String sku : purchase.getSkus()) {
            Product product = mProducts.get(sku);
            if (product == null) {
                continue;
            }
            product.setPurchaseToken(purchase.getPurchaseToken());
            product.setAcknowledged(purchase.isAcknowledged());
            product.setPurchaseState(purchase.getPurchaseState());
        }
    }

    @NonNull
    private Completable errorCompletable(IapError.Code code, @StringRes int messageId, Object... args) {
        String message = mContext.get().getString(messageId, args);
        IapError error = new IapError(code, message);
        return Completable.error(error);
    }

    @NonNull
    private <T> Single<T> errorSingle(IapError.Code code, @StringRes int messageId, Object... args) {
        String message = mContext.get().getString(messageId, args);
        IapError error = new IapError(code, message);
        return Single.error(error);
    }

    @NonNull
    protected Completable checkAllExist(List<String> skuList) {
        return Completable.defer(new Callable<CompletableSource>() {
            @Override
            public CompletableSource call() throws Exception {
                for (String sku : skuList) {
                    if (!mProducts.containsKey(sku)) {
                        return errorCompletable(IapError.Code.ITEM_UNAVAILABLE, R.string.product_not_found, sku);
                    }
                }
                return Completable.complete();
            }
        });
    }

    @NonNull
    protected Single<Product> getProduct(@NonNull String sku) {
        return Single.defer(new Callable<SingleSource<? extends Product>>() {
            @Override
            public SingleSource<? extends Product> call() throws Exception {
                Product product = mProducts.get(sku);
                if (product == null) {
                    return errorSingle(IapError.Code.ITEM_UNAVAILABLE, R.string.product_not_found, sku);
                }
                return Single.just(product);
            }
        });
    }

    @NonNull
    protected Single<SkuDetails> getSkuDetails(@NonNull String sku) {
        return getProduct(sku)
                .flatMap(new Function<Product, SingleSource<? extends SkuDetails>>() {
                    @Override
                    public SingleSource<? extends SkuDetails> apply(Product product) throws Exception {
                        SkuDetails skuDetails = product.getSkuDetails();
                        if (skuDetails != null) {
                            return Single.just(skuDetails);
                        }

                        List<String> skuList = new ArrayList<>(1);
                        skuList.add(sku);
                        return querySkuDetailsAsync(skuList)
                                .flatMap(new Function<List<SkuDetails>, SingleSource<? extends SkuDetails>>() {
                                    @Override
                                    public SingleSource<? extends SkuDetails> apply(List<SkuDetails> skuDetailsList) throws Exception {
                                        updateSkuDetails(skuDetailsList);

                                        for (SkuDetails item : skuDetailsList) {
                                            if (item.getSku().equals(sku)) {
                                                return Single.just(item);
                                            }
                                        }
                                        return errorSingle(IapError.Code.ITEM_UNAVAILABLE, R.string.product_not_found, sku);
                                    }
                                });
                    }
                });
    }

    @NonNull
    protected Single<String> getPurchaseToken(@NonNull String sku) {
        return getProduct(sku)
                .flatMap(new Function<Product, SingleSource<? extends String>>() {
                    @Override
                    public SingleSource<? extends String> apply(Product product) throws Exception {
                        String purchaseToken = product.getPurchaseToken();
                        if (purchaseToken != null) {
                            return Single.just(purchaseToken);
                        }

                        return queryPurchasesAsync()
                                .flatMap(new Function<List<Purchase>, SingleSource<? extends String>>() {
                                    @Override
                                    public SingleSource<? extends String> apply(List<Purchase> purchaseList) throws Exception {
                                        updatePurchases(purchaseList);

                                        for (Purchase purchase : purchaseList) {
                                            if (purchase.getSkus().contains(sku)) {
                                                return Single.just(purchase.getPurchaseToken());
                                            }
                                        }
                                        return errorSingle(IapError.Code.ITEM_NOT_OWNED, R.string.product_not_owned, sku);
                                    }
                                });
                    }
                });
    }

    @NonNull
    protected Completable isConsumable(@NonNull String sku) {
        return getProduct(sku)
                .flatMapCompletable(new Function<Product, CompletableSource>() {
                    @Override
                    public CompletableSource apply(Product product) throws Exception {
                        if (product.isConsumable()) {
                            return Completable.complete();
                        }
                        return errorCompletable(IapError.Code.ERROR, R.string.product_not_consumable, sku);
                    }
                });
    }

    @NonNull
    protected Completable addLog(@NonNull String title, @NonNull Completable completable) {
        return completable
                .doOnSubscribe(new Consumer<Disposable>() {
                    @Override
                    public void accept(Disposable disposable) throws Exception {
                        logMap(String.format("%s.OnSubscribe.", title));
                    }
                })
                .doOnComplete(new Action() {
                    @Override
                    public void run() throws Exception {
                        logMap(String.format("%s.OnComplete.", title));
                    }
                })
                .doOnError(new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        Log.i(TAG, String.format("%s.OnError: %s", title, throwable.getLocalizedMessage()));
                        throwable.printStackTrace();
                    }
                });
    }

    @NonNull
    protected <T> Single<T> addLog(@NonNull String title, @NonNull Single<T> single) {
        return single
                .doOnSubscribe(new Consumer<Disposable>() {
                    @Override
                    public void accept(Disposable disposable) throws Exception {
                        logMap(String.format("%s.OnSubscribe.", title));
                    }
                })
                .doOnSuccess(new Consumer<T>() {
                    @Override
                    public void accept(T t) throws Exception {
                        Log.i(TAG, String.format("%s.OnSuccess.item=%s", title, t));
                        logMap(String.format("%s.OnSuccess.", title));
                    }
                })
                .doOnError(new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        Log.i(TAG, String.format("%s.OnError: %s", title, throwable.getLocalizedMessage()));
                        throwable.printStackTrace();
                    }
                });
    }

    @NonNull
    protected <T> Maybe<T> addLog(@NonNull String title, @NonNull Maybe<T> maybe) {
        return maybe
                .doOnSubscribe(new Consumer<Disposable>() {
                    @Override
                    public void accept(Disposable disposable) throws Exception {
                        logMap(String.format("%s.OnSubscribe.", title));
                    }
                })
                .doOnSuccess(new Consumer<T>() {
                    @Override
                    public void accept(T t) throws Exception {
                        Log.i(TAG, String.format("%s.OnSuccess.item=%s", title, t));
                        logMap(String.format("%s.OnSuccess.", title));
                    }
                })
                .doOnError(new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        Log.i(TAG, String.format("%s.OnError: %s", title, throwable.getLocalizedMessage()));
                        throwable.printStackTrace();
                    }
                })
                .doOnComplete(new Action() {
                    @Override
                    public void run() throws Exception {
                        logMap(String.format("%s.OnComplete.", title));
                    }
                });
    }

    protected void logMap(@NonNull String title) {
        StringBuilder strProducts = new StringBuilder(title);
        strProducts.append("Products: [");
        if (mProducts.size() > 0) {
            Object[] values = mProducts.values().toArray();
            strProducts.append(values[0]);
            for (int i = 1; i < values.length; i++) {
                strProducts.append(", ").append(values[i]);
            }
        }
        strProducts.append("]");
        Log.i(TAG, strProducts.toString());
    }
}
