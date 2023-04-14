package com.tzx.launchanywhere;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

/**
 * Created by Tanzhenxing
 * Date: 2023/3/31 20:35
 * Description:
 */
public class MyClass implements Parcelable {
    int a;
    int b;

    protected MyClass(Parcel in) {
        Log.d("tanzhenxing33", "MyClass:Parcel:" + in.dataPosition());
        a = in.readInt();
        b = 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Log.d("tanzhenxing33", "MyClass:writeToParcel");
        dest.writeInt(a);
        dest.writeInt(b);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<MyClass> CREATOR = new Creator<MyClass>() {
        @Override
        public MyClass createFromParcel(Parcel in) {
            return new MyClass(in);
        }

        @Override
        public MyClass[] newArray(int size) {
            return new MyClass[size];
        }
    };
}
