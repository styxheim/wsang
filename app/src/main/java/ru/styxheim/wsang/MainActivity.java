package ru.styxheim.wsang;

import android.app.*;
import android.os.*;
import android.widget.*;
import android.view.*;
import android.content.*;
import android.util.Log;
import java.util.ArrayList;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.ThreadMode;
import org.greenrobot.eventbus.Subscribe;

public class MainActivity extends Activity
{
  private class RowHelper {
    public int rowId;
    public int lapId;

    public int rowCrewId;
    public int rowTimeId;
    public int rowInfoId;

    public RowHelper(int rowId)
    {
      this.rowId = rowId;
    }

    public void genViewIds()
    {
      this.rowCrewId = View.generateViewId();
      this.rowTimeId = View.generateViewId();
      this.rowInfoId = View.generateViewId();
    }

    public boolean isThis(int id)
    {
      return ( id == this.rowCrewId ||
               id == this.rowTimeId ||
               id == this.rowInfoId );
    }

    public void setIds(int lapId)
    {
      this.lapId = lapId;
    }
  };

  ArrayList<RowHelper> lapId2RowId;

  private int lastCrewId;
  private int lastLapId;
  private boolean countDownMode = false;
  private int countDownLap = -1;


  private RowHelper getHelper(int viewId)
  {
    for( RowHelper helper : lapId2RowId ) {
      if( helper.isThis(viewId) ) {
        return helper;
      }
    }
    return null;
  }

/*
  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
      super.onKeyDown(keyCode, event);
      if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
      {
          Toast.makeText(MainActivity.this,"Down working",Toast.LENGTH_SHORT).show();
          return true;
      }
    return false;
  }
*/

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    Log.d("wsa-ng", "MainActivity:onCreate()");

    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_start);
    
    lapId2RowId = new ArrayList<RowHelper>();
  }

  @Override
  public void onStart()
  {
    Log.d("wsa-ng", "MainActivity:onStart()");

    super.onStart();
    EventBus.getDefault().register(this);
    
    Intent intent = new Intent(this, MainService.class);
    startService(intent); /* boot data records */
  }

  @Override
  public void onStop()
  {
    Log.d("wsa-ng", "MainActivity:onStop()");

    EventBus.getDefault().unregister(this);
    super.onStop();
  }

  private void publishStartRow(StartRow row)
  {
    final ScrollView sv = findViewById(R.id.start_scroll);
    TextView v;
    View tr_crew = null;
    View tr_time = null;
    boolean visible = false;
    RowHelper helper = null;

    if( row.crewId > this.lastCrewId )
      this.lastCrewId = row.crewId;

    if( row.lapId > this.lastLapId )
      this.lastLapId = row.lapId;

    for( RowHelper _helper : lapId2RowId ) {
      if( _helper.rowId == row.getRowId() ) {
        helper = _helper;
        break;
      }
      else {
        _helper = null;
      }
    }

    Log.d("wsa-ng", "Update row #" + Integer.toString(row.getRowId()));

    try {
      TableLayout tl_crew = findViewById(R.id.start_table_crew);
      TableLayout tl_time = findViewById(R.id.start_table_time);
      LayoutInflater inflater = getLayoutInflater();

      if( helper != null ) {
        tr_crew = findViewById(helper.rowCrewId);
        tr_time = findViewById(helper.rowTimeId);
        if( tr_time != null && tr_crew != null ) {
          visible = true;
        }
      }
      else {
        helper = new RowHelper(row.getRowId());
        lapId2RowId.add(helper);
      }

      if( !visible ) {
        /* inflate new row */
        helper.genViewIds();

        tr_crew = inflater.inflate(R.layout.start_row_crew, null);
        tr_time = inflater.inflate(R.layout.start_row_time, null);

        tr_time.setId(helper.rowTimeId);
        tr_crew.setId(helper.rowCrewId);

        v = tr_time.findViewById(R.id.start_row_time_info);
        v.setId(helper.rowInfoId);
      }

      v = tr_crew.findViewById(R.id.start_row_crew_id);
      v.setText("C" + Integer.toString(row.crewId));

      v = tr_crew.findViewById(R.id.start_row_lap_id);
      v.setText("L" + Integer.toString(row.lapId));

      v = tr_crew.findViewById(R.id.start_row_synced);
      switch( row.state ) {
      case SYNCED:
        v.setBackgroundResource(R.color.Synced);
        break;
      case ERROR:
        v.setBackgroundResource(R.color.errSync);
        break;
      default:
          v.setBackgroundResource(R.color.notSynced);
          break;
      }

      v = tr_time.findViewById(R.id.start_row_time_view);
      v.setText(row.startAt);

      /* FIXME: rowId not trustable */
      if( row.getRowId() % 2 == 0 ) {
        tr_crew.setBackgroundResource(R.color.rowEven);
        tr_time.setBackgroundResource(R.color.rowEven);
      }

      if( !visible ) {
        tl_crew.addView(tr_crew);
        tl_time.addView(tr_time);
        /* tell to scrollview what need update position */
        sv.post(new Runnable() {
          @Override
          public void run()
          {
            sv.scrollTo(0, sv.getBottom());
          }
        });

        registerForContextMenu(tr_time);
      }

      helper.setIds(row.lapId);
    } catch( Exception e ) {
      e.printStackTrace();
    }
  }

  public void showTimeCounterPopup(View v, RowHelper helper)
  {

    PopupMenu popup = new PopupMenu(this, v);
    if( countDownMode ) {
      popup.inflate(R.menu.start_cancel);
      popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem item)
        {
          switch( item.getItemId() ) {
          case R.id.start_cancel_stop:
            /* stop countdown */
            EventBus.getDefault().post(new EventMessage(EventMessage.EventType.COUNTDOWN_STOP, null));
            break;
          default:
            return false;
          }
          return true;
        }
      });
    } else {
      final int lapId = helper.lapId;
      final String title = "Старт заезда №" + Integer.toString(lapId) + " через";
      popup.inflate(R.menu.start_time);
      popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem item)
        {
          switch( item.getItemId() ) {
          case R.id.start_time_menu_ten:
            startCountDown(lapId, 10);
            break;
          case R.id.start_time_menu_thiry:
            startCountDown(lapId, 30);
            break;
          case R.id.start_time_menu_sixty:
            startCountDown(lapId, 60);
            break;
          default:
            return false;
          }
          return true;
        }
      });
      MenuItem titleItem = popup.getMenu().findItem(R.id.start_time_menu_title);
      titleItem.setTitle(title);
    }
    popup.show();
  }

  
  public void startCountDown(int lapId, int seconds)
  {
    Log.i("wsa-ng", "Send COUNTDOWN event for lap #" +
          Integer.toString(lapId) + ": " +
          Integer.toString(seconds) + "s");
    EventMessage.CountDownMsg msg = new EventMessage.CountDownMsg(lapId, seconds * 1000, 0);
    EventBus.getDefault().post(new EventMessage(EventMessage.EventType.COUNTDOWN_START, msg));
  }
  
  public void cancelOnClick(View v)
  {
    Intent intent = new Intent(this, SettingsActivity.class);
    startActivity(intent);
  }

  public void startOnClick(View v)
  {
    StartLineEditDialog sled = new StartLineEditDialog(this.lastCrewId + 1, this.lastLapId + 1);
    sled.setStartLineEditDialogListener(new StartLineEditDialog.StartLineEditDialogListener() {
      @Override
      public void onStartLineEditDialogResult(StartLineEditDialog sled, int crewId, int lapId) {
        EventMessage.ProposeMsg req;
        lastCrewId = crewId;
        lastLapId = lapId;

        req = new EventMessage.ProposeMsg(crewId, lapId);

        EventBus.getDefault().post(new EventMessage(EventMessage.EventType.PROPOSE, req));
      }
    });
    sled.show(getFragmentManager(), "StartLineEditDialog");

  }

  private void _event_countdown_start(EventMessage.CountDownMsg msg)
  {
    ProgressBar pb = (ProgressBar)findViewById(R.id.start_progress);

    pb.setMax((int)msg.leftMs);
    pb.setMin(0);
    pb.setProgress(0, true);

    countDownMode = true;
    countDownLap = msg.lapId;
  }

  private void _event_countdown(EventMessage.CountDownMsg msg)
  {
    TextView tv;
    ProgressBar pb = (ProgressBar)findViewById(R.id.start_progress);

    pb.setProgress(pb.getMax() - (int)msg.leftMs, true);

    countDownMode = true;
    countDownLap = msg.lapId;

    Log.d("wsa-ng",
          "COUNTDOWN: lapId=" + Integer.toString(msg.lapId) +
          " left=" + Long.toString(msg.leftMs));

    for( RowHelper helper : lapId2RowId ) {
      if( helper.lapId == msg.lapId ) {
        tv = findViewById(helper.rowInfoId);
        tv.setText("Старт через " + Long.toString(msg.leftMs / 1000 + 1));
      }
    }
  }

  private void _event_countdown_end(EventMessage.CountDownMsg msg)
  {
    TextView tv;
    ProgressBar pb = (ProgressBar)findViewById(R.id.start_progress);
    int lapId = (msg.endAtMs == -1 ? countDownLap : msg.lapId);

    pb.setProgress(pb.getMax(), true);

    countDownMode = false;

    for( RowHelper helper : lapId2RowId ) {
      if( helper.lapId == lapId ) {
        tv = findViewById(helper.rowInfoId);
        if( msg.endAtMs == -1 )
          tv.setText("Старт отменён");
        else
          tv.setText("Старт выполнен");
      }
    }

  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onServiceMessageUpdate(EventMessage ev)
  {
    switch( ev.type ) {
    case UPDATE:
      publishStartRow((StartRow)ev.obj);
      break;
    case COUNTDOWN_START:
      _event_countdown_start((EventMessage.CountDownMsg)ev.obj);
      break;
    case COUNTDOWN:
      _event_countdown((EventMessage.CountDownMsg)ev.obj);
      break;
    case COUNTDOWN_END:
      _event_countdown_end((EventMessage.CountDownMsg)ev.obj);
      break;
    }
  }

  public void startTimeOnClick(View v)
  {
    RowHelper helper = getHelper(v.getId());

    if( helper == null )
      return;

    showTimeCounterPopup(v, helper);
  }

  public void startCrewOnClick(View v)
  {
    RowHelper helper = getHelper(v.getId());

    if( helper == null )
      return;

    Log.i("wsa-ng", "clicked: " + Integer.toString(helper.rowId));
  }
}
