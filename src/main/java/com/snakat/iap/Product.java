package com.snakat.iap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.SkuDetails;

public class Product {

    private final String mSku;
    private final Type mType;

    private SkuDetails mSkuDetails;

    private String mPurchaseToken = null;
    private PurchaseState mPurchaseState = PurchaseState.UNSPECIFIED;
    private boolean mAcknowledged = false;

    public Product(@NonNull String sku, @NonNull Type type) {
        mSku = sku;
        mType = type;
    }

    @NonNull
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder()
                .append("(")
                .append(mSku)
                .append(", ").append(mType)
                .append(", PurchaseState=").append(mPurchaseState)
                .append(", Acknowledged=").append(mAcknowledged);
        if (Purchaser.LOG_ENABLED) {
            sb.append(", PurchaseToken=").append(mPurchaseToken);
        }
        sb.append(")");
        return sb.toString();
    }

    @NonNull
    public String getSku() {
        return mSku;
    }

    public boolean isOneTime() {
        return mType == Type.ONE_TIME;
    }

    public boolean isConsumable() {
        return mType == Type.CONSUMABLE;
    }

    @Nullable
    SkuDetails getSkuDetails() {
        return mSkuDetails;
    }

    void setSkuDetails(@Nullable SkuDetails skuDetails) {
        mSkuDetails = skuDetails;
    }

    public boolean isLoaded() {
        return mSkuDetails != null;
    }

    @Nullable
    public String getTitle() {
        return mSkuDetails != null ? mSkuDetails.getTitle() : null;
    }

    @Nullable
    public String getDescription() {
        return mSkuDetails != null ? mSkuDetails.getDescription() : null;
    }

    @Nullable
    public String getPrice() {
        return mSkuDetails != null ? mSkuDetails.getPrice() : null;
    }

    public long getPriceAmount() {
        return mSkuDetails != null ? mSkuDetails.getPriceAmountMicros() : 0;
    }

    @Nullable
    public String getCurrency() {
        return mSkuDetails != null ? mSkuDetails.getPriceCurrencyCode() : null;
    }

    void setPurchaseToken(@Nullable String purchaseToken) {
        mPurchaseToken = purchaseToken;
    }

    @Nullable
    public String getPurchaseToken() {
        return mPurchaseToken;
    }

    void setPurchaseState(int purchaseState) {
        switch (purchaseState) {
            case Purchase.PurchaseState.PURCHASED:
                mPurchaseState = PurchaseState.PURCHASED;
                break;

            case Purchase.PurchaseState.PENDING:
                mPurchaseState = PurchaseState.PENDING;
                break;

            default:
                mPurchaseState = PurchaseState.UNSPECIFIED;
                break;
        }
    }

    @NonNull
    public PurchaseState getPurchaseState() {
        return mPurchaseState;
    }

    public boolean isPurchased() {
        return mAcknowledged && mPurchaseState == PurchaseState.PURCHASED;
    }

    public boolean isPending() {
        return mPurchaseState == PurchaseState.PENDING ||
                (!mAcknowledged && mPurchaseState == PurchaseState.PURCHASED);
    }

    void setAcknowledged(boolean acknowledged) {
        mAcknowledged = acknowledged;
    }

    public boolean isAcknowledged() {
        return mAcknowledged;
    }

    public enum Type {
        ONE_TIME,
        CONSUMABLE,
        ;

        @NonNull
        @Override
        public String toString() {
            switch (this) {
                case ONE_TIME:
                    return "ONE_TIME";
                case CONSUMABLE:
                    return "CONSUMABLE";
            }
            return super.toString();
        }
    }

    enum PurchaseState {
        UNSPECIFIED,
        PURCHASED,
        PENDING,
        ;

        @NonNull
        @Override
        public String toString() {
            switch (this) {
                case UNSPECIFIED:
                    return "UNSPECIFIED";
                case PURCHASED:
                    return "PURCHASED";
                case PENDING:
                    return "PENDING";
            }
            return super.toString();
        }
    }
}
