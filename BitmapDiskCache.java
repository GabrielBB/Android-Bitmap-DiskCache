package com.github.gabrielbb;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Environment;
import com.jakewharton.disklrucache.DiskLruCache;
import java.io.File;
import java.io.IOException;
import java.util.UUID;
import static android.os.Environment.isExternalStorageRemovable;

public class BitmapDiskCache {

    private DiskLruCache mDiskLruCache;
    private final Object mDiskCacheLock = new Object();
    private boolean mDiskCacheStarting = true;
    private static final long DISK_CACHE_SIZE = 1024 * 1024 * 20; // 20MB
    private static final String DISK_CACHE_SUBDIR = "BITMAP_CACHE";

    public BitmapDiskCache(Context context) {
        File cacheDir = getDiskCacheDir(context);
        new InitDiskCacheTask().execute(cacheDir);
    }

    private class InitDiskCacheTask extends AsyncTask<File, Void, Void> {
        @Override
        protected Void doInBackground(File... params) {
            synchronized (mDiskCacheLock) {
                File cacheDir = params[0];
                try {
                    mDiskLruCache = DiskLruCache.open(cacheDir, 1, 1, DISK_CACHE_SIZE);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                mDiskCacheLock.notifyAll(); // Wake any waiting threads
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            mDiskCacheStarting = false; // Finished initialization
        }
    }

    public interface DecodingCallback {
        void done(Bitmap bitmap);
    }

    public void get(String key, DecodingCallback callback) {
        new DecodingTask(callback).execute(key);
    }

    private class DecodingTask extends AsyncTask<String, Void, Bitmap> {

        private DecodingCallback callback;

        private DecodingTask(DecodingCallback callback) {
            this.callback = callback;
        }

        // Decode image in background.
        @Override
        protected Bitmap doInBackground(String... params) {
            final String imageKey = String.valueOf(params[0]);

            synchronized (mDiskCacheLock) {
                // Wait while disk cache is started from background thread
                while (mDiskCacheStarting) {
                    try {
                        mDiskCacheLock.wait();
                    } catch (InterruptedException ignored) {
                    }
                }

                if (mDiskLruCache != null) {

                    try (DiskLruCache.Snapshot snapshot = mDiskLruCache.get(imageKey)) {
                        return BitmapFactory.decodeStream(snapshot.getInputStream(0));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            return null;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            callback.done(bitmap);
        }
    }

    public interface EncodingCallback {
        void done(String key);
    }

    public void add(Bitmap bitmap, EncodingCallback callback) {
        new EncodingTask(callback).execute(bitmap);
    }

    private class EncodingTask extends AsyncTask<Bitmap, Void, String> {

        private EncodingCallback callback;

        private EncodingTask(EncodingCallback callback) {
            this.callback = callback;
        }

        // Encode image in background.
        @Override
        protected String doInBackground(Bitmap... bitmaps) {
            synchronized (mDiskCacheLock) {
                DiskLruCache.Editor editor = null;

                try {
                    String key = UUID.randomUUID().toString();

                    if (mDiskLruCache != null && mDiskLruCache.get(key) == null) {
                        editor = mDiskLruCache.edit(key);
                        bitmaps[0].compress(Bitmap.CompressFormat.PNG, 95, editor.newOutputStream(0));
                        editor.commit();
                        return key;
                    }
                } catch (IOException e) {
                    e.printStackTrace();

                    if (editor != null) {
                        try {
                            editor.abort();
                        } catch (IOException ignored) {

                        }
                    }
                }

                return null;
            }
        }

        @Override
        protected void onPostExecute(String key) {
            callback.done(key);
        }
    }

    // Creates a unique subdirectory of the designated app cache directory. Tries to use external
// but if not mounted, falls back on internal storage.
    private static File getDiskCacheDir(Context context) {
        // Check if media is mounted or storage is built-in, if so, try and use external cache dir
        // otherwise use internal cache dir
        final String cachePath =
                Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) ||
                        !isExternalStorageRemovable() ? context.getExternalCacheDir().getPath() :
                        context.getCacheDir().getPath();

        return new File(cachePath + File.separator + DISK_CACHE_SUBDIR);
    }

    public void close() {
        synchronized (mDiskCacheLock) {
            try {
                if (!mDiskLruCache.isClosed()) {
                    mDiskLruCache.close();
                }

                mDiskLruCache.delete();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
