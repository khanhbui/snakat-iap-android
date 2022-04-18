# snakat-iap-android

A wrapper of [Google Play Billing Library](https://developer.android.com/google/play/billing) that you no need to care about connecting the BillingClient, just call the method (*getProducts*, *purchase*, *acknowledge*, *consume*, *restorePurchase*) whenever you want.

## Installation
1. Add this to your project as a git submodule
```sh
cd ~/sample_app/
git submodule add https://github.com/khanhbui/snakat-iap-android.git snakat-iap
```
2. Create a file, named *config.gradle*, which defines sdk versions, target versions and dependencies.
```groovy
ext {
    plugins = [
            library: 'com.android.library'
    ]

    android = [
            compileSdkVersion: 31,
            buildToolsVersion: "31.0.0",
            minSdkVersion    : 14,
            targetSdkVersion : 31
    ]

    dependencies = [
            appcompat: 'androidx.appcompat:appcompat:1.4.1',
            billing: 'com.android.billingclient:billing:4.1.0',
            rxjava: 'io.reactivex.rxjava2:rxjava:2.2.21',
            rxandroid: 'io.reactivex.rxjava2:rxandroid:2.1.1'
    ]
}
```
3. Add this line on top of *build.gradle*
```groovy
apply from: "config.gradle"
```
4. Add this line to *settings.gradle*
```groovy
include ':snakat-iap'
```
5. Add this line to dependencies section of *app/build.gradle*
```groovy
implementation project(path: ':snakat-iap')
```

## Usage

### Initialization
```java
public class App extends Application {

  @Override
  public void onCreate() {
    super.onCreate();

    Context context = getApplicationContext();

    ProductList products = new ProductList();
    // An one-time purchase product.
    products.add(new Product("com.example.sku1", Product.Type.ONE_TIME));
    // A consumable purchase product.
    products.add(new Product("com.example.sku2", Product.Type.CONSUMABLE));

    Purchaser.createInstance(context, product);
  }

  @Override
  public void onTerminate() {
    Purchaser.destroyInstance();

    super.onTerminate();
  }
}
```

If you want to see logs while developing your app, enable the logging by passing *true* to the third parameter.
```java
Purchaser.createInstance(context, product, true);
```

### Get Products
```java
List<String> skuList = new ArrayList<>();
skuList.add("com.example.sku");

Purchaser.getInstance()
  .getProducts(skuList)
  .subscribeOn(Schedulers.io())
  .observeOn(AndroidSchedulers.mainThread())
  .subscribe(new SingleObserver<ProductList>() {
    @Override
    public void onSubscribe(Disposable d) {
      // Getting products started.

      mCompositeDisposable.add(d);
    }

    @Override
    public void onSuccess(ProductList products) {
      // Getting products done with a list of products.

      mView.showProducts(products);
    }

    @Override
    public void onError(Throwable e) {
      // Getting products failed.

      mView.showError(e);
    }
  });
```

### Purchase a product
```java
Activity activity = ...;
String sku = "com.example.sku";

Purchaser.getInstance()
  .purchase(activity, sku)
  .subscribeOn(Schedulers.io())
  .observeOn(AndroidSchedulers.mainThread())
  .subscribe(new MaybeObserver<Product>() {
    @Override
    public void onSubscribe(Disposable d) {
      // Purchase started.

      mCompositeDisposable.add(d);
    }

    @Override
    public void onSuccess(Product product) {
      // Purchase done.

      // Here now you should do:
      //  1. Send the corresponding purchaseToken to your backend to verify and grant entitlement.
      sendToBackend(product.getPurchaseToken());

      //  2. Consume the purchased product if it's consumable product.
      if (product.isConsumable()) {
        consumeProduct(product.getSku());
      }
    }

    @Override
    public void onError(Throwable e) {
      // Purchase faileds.

      mView.showError(e);
    }

    @Override
    public void onComplete() {
      // Purchase is in pending state.

      // You should handle the pending purchase by calling restorePurchase in onResume.
    }
  });
```

Note that the acknowledge will be send automatically at client. If you want to at server side via the Google Developer API or acknowledge manually, please use this.
```java
Purchaser.getInstance()
  .purchase(activity, sku, false);
```

### Acknowledge the purchase.
```java
String sku = "com.example.sku";

Purchaser.getInstance()
  .acknowledge(sku)
  .subscribeOn(Schedulers.io())
  .observeOn(AndroidSchedulers.mainThread())
  .subscribe(new SingleObserver<Product>() {
    @Override
    public void onSubscribe(Disposable d) {
      // Acknowledge started.
    }

    @Override
    public void onSuccess(Product product) {
      // Acknowledge done.
    }

    @Override
    public void onError(Throwable e) {
      // Acknowledge failed.
    }
  });
```

### Consume a purchased product
```java
String sku = "com.example.sku";

Purchaser.getInstance()
  .consume(sku)
  .subscribeOn(Schedulers.io())
  .observeOn(AndroidSchedulers.mainThread())
  .subscribe(new CompletableObserver() {
    @Override
    public void onSubscribe(Disposable d) {
      // Consume started.
    }

    @Override
    public void onComplete() {
      // Consume done.
    }

    @Override
    public void onError(Throwable e) {
      // Consume failed.
    }
  });
```

## License
```
MIT License

Copyright (c) 2022 Khanh Bui

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
