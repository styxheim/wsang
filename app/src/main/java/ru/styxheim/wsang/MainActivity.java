package ru.styxheim.wsang;

import android.app.*;
import android.os.*;
import android.widget.*;
import android.view.*;
import android.view.ViewGroup.LayoutParams;
import android.content.*;
import android.text.TextWatcher;
import android.text.Editable;
import android.util.Log;

import java.io.*;
import java.util.ArrayList;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import android.widget.RelativeLayout.*;
import android.graphics.*;

public class MainActivity extends Activity
{
  protected int lastCrewId = 0;
  protected int lastLapId = 0;
  protected SharedPreferences settings;
  protected SharedPreferences settingsRace;
  protected TerminalStatus term;
  protected RaceStatus race;
  
  protected ArrayList<ViewData> dataList = new ArrayList<ViewData>();

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);

    settings = getSharedPreferences("main", Context.MODE_PRIVATE);
    settingsRace = getSharedPreferences("race", Context.MODE_PRIVATE);
    setContentView(R.layout.main);
  }

  @Override
  public void onStart()
  {
    final Runnable cron;

    super.onStart();

    EventBus.getDefault().register(this);

    Intent intent = new Intent(this, MainService.class);
    startService(intent); /* boot data records */

    /* chronometer */
    final TextView tv = findViewById(R.id.chronometer);

    cron = new Runnable() {
      public void run() {
        long offsetMillis = settings.getLong("chrono_offset", Default.chrono_offset);

        tv.setText(Default.millisecondsToString(System.currentTimeMillis() - offsetMillis));
        tv.postDelayed(this, 20);
      }
    };

    tv.post(cron);

    _tableSetup();
  }

  @Override
  public void onStop()
  {
    EventBus.getDefault().unregister(this);
    super.onStop();
  }

  public void settingsOnClick(View v)
  {
    Intent intent = new Intent(this, SettingsActivity.class);
    startActivity(intent);
  }

  public void startOnClick(View v)
  {
    StartLineEditDialog sled = new StartLineEditDialog(this.lastCrewId + 1, this.lastLapId + 1);

    Log.d("wsa-ng-ui", "emit StartLineEditDialog");
    sled.setStartLineEditDialogListener(new StartLineEditDialog.StartLineEditDialogListener() {
    @Override
    public void onStartLineEditDialogResult(StartLineEditDialog sled, int crewId, int lapId) {
      EventMessage.ProposeMsg req;

      lastCrewId = crewId;
      lastLapId = lapId;

      req = new EventMessage.ProposeMsg(crewId, lapId);

      EventBus.getDefault().post(new EventMessage(req));
    }
    });
    sled.show(getFragmentManager(), "StartLineEditDialog");
  }

  protected ViewData _getViewDataById(int rowId)
  {
    for( ViewData vd : dataList ) {
      if( vd.rowId == rowId )
        return vd;
    }

    return null;
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void _event_reloadSettings(EventMessage.ReloadSettings msg)
  {
    _tableSetup();
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void _event_StartRow(StartRow row)
  {
    /* Update or add new data */
    ViewData vd = _getViewDataById(row.getRowId());

    Log.d("wsa-ng-ui",
          "got StartRow, visible=" + (vd == null ? "false" : "true"));

    if( vd == null ) {
      /* add new row */
      vd = new ViewData(row.getRowId(), term, this);
      ((TableLayout)findViewById(R.id.table)).addView(vd.getView());
      dataList.add(vd);
    }

    /* auto-confirm */
    if( !row.isQueueEmpty() &&
        row.state == StartRow.SyncState.NONE ) {
      EventMessage.ProposeMsg confirm;

      confirm = new EventMessage.ProposeMsg(EventMessage.ProposeMsg.Type.CONFIRM);
      confirm.setRowId(row.getRowId());
      EventBus.getDefault().post(new EventMessage(EventMessage.EventType.PROPOSE, confirm));
    }

    vd.update(row);
  }

  private class ViewData
  {
    public int rowId;

    protected int lap;
    protected int crew;
    protected long finish;
    protected long start;

    protected Context context;

    protected TableRow tRow;
    protected TextView tLap;
    protected TextView tCrew;
    protected TextView tStart;
    protected TextView tFinish;
    protected ArrayList<TextView> tGates = new ArrayList<TextView>();

    protected TerminalStatus term;

    public ViewData(int id, TerminalStatus term, Context context)
    {
      this.term = term;
      this.rowId = id;
      this.context = context;
    }

    protected void _update()
    {
      if( tLap != null ) {
        tLap.setText(Integer.toString(lap));
      }

      if( tCrew != null ) {
        tCrew.setText(Integer.toString(crew));
      }

      if( tStart != null ) {
        tStart.setText(Default.millisecondsToString(start));
      }

      /* TODO: set data to gates */

      if( tFinish != null ) {
        tFinish.setText(Default.millisecondsToString(finish));
      }
    }

    public void update(StartRow row)
    {
      lap = row.lapId;
      crew = row.crewId;
      finish = row.finishAt;
      start = row.startAt;

      if( tRow != null ) {
        tRow.post(new Runnable() {
          public void run() {
            _update();
          }
        });
      }
    }

    public View getView()
    {
      tRow = (TableRow)LayoutInflater.from(this.context).inflate(R.layout.data_row, null);
      tCrew = tRow.findViewById(R.id.crew);
      tLap = tRow.findViewById(R.id.lap);

      if( term.hasStartGate() )
        tStart = tRow.findViewById(R.id.start_gate);
      if( term.hasFinishGate() )
        tFinish = tRow.findViewById(R.id.finish_gate);

      tRow.removeAllViews();

      tRow.addView(tLap);
      tRow.addView(tCrew);

      if( tStart != null )
        tRow.addView(tStart);

      if( tFinish != null )
        tRow.addView(tFinish);

      return tRow;
    }
  }

  protected View _newDataCol(int id)
  {
    View v = LayoutInflater.from(this).inflate(R.layout.data_row, null);
    if( id != R.layout.data_row ) {
      v = v.findViewById(id);
      ((ViewGroup)v.getParent()).removeView(v);
    }
    v.setId(v.generateViewId());
    return v;
  }

  protected void _tableSetup()
  {
    LinearLayout dswitch = findViewById(R.id.discipline_switch);
    TableLayout table = findViewById(R.id.table);
    term = new TerminalStatus(settingsRace);
    race = new RaceStatus(settingsRace);

    dataList.clear();
    table.removeAllViews();
    dswitch.removeAllViews();

    TableRow header = (TableRow)_newDataCol(R.layout.data_row);
    header.removeAllViews();

    TextView crew = (TextView)_newDataCol(R.id.crew);
    TextView lap = (TextView)_newDataCol(R.id.lap);

    crew.setTypeface(crew.getTypeface(), Typeface.BOLD);
    lap.setTypeface(crew.getTypeface(), Typeface.BOLD);

    header.addView(lap);
    header.addView(crew);

    if( term.hasStartGate() ) {
      TextView start = (TextView)_newDataCol(R.id.start_gate);
      start.setTypeface(start.getTypeface(), Typeface.BOLD);
      header.addView(start);
    }

    if( term.gates.size() != 0 ) {
      for( int i = 0; i < term.gates.size(); i++ ) {
        /* TODO */
      }
    }

    if( term.hasFinishGate() ) {
      TextView finish = (TextView)_newDataCol(R.id.finish_gate);
      finish.setTypeface(finish.getTypeface(), Typeface.BOLD);
      header.addView(finish);
    }

    table.addView(header);

    table.post(new Runnable() {
      public void run() {
        EventBus.getDefault().post(new EventMessage.Boot());
      }
    });
  }
}
