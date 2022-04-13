package com.snakat.iap;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.Completable;
import io.reactivex.CompletableEmitter;
import io.reactivex.CompletableOnSubscribe;
import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.SingleSource;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

public class Purchaser {

    private static Purchaser mInstance;
    public static Purchaser getInstance() {
        return mInstance;
    }
    public static void createInstance(Context context) {
        if (mInstance == null) {
            mInstance = new Purchaser(context);
        }
    }
    public static void destroyInstance() {
        mInstance.endConnection();
        mInstance = null;
    }

    private final BillingClient mBillingClient;

    private final Map<String, SkuDetails> mProducts;

    private final List<CompletableEmitter> mConnectEmitters = new ArrayList<>();
    private SingleEmitter<Purchase> mPurchaseEmitter;

    private Purchaser(Context context) {
        mBillingClient = BillingClient.newBuilder(context)
                .setListener(new PurchasesUpdatedListener() {
                    @Override
                    public void onPurchasesUpdated(BillingResult billingResult, List<Purchase> purchases) {
                        Purchaser.this.onPurchasesUpdated(billingResult, purchases);
                    }
                })
                .enablePendingPurchases()
                .build();

        mProducts = new HashMap<>();
    }

    private void onPurchasesUpdated(BillingResult billingResult, List<Purchase> purchases) {
        if (mPurchaseEmitter == null) {
            return;
        }

        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase purchase : purchases) {
                if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                    mPurchaseEmitter.onSuccess(purchase);
                }
            }
        }
        else {
            mPurchaseEmitter.onError(new Throwable(billingResult.getDebugMessage()));
        }
        mPurchaseEmitter = null;
    }

    private Completable startConnection() {
        return Completable.create(new CompletableOnSubscribe() {
            @Override
            public void subscribe(CompletableEmitter emitter) throws Exception {
                if (mBillingClient.isReady()) {
                    emitter.onComplete();
                }
                else {
                    synchronized (Purchaser.this) {
                        mConnectEmitters.add(emitter);
                    }

                    if (mBillingClient.getConnectionState() != BillingClient.ConnectionState.CONNECTING) {
                        mBillingClient.startConnection(new BillingClientStateListener() {
                            @Override
                            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                                List<CompletableEmitter> list;
                                synchronized (Purchaser.this) {
                                    list = new ArrayList<>(mConnectEmitters);
                                    mConnectEmitters.clear();
                                }

                                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                                    for (CompletableEmitter item : list) {
                                        item.onComplete();
                                    }
                                } else {
                                    for (CompletableEmitter item : list) {
                                        item.onError(new Exception(billingResult.getDebugMessage()));
                                    }
                                }
                            }

                            @Override
                            public void onBillingServiceDisconnected() {
                            }
                        });
                    }
                }
            }
        });
    }

    @NonNull
    private Single<List<SkuDetails>> querySkuDetailsAsync(@NonNull List<String> skuList) {
        return Single.create(new SingleOnSubscribe<List<SkuDetails>>() {
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
                        }
                        else {
                            emitter.onError(new Throwable(billingResult.getDebugMessage()));
                        }
                    }
                });
            }
        });
    }

    @NonNull
    private Single<List<Purchase>> queryPurchasesAsync() {
        return Single.create(new SingleOnSubscribe<List<Purchase>>() {
            @Override
            public void subscribe(SingleEmitter<List<Purchase>> emitter) throws Exception {
                mBillingClient.queryPurchasesAsync(BillingClient.SkuType.INAPP, new PurchasesResponseListener() {
                    @Override
                    public void onQueryPurchasesResponse(@NonNull BillingResult billingResult, @NonNull List<Purchase> list) {
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                            emitter.onSuccess(list);
                        }
                        else {
                            emitter.onError(new Throwable(billingResult.getDebugMessage()));
                        }
                    }
                });
            }
        });
    }

    @NonNull
    private Single<Purchase> launchBillingFlow(Activity activity, SkuDetails product) {
        return Single.create(new SingleOnSubscribe<Purchase>() {
            @Override
            public void subscribe(SingleEmitter<Purchase> emitter) throws Exception {
                mPurchaseEmitter = emitter;

                BillingFlowParams params = BillingFlowParams.newBuilder()
                        .setSkuDetails(product)
                        .build();
                BillingResult billingResult = mBillingClient.launchBillingFlow(activity, params);
                if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                    mPurchaseEmitter = null;
                    emitter.onError(new Throwable(billingResult.getDebugMessage()));
                }
            }
        });
    }

    @NonNull
    private Single<Purchase> acknowledgePurchase(@NonNull Purchase purchase) {
        return Single.create(new SingleOnSubscribe<Purchase>() {
            @Override
            public void subscribe(SingleEmitter<Purchase> emitter) throws Exception {
                AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.getPurchaseToken())
                        .build();
                mBillingClient.acknowledgePurchase(params, new AcknowledgePurchaseResponseListener() {
                    @Override
                    public void onAcknowledgePurchaseResponse(@NonNull BillingResult billingResult) {
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                            emitter.onSuccess(purchase);
                        }
                        else {
                            emitter.onError(new Throwable(billingResult.getDebugMessage()));
                        }
                    }
                });
            }
        });
    }

    @NonNull
    private Completable consumeAsync(@NonNull Purchase purchase) {
        return Completable.create(new CompletableOnSubscribe() {
            @Override
            public void subscribe(CompletableEmitter emitter) throws Exception {
                ConsumeParams params = ConsumeParams.newBuilder()
                        .setPurchaseToken(purchase.getPurchaseToken())
                        .build();
                mBillingClient.consumeAsync(params, new ConsumeResponseListener() {
                    @Override
                    public void onConsumeResponse(@NonNull BillingResult billingResult, @NonNull String purchaseToken) {
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                            emitter.onComplete();
                        }
                        else {
                            emitter.onError(new Throwable(billingResult.getDebugMessage()));
                        }
                    }
                });
            }
        });
    }

    private void endConnection() {
        mBillingClient.endConnection();
    }

    private boolean containsAll(@NonNull List<String> skuList) {
        for (int i = 0; i < skuList.size(); i++) {
            String sku = skuList.get(i);
            if (!mProducts.containsKey(sku)) {
                return false;
            }
        }
        return true;
    }

    @NonNull
    private List<SkuDetails> filterProducts(@NonNull List<String> skuList) {
        List<SkuDetails> products = new ArrayList<>();
        for (int i = 0; i < skuList.size(); i++) {
            String sku = skuList.get(i);
            if (mProducts.containsKey(sku)) {
                SkuDetails product = mProducts.get(sku);
                products.add(product);
            }
        }
        return products;
    }

    private void updateProducts(@Nullable List<SkuDetails> list) {
        for (int i = 0; i < list.size(); i++) {
            SkuDetails item = list.get(i);
            String sku = item.getSku();
            mProducts.put(sku, item);
        }
    }

    @NonNull
    public Single<List<SkuDetails>> getProducts(@NonNull List<String> skuList) {
        if (containsAll(skuList)) {
            List<SkuDetails> products = filterProducts(skuList);
            return Single.just(products);
        }

        Single<List<SkuDetails>> single = mBillingClient.isReady() ? querySkuDetailsAsync(skuList) :
                startConnection().andThen(querySkuDetailsAsync(skuList));
        return single.doOnSuccess(new Consumer<List<SkuDetails>>() {
            @Override
            public void accept(List<SkuDetails> list) throws Exception {
                updateProducts(list);
            }
        });
    }

    @NonNull
    public Single<List<Purchase>> getPurchases() {
        return mBillingClient.isReady() ? queryPurchasesAsync() :
                startConnection().andThen(queryPurchasesAsync());
    }

    @NonNull
    public Single<Purchase> purchase(@NonNull Activity activity, @NonNull SkuDetails product) {
        return purchase(activity, product, false);
    }

    @NonNull
    public Single<Purchase> purchase(@NonNull Activity activity, @NonNull SkuDetails product, boolean autoAcknowledge) {
        Single<Purchase> single = mBillingClient.isReady() ? launchBillingFlow(activity, product) :
                startConnection()
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .andThen(launchBillingFlow(activity, product));

        if (autoAcknowledge) {
            return single.flatMap(new Function<Purchase, SingleSource<? extends Purchase>>() {
                @Override
                public SingleSource<? extends Purchase> apply(Purchase purchase) throws Exception {
                    return acknowledge(purchase);
                }
            });
        }
        else {
            return single;
        }
    }

    @NonNull
    public Single<Purchase> acknowledge(@NonNull Purchase purchase) {
        if (purchase.isAcknowledged()) {
            return Single.just(purchase);
        }
        return mBillingClient.isReady() ? acknowledgePurchase(purchase) :
                startConnection().andThen(acknowledgePurchase(purchase));
    }

    @NonNull
    public Completable consume(@NonNull Purchase purchase) {
        return mBillingClient.isReady() ? consumeAsync(purchase) :
                startConnection().andThen(consumeAsync(purchase));
    }
}
