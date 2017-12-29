package com.patex.shingle;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.patex.shingle.byteSet.ByteHashSet;
import com.patex.shingle.byteSet.ByteSetFactory;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Created by Alexey on 16.07.2017.
 */
public class ShingleMatcher<T, ID> {

    private final Cache<ID, Shingler> cache;
    private final Function<T, Shingleable> mapFunc;
    private final Function<T, ID> idFunc;
    private final ShingleCache<T> shingleCache;
    private int coef;

    public ShingleMatcher(Function<T, Shingleable> mapFunc, Function<T, ID> idFunc, int coef, int cacheSize) {
        this.mapFunc = mapFunc;
        this.idFunc = idFunc;
        shingleCache = new ShingleCache<>();
        this.coef = coef;
        cache =
                CacheBuilder.newBuilder().
                        maximumSize(cacheSize).
                        expireAfterAccess(10, TimeUnit.MINUTES).build();
    }

    public boolean isSimilar(T first, T second) {
        Shingler firstS = getShigler(first);
        Shingler secondS = getShigler(second);
        return isSimilar(firstS, secondS);
    }

    private boolean isSimilar(Shingler first, Shingler second) {
        Shingler bigger, smaller;
        if (first.size() > second.size()) {
            bigger = first;
            smaller = second;
        } else {
            smaller = first;
            bigger = second;
        }
        if (((float) smaller.size()) / ((float) bigger.size()) < 0.7f) {
            return false;
        }
        int notmatch = smaller.size() / 5;
        for (byte[] shingleHash : smaller) {
            if (!bigger.contains(shingleHash)) {
                notmatch--;
                if (notmatch < 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private Shingler getShigler(T t) {
        ID id = idFunc.apply(t);
        try {
            return cache.get(id, () ->
                    shingleCache.getFromCache(t).orElseGet(() ->
                            createShingler(t)));
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private Shingler createShingler(T t) {
        LazyShingler lazyShingler =  new LazyShingler(mapFunc.apply(t), coef, 16);
        lazyShingler.loadAll();

        ByteHashSet shinglesSet = ByteSetFactory.createByteSet(lazyShingler.getShingles().getSize(), 16);
        for (byte[] shingle : lazyShingler.getShingles()) {
            shinglesSet.add(shingle);
        }
        LoadedShingler shingler = new LoadedShingler(lazyShingler.getShingles());
        try {
            shingleCache.saveToCache(shingler, t);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return shingler;
    }

    public void invalidate(T obj) {
        cache.invalidate(idFunc.apply(obj));
    }

    public void setStorage(ShingleCacheStorage<T> storage) {
        shingleCache.setStorage(storage);
    }
}
