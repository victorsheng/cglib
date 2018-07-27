package net.sf.cglib.core.internal;

import java.util.concurrent.*;

/**
 * 这个设计的场景应该是比较常见的，产生一个对象比较消耗，这时候自然会想到把它缓存起来，一般的写法就向下面的代码：
 * 先组装这个对象，然后放入缓存，放入的时候判断是否已存在。但是这种写法在高并发时一波线程全部同时到达第一步代码，然后都去执行消耗的代码，然后进入第二步的时候就要不断替换，虽然最后的结果可能是正确的，不过会有无谓的浪费。现在再看一下cglib的实现就可以学到了。
 *
 * @param <K>
 * @param <KK>
 * @param <V>
 */
public class LoadingCache<K, KK, V> {
    protected final ConcurrentMap<KK, Object> map;
    protected final Function<K, V> loader;
    protected final Function<K, KK> keyMapper;

    public static final Function IDENTITY = new Function() {
        public Object apply(Object key) {
            return key;
        }
    };

    public LoadingCache(Function<K, KK> keyMapper, Function<K, V> loader) {
        this.keyMapper = keyMapper;
        this.loader = loader;
        this.map = new ConcurrentHashMap<KK, Object>();
    }

    @SuppressWarnings("unchecked")
    public static <K> Function<K, K> identity() {
        return IDENTITY;
    }

    public V get(K key) {
        final KK cacheKey = keyMapper.apply(key);
        Object v = map.get(cacheKey);
        // 如果是FutureTask 则说明还在创建中，如果不是FutureTask，则说明已经创建好可直接返回
        if (v != null && !(v instanceof FutureTask)) {
            return (V) v;
        }

        return createEntry(key, cacheKey, v);
    }

    /**
     * Loads entry to the cache.
     * If entry is missing, put {@link FutureTask} first so other competing thread might wait for the result.
     * @param key original key that would be used to load the instance
     * @param cacheKey key that would be used to store the entry in internal map
     * @param v null or {@link FutureTask<V>}
     * @return newly created instance
     */
    protected V createEntry(final K key, KK cacheKey, Object v) {
        FutureTask<V> task;
        // 标记是一个新建的流程
        boolean creator = false;
        // v有值说明是已经找到在执行的FutureTask
        if (v != null) {
            // Another thread is already loading an instance
            task = (FutureTask<V>) v;
        } else {
            task = new FutureTask<V>(new Callable<V>() {
                public V call() throws Exception {
                    return loader.apply(key);
                }
            });
            Object prevTask = map.putIfAbsent(cacheKey, task);
            // 三种情况
            // 1，没值 则是新放的task 就启动这个task
            // 2，有值 是FutureTask 说明有线程在我执行putIfAbsent之前已经捷足先登了 那就把自己新建的task抛弃掉
            // 3，有值 不是FutureTask 说明已经有task已经执行完成并放入了result 那就直接返回这个resutl即可
            if (prevTask == null) {
                // creator does the load
                creator = true;
                task.run();
            } else if (prevTask instanceof FutureTask) {
                task = (FutureTask<V>) prevTask;
            } else {
                return (V) prevTask;
            }
        }

        V result;
        try {
            // task执行完毕返回值
            result = task.get();
        } catch (InterruptedException e) {
            throw new IllegalStateException("Interrupted while loading cache item", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw ((RuntimeException) cause);
            }
            throw new IllegalStateException("Unable to load cache item", cause);
        }
        if (creator) {
            // 放缓存
            map.put(cacheKey, result);
        }
        return result;
    }
}
