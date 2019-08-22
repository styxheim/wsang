package ru.styxheim.wsang;

import android.app.*;
import android.os.*;
import android.content.*;
import android.util.JsonReader;
import android.util.Log;
import android.util.JsonWriter;
import java.io.*;
import android.widget.Toast;

import org.apache.http.HttpResponse;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.util.EntityUtils;
import java.net.URISyntaxException;

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

  private static final String SET_URL = "http://%s/api/update/%d/%s";
  private static final String GET_URL = "http://%s/api/data/%d/%d/%s";

  private long timestamp = 0;

  private enum CountDownMode {
    NONE,
    COUNTDOWN,
    CANCEL
  };

  private EventMessage.CountDownMsg cdmsg = null;
  private MediaPlayer mPlayer = null;
  private CountDownMode inCountDownMode = CountDownMode.NONE;
  private long startCountDownAt = 0;
  /* simple event queue */
  private EventMessage.CountDownMsg nextCountDownMsg = null;

  private SharedPreferences settings;
  private SharedPreferences race_settings;
  private SharedPreferences chrono_settings;

  public String _(String format, Object ... args) {
    return "[" + android.os.Process.myTid() + "] " + String.format(format, args);
  }

  private void _updateTimeStamp(long newTimeStamp)
  {
    timestamp = newTimeStamp;

    SharedPreferences.Editor ed = settings.edit();
    ed.putLong(RaceStatus.TIMESTAMP, newTimeStamp);
    ed.apply();
  }

  private void _sync_next_row() {
    /* sync next row in queue */
    for( StartRow row : starts ) {
      if( row.state == StartRow.SyncState.PENDING ) {
        _sync_row(row);

        /* allow only one sync thread at time
           note: for disable find all usages `isSyncNow`
        */
        break;
      }
    }
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

    /* TODO: disable sync when apllication no visible */
    _sync_handler.post(new Runnable() {
      public void run() {
        _sync_receive();
        _sync_handler.postDelayed(this, 3000);
      }
    });
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

    for( StartRow row : starts ) {
      Log.d("wsa-ng", _("Post row: " + row.toString()));
      EventBus.getDefault().post(row);
    }

    if( cdmsg != null )
      EventBus.getDefault().post(cdmsg);

    Log.i("wsa-ng", _("Boot End"));
  }

  private HttpClient _build_client()
  {
    RequestConfig config = RequestConfig.custom()
                           .setConnectTimeout(30000)
                           .setConnectionRequestTimeout(30000)
                           .setSocketTimeout(30000)
                           .build();
    return HttpClientBuilder.create()
                            .setDefaultRequestConfig(config)
                            .build();
  }

  private Handler _sync_handler = new Handler() {
    @Override
    public void handleMessage(Message msg) {
      Bundle data = msg.getData();
      int rowId = data.getInt("rowId");
      int inprintCount = data.get("inprintCount");
      StartRow row = starts.getRecord(rowId);
      StartRow.SyncState state = StartRow.SyncState.values()[data.getInt("state")];

      if( row == null ) {
        Log.e("wsa-ng", _("unknown rowId #" + rowId + " from sync service"));
        return;
      }

      row.setState(state, inprintCount);

      Log.d("wsa-ng", _("publish sync result for rowId #" + rowId + " new state: " + state.name()));
      EventBus.getDefault().post(row);

      isSyncNow = false;
      if( state == StartRow.SyncState.SYNCED ) {
        starts.Save(getApplicationContext());
        _sync_next_row();
      }
    }
  };

  /* send row to server */
  private void _sync_row(StartRow row)
  {
    final int inprintCount;
    final Launcher.Mode mode;
    final byte[] body;
    final String url;
    final int rowId = row.getRowId();

    if( isSyncNow )
      return;

    if( !settings.contains("server_addr") ) {
      Log.d("wsa-ng", _("`server_addr` is not defined: row not synced"));
      return;
    }

    StringWriter sw = new StringWriter();
    JsonWriter jw = new JsonWriter(sw);

    try {
      jw.beginArray();
      url = String.format(SET_URL,
                          settings.getString("server_addr", Default.server_addr),
                          raceStatus.competitionId,
                          terminalStatus.terminalId);
      inprintCount = row.prepareJSON(jw);
      jw.endArray();
      body = sw.toString().getBytes("utf-8");
    } catch( Exception e ) {
      StringWriter psw = new StringWriter();
      PrintWriter pw = new PrintWriter(psw);

      e.printStackTrace(pw);
      Log.e("wsa-ng", _("sync rowId #" + Integer.toString(rowId) + " error: " + e.getMessage() + " ->\n" + psw.toString()));
      row.setState(StartRow.SyncState.ERROR, 0);
      return;
    }

    Log.d("wsa-ng", _("sync rowId #" + Integer.toString(rowId) + " going to new thread: inprint=" + Integer.toString(inprintCount)));

    isSyncNow = true;

    row.setState(StartRow.SyncState.SYNCING, 0);
    /* publish status */
    EventBus.getDefault().post(row);

    /* run sync */
    Thread thread = new Thread(new Runnable() {
      public void run() {
        HttpClient client = _build_client();
        Message msg;
        Bundle data;
        StartRow.SyncState state = StartRow.SyncState.PENDING;
        HttpPost rq = new HttpPost(url);

        rq.setHeader("User-Agent", "wsa-ng/1.0");
        rq.setEntity(new ByteArrayEntity(body));

        HttpResponse rs;
        do {
          Log.i("wsa-ng", _("sync rowId #" + Integer.toString(rowId) + " url: " + url));
          try {
            rs = client.execute(rq);
          } catch( ClientProtocolException e ) {
            Log.e("wsa-ng", _("sync rowId #" + Integer.toString(rowId) + " error: " + e.getMessage()));
            break;
          } catch( IOException e ) {
            Log.e("wsa-ng", _("sync rowId #" + Integer.toString(rowId) + " error: " + e.getMessage()));
            break;
          }
          int rs_code = rs.getStatusLine().getStatusCode();

          if( rs_code == 200 ) {
            String result;
            try {
              result = EntityUtils.toString(rs.getEntity(), "UTF-8");
              if( result.compareTo("true") == 0 ) {
                state = StartRow.SyncState.SYNCED;
                Log.d("wsa-ng", _("sync rowId #" + Integer.toString(rowId) + " complete"));
              }
              else {
                state = StartRow.SyncState.ERROR;
                Log.d("wsa-ng", _("sync rowId #" + Integer.toString(rowId) + " server not return 'true': " + result));
              }
            }
            catch( IOException e ) {
              Log.e("wsa-ng", _("sync rowId #" + Integer.toString(rowId) + " unpack result from server failed: " + e.getMessage()));
            }
          }
          else {
            Log.d("wsa-ng", _("sync rowId #" + Integer.toString(rowId) + " invalid code: " + Integer.toString(rs_code)));
          }
        } while( false );

        data = new Bundle();
        data.putInt("rowId", rowId);
        data.putInt("state", state.ordinal());
        data.putInt("inprintCount", inprintCount);

        msg = _sync_handler.obtainMessage();
        msg.setData(data);
        _sync_handler.sendMessage(msg);
      }
    });

    thread.setDaemon(true);
    thread.start();
  }

  private boolean isSyncNow = false;

  /* receive and sync data from server */
  private void _sync_receive()
  {
    final String url;

    if( isSyncNow )
      return;

    isSyncNow = true;
    url = String.format(GET_URL,
                        settings.getString("server_addr", Default.server_addr),
                        this.raceStatus.competitionId,
                        this.timestamp,
                        this.terminalStatus.terminalId);

    Thread thread = new Thread(new Runnable() {
      public void run() {
        HttpClient client = _build_client();
        HttpGet rq = new HttpGet(url);
        ServerStatus serverStatus = new ServerStatus();

        Log.d("wsa-ng", _("rsync addr: %s", url));
        rq.setHeader("User-Agent", "wsa-ng/1.0");

        HttpResponse rs;
        try {
          try {
            rs = client.execute(rq);
          } catch( ClientProtocolException|IOException e) {
            Log.e("wsa-ng", _("rsync failed: " + e.getMessage()));
            return;
          }
          int rs_code = rs.getStatusLine().getStatusCode();

          if( rs_code == 200 ) {
            String result;
            try {
              result = EntityUtils.toString(rs.getEntity(), "UTF-8");
              Log.d("wsa-ng", _("rsync response: " + result));

              JsonReader jr = new JsonReader(new StringReader(result));
              serverStatus.loadJSON(jr);
            }
            catch( IOException|IllegalStateException e ) {
              Log.e("wsa-ng", _("rsync decoding exception: " + e.getMessage()));
              return;
            }
          }
          else {
            Log.e("wsa-ng", _("rsync code: " + Integer.toString(rs_code)));
          }
        }
        finally {
          EventBus.getDefault().post(serverStatus);
        }
      }
    });

    thread.setDaemon(true);
    thread.start();
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

      if( !row.changePossible() ) {
        Log.e("wsa-ng", _("update rowId #" +
                          Integer.toString(msg.rowId) + ": msg " + msg.type.name() +
                          " -> update not possible: row in SYNCING"));
        return;
      }

      switch( msg.type ){
      case CONFIRM:
        /* instant update */
        /* FIXME: not possible until messages send without LapId in server mode */
        /*_sync_row(row);*/
        row.setState(StartRow.SyncState.PENDING, 0);
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
        /*
      case PENALTY:
        row.setPenalty(msg.penalty);
        break;*/
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
  public void _event_receive(ServerStatus status)
  {
    isSyncNow = false;

    Log.d("wsa-ng", _("[RECEIVE] process ServerStatus -> [ " +
                      (status.raceStatus == null ? "" : "RaceStatus ") +
                      (status.terminalStatus.size() == 0 ? "" : " T" + Integer.toString(status.terminalStatus.size())) +
                      (status.lap.size() == 0 ? "" : " L" + Integer.toString(status.lap.size())) +
                      "]"));

    try {
      boolean changed = false;
      boolean needReload = false;

      if( status.raceStatus != null ) {
        if( status.raceStatus.competitionId != raceStatus.competitionId ) {
          Log.i("wsa-ng", _("[RECEIVE] CompetitionId: local = %d, remote = %d",
                            raceStatus.competitionId,
                            status.raceStatus.competitionId));
          /* clear all data */
          starts = new StartList();
          /* update settings */
          _updateTimeStamp(status.raceStatus.timestamp);
          raceStatus = status.raceStatus;
        }
        else if ( status.raceStatus.timestamp > timestamp ) {
          Log.i("wsa-ng", _("[RECEIVE] RaceStatus timestamp: local = %d, remote = %d",
                            timestamp,
                            status.raceStatus.timestamp));
          /* update settings */
          _updateTimeStamp(status.raceStatus.timestamp);
          raceStatus = status.raceStatus;
        }

        raceStatus.saveSettings(race_settings);
        needReload = true;
      }

      /* check terminalStatus data */
      for( int i = 0; i < status.terminalStatus.size(); i++ ) {
        TerminalStatus term = status.terminalStatus.get(i);

        if( term.terminalId.compareTo(this.terminalStatus.terminalId) == 0 ) {
          Log.i("wsa-ng", _("[RECEIVE] apply new TerminalStatus"));
          this.terminalStatus = term;
          this.terminalStatus.saveSettings(race_settings);
          /* update Screen */
          needReload = true;
        }

        if( timestamp < term.timestamp ) {
          /* apply timestamp from any received struct */
          Log.i("wsa-ng", _("[RECEIVE] TerminalStatus timestamp: local = %d, remote = %d",
                            timestamp,
                            term.timestamp));
          _updateTimeStamp(term.timestamp);
        }
      }

      /* check laps data */
      for( int i = 0; i < status.lap.size(); i++ ) {
        StartRow rrow = status.lap.get(i);
        StartRow lrow = starts.getRecord(rrow.getRowId());

        changed = true;
        if( lrow == null ) {
          starts.addRecord(rrow);
          rrow.setState(StartRow.SyncState.SYNCED, 0);
          EventBus.getDefault().post(rrow.clone());
        }
        else {
          lrow.update(rrow);
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
      if( needReload )
        EventBus.getDefault().post(new EventMessage.ReloadSettings());
    } catch( Exception e ) {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);

      e.printStackTrace(pw);
      Log.e("wsa-ng", _("[RECEIVE] Got error: %s ->\n%s", e.getMessage(), sw.toString()));
    }

    _sync_next_row();
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
