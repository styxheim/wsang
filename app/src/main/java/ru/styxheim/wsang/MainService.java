package ru.styxheim.wsang;

import android.app.*;
import android.os.*;
import android.content.*;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

/* TODO:
 *  Receive message from MainActivity
 *  Send START data to server
 *  Update sended rows
 */

public class MainService extends Service
{
  private StartList starts;

  @Override
  public void onCreate() {
    // The service is being created
    Log.i("wsa-ng", "service created");
    EventBus.getDefault().register(this);
    starts = new StartList();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    // The service is starting, due to a call to startService()
    Log.i("wsa-ng", "service got start command: flags=" +
                          Integer.toString(flags) + " startId=" +
                          Integer.toString(startId));
    return super.onStartCommand(intent, flags, startId);
  }

  @Override
  public void onDestroy() {
    EventBus.getDefault().unregister(this);
    // The service is no longer used and is being destroyed
    Log.i("wsa-ng", "service destroyed");
    super.onDestroy();
  }


  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onEventMessage(EventMessage msg) {
    /* TODO:
     * 1. Read list
     * 2. Get not synced rows
     * 3. Sync
     * 4. Send `Refresh Screen` message
     */
    if( !msg.newData )
      return;

    starts.Load(getApplicationContext());
    for( StartRow row : starts ) {
      if( row.synced )
        continue;
      Log.i("wsa-ng", "sync: " + row.toString());
      row.synced = true;
    }
    starts.Save(getApplicationContext());
    EventBus.getDefault().post(new EventMessage(true, false));
  }
}
