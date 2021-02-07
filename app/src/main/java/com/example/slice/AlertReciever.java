package com.example.slice;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlertReciever extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        System.out.println("Keeping the CPU on");
    }
}
