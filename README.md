# Android-Bitmap-DiskCache

Java implementation of a Disk-based LRU cache for Android Bitmaps. This is an asynchronous implementation of [JakeWharton's DiskLruCache](https://github.com/JakeWharton/DiskLruCache) targeted for bitmaps and easier to use

### Requirements

1 - Add the following library to your `build.gradle`:

```
implementation 'com.jakewharton:disklrucache:2.0.2'
```

2 - Copy the BitmapDiskCache class that you can find in this repo to your project

### Usage

You can instantiate the BitmapDiskCache in your activity like this:

```
final BitmapDiskCache cache = new BitmapDiskCache(getApplicationContext());
```

You can add bitmaps to the cache and get the assigned key like this:

```
cache.add(someBitmap, key -> {
  // Store the key
 });
```

You can retrieve a bitmap by its key like this:
```
cache.get(key, bitmap -> {
  // Use bitmap
 });
```

When you finish using the cache or when your activity finishes you need to close the cache reference:

```
cache.close();
```
