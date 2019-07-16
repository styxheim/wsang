package ru.styxheim.wsang;

import android.app.*;
import android.os.*;
import android.content.*;
import android.util.Log;
import android.util.JsonWriter;
import java.io.*;

import org.apache.http.HttpResponse;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.client.ClientProtocolException;


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
  private static final String LAPS_URL = "http://127.0.0.1:5000/api/laps/updatelaps";
  private HttpClient client;

  @Override
  public void onCreate() {
    // The service is being created
    Log.i("wsa-ng", "service created");
    EventBus.getDefault().register(this);
    starts = new StartList();
    client = HttpClientBuilder.create().build();
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

  private void _sync_row(StartRow row)
  {
    final byte[] body;

    StringWriter sw = new StringWriter();
    JsonWriter jw = new JsonWriter(sw);

    try {
      jw.beginArray();
      row.saveJSON(jw, false /* not a system json */);
      jw.endArray();

      body = sw.toString().getBytes("utf-8");
    } catch( Exception e ) {
      e.printStackTrace();
      row.errored = true;
      return;
    }

    /* TODO: send query in another Thread */
    HttpPost rq = new HttpPost(LAPS_URL);
    rq.setHeader("User-Agent", "wsa-ng/1.0");
    rq.setEntity(new ByteArrayEntity(body));

    HttpResponse rs;
    Log.i("wsa-ng", "sync url: " + LAPS_URL);
    try {
      rs = client.execute(rq);
    } catch( ClientProtocolException e ) {
      row.errored = true;
      Log.e("wsa-ng", "sync " + row.toString() + " client errored: " + e.getMessage());
      return;
    } catch( IOException e ) {
      row.errored = true;
      Log.e("wsa-ng", "sync " + row.toString() + " io errored: " + e.getMessage());
      return;
    }
    int rs_code = rs.getStatusLine().getStatusCode();

    Log.i("wsa-ng", "sync " + row.toString() + "code: " + Integer.toString(rs_code));
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onEventMessage(EventMessage msg) {
    if( !msg.newData )
      return;

    /* 1: read list */
    starts.Load(getApplicationContext());
    for( StartRow row : starts ) {
      if( row.synced )
        continue;
      /* 2: get non-synced rows */
      /* 3: sync */
      _sync_row(row);
    }
    /* 4: update screen */
    starts.Save(getApplicationContext());
    EventBus.getDefault().post(new EventMessage(true, false));
  }
}
