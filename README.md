# snakat-iap-android

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
        billing: 'com.android.billingclient:billing:4.1.0'
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
        Purchaser.createInstance(context);
    }

    @Override
    public void onTerminate() {
        Purchaser.destroyInstance();

        super.onTerminate();
    }
}
```

### Get Products
```java
List<String> skuList = new ArrayList<>();
skuList.add("com.example.sku");

Purchaser.getInstance().getProducts(skuList)
  .subscribeOn(Schedulers.io())
  .observeOn(AndroidSchedulers.mainThread())
  .subscribe(new SingleObserver<List<SkuDetails>>() {
    @Override
    public void onSubscribe(Disposable d) {
        mCompositeDisposable.add(d);
    }

    @Override
    public void onSuccess(List<SkuDetails> products) {
      mView.showProducts(products);
    }

    @Override
    public void onError(Throwable e) {
      mView.showError(e);
    }
  });
```

### Get purchased products
```java
Purchaser.getInstance().getPurchases()
  .subscribeOn(Schedulers.io())
  .observeOn(AndroidSchedulers.mainThread())
  .subscribe(new SingleObserver<List<Purchase>>() {
    @Override
    public void onSubscribe(Disposable d) {
        mCompositeDisposable.add(d);
    }

    @Override
    public void onSuccess(List<Purchase> purchases) {
      mView.showPurchases(products);
    }

    @Override
    public void onError(Throwable e) {
      mView.showError(e);
    }
  });
```

### Purchase a product
```java
Activity activity = ...;
SkuDetails product = ...;

Purchaser.getInstance().purchase(activity, product, true)
  .observeOn(AndroidSchedulers.mainThread())
  .subscribe(new SingleObserver<Purchase>() {
    @Override
    public void onSubscribe(Disposable d) {
        mCompositeDisposable.add(d);
    }

    @Override
    public void onSuccess(Purchase purchase) {
        mView.purchaseSuccess();
    }

    @Override
    public void onError(Throwable e) {
        mView.showError(e);
    }
  });
```

### Acknowledge a purchased product
```java
Purchase purchase = ...;

Purchaser.getInstance().acknowledge(purchase)
  .observeOn(AndroidSchedulers.mainThread())
  .subscribe(new SingleObserver<Purchase>() {
    @Override
    public void onSubscribe(Disposable d) {
        mCompositeDisposable.add(d);
    }

    @Override
    public void onSuccess(Purchase purchase) {
      mView.acknowledgeSuccess(e);
    }

    @Override
    public void onError(Throwable e) {
        mView.showError(e);
    }
  });
```

### Consume a purchased product
```java
Purchase purchase = ...;

Purchaser.getInstance().consume(purchase)
  .observeOn(AndroidSchedulers.mainThread())
  .subscribe(new SingleObserver<Purchase>() {
    @Override
    public void onSubscribe(Disposable d) {
        mCompositeDisposable.add(d);
    }

    @Override
    public void onSuccess(Purchase purchase) {
      mView.consumeSuccess(e);
    }

    @Override
    public void onError(Throwable e) {
        mView.showError(e);
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
