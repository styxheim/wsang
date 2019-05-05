package ru.styxheim.wsang;

import android.app.*;
import android.os.*;
import android.widget.*;
import android.view.*;
import android.util.Log;

public class MainActivity extends Activity
{
  private StartList starts;

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    starts = new StartList();
    setContentView(R.layout.activity_start);

    starts.Load(getApplicationContext());
    updateStartView();
  }

  private void updateStartView()
  {
    int i = 0;
    TextView v;
    View tr_crew;
    View tr_time;
    try {
      TableLayout tl_crew = findViewById(R.id.start_table_crew);
      TableLayout tl_time = findViewById(R.id.start_table_time);
      LayoutInflater inflater = getLayoutInflater();

      for( StartRow row : starts ) {
        i++;
        if( row.visible )
          continue;

        tr_crew = inflater.inflate(R.layout.start_row_crew, null);
        tr_time = inflater.inflate(R.layout.start_row_time, null);

        tr_time.setId(row.getRowId());
        v = tr_crew.findViewById(R.id.start_row_crew_id);

        v.setText("C" + Integer.toString(row.crewId));
        v = tr_crew.findViewById(R.id.start_row_lap_id);
        v.setText("L" + Integer.toString(row.lapId));

        if( i % 2 == 0 ) {
          tr_crew.setBackgroundResource(R.color.rowEven);
          tr_time.setBackgroundResource(R.color.rowEven);
        }
        /*
        else {
          tr_crew.setBackgroundResource(R.color.rowOdd);
          tr_time.setBackgroundResource(R.color.rowOdd);
        }
        */

        tl_crew.addView(tr_crew);
        tl_time.addView(tr_time);

        row.visible = true;
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
  }

  public void startTimeOnClick(View v)
  {
    int id = v.getId();
    Log.i("wsa-ng", "clicked: " + Integer.toString(id));
  }
}
