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

import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import java.io.*;
import java.util.ArrayList;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import android.widget.RelativeLayout.*;
import android.text.style.*;
import org.apache.http.impl.client.*;

public class MainActivity extends Activity
{
  protected int lastCrewId = 0;
  protected int lastLapId = 0;
  protected SharedPreferences settings;
  protected SharedPreferences settingsRace;
  protected SharedPreferences settingsChrono;
  protected TerminalStatus term;
  protected RaceStatus race;
  protected Chrono chrono;

  protected int selectedRowId = -1;
  protected int selectedLapId = -1;

  protected ArrayList<ViewData> dataList = new ArrayList<ViewData>();

  protected boolean countDownMode = false;
  protected long countDownLap;
  protected long countDownEndAt;
  protected long countDownStartAt;

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);

    settings = getSharedPreferences("main", Context.MODE_PRIVATE);
    settingsRace = getSharedPreferences("race", Context.MODE_PRIVATE);
    settingsChrono = getSharedPreferences("chrono", Context.MODE_PRIVATE);
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
    final ProgressBar pb = findViewById(R.id.start_progress);
    final Drawable drw = tv.getBackground();

    cron = new Runnable() {
      public void run() {
        long current = System.currentTimeMillis();
        long offsetMillis = settingsChrono.getLong("offset", Default.chrono_offset);

        if( current > countDownEndAt ) {
          if( current < countDownEndAt + 1000 ) {
            tv.setText(Default.millisecondsToStringShort(0));
            tv.setBackgroundResource(R.color.selected_row);
          }
          else {
            tv.setBackground(drw);
          }
        }

        if( countDownMode ) {
          if( current > countDownEndAt ) {
            _disableCountDownMode();
          }
          else {
            tv.setText(Default.millisecondsToStringShort(countDownEndAt - current + 1000));
            pb.setProgress((int)(countDownEndAt - current));
          }
        }
        else {
          tv.setText(Default.millisecondsToStringShort(current - offsetMillis));
        }
        tv.postDelayed(this, 300);
      }
    };

    tv.post(cron);

    findViewById(R.id.settings_button).setEnabled(true);

    /* FIXME: sleep for wait service - bad idea */
    tv.postDelayed(new Runnable() {
      public void run() {
        EventBus.getDefault().post(new EventMessage.Boot());
      }
    }, 1000);
  }

  @Override
  public void onStop()
  {
    EventBus.getDefault().unregister(this);
    super.onStop();
  }

  public void settingsOnClick(View v)
  {
    v.setEnabled(false);

    Intent intent = new Intent(this, SettingsActivity.class);
    startActivity(intent);
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event)
  {
    if( chrono != null && term.hasFinishGate() ) {
      if( chrono.onKeyDown(keyCode, event) )
        return true;
    }
    return super.onKeyDown(keyCode, event);
  }
  
  public void cancelOnClick(View v)
  {
    EventBus.getDefault().post(new EventMessage(EventMessage.EventType.COUNTDOWN_STOP, null));
  }

  public void startOnClick(View v)
  {
    StartLineEditDialog sled;

    if( race.crews.size() != 0 ) {
      sled = new StartLineEditDialog(-1, this.lastLapId + 1);
      sled.setCrewValues(race.crews);
    }
    else {
      sled = new StartLineEditDialog(this.lastCrewId + 1, this.lastLapId + 1);
    }

    Log.d("wsa-ng-ui", "emit StartLineEditDialog");
    sled.setStartLineEditDialogListener(new StartLineEditDialog.StartLineEditDialogListener() {
    @Override
    public void onStartLineEditDialogResult(StartLineEditDialog sled, int crewNum, int lapId) {
      EventMessage.ProposeMsg req;
      int crewId;

      if( race.crews.size() != 0 )
        crewId = race.crews.get(crewNum);
      else
        crewId = crewNum;

      lastCrewId = crewId;
      lastLapId = lapId;

      req = new EventMessage.ProposeMsg(crewId, lapId);

      EventBus.getDefault().post(new EventMessage(req));
    }
    });
    sled.show(getFragmentManager(), sled.getClass().getCanonicalName());
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
  public void _event_terminalStatus(TerminalStatus new_term)
  {
    if( term == null || term.timestamp != new_term.timestamp ) {
      term = new_term;
      _tableSetup();
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void _event_raceStatus(RaceStatus new_race)
  {
    if( race == null || race.timestamp != new_race.timestamp ) {
      race = new_race;
      _tableSetup();
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void _event_StartRowList(ArrayList<StartRow> rows)
  {
    for( StartRow row : rows ) {
      _update_StartRow(row);
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void _update_StartRow(StartRow row)
  {
    /* Update or add new data */
    ViewData vd = _getViewDataById(row.getRowId());

    Log.d("wsa-ng-ui",
          "got " + row.toString() + ", visible=" + (vd == null ? "false" : "true"));

    if( vd == null ) {
      final ScrollView sv = findViewById(R.id.vscroll);
      /* add new row */
      final TableLayout table = findViewById(R.id.table);
      final View v;
      vd = new ViewData(row.getRowId(), term, this);
      v = vd.getView();
      if( table.getChildCount() % 2 == 0 )
        v.setBackgroundResource(R.color.rowEven);

      table.addView(v);
      dataList.add(vd);

      sv.post(new Runnable() {
        public void run() {
          sv.scrollTo(0, findViewById(R.id.spacer).getBottom());
        }
      });
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
  
  protected void _enableCountDownMode(int lapId, long startAt, long endAt)
  {
    ProgressBar pb = findViewById(R.id.start_progress);
    TextView chronometer = findViewById(R.id.chronometer);

    if( countDownMode )
      return;

    countDownMode = true;
    countDownLap = lapId;
    countDownEndAt = endAt;
    countDownStartAt = startAt;
    
    pb.setMin(0);
    pb.setMax((int)(endAt - startAt));
    pb.setProgress(0);
    
    findViewById(R.id.countdown_cancel_button).setVisibility(View.VISIBLE);
    findViewById(R.id.settings_button).setVisibility(View.GONE);
    chronometer.setTypeface(null, Typeface.BOLD);
  }
  
  protected void _disableCountDownMode()
  {
    ProgressBar pb = findViewById(R.id.start_progress);
    TextView chronometer = findViewById(R.id.chronometer);
    
    countDownMode = false;
    pb.setProgress(0);
    
    findViewById(R.id.countdown_cancel_button).setVisibility(View.GONE);
    findViewById(R.id.settings_button).setVisibility(View.VISIBLE);
    chronometer.setTypeface(null, Typeface.NORMAL);
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void _event_countdown(EventMessage.CountDownMsg msg)
  {
    _enableCountDownMode(msg.lapId, msg.startAt, msg.endAtMs);
  }
  
  @Subscribe(threadMode = ThreadMode.MAIN)
  public void _event_countDownCanceled(EventMessage.CountDownCancelled msg)
  {
    _disableCountDownMode();
  }

  private class ViewData
  {
    public int rowId;

    protected StartRow.SyncState state;
    protected int lap;
    protected int crew;
    protected long finish;
    protected long start;
    protected ArrayList<Integer> gates = new ArrayList<Integer>();

    protected Context context;

    protected View tSyncer;
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

    public void select()
    {
      boolean selected = tRow.getTag(R.id.tag_selected);

      if( selected )
        return;

      tRow.setTag(R.id.tag_selected, true);
      tRow.setTag(R.id.tag_background, tRow.getBackground());
      tRow.setBackgroundResource(R.color.selected_row);
    }

    public void deselect()
    {
      boolean selected = tRow.getTag(R.id.tag_selected);

      if( !selected )
        return;

      tRow.setTag(R.id.tag_selected, false);
      tRow.setBackground((Drawable)tRow.getTag(R.id.tag_background));
    }

    protected void _update()
    {
      switch( state ) {
        case PENDING:
          tSyncer.setBackgroundResource(R.color.Pending);
          break;
        case SYNCED:
          tSyncer.setBackgroundResource(R.color.Synced);
          break;
        case SYNCING:
          tSyncer.setBackgroundResource(R.color.Syncing);
          break;
        case ERROR:
          tSyncer.setBackgroundResource(R.color.errSync);
          break;
        default:
          tSyncer.setBackgroundResource(R.color.notSynced);
        break;
      }

      if( tLap != null ) {
        tLap.setText(Integer.toString(lap));
      }

      if( tCrew != null ) {
        tCrew.setText(Integer.toString(crew));
      }

      if( tStart != null ) {
        tStart.setText(Default.millisecondsToString(start));
      }

      for( int i = 0; i < gates.size(); i++ ) {
        int gatePenaltyId = gates.get(i);
        int pvalue = race.penalties.get(gatePenaltyId);
        TextView tGate = tGates.get(i);

        if( pvalue == 0 )
          tGate.setText("");
        else
          tGate.setText(Integer.toString(pvalue));
      }

      if( tFinish != null ) {
        tFinish.setText(Default.millisecondsToString(finish));
      }
    }

    public void update(StartRow row)
    {
      Chrono.Record r;

      if( chrono != null && finish != row.finishAt ) {
        if( (r = chrono.getRecord(finish)) != null )
          r.deselect();
        if( (r = chrono.getRecord(row.finishAt)) != null )
          r.select();
      }

      state = row.state;
      lap = row.lapId;
      crew = row.crewId;
      finish = row.finishAt;
      start = row.startAt;

      gates.clear();
      for( int i = 0; i < term.gates.size(); i++ ) {
        int gateId = term.gates.get(i);
        gates.add(0);
        for( StartRow.Gate lgate : row.gates ) {
          if( lgate.gate == gateId ) {
            gates.set(i, lgate.penalty);
            break;
          }
        }
      }

      if( tRow != null ) {
        tRow.post(new Runnable() {
          public void run() {
            if( selectedRowId == rowId || selectedLapId == lap ) {
              select();
            }
            else {
              deselect();
            }

            _update();
          }
        });
      }
    }

    public View getView()
    {
      tRow = (TableRow)LayoutInflater.from(this.context).inflate(R.layout.data_row, null);
      tSyncer = tRow.findViewById(R.id.syncer);
      tCrew = tRow.findViewById(R.id.crew);
      tLap = tRow.findViewById(R.id.lap);

      tRow.setTag(R.id.tag_selected, false);
      tRow.setTag(R.id.tag_background, null);

      View.OnClickListener lapcrewListener = new View.OnClickListener() {
        @Override
        public void onClick(View v)
        {
          StartLineEditDialog sled;
          if( race.crews.size() != 0 ) {
            sled = new StartLineEditDialog(race.crews.indexOf(crew), lap, true);
            sled.setCrewValues(race.crews);
          }
          else {
            sled = new StartLineEditDialog(crew, lap, true);
          }

          for( ViewData vd : dataList ) {
            if( vd.rowId == rowId )
              vd.select();
            else
              vd.deselect();
          }
          selectedRowId = rowId;
          selectedLapId = -1;

          if( countDownMode && countDownLap == lap ) {
            Toast.makeText(MainActivity.this,
                           "Идёт отсчёт",
                           Toast.LENGTH_SHORT).show();
            return;
          }

          sled.setStartLineEditDialogListener(new StartLineEditDialog.StartLineEditDialogListener() {
              @Override
              public void onStartLineEditDialogResult(StartLineEditDialog sled, int crewNum, int lapId) {
                EventMessage.ProposeMsg req;
                int crewId;

                if( race.crews.size() != 0 )
                  crewId = race.crews.get(crewNum);
                else
                  crewId = crewNum;

                Log.i("wsa-ng", "Set new lap/crew for row #" + Integer.toString(rowId) +
                                " crew=" + Integer.toString(crewId) + " lap=" + Integer.toString(lapId) );
                req = new EventMessage.ProposeMsg(crewId, lapId);

                req.setRowId(rowId);

                EventBus.getDefault().post(new EventMessage(EventMessage.EventType.PROPOSE, req));
              }
            });
          sled.show(getFragmentManager(), sled.getClass().getCanonicalName());
        }
      };

      View.OnClickListener finishListener = new View.OnClickListener() {
        @Override
        public void onClick(View v)
        {
          int i = 0;
          int size = chrono.getSize();
          long offset = 0;
          PopupMenu pmenu = new PopupMenu(MainActivity.this, v);

          for( ViewData vd : dataList ) {
            if( vd.rowId == rowId )
              vd.select();
            else
              vd.deselect();
          }
          selectedRowId = rowId;
          selectedLapId = -1;

          if( finish == 0 ) {
            if( size == 0 ) {
              Toast.makeText(MainActivity.this,
                             "Используйте кнопку секундомера для отсечки времени",
                             Toast.LENGTH_SHORT).show();
              return;
            }
            
            for( Chrono.Record r : chrono ) {
              String title;

              if( i == 0 ) {
                offset = r.getValue();
              }
              title = String.format("%2d. %s %s%s",
                                    size - i,
                                    Default.millisecondsToString(r.getValue()),
                                    ((offset >= r.getValue()) ? ("+") : ("-")),
                                    Default.millisecondsToString(offset - r.getValue()));

              pmenu.getMenu().add(1, i, i, title);
              if( r.isSelected() ) {
                pmenu.getMenu().getItem(i).setEnabled(false);
              }

              i++;
            }
          }
          else {
            pmenu.getMenu().add(1, 1, 1, "Отменить финиш");
          }

          pmenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item)
            {
              if( finish == 0 ) {
                Chrono.Record r = chrono.getRecord(item.getItemId());
                EventMessage.ProposeMsg req;

                if( r == null )
                  return false;

                req = new EventMessage.ProposeMsg(r.getValue(), EventMessage.ProposeMsg.Type.FINISH);
                req.setRowId(rowId);
                EventBus.getDefault().post(new EventMessage(EventMessage.EventType.PROPOSE, req));
              }
              else {
                switch( item.getItemId() ) {
                case 1:
                  AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                  builder.setMessage("Обнулить финишное время?");
                  builder.setPositiveButton("Да", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                      EventMessage.ProposeMsg req;

                      req = new EventMessage.ProposeMsg(0, EventMessage.ProposeMsg.Type.FINISH);
                      req.setRowId(rowId);
                      EventBus.getDefault().post(new EventMessage(EventMessage.EventType.PROPOSE, req));
                    }
                  });
                  builder.setNegativeButton("Нет", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                    }
                  });
                  builder.create().show();
                  break;
                }
              }

              return true;
            }
          });

          pmenu.show();
        }
      };

      View.OnClickListener gateListener = new View.OnClickListener() {
        @Override
        public void onClick(View v)
        {
          Bundle extras = new Bundle();
          Intent intent = new Intent(MainActivity.this, PenaltyActivity.class);

          for( ViewData vd : dataList ) {
            if( vd.rowId == rowId )
              vd.select();
            else
              vd.deselect();
          }

          selectedRowId = rowId;
          selectedLapId = -1;

          extras.putInt("rowId", rowId);
          extras.putInt("lap", lap);
          extras.putInt("crew", crew);

          extras.putIntegerArrayList("gates", term.gates);
          extras.putIntegerArrayList("penalties", race.penalties);
          extras.putIntegerArrayList("values", gates);

          extras.putLong("term_timestamp", term.timestamp);
          extras.putLong("race_timestamp", race.timestamp);

          intent.putExtras(extras);
          startActivity(intent);
        }
      };

      View.OnClickListener startListener = new View.OnClickListener() {
        @Override
        public void onClick(View v)
        {
          PopupMenu popup = new PopupMenu(MainActivity.this, v);

          for( ViewData vd : dataList ) {
            if( vd.lap == lap ) {
              vd.select();
            }
            else {
              vd.deselect();
            }
          }
          selectedRowId = -1;
          selectedLapId = lap;

          if( start != 0 ) {
            /* reset start time */
            popup.getMenu().add(1, 3, 3, R.string.false_start);
          }
          else if( !countDownMode ) {
            popup.getMenu().add(1, 10, 10, R.string.ten_seconds_button);
            popup.getMenu().add(1, 30, 30, R.string.thirty_seconds_button);
            popup.getMenu().add(1, 60, 60, R.string.sixty_seconds_button);
          }

          if( (countDownMode && (countDownLap == lap || start == 0)) ) {
            popup.getMenu().add(1, 1, 1, R.string.start_cancel_stop);
          }

          popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item)
            {
              AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);

              switch( item.getItemId() ) {
              case 1:
                /* stop countdown */
                EventBus.getDefault().post(new EventMessage(EventMessage.EventType.COUNTDOWN_STOP, null));
                return true;
              case 3:
                builder.setTitle(R.string.false_start);
                builder.setMessage("Отменить результаты заезда " + Integer.toString(lap) + "?");
                builder.setPositiveButton("Да", new DialogInterface.OnClickListener() {
                  @Override
                  public void onClick(DialogInterface dialog, int id) {
                    EventMessage.ProposeMsg req;

                    req = new EventMessage.ProposeMsg(EventMessage.ProposeMsg.Type.START);
                    for( ViewData vd : dataList ) {
                      if( vd.lap == lap ) {
                        req.setRowId(vd.rowId);
                        EventBus.getDefault().post(new EventMessage(EventMessage.EventType.PROPOSE, req));
                      }
                    }
                  }
                });
                builder.setNegativeButton("Нет", new DialogInterface.OnClickListener() {
                  @Override
                  public void onClick(DialogInterface dialog, int id) {
                  }
                });
                builder.create().show();
                break;
              case 10:
              case 30:
              case 60:
                long seconds = item.getItemId();
                EventMessage.CountDownMsg msg;

                msg = new EventMessage.CountDownMsg(lap, seconds * 1000);
                EventBus.getDefault().post(new EventMessage(EventMessage.EventType.COUNTDOWN_START, msg));
                break;
              default:
                return false;
              }
              return true;
            }
          });

          popup.show();
        }
      };

      tCrew.setOnClickListener(lapcrewListener);
      tLap.setOnClickListener(lapcrewListener);

      if( term.hasStartGate() ) {
        tStart = tRow.findViewById(R.id.start_gate);
      }

      for( int i = 0; i < term.gates.size(); i++ ) {
        TextView tGate = (TextView)_newDataCol(R.id.any_gate);

        tGate.setOnClickListener(gateListener);
        tGates.add(tGate);
      }

      if( term.hasFinishGate() ) {
        tFinish = tRow.findViewById(R.id.finish_gate);
      }

      tRow.removeAllViews();

      tRow.addView(tSyncer);
      tRow.addView(tLap);
      tRow.addView(tCrew);

      if( tStart != null ) {
        tStart.setOnClickListener(startListener);
        tRow.addView(tStart);
      }

      for( TextView v : tGates ) {
        tRow.addView(v);
      }

      if( tFinish != null ) {
        tFinish.setOnClickListener(finishListener);
        tRow.addView(tFinish);
      }

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

    if( term == null || race == null )
      return;

    dataList.clear();
    table.removeAllViews();
    dswitch.removeAllViews();

    TableRow header = (TableRow)_newDataCol(R.layout.data_row);
    header.removeAllViews();

    View syncer = _newDataCol(R.id.syncer);
    TextView crew = (TextView)_newDataCol(R.id.crew);
    TextView lap = (TextView)_newDataCol(R.id.lap);

    crew.setTypeface(null, Typeface.BOLD);
    lap.setTypeface(null, Typeface.BOLD);

    header.addView(syncer);
    header.addView(lap);
    header.addView(crew);

    if( term.hasStartGate() ) {
      TextView start = (TextView)_newDataCol(R.id.start_gate);
      start.setTypeface(null, Typeface.BOLD);
      header.addView(start);
    }

    for( int i = 0; i < term.gates.size(); i++ ) {
      TextView gate = (TextView)_newDataCol(R.id.any_gate);

      gate.setTypeface(null, Typeface.BOLD);
      gate.setText(Integer.toString(term.gates.get(i)));
      header.addView(gate);
    }

    if( term.hasFinishGate() ) {
      TextView finish = (TextView)_newDataCol(R.id.finish_gate);
      finish.setTypeface(null, Typeface.BOLD);
      header.addView(finish);
    }

    table.addView(header);

    chrono = new Chrono(getSharedPreferences("chrono", Context.MODE_PRIVATE),
                        getSharedPreferences("chrono_data", Context.MODE_PRIVATE),
                        (Vibrator)getSystemService(Context.VIBRATOR_SERVICE));

    table.post(new Runnable() {
      public void run() {
        EventBus.getDefault().post(new EventMessage.Boot());
      }
    });
  }
}
