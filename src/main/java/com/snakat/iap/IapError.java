package com.snakat.iap;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingResult;

public class IapError extends Exception {

    private final Code mCode;

    public IapError(Code code, String message) {
        super(message);
        mCode = code;
    }

    IapError(BillingResult billingResult) {
        super(billingResult.toString());

        switch (billingResult.getResponseCode()) {
            case BillingClient.BillingResponseCode.SERVICE_TIMEOUT:
                mCode = Code.SERVICE_TIMEOUT;
                break;
            case BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED:
                mCode = Code.FEATURE_NOT_SUPPORTED;
                break;
            case BillingClient.BillingResponseCode.SERVICE_DISCONNECTED:
                mCode = Code.SERVICE_DISCONNECTED;
                break;
            case BillingClient.BillingResponseCode.USER_CANCELED:
                mCode = Code.USER_CANCELED;
                break;
            case BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE:
                mCode = Code.SERVICE_UNAVAILABLE;
                break;
            case BillingClient.BillingResponseCode.BILLING_UNAVAILABLE:
                mCode = Code.BILLING_UNAVAILABLE;
                break;
            case BillingClient.BillingResponseCode.ITEM_UNAVAILABLE:
                mCode = Code.ITEM_UNAVAILABLE;
                break;
            case BillingClient.BillingResponseCode.DEVELOPER_ERROR:
                mCode = Code.DEVELOPER_ERROR;
                break;
            case BillingClient.BillingResponseCode.ERROR:
                mCode = Code.ERROR;
                break;
            case BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED:
                mCode = Code.ITEM_ALREADY_OWNED;
                break;
            case BillingClient.BillingResponseCode.ITEM_NOT_OWNED:
                mCode = Code.ITEM_NOT_OWNED;
                break;
            default:
                mCode = Code.UNKNOWN;
                break;
        }
    }

    public Code getCode() {
        return mCode;
    }

    public enum Code {
        UNKNOWN,
        SERVICE_TIMEOUT,
        FEATURE_NOT_SUPPORTED,
        SERVICE_DISCONNECTED,
        USER_CANCELED,
        SERVICE_UNAVAILABLE,
        BILLING_UNAVAILABLE,
        ITEM_UNAVAILABLE,
        DEVELOPER_ERROR,
        ERROR,
        ITEM_ALREADY_OWNED,
        ITEM_NOT_OWNED,
    }
}
