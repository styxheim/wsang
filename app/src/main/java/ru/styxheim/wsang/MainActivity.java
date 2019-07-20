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

public class MainActivity extends Activity implements PopupMenu.OnMenuItemClickListener
{
  /* offset for start_row_time id from rowId */
  static final int ROW_TIME_ID_OFFSET = 1000000;
  /* offset for start_crew_time id */
  static final int ROW_CREW_ID_OFFSET = 5000000;
  /* offset for time field in table from rowId */
  static final int ROW_TIME_INFO_ID_OFFSET = 9000000;
  
  private int menuLapId;
  ArrayList<int[]> lapId2RowId;

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    Log.d("wsa-ng", "MainActivity:onCreate()");

    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_start);
    
    lapId2RowId = new ArrayList<int[]>();
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
    TextView v;
    View tr_crew;
    View tr_time;
    boolean visible = false;

    Log.d("wsa-ng", "Update row #" + Integer.toString(row.getRowId()));
    
    try {
      TableLayout tl_crew = findViewById(R.id.start_table_crew);
      TableLayout tl_time = findViewById(R.id.start_table_time);
      LayoutInflater inflater = getLayoutInflater();

      tr_crew = findViewById(row.getRowId() + ROW_CREW_ID_OFFSET);
      tr_time = findViewById(row.getRowId() + ROW_TIME_ID_OFFSET);
      if( tr_time != null && tr_crew != null ) {
        visible = true;
        
        v = findViewById(row.getRowId() + ROW_TIME_INFO_ID_OFFSET);
        v.setText("");
      }
      else {
        visible = false;
        tr_crew = inflater.inflate(R.layout.start_row_crew, null);
        tr_time = inflater.inflate(R.layout.start_row_time, null);

        tr_time.setId(row.getRowId() + ROW_TIME_ID_OFFSET);
        tr_crew.setId(row.getRowId() + ROW_CREW_ID_OFFSET);
        
        v = tr_time.findViewById(R.id.start_row_time_info);
        v.setId(row.getRowId() + ROW_TIME_INFO_ID_OFFSET);
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
        int[] helper = new int[3];
        
        tl_crew.addView(tr_crew);
        tl_time.addView(tr_time);
        
        /* {rowid, lapId, textview id} */
        helper[0] = row.getRowId();
        helper[1] = row.lapId;
        helper[2] = row.getRowId() + ROW_TIME_INFO_ID_OFFSET;
        lapId2RowId.add(helper);

        registerForContextMenu(tr_time);
      }
      else {
        /* TODO: update lapId in lapId2RowId */
      }
    } catch( Exception e ) {
      e.printStackTrace();
    }
  }

  public void showTimeCounterPopup(View v)
  {
    String title;
    int lapId = v.getId() - ROW_TIME_ID_OFFSET;

    title = "Старт заезда №" + Integer.toString(lapId) + " через";

    PopupMenu popup = new PopupMenu(this, v);
    popup.inflate(R.menu.start_time);
    popup.setOnMenuItemClickListener(this);
    MenuItem titleItem = popup.getMenu().findItem(R.id.start_time_menu_title);
    titleItem.setTitle(title);
    popup.show();
    menuLapId = lapId;
  }

  @Override
  public boolean onMenuItemClick(MenuItem item)
  {
    int lapId = menuLapId;
    
    switch( item.getItemId() )
    {
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
  
  public void startCountDown(int lapId, int seconds)
  {
    Log.i("wsa-ng", "Send COUNTDOWN event for lap #" +
          Integer.toString(lapId) + ": " +
          Integer.toString(seconds) + "s");
    EventMessage.CountDownMsg msg = new EventMessage.CountDownMsg(lapId, seconds, 0);
    EventBus.getDefault().post(new EventMessage(EventMessage.EventType.COUNTDOWN_START, msg));
  }
  
  public void cancelOnClick(View v)
  {
    EventBus.getDefault().post(new EventMessage(EventMessage.EventType.COUNTDOWN_STOP, null));
  }

  public void startOnClick(View v)
  {
    EventBus.getDefault().post(new EventMessage(EventMessage.EventType.NEW, null));
  }

  private void _event_countdown(EventMessage.CountDownMsg msg)
  {
    TextView tv;
    
    Log.d("wsa-ng",
          "COUNTDOWN: lapId=" + Integer.toString(msg.lapId) +
          " left=" + Long.toString(msg.leftMs));
    
    for( int[] helper : lapId2RowId ) {
      if( helper[1] == msg.lapId ) {
        tv = findViewById(helper[2]);
        tv.setText("Старт через " + Long.toString(msg.leftMs / 1000 + 1));
      }
    }
  }
  
  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onServiceMessageUpdate(EventMessage ev) {
    if( ev.type == EventMessage.EventType.UPDATE ) {
      publishStartRow((StartRow)ev.obj);
    }
    else if( ev.type == EventMessage.EventType.COUNTDOWN ) {
      _event_countdown((EventMessage.CountDownMsg)ev.obj);
    }
  }

  public void startTimeOnClick(View v)
  {
    int id = v.getId();
    Log.i("wsa-ng", "clicked: " + Integer.toString(id - ROW_TIME_ID_OFFSET));
    showTimeCounterPopup(v);
  }
}
