package com.mondcrest.aurora;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Opens Aurora when the TV starts up, so it's the first thing you see. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        Intent i = new Intent(ctx, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { ctx.startActivity(i); } catch (Exception ignored) { }
    }
}
