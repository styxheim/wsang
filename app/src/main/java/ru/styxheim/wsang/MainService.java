package ru.styxheim.wsang;

import android.app.*;
import android.os.*;
import android.content.*;
import android.util.JsonReader;
import android.util.Log;
import android.util.JsonWriter;
import java.io.*;
import android.widget.Toast;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.Call;
import okhttp3.Callback;

import java.net.URISyntaxException;
import java.util.ArrayList;

import android.media.MediaPlayer;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import android.widget.*;

/* TODO:
 *  Receive message from MainActivity
 *  Send START data to server
 *  Update sended rows
 */

public class MainService extends Service
{
  private StartList starts;
  private TerminalStatus terminalStatus;
  private RaceStatus raceStatus;

  protected int syncTimeout = 3000;

  private static final int TIME_TIMES = 10;
  private static final String SET_URL = "http://%s/api/update/%d/%s";
  private static final String GET_URL = "http://%s/api/data/%d/%d/%s";
  private static final String TIME_URL = "http://%s/api/timesync/%d";
  private OkHttpClient http_client = new OkHttpClient();

  private long timestamp = 0;

  private enum CountDownMode {
    NONE,
    COUNTDOWN,
    CANCEL
  };

  private class ScreenIntent extends BroadcastReceiver
  {
    protected boolean is_on;
    protected MainService ctx;

    public ScreenIntent(boolean is_on, MainService ctx)
    {
      this.is_on = is_on;
      this.ctx = ctx;
    }

    @Override
    public void onReceive(Context p1, Intent p2)
    {
      // TODO: Implement this method
      ctx.onScreen(this.is_on);
    }
  }

  private EventMessage.CountDownMsg cdmsg = null;
  private MediaPlayer mPlayer = null;
  private CountDownMode inCountDownMode = CountDownMode.NONE;
  private long startCountDownAt = 0;
  /* simple event queue */
  private EventMessage.CountDownMsg nextCountDownMsg = null;

  private SharedPreferences settings;
  private SharedPreferences race_settings;
  private SharedPreferences chrono_settings;

  private boolean isTimeSyncNow = false;

  private Handler _sync_handler = new Handler();

  public String _(String format, Object ... args) {
    return "[" + android.os.Process.myTid() + "] " + String.format(format, args);
  }

  public String e2trace(Exception e)
  {
    StringWriter psw = new StringWriter();
    PrintWriter pw = new PrintWriter(psw);

    e.printStackTrace(pw);
    return psw.toString();
  }

  private void _updateTimeStamp(long newTimeStamp)
  {
    this.timestamp = newTimeStamp;
    Log.d("wsa-ng", _("Update timestamp to: %d", newTimeStamp));
    SharedPreferences.Editor ed = settings.edit();
    ed.putLong(RaceStatus.TIMESTAMP, this.timestamp);
    ed.apply();
  }

