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

public class MainActivity extends Activity
{
  protected int lastCrewId = 0;
  protected int lastLapId = 0;
  protected SharedPreferences settings;
  protected SharedPreferences settingsRace;
  protected TerminalStatus term;
  protected RaceStatus race;
  protected Chrono chrono;

  protected View selectedRow;
  protected Drawable selectedDrawable;

  protected ArrayList<ViewData> dataList = new ArrayList<ViewData>();

  protected boolean countDownMode = false;

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

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event)
  {
    if( chrono != null ) {
      if( chrono.onKeyDown(keyCode, event) )
        return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  protected void _selectRow(final View v)
  {
    final Drawable drw = this.selectedDrawable;
    final View row = this.selectedRow;

    if( row != null ) {
      row.setBackground(drw);
    }

    if( v != null ) {
      this.selectedRow = v;
      this.selectedDrawable = v.getBackground();
      v.setBackgroundResource(R.color.selected_row);
    }
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
      TableLayout table = findViewById(R.id.table);
      View v;
      vd = new ViewData(row.getRowId(), term, this);
      v = vd.getView();
      if( table.getChildCount() % 2 == 0 )
        v.setBackgroundResource(R.color.rowEven);

      table.addView(v);
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
      Chrono.Record r;

      if( chrono != null && finish != row.finishAt ) {
        if( (r = chrono.getRecord(finish)) != null )
          r.deselect();
        if( (r = chrono.getRecord(row.finishAt)) != null )
          r.select();
      }

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

      View.OnClickListener lapcrew = new View.OnClickListener() {
        @Override
        public void onClick(View v)
        {
          StartLineEditDialog sled = new StartLineEditDialog(crew, lap, true);

          _selectRow(tRow);

          sled.setStartLineEditDialogListener(new StartLineEditDialog.StartLineEditDialogListener() {
              @Override
              public void onStartLineEditDialogResult(StartLineEditDialog sled, int crewId, int lapId) {
                EventMessage.ProposeMsg req;

                Log.i("wsa-ng", "Set new lap/crew for row #" + Integer.toString(rowId));
                req = new EventMessage.ProposeMsg(crewId, lapId);

                req.setRowId(rowId);

                EventBus.getDefault().post(new EventMessage(EventMessage.EventType.PROPOSE, req));
              }
            });
          sled.show(getFragmentManager(), "StartLineEditDialog");
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

          _selectRow(tRow);

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

          pmenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item)
            {
              final Chrono.Record r = chrono.getRecord(item.getItemId());
              final Runnable set = new Runnable() {
                public void run() {
                  EventMessage.ProposeMsg req;


                  req = new EventMessage.ProposeMsg(r.getValue(), EventMessage.ProposeMsg.Type.FINISH);
                  req.setRowId(rowId);
                  EventBus.getDefault().post(new EventMessage(EventMessage.EventType.PROPOSE, req));
                }
              };

              if( r == null )
                return false;

              if( finish != 0 ) {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setMessage("Заменить финишное время?");
                builder.setPositiveButton("Да", new DialogInterface.OnClickListener() {
                  @Override
                  public void onClick(DialogInterface dialog, int id) {
                    set.run();
                  }
                });
                builder.setNegativeButton("Нет", new DialogInterface.OnClickListener() {
                  @Override
                  public void onClick(DialogInterface dialog, int id) {
                  }
                });
                builder.create().show();
              }
              else {
                set.run();
              }

              return true;
            }
          });

          pmenu.show();
        }
      };

      View.OnClickListener startListener = new View.OnClickListener() {
        @Override
        public void onClick(View v)
        {
          PopupMenu popup = new PopupMenu(MainActivity.this, v);

          _selectRow(tRow);

          if( start != 0 ) {
            /* reset start time */
            popup.getMenu().add(1, 3, 3, R.string.false_start);
          }
          else if( !countDownMode ) {
            popup.getMenu().add(1, 10, 10, R.string.ten_seconds_button);
            popup.getMenu().add(1, 30, 30, R.string.thirty_seconds_button);
            popup.getMenu().add(1, 60, 60, R.string.sixty_seconds_button);
          }

          if( countDownMode ) {
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
                builder.setPositiveButton("Отменить", new DialogInterface.OnClickListener() {
                  @Override
                  public void onClick(DialogInterface dialog, int id) {
                    Toast.makeText(MainActivity.this,
                                   "Not now",
                                   Toast.LENGTH_SHORT).show();
                    /*_reset_start_time_for_lap(lapId);*/
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
                Toast.makeText(MainActivity.this,
                               "Not now",
                               Toast.LENGTH_SHORT).show();
                /* startCountDown(lapId, item.getItemId()); */
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

      tCrew.setOnClickListener(lapcrew);
      tLap.setOnClickListener(lapcrew);

      if( term.hasStartGate() ) {
        tStart = tRow.findViewById(R.id.start_gate);
      }

      if( term.hasFinishGate() ) {
        tFinish = tRow.findViewById(R.id.finish_gate);
      }

      tRow.removeAllViews();

      tRow.addView(tLap);
      tRow.addView(tCrew);

      if( tStart != null ) {
        tStart.setOnClickListener(startListener);
        tRow.addView(tStart);
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
