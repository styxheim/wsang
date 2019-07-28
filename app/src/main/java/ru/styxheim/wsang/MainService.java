package ru.styxheim.wsang;

import android.app.*;
import android.os.*;
import android.content.*;
import android.util.Log;
import android.util.JsonWriter;
import java.io.*;
import android.widget.Toast;

import org.apache.http.HttpResponse;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.client.ClientProtocolException;

import android.media.MediaPlayer;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import android.icu.util.*;
import android.widget.*;

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
  private CountDownTimer timer;
  MediaPlayer mPlayer;

  public String _(String in) {
    return "[" + android.os.Process.myTid() + "] " + in;
  }

  @Override
  public void onCreate() {
    // The service is being created
    Log.i("wsa-ng", _("service created"));
    EventBus.getDefault().register(this);
    starts = new StartList();
    client = HttpClientBuilder.create().build();
    mPlayer = new MediaPlayer();
    try {
      mPlayer.setDataSource("TODO");
      mPlayer.prepare();
    } catch( IOException e ) {
      Log.e("wsa-ng", "Mplayer exception: " + e.getMessage());
    }
    /* Load data */
    starts.Load(getApplicationContext());
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    // The service is starting, due to a call to startService()
    Log.i("wsa-ng", _("service got start command: flags= " +
                      Integer.toString(flags) + " startId=" +
                      Integer.toString(startId)));

    _boot();
    return super.onStartCommand(intent, flags, startId);
  }

  @Override
  public void onDestroy() {
    EventBus.getDefault().unregister(this);

    starts.Save(getApplicationContext());

    Log.i("wsa-ng", _("service destroyed"));
    super.onDestroy();
  }

  private void _boot()
  {
    Log.i("wsa-ng", _("Begin boot"));

    for( StartRow row : starts ) {
      Log.d("wsa-ng", _("Post row: " + row.toString()));
      EventBus.getDefault().post(new EventMessage(EventMessage.EventType.UPDATE, row));
    }

    Log.i("wsa-ng", _("Boot End"));
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
      row.state = StartRow.SyncState.ERROR;
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
      row.state = StartRow.SyncState.ERROR;
      Log.e("wsa-ng", "sync " + row.toString() + " client errored: " + e.getMessage());
      return;
    } catch( IOException e ) {
      row.state = StartRow.SyncState.ERROR;
      Log.e("wsa-ng", "sync " + row.toString() + " io errored: " + e.getMessage());
      return;
    }
    int rs_code = rs.getStatusLine().getStatusCode();

    Log.i("wsa-ng", "sync " + row.toString() + "code: " + Integer.toString(rs_code));
  }

  private void _timer_free(boolean forced)
  {
    if( timer == null )
      return;

    timer.cancel();
    timer = null;

    if( forced ) {
      /* send message about timer force stopped */
      EventMessage.CountDownMsg smsg = new EventMessage.CountDownMsg(-1, -1, -1);
      EventBus.getDefault().post(new EventMessage(EventMessage.EventType.COUNTDOWN_END, smsg));
    }
  }
  
  private void _event_countdown_start(EventMessage.CountDownMsg msg)
  {
    final Calendar cal = Calendar.getInstance();
    final int lapId = msg.lapId;
    
    if( timer != null ) {
      Toast toast = Toast.makeText(getApplicationContext(),
                                   R.string.timer_started,
                                   Toast.LENGTH_SHORT);
      toast.show();
      return;
    }
    
    timer = new CountDownTimer(msg.leftMs, 1000) {
      public void onTick(long left) {
        EventMessage.CountDownMsg smsg = new EventMessage.CountDownMsg(lapId, left, 0);
        
        Log.d("wsa-ng", _("Tick: " + Long.toString(left) + " " +
                          "msec: " + Long.toString(cal.getTimeInMillis())));
        
        EventBus.getDefault().post(new EventMessage(EventMessage.EventType.COUNTDOWN, smsg));
      }
      public void onFinish() {
        long endAt = cal.getTimeInMillis();
        EventMessage.CountDownMsg smsg = new EventMessage.CountDownMsg(lapId, 0, endAt);
        
        Log.d("wsa-ng", _("Tick: finish " +
                          "msec: " + Long.toString(endAt)));
        /* send notice about complete time */
        EventBus.getDefault().post(new EventMessage(EventMessage.EventType.COUNTDOWN_END, smsg));
        _timer_free(false);
      }
    };
    timer.start();
  }
  
  private void _event_countdown_end(EventMessage.CountDownMsg msg)
  {
    Calendar cal = Calendar.getInstance();
    String time;

    if( msg.endAtMs == -1 )
      return;

    cal.setTimeInMillis(msg.endAtMs);
    time = String.format("%02d:%02d:%02d",
                         cal.get(Calendar.HOUR),
                         cal.get(Calendar.MINUTE),
                         cal.get(Calendar.SECOND));
    
    for( StartRow row : starts ) {
      if( row.lapId != msg.lapId )
        continue;
      Log.d("wsa-ng", _("Set time to " + time + " (" + msg.endAtMs + ") for row #" + row.getRowId()));
      row.startAt = time;
      row.state = StartRow.SyncState.NONE;
      EventBus.getDefault().post(new EventMessage(EventMessage.EventType.UPDATE, row));
    }
    starts.Save(getApplicationContext());
  }
  
  private void _event_propose(EventMessage.ProposeMsg msg)
  {
    StartRow row;
    if( msg.rowId == -1 ) {
      /* new record */
      row = starts.addRecord(msg.crewId, msg.lapId, "00:00:00");
    }
    else {
      row = starts.getRecord(msg.rowId);
      if( row == null ) {
        Log.e("wsa-ng", "StartList does not have rowId #" +
                        Integer.toString(msg.rowId));
        return;
      }
      row.crewId = msg.crewId;
      row.lapId = msg.lapId;
      row.state = StartRow.SyncState.NONE;
    }
    EventBus.getDefault().post(new EventMessage(EventMessage.EventType.UPDATE, row));
    starts.Save(getApplicationContext());
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onEventMessage(EventMessage ev)
  {
    switch( ev.type ) {
    case COUNTDOWN_START:
      Log.i("wsa-ng", _("Event " + ev.type.name() + " received"));
      _event_countdown_start((EventMessage.CountDownMsg)ev.obj);
      break;
    case COUNTDOWN_END:
      Log.i("wsa-ng", _("Event " + ev.type.name() + " received"));
      _event_countdown_end((EventMessage.CountDownMsg)ev.obj);
      break;
    case COUNTDOWN_STOP:
      Log.i("wsa-ng", _("Event " + ev.type.name() + " received"));
      _timer_free(true);
      break;
    case PROPOSE:
      Log.i("wsa-ng", _("Event " + ev.type.name() + " received"));
      _event_propose((EventMessage.ProposeMsg)ev.obj);
      break;
    default:
      Log.i("wsa-ng", _("Event " + ev.type.name() + " received"));
      break;
    }
  }
}