  private void _sync_all_rows()
  {
    final String url;
    final ArrayList<StartRow> rows = new ArrayList<StartRow>();
    StringWriter sw = new StringWriter();
    JsonWriter jw = new JsonWriter(sw);

    if( !settings.contains("server_addr") ) {
      Log.d("wsa-ng", _("sync: `server_addr` is not defined: rows not synced"));
      /* FIXME: why need restart sync when server address not defined */
      _sync_sched();
      return;
    }

    try {
    jw.beginArray();
      for( StartRow row : starts ) {
        // sync all PENDING and SYNCING rows
        // this block run after receive data from server and all previous
        // sended rows should be already received and marked as `SYNCED`
        if( row.state == StartRow.SyncState.PENDING ||
            row.state == StartRow.SyncState.SYNCING ) {
          int inprint = 0;
          try {
            inprint = row.prepareJSON(jw);
          } catch( Exception e ) {
            Log.e("wsa-ng", _("sync rowId #%d error: %s ->\n%s",
                              row.getRowId(), e.getMessage(), e2trace(e)));
            row.setState(StartRow.SyncState.ERROR);
            continue;
          }
          Log.d("wsa-ng", _("sync: prepare row %s -> inprint=%d",
                            row.toString(), inprint));
          rows.add(row);
          row.setState(StartRow.SyncState.SYNCING);
        }
      }
      jw.endArray();
    } catch( IOException e ) {
      Log.e("wsa-ng", _("sync array error: %s ->\n%s",
                        e.getMessage(), e2trace(e)));
      _sync_sched();
      return;
    }

    if( rows.size() == 0 ) {
      _sync_sched();
      return;
    }

    /* event about change state of rows */
    EventBus.getDefault().post(rows);

    /* tell to server */
    url = String.format(SET_URL,
                        settings.getString("server_addr", Default.server_addr),
                        raceStatus.competitionId,
                        terminalStatus.terminalId);

    Log.d("wsa-ng", _("sync: push %d rows to %s", rows.size(), url));
    final MediaType MIME_JSON = MediaType.get("application/json; charset=utf-8");
    RequestBody body = RequestBody.create(MIME_JSON, sw.toString());
    Request request = new Request.Builder().url(url).post(body).build();
    Call call = http_client.newCall(request);

    call.enqueue(new Callback() {
      public void onResponse(Call call, Response response) throws IOException
      {
        int code = response.code();
        String body = response.body().string();

        if( code == 200 && body.compareTo("true") == 0 ) {
          /* sync ok */
          Log.d("wsa-ng", _("sync: %d rows pushed to server", rows.size()));
          EventBus.getDefault().post(new EventMessage.SyncSuccess(rows));
          return;
        }

        Log.e("wsa-ng", _("sync: %d rows not pushed: code == %d (%s)",
                          rows.size(), code, body));
        EventBus.getDefault().post(new EventMessage.SyncFailure(rows));
      }

      public void onFailure(Call call, IOException e)
      {
        Log.e("wsa-ng", _("sync: failed: %s", e.getMessage()));
        EventBus.getDefault().post(new EventMessage.SyncFailure(rows));
      }
    });
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void _event_sync_success(EventMessage.SyncSuccess ss)
  {
    _sync_sched();

    /* set SENDED status? */
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void _event_sync_failure(EventMessage.SyncFailure sf)
  {
    _sync_sched();

    if( sf.rows == null )
      return;

    for( StartRow row : sf.rows ) {
      row.setState(StartRow.SyncState.PENDING);
    }

    EventBus.getDefault().post(sf.rows);
  }

  public void onScreen(boolean is_on)
  {
    if( !is_on ) {
      this.syncTimeout = 60000;
    }
    else {
      this.syncTimeout = 3000;
      _sync_handler.removeCallbacks(_sync_runnable);
      // TODO: fix raise conditional
      Log.d("wsa-ng", _("sync: Urgent sync call"));
      _sync_receive();
    }
    Log.d("wsa-ng", _("sync: Set syncTimeout to '%d'", syncTimeout));
  }

  protected Runnable _sync_runnable = new Runnable()
  {
    public void run() {
      _sync_receive();
    }
  };

  protected void _sync_sched()
  {
    Log.d("wsa-ng", _("sync: delay next event at %d seconds", syncTimeout));
    _sync_handler.removeCallbacks(_sync_runnable);
    _sync_handler.postDelayed(_sync_runnable, syncTimeout);

  }

  @Override
  public void onCreate() {
    chrono_settings = getSharedPreferences("chrono", Context.MODE_PRIVATE);
    race_settings = getSharedPreferences("race", Context.MODE_PRIVATE);
    settings = getSharedPreferences("main", Context.MODE_PRIVATE);
    // The service is being created
    Log.i("wsa-ng", _("service created"));
    EventBus.getDefault().register(this);
    starts = new StartList();
    /* Load data */
    starts.Load(getApplicationContext());

    this.terminalStatus = new TerminalStatus(race_settings);
    this.raceStatus = new RaceStatus(race_settings);

    if( this.terminalStatus.terminalId.compareTo("") == 0 ) {
      this.terminalStatus.terminalId = Long.toHexString(java.util.concurrent.ThreadLocalRandom.current().nextLong());
      this.terminalStatus.saveSettings(race_settings);
    }

    this.timestamp = settings.getLong(raceStatus.TIMESTAMP, 0);

    Log.i("wsa-ng", _("TerminalId=" + this.terminalStatus.terminalId));

    IntentFilter filter;
    filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
    registerReceiver(new ScreenIntent(true, this), filter);
    filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
    registerReceiver(new ScreenIntent(false, this), filter);

    // sync: first step: receive race data
    _sync_receive();
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

    /*
    if( startId == 1 )
      _boot();
      */

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
    ArrayList<StartRow> row_list = new ArrayList<StartRow>();

    EventBus.getDefault().post(raceStatus);
    EventBus.getDefault().post(terminalStatus);

    for( StartRow row : starts ) {
      row_list.add(row.clone());
    }

    EventBus.getDefault().post(row_list);

    if( cdmsg != null )
      EventBus.getDefault().post(cdmsg);

    Log.i("wsa-ng", _("Boot End"));
  }

  /* receive and sync data from server */
  private void _sync_receive()
  {
    final String url;
    Request request;
    Call call;

    url = String.format(GET_URL,
                        settings.getString("server_addr", Default.server_addr),
                        this.raceStatus.competitionId,
                        this.timestamp,
                        this.terminalStatus.terminalId);

    Log.d("wsa-ng", _("rsync: query url %s", url));
    request = new Request.Builder().url(url).build();
    call = http_client.newCall(request);
    call.enqueue(new Callback() {
      public void onResponse(Call call, Response response) throws IOException
      {
        ServerStatus serverStatus = new ServerStatus();
        Log.d("wsa-ng", _("rsync: result code == %d", response.code()));

        if( response.code() == 200 ) {
          try ( StringReader sr = new StringReader(response.body().string());
                JsonReader jr = new JsonReader(sr) ) {
            serverStatus.loadJSON(jr);
          }
        }
        EventBus.getDefault().post(new EventMessage.RSyncResult(serverStatus));
      }

      public void onFailure(Call call, IOException e)
      {
        Log.e("wsa-ng", _("rsync: failed: %s", e.getMessage()));
        EventBus.getDefault().post(new EventMessage.RSyncResult(null));
      }
    });
  }

  private void _sync_time()
  {
    String url = String.format(TIME_URL,
                               settings.getString("server_addr", Default.server_addr),
                               System.currentTimeMillis());
    Request request = new Request.Builder().url(url).build();
    http_client.newCall(request).enqueue(new Callback() {
      public void onResponse(Call call, Response response) throws IOException
      {
        String[] data;
        EventMessage.TimeSync msg;
        String body = response.body().string();
        
        Log.d("wsa-ng", _("tsync: response code == %d, data = %s",
                          response.code(), body));
        
        data = body.split(":", 3);
        
        Long T1 = Long.parseLong(data[0]);
        Long T2 = Long.parseLong(data[1]);
        Long T3 = Long.parseLong(data[2]);
        Long T4 = new Long(System.currentTimeMillis());

        msg = new EventMessage.TimeSync(T1, T2, T3, T4);
        EventBus.getDefault().post(msg);
      }
      public void onFailure(Call call, IOException e)
      {
        Log.e("wsa-ng", _("tsync: failed: %s", e.getMessage()));
      }
    });
    
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onTimeSync(EventMessage.TimeSync msg)
  {
    if( !msg.isEmpty ) {
      SharedPreferences.Editor ed = chrono_settings.edit();
      long dtime = (((msg.T2 - msg.T1) + (msg.T3 - msg.T4)) / 2);
      long syncdtime = (msg.T4 + dtime) - raceStatus.syncPoint;
      long ctime = msg.T4 - syncdtime;

      Log.d("wsa-ng", _("tsync SyncPoint: %d", raceStatus.syncPoint));
      Log.d("wsa-ng", _("tsync delta: %d", dtime));
      Log.d("wsa-ng", _("tsync local time: %d", msg.T4));
      Log.d("wsa-ng", _("tsync server time: %d", (msg.T4 + dtime)));
      Log.d("wsa-ng", _("tsync zero delta: %d", syncdtime));

      ed.putLong("offset", ctime);
      ed.apply();
    }
    isTimeSyncNow = false;
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

    EventBus.getDefault().post(new EventMessage.CountDownCancelled());
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

    /* setup mPlayer */
    mPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
      @Override
      public void onPrepared(MediaPlayer mp) {
        long millis = System.currentTimeMillis();
        mp.start();
        Log.d("wsa-ng", _("MediaPlayer: prepared at " + Long.toString(millis)));
        startCountDownAt = millis;
        cdmsg = new EventMessage.CountDownMsg(lapId, millis, millis + signal_offset);
        EventBus.getDefault().post(cdmsg);
      }
    });

    mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
      @Override
      public void onCompletion(MediaPlayer mp) {
        long millis = System.currentTimeMillis();
        Log.d("wsa-ng", _("MediaPlayer: end at " + Long.toString(millis)));

        long endAt = startCountDownAt - chrono_settings.getLong("offset", Default.chrono_offset) + signal_offset;

        EventMessage.CountDownMsg smsg = new EventMessage.CountDownMsg(lapId, 0, endAt);
        EventBus.getDefault().post(new EventMessage(EventMessage.EventType.COUNTDOWN_END, smsg));
        
        _countdown_cleanup();
      }
    });
  }

  private void _event_countdown_end(EventMessage.CountDownMsg msg)
  {
    String time;

    time = Default.millisecondsToString(msg.endAtMs);

    for( StartRow row : starts ) {
      if( row.lapId != msg.lapId )
        continue;
      Log.d("wsa-ng", _("Set time to " + time + " (" + msg.endAtMs + ") for row #" + row.getRowId()));
      row.setStartData(msg.endAtMs);
      EventBus.getDefault().post(row);
    }
    starts.Save(getApplicationContext());
  }

  private void _event_propose(EventMessage.ProposeMsg msg)
  {
    StartRow row;

    if( msg.rowId == -1 ) {
      /* new record */
      Log.d("wsa-ng", _("Add new StartRow record"));

      row = starts.addRecord(msg.crewId, msg.lapId);
    }
    else {
      row = starts.getRecord(msg.rowId);
      if( row == null ) {
        Log.e("wsa-ng", _("StartList does not have rowId #" +
                          Integer.toString(msg.rowId)));
        return;
      }

      Log.d("wsa-ng", _("Got message " + msg.type.name() + " for rowId #" +
                        Integer.toString(msg.rowId)));

      switch( msg.type ){
      case CONFIRM:
        /* instant update */
        /* FIXME: not possible until messages send without LapId in server mode */
        /*_sync_row(row);*/
        row.setState(StartRow.SyncState.PENDING);
        break;
      case FINISH:
        row.setFinishData(msg.time);
        break;
      case IDENTIFY:
        row.setIdentify(msg.crewId, msg.lapId);
        break;
      case START:
        row.setStartData(msg.time);
        break;
      case PENALTY:
        row.setGateData(msg.gate, msg.penalty);
        break;
      default:
        Log.e("wsa-ng", _("Unknown msg type for rowId #" +
                          Integer.toString(msg.rowId) + ": " + msg.type.name()));
        return;
      }
    }

    EventBus.getDefault().post(row);
    starts.Save(getApplicationContext());
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void _event_receive(EventMessage.RSyncResult rstatus)
  {
    /* process received data from server
     * when server return error or not reached -> status is empty
     */
    ServerStatus status = rstatus.serverStatus;

    if( status == null ) {
      Log.d("wsa-ng", _("[RECEIVE] Empty ServerStatus, sched next sync"));
      _sync_sched();
      return;
    }

    Log.d("wsa-ng", _("[RECEIVE] process ServerStatus -> [ " +
                      (status.raceStatus == null ? "" : "RaceStatus ") +
                      (status.terminalStatus.size() == 0 ? "" : " T" + Integer.toString(status.terminalStatus.size())) +
                      (status.lap.size() == 0 ? "" : " L" + Integer.toString(status.lap.size())) +
                      "]"));

    try {
      boolean changed = false;

      if( status.raceStatus != null ) {
        if( status.raceStatus.competitionId != raceStatus.competitionId ) {
          Log.i("wsa-ng", _("[RECEIVE] CompetitionId: local = %d, remote = %d",
                            raceStatus.competitionId,
                            status.raceStatus.competitionId));
          /* clear all data */
          _updateTimeStamp(0);
          starts = new StartList();
          starts.Save(getApplicationContext());

          SharedPreferences chrono_data = getSharedPreferences("chrono_data", Context.MODE_PRIVATE);
          SharedPreferences.Editor ed;

          /* clear stopwatch memory */
          ed = chrono_data.edit();
          ed.clear();
          ed.commit();

          /* clear stopwatch offset */
          ed = chrono_settings.edit();
          ed.putLong("offset", 0L);
          ed.commit();

          /* sync time */
          _sync_time();
        }

        if ( status.raceStatus.timestamp > timestamp ) {
          Log.i("wsa-ng", _("[RECEIVE] RaceStatus timestamp: local = %d, remote = %d",
                            timestamp,
                            status.raceStatus.timestamp));
          /* update settings */
          _updateTimeStamp(status.raceStatus.timestamp);
        }

        raceStatus = status.raceStatus;
        raceStatus.saveSettings(race_settings);
        EventBus.getDefault().post(raceStatus);
      }

      /* check terminalStatus data */
      for( int i = 0; i < status.terminalStatus.size(); i++ ) {
        TerminalStatus term = status.terminalStatus.get(i);

        if( term.terminalId.compareTo(this.terminalStatus.terminalId) == 0 ) {
          Log.i("wsa-ng", _("[RECEIVE] apply new TerminalStatus"));
          this.terminalStatus = term;
          this.terminalStatus.saveSettings(race_settings);
          /* update Screen */
          EventBus.getDefault().post(term);
        }

        if( term.timestamp > timestamp ) {
          /* apply timestamp from any received struct */
          Log.i("wsa-ng", _("[RECEIVE] TerminalStatus timestamp: local = %d, remote = %d",
                            timestamp,
                            term.timestamp));
          _updateTimeStamp(term.timestamp);
        }
      }

      /* check laps data */
      for( int i = 0; i < status.lap.size(); i++ ) {
        StartRow.SyncData rrow = status.lap.get(i);
        StartRow lrow;

        if( rrow.rowId == null ) {
          Log.e("wsa-ng", _("[RECEIVE]: Sync row without rowId, skip"));
          continue;
        }

        if( rrow.timestamp == null ) {
          Log.e("wsa-ng", _("[RECEIVE]: not timestamp in lap data, skip"));
          continue;
        }

        lrow = starts.getRecord(rrow.rowId);

        if( lrow == null ) {
          lrow = starts.addRecord(rrow);
          lrow.setState(StartRow.SyncState.SYNCED);
          EventBus.getDefault().post(lrow.clone());
          changed = true;
        }
        else {
          StartRow.SyncData previous = new StartRow.SyncData();
          StartRow.SyncData diff = new StartRow.SyncData();

          lrow.updateNotPendingFields(rrow, previous, diff);

          if( lrow.state == StartRow.SyncState.SYNCED ) {
            if( !previous.isEmpty() )
              changed = true;
            if( diff.isEmpty() )
              Log.e("wsa-ng", _("rsync: diff is not empty"));
          }
          else {
            if( diff.isEmpty() ) {
              lrow.setState(StartRow.SyncState.SYNCED);
            }
            else if( lrow.state == StartRow.SyncState.SYNCING ) {
              /* Set PENDING only to already sended rows */
              lrow.setState(StartRow.SyncState.PENDING);
            }
            changed = true;
          }

          /* event UI */
          EventBus.getDefault().post(lrow.clone());
        }

        if( rrow.timestamp > timestamp ) {
          _updateTimeStamp(rrow.timestamp);
          Log.i("wsa-ng", _("[RECEIVE] StartRow timestamp: local = %d, remote = %d",
                            timestamp,
                            rrow.timestamp));
        }
      }

      if( changed )
        starts.Save(getApplicationContext());
    } catch( Exception e ) {
      Log.e("wsa-ng", _("[RECEIVE] Got error: %s ->\n%s",
                        e.getMessage(), e2trace(e)));
    }

    // sync: next step: send local rows to server
    _sync_all_rows();
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
  
  @Subscribe(threadMode = ThreadMode.MAIN)
  public void _event_boot(EventMessage.Boot msg)
  {
    _boot();
  }
  
}
