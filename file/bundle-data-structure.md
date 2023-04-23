# Bundle数据结构和反序列化分析


## 前言


![Screenshot_2023-04-14-16-33-28-107_com.ss.android.article.news-edit.jpg](../imgs/Screenshot_2023-04-14-16-33-28-107_com.ss.android.article.news-edit.jpg)

最近发生的新闻：拼多多疑似利用Android序列化漏洞攻击用户手机，窃取竞争对手软件数据，防止自己被卸载。

序列化和反序列化是指将内存数据结构转换为字节流，通过网络传输或者保存到磁盘，然后再将字节流恢复为内存对象的过程。在 `Web` 安全领域，出现过很多反序列化漏洞，比如 `PHP反序列化`、`Java反序列化`等。由于在反序列化的过程中触发了非预期的程序逻辑，从而被攻击者用精心构造的字节流触发并利用漏洞从而最终实现任意代码执行等目的。

`Android` 中除了传统的 `Java序列化机制`，还有一个特殊的序列化方法，即 `Parcel`。根据官方文档的介绍，`Parcelable` 和 `Bundle` 对象主要的作用是用于跨进程边界的数据传输(IPC/Binder)，但 `Parcel` 并不是一个通用的序列化方法，因此不建议开发者将 `Parcel` 数据保存到磁盘或者通过网络传输。

作为 `IPC` 传输的数据结构，`Parcel` 的设计初衷是轻量和高效，因此缺乏完善的安全校验。这就引发了历史上出现过多次的 `Android 反序列化漏洞`。

