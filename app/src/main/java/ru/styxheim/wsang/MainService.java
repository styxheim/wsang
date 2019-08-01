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
import android.widget.*;
import java.util.Calendar;
import java.util.TimeZone;

/* TODO:
 *  Receive message from MainActivity
 *  Send START data to server
 *  Update sended rows
 */

public class MainService extends Service
{
  private StartList starts;
  private static final String LAPS_URL = "http://%s/api/laps/updatelaps";
  private HttpClient client;

  private enum CountDownMode {
    NONE,
    COUNTDOWN,
    CANCEL
  };

  private CountDownTimer mTimer = null;
  private MediaPlayer mPlayer = null;
  private CountDownMode inCountDownMode = CountDownMode.NONE;
  private long startCountDownAt = 0;
  /* simple event queue */
  private EventMessage.CountDownMsg nextCountDownMsg = null;

  private SharedPreferences settings;

  public String _(String in) {
    return "[" + android.os.Process.myTid() + "] " + in;
  }

  @Override
  public void onCreate() {
    settings = getSharedPreferences("main", Context.MODE_PRIVATE);
    // The service is being created
    Log.i("wsa-ng", _("service created"));
    EventBus.getDefault().register(this);
    starts = new StartList();
    client = HttpClientBuilder.create().build();
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

    if( inCountDownMode != CountDownMode.COUNTDOWN )
      EventBus.getDefault().post(new EventMessage(EventMessage.EventType.COUNTDOWN_END, null));

    Log.i("wsa-ng", _("Boot End"));
  }

  private void _sync_row(StartRow row)
  {
    final byte[] body;
    String url;

    if( !settings.contains("server_addr") ) {
      Log.d("wsa-ng", _("`server_addr` is not defined: row not synced"));
      return;
    }

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


    url = String.format(LAPS_URL, settings.getString("server_addr", Default.server_addr));
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

  private void _countdown_cleanup()
  {
    if( inCountDownMode == CountDownMode.NONE )
      return;

    inCountDownMode = CountDownMode.NONE;

    if( mPlayer != null ) {
      mPlayer.stop();
      mPlayer.release();
      mPlayer = null;
    }

    if( mTimer != null ) {
      mTimer.cancel();
      mTimer = null;
    }

    EventBus.getDefault().post(new EventMessage(EventMessage.EventType.COUNTDOWN_END, null));
  }

  private void _event_countdown_stop(Object none)
  {
    if( inCountDownMode != CountDownMode.COUNTDOWN ) {
      return;
    }

    _countdown_cleanup();

    inCountDownMode = CountDownMode.CANCEL;

    Log.d("wsa-ng", _("MediaPlayer: play stop sound"));
    mPlayer = MediaPlayer.create(MainService.this, R.raw.seconds_stop);
    mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
      @Override
      public void onCompletion(MediaPlayer mp) {
        _countdown_cleanup();
        if( nextCountDownMsg != null ) {
          _event_countdown_start(nextCountDownMsg);
          nextCountDownMsg = null;
        }
        Log.d("wsa-ng", _("MediaPlayer: stop sound ended"));
        mp.release();
      }
    });

    mPlayer.start();
  }

  private void _event_countdown_start(EventMessage.CountDownMsg msg)
  {
    final int lapId = msg.lapId;
    final long timeout = msg.leftMs;
    final int sound_id;
    final int signal_offset; /* time in ms from start of sound for signal */

    /* exit on countdown mode */
    if( inCountDownMode == CountDownMode.COUNTDOWN ) {
      Toast toast = Toast.makeText(getApplicationContext(),
                                   R.string.timer_started,
                                   Toast.LENGTH_SHORT);
      toast.show();
      return;
    }
    else if( inCountDownMode == CountDownMode.CANCEL ) {
      /* setup count down queue */
      nextCountDownMsg = msg;
      return;
    }

    inCountDownMode = CountDownMode.COUNTDOWN;

    switch( (int)timeout ) {
    case 60000:
      sound_id = R.raw.seconds_60;
      signal_offset = 60000;
      break;
    case 30000:
      sound_id = R.raw.seconds_30;
      signal_offset = 32000;
      break;
    case 10000:
      sound_id = R.raw.seconds_10;
      signal_offset = 12000;
      break;
    default:
      Log.d("wsa-ng", "Unknown timeout mode: " + Long.toString(timeout));
      return;
    }

    /* create mplayer and timer */
    mPlayer = MediaPlayer.create(MainService.this, sound_id);
    mTimer = new CountDownTimer(signal_offset, 1000) {
      public void onTick(long left) {
        EventMessage.CountDownMsg smsg = new EventMessage.CountDownMsg(lapId, left, 0);
        EventBus.getDefault().post(new EventMessage(EventMessage.EventType.COUNTDOWN, smsg));
      }
      public void onFinish() {
        EventMessage.CountDownMsg smsg = new EventMessage.CountDownMsg(lapId, 0, 0);
        EventBus.getDefault().post(new EventMessage(EventMessage.EventType.COUNTDOWN, smsg));
      }
    };

    /* setup mPlayer */
    mPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
      @Override
      public void onPrepared(MediaPlayer mp) {
        long millis = System.currentTimeMillis();
        mp.start();
        mTimer.start();
        Log.d("wsa-ng", _("MediaPlayer: prepared at " + Long.toString(millis)));
        startCountDownAt = millis;
      }
    });

    mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
      @Override
      public void onCompletion(MediaPlayer mp) {
        long millis = System.currentTimeMillis();
        Log.d("wsa-ng", _("MediaPlayer: end at " + Long.toString(millis)));
        mTimer.cancel();

        long endAt = startCountDownAt - settings.getLong("chrono_offset", Default.chrono_offset) + signal_offset;
        EventMessage.CountDownMsg smsg = new EventMessage.CountDownMsg(lapId, 0, endAt);
        EventBus.getDefault().post(new EventMessage(EventMessage.EventType.COUNTDOWN_END, smsg));

        _countdown_cleanup();
      }
    });
  }

  private void _event_countdown_end(EventMessage.CountDownMsg msg)
  {
    Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    String time;

    cal.setTimeInMillis(msg.endAtMs);
    time = String.format("%02d:%02d:%02d.%02d",
                         cal.get(Calendar.HOUR),
                         cal.get(Calendar.MINUTE),
                         cal.get(Calendar.SECOND),
                         (int)(cal.get(Calendar.MILLISECOND) / 10));
    
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

      if( ev.obj == null )
        return;

      _event_countdown_end((EventMessage.CountDownMsg)ev.obj);
      break;
    case COUNTDOWN_STOP:
      Log.i("wsa-ng", _("Event " + ev.type.name() + " received"));
      _event_countdown_stop(null);
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
