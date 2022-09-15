package my.slice.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

// This class is here just to keep the CPU on when slices/threads are running and the phone is off
public class AlertReciever extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        System.out.println("Keeping the CPU on");
    }
}