上一篇文章 [launchAnyWhere: Activity组件权限绕过漏洞解析](https://ishare.58corp.com/articleDetail?id=111278) 对`launchAnyWhere`漏洞进行了介绍和分析。

要想继续学习关于这个漏洞后续的知识就需要掌握`Bundle`的数据结构以及它怎么进行的序列化和反序列化。

## Bundle简介

```
"In the Android platform, the binder is used for nearly everything that happens across processes in the core platform."

–Dianne Hackborn,Google
```

[https://lkml.org/lkml/2009/6/25/3](https://lkml.org/lkml/2009/6/25/3)


`Android Binder`是知名女程序员`Dianne Hackborn`基于自己开发的`OpenBinder`重新实现的`Android` IPC机制，是`Android`里最核心的机制。不同于`Linux`下的管道、共享内存、消息队列、socket等，它是一套传输效率高、可操作性好、安全性高的`Client-Server`通信机制。`Android Binder`通过`/dev/binder`驱动实现底层的进程间通信，通过共享内存实现高性能，它的安全通过`Binder Token`来保证。


`Binder`里用到了代理模式（`Proxy Pattern`）、中介者模式（`Mediator Pattern`）、桥接模式（`Bridge Pattern`）。熟悉这些设计模式有助于更好的理解`Binder机制`。需要了解以下概念：`Binder`、`Binder Object`、`Binder Protocol`、`IBinder interface`、`Binder Token`、`AIDL`（Android interface definition language）、ServiceManager等。下图大致描述了Binder从kernel层、中间件层到应用层中涉及的重要函数，本文漏洞利用部分会用到。

## 源码分析

`Bundle`源代码：

```java
public final class Bundle extends BaseBundle implements Cloneable, Parcelable {
    /****部分代码省略****/
}
```

Parcel``的反序列化核心函数位于`android.os.BaseBundle`类中：

```java
//路径：frameworks/base/core/java/android/os/BaseBundle.java
public class BaseBundle {
    BaseBundle(Parcel parcelledData) {
        readFromParcelInner(parcelledData);
    }
    void readFromParcelInner(Parcel parcel) {
        // Keep implementation in sync with readFromParcel() in
        // frameworks/native/libs/binder/PersistableBundle.cpp.
        int length = parcel.readInt();//所有的数据长度
        readFromParcelInner(parcel, length);
    }
}
```

`BaseBundle`的序列化构造主要逻辑再`readFromParcelInner`中:

```java
//路径：frameworks/base/core/java/android/os/BaseBundle.java
public class BaseBundle {
    BaseBundle(Parcel parcelledData) {
        readFromParcelInner(parcelledData);
    }
    void readFromParcelInner(Parcel parcel) {
        // Keep implementation in sync with readFromParcel() in
        // frameworks/native/libs/binder/PersistableBundle.cpp.
        int length = parcel.readInt();//所有的数据长度
        readFromParcelInner(parcel, length);
    }
    private void readFromParcelInner(Parcel parcel, int length) {
        if (length < 0) {
            throw new RuntimeException("Bad length in parcel: " + length);
        } else if (length == 0) {
            // Empty Bundle or end of data.
            mParcelledData = NoImagePreloadHolder.EMPTY_PARCEL;
            mParcelledByNative = false;
            return;
        }
        final int magic = parcel.readInt();//读取魔数，判断是JavaBundle还是NativeBundle
        final boolean isJavaBundle = magic == BUNDLE_MAGIC;//0x4C444E42
        final boolean isNativeBundle = magic == BUNDLE_MAGIC_NATIVE;//0x4C444E44
        if (!isJavaBundle && !isNativeBundle) {
            throw new IllegalStateException("Bad magic number for Bundle: 0x" +
                Integer.toHexString(magic));
        }
        //如果Parcel存在读写Helper，就不懒惰进行数据解析，而是直接数据解析操作
        if (parcel.hasReadWriteHelper()) {
            synchronized (this) {
                initializeFromParcelLocked(parcel, /*recycleParcel=*/
                    false, isNativeBundle);
            }
            return;
        }
        //对这个Parcel进行数据解析
        int offset = parcel.dataPosition();
        parcel.setDataPosition(MathUtils.addOrThrow(offset, length));
        Parcel p = Parcel.obtain();
        p.setDataPosition(0);
        p.appendFrom(parcel, offset, length);
        p.adoptClassCookies(parcel);
        p.setDataPosition(0);
        mParcelledData = p;
        mParcelledByNative = isNativeBundle;
    }   
}
```

正常都是使用`lazily-unparcel`模式，所以在对`Bundle`内容进行操作的时候才会实际调用`initializeFromParcelLocked`来执行反序列化，这种方法有助于在多个进程之间连续传递同一个`Bundle`而不需要访问其中的内容时提高性能。

```java
//路径：frameworks/base/core/java/android/os/BaseBundle.java
public class BaseBundle {
    private void initializeFromParcelLocked(@NonNull Parcel parcelledData, boolean recycleParcel,
            boolean parcelledByNative) {
        if (isEmptyParcel(parcelledData)) {//判断是否为空的Bundle
            if (mMap == null) {
                mMap = new ArrayMap<>(1);
            } else {
                mMap.erase();
            }
            mParcelledData = null;
            mParcelledByNative = false;
            return;
        }
        //Bundle中键值对的数量
        final int count = parcelledData.readInt();
        if (count < 0) {
            return;
        }
        ArrayMap<String, Object> map = mMap;
        if (map == null) {
            map = new ArrayMap<>(count);
        } else {
            map.erase();
            map.ensureCapacity(count);
        }
        try {
            if (parcelledByNative) {
                // If it was parcelled by native code, then the array map keys aren't sorted
                // by their hash codes, so use the safe (slow) one.
                //对于Native Bundle，其Key没有按照hashcode进行排序，使用另一个安全方式读取
                parcelledData.readArrayMapSafelyInternal(map, count, mClassLoader);
            } else {
                // If parcelled by Java, we know the contents are sorted properly,
                // so we can use ArrayMap.append().
                //对于JavaBundle，我们知道内容已经正确排序，因此可以使用ArrayMap.append()。
                parcelledData.readArrayMapInternal(map, count, mClassLoader);
            }
        } catch (BadParcelableException e) {
            if (sShouldDefuse) {
                Log.w(TAG, "Failed to parse Bundle, but defusing quietly", e);
                map.erase();
            } else {
                throw e;
            }
        } finally {
            mMap = map;
            if (recycleParcel) {
                recycleParcel(parcelledData);
            }
            mParcelledData = null;
            mParcelledByNative = false;
        }
    }        
}
```

这里面有一个值得注意的问题是`Bundle`中`Key`排序的问题，我们在初始构造原始`Parcel`数据的时候，要考虑到`Key`的`hashcode排序`问题。否则在反序列化之后`Bundle`的`key`会被重新排序，影响我们后续的利用。

> 再次提醒一下这里的`hashcode排序`，很重要！！！

```java
//路径：frameworks/base/core/java/android/os/Parcel.java
public final class Parcel {
    /* package */ void readArrayMapInternal(ArrayMap outVal, int N, ClassLoader loader) {
        if (DEBUG_ARRAY_MAP) {
            RuntimeException here = new RuntimeException("here");
            here.fillInStackTrace();
            Log.d(TAG, "Reading " + N + " ArrayMap entries", here);
        }
        int startPos;
        //循环将Parcel中的数据读取到Map中
        while (N > 0) {
            if (DEBUG_ARRAY_MAP) {
                startPos = dataPosition();
            }
            //读取key，key是一个字符串类型
            String key = readString();
            //读取value，是一个Object类型
            Object value = readValue(loader);
            if (DEBUG_ARRAY_MAP) {
                Log.d(TAG,
                    "  Read #" + (N - 1) + " " + (dataPosition() - startPos) +
                    " bytes: key=0x" +
                    Integer.toHexString(((key != null) ? key.hashCode() : 0)) +
                    " " + key);
            }
            //需要了解ArrayMap的append和put的区别
            outVal.append(key, value);
            N--;
        }
        outVal.validate();
    }
}
```

`readValue`的实现则会根据不同类型的`value`而有所不同。

```java
public final class Parcel {
    public final Object readValue(ClassLoader loader) {
        int type = readInt();
        switch (type) {
        case VAL_NULL:
            return null;
        case VAL_STRING:
            return readString();
        case VAL_INTEGER:
            return readInt();
        case VAL_MAP:
            return readHashMap(loader);
        case VAL_PARCELABLE:
            return readParcelable(loader);
        case VAL_SHORT:
            return (short) readInt();
        case VAL_LONG:
            return readLong();
        case VAL_FLOAT:
            return readFloat();
        case VAL_DOUBLE:
            return readDouble();
        case VAL_BOOLEAN:
            return readInt() == 1;
        case VAL_CHARSEQUENCE:
            return readCharSequence();
        case VAL_LIST:
            return readArrayList(loader);
        case VAL_BOOLEANARRAY:
            return createBooleanArray();
        case VAL_BYTEARRAY:
            return createByteArray();
        case VAL_STRINGARRAY:
            return readStringArray();
        case VAL_CHARSEQUENCEARRAY:
            return readCharSequenceArray();
        case VAL_IBINDER:
            return readStrongBinder();
        case VAL_OBJECTARRAY:
            return readArray(loader);
        case VAL_INTARRAY:
            return createIntArray();
        case VAL_LONGARRAY:
            return createLongArray();
        case VAL_BYTE:
            return readByte();
        case VAL_SERIALIZABLE:
            return readSerializable(loader);
        case VAL_PARCELABLEARRAY:
            return readParcelableArray(loader);
        case VAL_SPARSEARRAY:
            return readSparseArray(loader);
        case VAL_SPARSEBOOLEANARRAY:
            return readSparseBooleanArray();
        case VAL_BUNDLE:
            return readBundle(loader); // loading will be deferred
        case VAL_PERSISTABLEBUNDLE:
            return readPersistableBundle(loader);
        case VAL_SIZE:
            return readSize();
        case VAL_SIZEF:
            return readSizeF();
        case VAL_DOUBLEARRAY:
            return createDoubleArray();
        default:
            int off = dataPosition() - 4;
            throw new RuntimeException("Parcel " + this +
                ": Unmarshalling unknown type code " + type + " at offset " +
                off);
        }
    }
    public final String readString() {
        return mReadWriteHelper.readString(this);
    }
    public static class ReadWriteHelper {
        public static final ReadWriteHelper DEFAULT = new ReadWriteHelper();
        public void writeString(Parcel p, String s) {
            nativeWriteString(p.mNativePtr, s);
        }
        public String readString(Parcel p) {
            return nativeReadString(p.mNativePtr);
        }
    }
    static native String nativeReadString(long nativePtr);
}
```

`readString`调用到`ReadWriteHelper.readString`，最总调用到`Native`的`nativeReadString`方法。

```cpp
//路径：/frameworks/base/core/jni/android_os_Parcel.cpp
static jstring android_os_Parcel_readString(JNIEnv* env, jclass clazz, jlong nativePtr)
{
    Parcel* parcel = reinterpret_cast<Parcel*>(nativePtr);
    if (parcel != NULL) {
        size_t len;
        const char16_t* str = parcel->readString16Inplace(&len);
        if (str) {
            return env->NewString(reinterpret_cast<const jchar*>(str), len);
        }
        return NULL;
    }
    return NULL;
}
```

通过`readString16Inplace`方法获取对应的字符串。

```cpp
//路径：/frameworks/native/libs/binder/Parcel.cpp
const char16_t* Parcel::readString16Inplace(size_t* outLen) const
{
    int32_t size = readInt32();//获取字符串的长度
    // watch for potential int overflow from size+1
    if (size >= 0 && size < INT32_MAX) {
        *outLen = size;
        //注意，即使是size=0长度的String16,依旧会调用readInplace(1*sizeof(char16_t))，也就是4字节。
        const char16_t* str = (const char16_t*)readInplace((size+1)*sizeof(char16_t));
        if (str != NULL) {
            return str;
        }
    }
    *outLen = 0;
    return NULL;
}
const void* Parcel::readInplace(size_t len) const
{
    if (len > INT32_MAX) {
        // don't accept size_t values which may have come from an
        // inadvertent conversion from a negative int.
        return NULL;
    }
    if ((mDataPos+pad_size(len)) >= mDataPos && (mDataPos+pad_size(len)) <= mDataSize
            && len <= pad_size(len)) {
        if (mObjectsSize > 0) {
            status_t err = validateReadData(mDataPos + pad_size(len));
            if(err != NO_ERROR) {
                // Still increment the data position by the expected length
                mDataPos += pad_size(len);
                ALOGV("readInplace Setting data pos of %p to %zu", this, mDataPos);
                return NULL;
            }
        }
        const void* data = mData+mDataPos;
        mDataPos += pad_size(len);
        ALOGV("readInplace Setting data pos of %p to %zu", this, mDataPos);
        return data;
    }
    return NULL;
}
```

同时`Bundle`的`writeToParcel`也具有类似的逻辑。

## Bundle结构

序列化之后的对象一般不会单独的进行传输，而是将其塞入`Bundle`中，利用`Bundle`对象进行携带。`Bundle`内部有一个`ArrayMap`用`hash`表进行管理，所以它是以`Key-Value`键值对的形式携带序列化后的数据的。`Value`可以为各种数据类型，包括`int`、`Boolean`、`String`和`Parcelable`对象等等。下图是序列化后的数据在`Bundle`中的简单示意图：

1. 头部为数据总长度；
2. Bundle魔数；
3. 键值对的数量；
4. Key的长度；
5. Key的值；
6. Value的长度；
7. Value的值；

![Bundle结构.png](../imgs/Bundle结构.png)

文章到这里就全部讲述完啦，若有其他需要交流的可以留言哦~！

想要了解`Bundle`序列化漏洞以及`LaunchAnyWhere`补丁漏洞可以继续阅读 [Bundle 风水 - Android Parcel 序列化与反序列化不匹配系列漏洞](bundle-fengshui.md)