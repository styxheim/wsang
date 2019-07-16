package ru.styxheim.wsang;

import android.app.*;
import android.os.*;
import android.widget.*;
import android.view.*;
import android.content.*;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.ThreadMode;
import org.greenrobot.eventbus.Subscribe;

public class MainActivity extends Activity
{
  private StartList starts;
  static final int ROW_TIME_ID_OFFSET = 1000000;
  static final int ROW_CREW_ID_OFFSET = 5000000;

  @Override
  public void onStart() {
    super.onStart();
    EventBus.getDefault().register(this);
  }

  @Override
  public void onStop() {
    EventBus.getDefault().unregister(this);
    super.onStop();
  }

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    starts = new StartList();
    setContentView(R.layout.activity_start);

    Intent intent = new Intent(this, MainService.class);
    startService(intent);

    starts.Load(getApplicationContext());
    updateStartView();
  }

  private void updateStartView()
  {
    int i = 0;
    TextView v;
    TextView s;
    View tr_crew;
    View tr_time;
    boolean visible = false;
    try {
      TableLayout tl_crew = findViewById(R.id.start_table_crew);
      TableLayout tl_time = findViewById(R.id.start_table_time);
      LayoutInflater inflater = getLayoutInflater();

      for( StartRow row : starts ) {
        i++;

        tr_crew = findViewById(row.getRowId() + ROW_CREW_ID_OFFSET);
        tr_time = findViewById(row.getRowId() + ROW_TIME_ID_OFFSET);
        if( tr_time != null && tr_crew != null ) {
          visible = true;
        }
        else {
          visible = false;
          tr_crew = inflater.inflate(R.layout.start_row_crew, null);
          tr_time = inflater.inflate(R.layout.start_row_time, null);

          tr_time.setId(row.getRowId() + ROW_TIME_ID_OFFSET);
          tr_crew.setId(row.getRowId() + ROW_CREW_ID_OFFSET);
        }

        v = tr_crew.findViewById(R.id.start_row_crew_id);

        v.setText("C" + Integer.toString(row.crewId));
        v = tr_crew.findViewById(R.id.start_row_lap_id);
        v.setText("L" + Integer.toString(row.lapId));

        s = tr_crew.findViewById(R.id.start_row_synced);
        if( row.synced ) {
          s.setBackgroundResource(R.color.Synced);
        }
        else if ( row.errored ) {
          s.setBackgroundResource(R.color.errSync);
        }
        else {
          s.setBackgroundResource(R.color.notSynced);
        }

        if( i % 2 == 0 ) {
          tr_crew.setBackgroundResource(R.color.rowEven);
          tr_time.setBackgroundResource(R.color.rowEven);
        }

        if( !visible ) {
          tl_crew.addView(tr_crew);
          tl_time.addView(tr_time);
        }
      }
    } catch( Exception e ) {
      e.printStackTrace();
    }
  }

  public void cancelOnClick(View v)
  {
    starts.Flush();
    starts.Save(v.getContext());
    updateStartView();
    setContentView(R.layout.activity_start);
  }

  public void startOnClick(View v)
  {
    StartRow last = starts.getRecord(-1);
    if( last != null ) {
      starts.addRecord(last.crewId + 1, last.lapId + 1, "00:00:00");
    }
    else {
      starts.addRecord(0, 0, "00:00:00");
    }
    starts.Save(v.getContext());
    updateStartView();
    EventBus.getDefault().post(new EventMessage(false, true));
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onServiceMessage(EventMessage msg) {
    if( !msg.dataSynced )
      return;

    Log.i("wsa-ng", "reload view on service event");
    starts.Load(getApplicationContext());
    updateStartView();
  }

  public void startTimeOnClick(View v)
  {
    int id = v.getId();
    Log.i("wsa-ng", "clicked: " + Integer.toString(id - ROW_TIME_ID_OFFSET));
  }
}
