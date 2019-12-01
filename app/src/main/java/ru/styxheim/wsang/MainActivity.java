package ru.styxheim.wsang;

import android.app.*;
import android.os.*;
import android.widget.*;
import android.view.*;
import android.content.*;
import android.text.TextWatcher;
import android.text.Editable;
import android.util.Log;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;

import org.greenrobot.eventbus.SubscriberExceptionEvent;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import android.widget.RelativeLayout.*;
import android.text.style.*;
import org.apache.http.impl.client.*;

public class MainActivity extends Activity
{
  protected int lastCrewId = 0;
  protected SharedPreferences settings;
  protected SharedPreferences settingsRace;
  protected SharedPreferences settingsChrono;
  protected RaceStatus race;
  protected TerminalStatus term;
  protected TerminalStatus.Discipline disp;

  protected Chrono chrono;

  protected int selectedRowId = -1;
  protected int selectedLapId = -1;

  protected ArrayList<ViewData> dataList = new ArrayList<ViewData>();

  protected boolean countDownMode = false;
  protected long countDownLap;
  protected long countDownEndAt;
  protected long countDownStartAt;

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onEvent(SubscriberExceptionEvent exceptionEvent)
  {
    Log.e("wsa-ng-ui", "Catch exception");
    StringWriter psw = new StringWriter();
    PrintWriter pw = new PrintWriter(psw);

    exceptionEvent.throwable.printStackTrace(pw);

    Log.e("wsa-ng-ui", "Exception: " + psw.toString());

    Toast.makeText(MainActivity.this,
           "Look to logcat 'wsa-ng-ui'", Toast.LENGTH_SHORT).show();
  }

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);

    settings = getSharedPreferences("main", Context.MODE_PRIVATE);
    settingsRace = getSharedPreferences("race", Context.MODE_PRIVATE);
    settingsChrono = getSharedPreferences("chrono", Context.MODE_PRIVATE);
    setContentView(R.layout.main);
    setShowWhenLocked(true);
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
        EventMessage.Boot boot_msg;

        if( disp != null ) {
          boot_msg = new EventMessage.Boot(disp.id);
        }
        else {
          boot_msg = new EventMessage.Boot();
        }
        EventBus.getDefault().post(boot_msg);
      }
    }, 1000);
  }

  @Override
  public void onStop()
  {
    EventBus.getDefault().unregister(this);
    super.onStop();
  }

  public void disciplineOnClick(View v)
  {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    final ArrayList<String> names = new ArrayList<String>();
    final ArrayList<Integer> ids = new ArrayList<Integer>();
    final String[] names_array;
    final int selected;
    int _selected = -1;

    if( race == null || disp == null )
      return;

    for( int i = 0; i < race.disciplines.size(); i++ ) {
      RaceStatus.Discipline d = race.disciplines.get(i);
      if( term.getDiscipline(d.id) == null )
        continue;
      if( d.id == disp.id )
        _selected = i;
      names.add(d.name);
      ids.add(d.id);
    }

    selected = _selected;

    names_array = names.toArray(new String[0]);

    builder.setTitle(R.string.disciplines);
    builder.setIcon(R.mipmap.ic_launcher);
    builder.setSingleChoiceItems(names_array,
                                 selected,
                                 new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int item)
      {
        SharedPreferences.Editor edit = settings.edit();

        if( item != selected ) {
          Log.d("wsa-ng-ui", "Select new discipline id#" + ids.get(item).toString());
          disp = term.getDiscipline(ids.get(item));
          edit.putInt("DisciplineId", disp.id);
          edit.apply();
          _tableSetup();
        }
        dialog.dismiss();
      }
    });

    builder.create().show();
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
    if( chrono != null && disp.finishGate ) {
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
    final ArrayList<Integer> lap_values = new ArrayList<Integer>();
    RaceStatus.Discipline rdisp;
    ViewData vd;
    int lastLapId = 0;

    if( race == null )
      Log.e("wsa-ng-ui", "RaceStatus is empty, cannot add new row");
    if( term == null )
      Log.e("wsa-ng-ui", "TerminalStatus is empty, cannot add new row");
    if( disp == null )
      Log.e("wsa-ng-ui", "Discipline is empty, cannot add new row");

    if( term == null || race == null ) {
      Toast.makeText(MainActivity.this,
                     "Look to logcat 'wsa-ng-ui'",
                     Toast.LENGTH_SHORT).show();
      return;
    }

    rdisp = race.getDiscipline(disp.id);

    if( dataList.size() > 0 ) {
      vd = dataList.get(dataList.size() - 1);
      lastLapId = vd.lap;
      // allow attach to last lap in 2 cases:
      // lap not started
      // parallel start is allowed
      if( vd.start == 0 && (rdisp == null || rdisp.parallel) ) {
        lap_values.add(vd.lap);
      }
    }

    lap_values.add(lastLapId + 1);

    if( race.crews.size() != 0 ) {
      sled = new StartLineEditDialog(-1, lap_values.size() - 1);
      sled.setCrewValues(race.crews);
    }
    else {
      sled = new StartLineEditDialog(this.lastCrewId + 1, lap_values.size() - 1);
    }

    Log.d("wsa-ng-ui", "show sled");
    sled.setLapValues(lap_values);
    sled.setStartLineEditDialogListener(new StartLineEditDialog.StartLineEditDialogListener() {
    @Override
    public void onStartLineEditDialogResult(StartLineEditDialog sled, int crewNum, int lapNum) {
      EventMessage.ProposeMsg req;
      int crewId;
      int lapId = lap_values.get(lapNum);

      if( race.crews.size() != 0 )
        crewId = race.crews.get(crewNum);
      else
        crewId = crewNum;

      lastCrewId = crewId;

      req = new EventMessage.ProposeMsg(crewId, lapId, disp.id);

      Log.d("wsa-ng-ui", "Propose new: crew=" + Integer.toString(crewId) + " lap=" + Integer.toString(lapId));
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
  public void _event_receive(EventMessage.RSyncResult rstatus)
  {
    final View errorView = findViewById(R.id.error_layout);
    final TextView errorText = findViewById(R.id.error_text);
    final ServerStatus status = rstatus.serverStatus;

    if( (status != null && status.error == null) || status == null ) {
      errorView.setVisibility(View.GONE);
      return;
    }

    errorText.setText(status.error.text);
    errorView.setVisibility(View.VISIBLE);
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void _event_terminalStatus(TerminalStatus new_term)
  {
    Log.d("wsa-ng-ui", "Receive new TerminalStatus: " + new_term.toString());
    if( term == null || term.timestamp != new_term.timestamp ) {
      Log.d("wsa-ng-ui", "Apply new TerminalStatus");
      term = new_term;
      /* get current discipline from new term info */
      if( disp != null ) {
        disp = new_term.getDiscipline(disp.id);
      }
      else {
        int disciplineId = settings.getInt("DisciplineId", Default.disciplineId);
        disp = new_term.getDiscipline(disciplineId);
      }

      if( disp == null )
        disp = new_term.getDiscipline();

      _tableSetup();
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void _event_raceStatus(RaceStatus new_race)
  {
    Log.d("wsa-ng-ui", "Receive new RaceStatus");
    if( race == null || (race.timestamp != new_race.timestamp ||
                         race.competitionId != new_race.competitionId) ) {
      Log.d("wsa-ng-ui", "Apply new RaceStatus");
      race = new_race;
      _tableSetup();
      if( chrono != null )
        chrono.reload();
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
    String skip_reason = null;
    /* Update or add new data */
    ViewData vd = _getViewDataById(row.getRowId());

    if( disp == null ) {
      skip_reason = "No active discipline";
    } else {
      if( disp.id != row.disciplineId ) {
        skip_reason = String.format("Mismatch disciplineId (%d)", disp.id);
      }
    }

    Log.d("wsa-ng-ui",
          "got " + row.toString() +
          ", visible=" + (vd == null ? "false" : "true") +
          ( skip_reason == null ? "" : ", skip: " + skip_reason ) );

    if( skip_reason != null )
      return;

    if( vd == null ) {
      final ScrollView sv = findViewById(R.id.vscroll);
      /* add new row */
      final TableLayout table = findViewById(R.id.table);
      final View v;
      vd = new ViewData(row.getRowId());
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

    public StartRow.SyncState state;
    public int lap;
    public int crew;
    public long finish;
    public long start;
    public boolean strike;
    public ArrayList<Integer> gates = new ArrayList<Integer>();

    protected Context context;

    protected View tSyncer;
    protected TableRow tRow;
    protected TextView tLap;
    protected TextView tCrew;
    protected TextView tStart;
    protected TextView tFinish;
    protected ArrayList<TextView> tGates = new ArrayList<TextView>();

    public ViewData(int id)
    {
      this.rowId = id;
      this.context = MainActivity.this;
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

    protected void _strikeTextView(TextView v)
    {
      if( strike ) {
        v.setPaintFlags(v.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        v.setTextColor(R.color.strike_text);
      }
      else {
        v.setPaintFlags(v.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
      }
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
        _strikeTextView(tLap);
      }

      if( tCrew != null ) {
        tCrew.setText(Integer.toString(crew));
        _strikeTextView(tCrew);
      }

      if( tStart != null ) {
        tStart.setText(Default.millisecondsToString(start));
        _strikeTextView(tStart);
      }

      for( int i = 0; i < gates.size(); i++ ) {
        int gatePenaltyId = gates.get(i);
        int pvalue = 0;
        TextView tGate = tGates.get(i);

        _strikeTextView(tGate);
        if( gatePenaltyId >= race.penalties.size() || gatePenaltyId < 0 )
          Log.e("wsa-ng-ui", String.format("Invalid penalty id#%d", gatePenaltyId));
        else
          pvalue = race.penalties.get(gatePenaltyId);

        if( gatePenaltyId == 0 )
          tGate.setText("  ");
        else
          tGate.setText(Integer.toString(pvalue));
      }

      if( tFinish != null ) {
        tFinish.setText(Default.millisecondsToString(finish));
        _strikeTextView(tFinish);
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
      strike = row.strike;

      gates.clear();
      for( int i = 0; i < disp.gates.size(); i++ ) {
        int gateId = disp.gates.get(i);
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

    protected void _show_strike_dialog()
    {
      AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
      builder.setMessage(String.format("Закрыть заезд %d экипажа %d?", lap, crew));
      builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int id) {
          EventMessage.ProposeMsg req;

          req = new EventMessage.ProposeMsg().setRowId(rowId).setStrike(true);
          EventBus.getDefault().post(new EventMessage(req));
        }
      });
      builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int id) {
        }
      });
      builder.create().show();
    }

    protected void _show_edit_dialog()
    {
      StartLineEditDialog sled;
      final ArrayList<Integer> lap_values = new ArrayList<Integer>();
      RaceStatus.Discipline rdisp = race.getDiscipline(disp.id);

      /* pass previous and next lap data */
      int cpos = dataList.indexOf(ViewData.this);
      int pos = cpos;

      if( start == 0 ) {
        /* previous lap */
        while ( --cpos >= 0 ) {
          ViewData prev = dataList.get(cpos);

          if( prev.lap != lap && lap_values.indexOf(prev.lap) == -1 ) {
            if( prev.start == 0 && (rdisp == null || rdisp.parallel) ) {
              /* not add when prev started */
              lap_values.add(prev.lap);
            }
            break;
          }
        }
      }

      lap_values.add(lap);
      cpos = pos;

      if( start == 0 ) {
        int lastLapId = lap;
        boolean found = false;
        /* next lap */
        while( ++cpos < dataList.size() ) {
          ViewData next = dataList.get(cpos);

          if( !found ) {
            if( lap_values.indexOf(next.lap) == -1 ) {
              if( next.start == 0 && (rdisp == null || rdisp.parallel) ) {
                lap_values.add(next.lap);
              }
              found = true;;
            }
          }
          lastLapId = next.lap;
        }
        lap_values.add(lastLapId + 1);
      }

      Log.d("wsa-ng-ui", String.format("<LAPS[%d] %s>", lap_values.size(), lap_values.toString()));

      if( race.crews.size() != 0 ) {
        sled = new StartLineEditDialog(race.crews.indexOf(crew), lap_values.indexOf(lap), true);
        sled.setCrewValues(race.crews);
      }
      else {
        sled = new StartLineEditDialog(crew, lap_values.indexOf(lap), true);
      }
      sled.setLapValues(lap_values);

      if( countDownMode && countDownLap == lap ) {
        Toast.makeText(MainActivity.this,
                       "Идёт отсчёт",
                       Toast.LENGTH_SHORT).show();
        return;
      }

      sled.setStartLineEditDialogListener(new StartLineEditDialog.StartLineEditDialogListener() {
          @Override
          public void onStartLineEditDialogResult(StartLineEditDialog sled, int crewNum, int lapNum) {
            EventMessage.ProposeMsg req;
            int crewId;
            int lapId = lap_values.get(lapNum);

            if( race.crews.size() != 0 )
              crewId = race.crews.get(crewNum);
            else
              crewId = crewNum;

            Log.i("wsa-ng-ui", "Set new lap/crew for row #" + Integer.toString(rowId) +
                            " crew=" + Integer.toString(crewId) + " lap=" + Integer.toString(lapId) );
            req = new EventMessage.ProposeMsg(crewId, lapId, disp.id);

            req.setRowId(rowId);

            EventBus.getDefault().post(new EventMessage(EventMessage.EventType.PROPOSE, req));
          }
        });
      sled.show(getFragmentManager(), sled.getClass().getCanonicalName());
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
          PopupMenu popup;

          for( ViewData vd : dataList ) {
            if( vd.rowId == rowId )
              vd.select();
            else
              vd.deselect();
          }
          selectedRowId = rowId;
          selectedLapId = -1;

          if( disp.startGate && ((disp.gates.size() == 0) ||
                                 (disp.gates.size() != 0 && strike)) ) {
            // instand dialog: when only start gate
            // or not striked on linear judge
            _show_edit_dialog();
            return;
          }

          popup = new PopupMenu(MainActivity.this, v);

          if( disp.gates.size() == 0 || strike )
            return;

          // show menu only for linear judge (and not striked)

          if( disp.startGate )
            popup.getMenu().add(1, 3, 3, R.string.edit_lapcrew);
          popup.getMenu().add(1, 4, 4, R.string.set_strike);

          popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item)
            {
              switch( item.getItemId() ) {
              case 3:
                _show_edit_dialog();
                break;
              case 4:
                _show_strike_dialog();
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

          extras.putIntegerArrayList("gates", disp.gates);
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

                msg = new EventMessage.CountDownMsg(lap, disp.id, seconds * 1000);
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

      if( disp.startGate ) {
        tStart = tRow.findViewById(R.id.start_gate);
      }

      for( int i = 0; i < disp.gates.size(); i++ ) {
        TextView tGate = (TextView)_newDataCol(R.id.any_gate);

        tGate.setOnClickListener(gateListener);
        tGates.add(tGate);
      }

      if( disp.finishGate ) {
        tFinish = tRow.findViewById(R.id.finish_gate);
      }

      tRow.removeAllViews();

      tRow.addView(tSyncer);
      tRow.addView(tLap);
      tRow.addView(tCrew);
      tRow.addView(_build_spacer());

      if( tStart != null ) {
        tStart.setOnClickListener(startListener);
        tRow.addView(tStart);
        tRow.addView(_build_spacer());
      }

      for( TextView v : tGates ) {
        tRow.addView(v);
        tRow.addView(_build_spacer());
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

  public View _build_spacer()
  {
    return _newDataCol(R.id.spacer);
  }

  protected void _tableSetup()
  {
    TableLayout table = findViewById(R.id.table);
    Button disp_btn = findViewById(R.id.discipline_title);
    RaceStatus.Discipline rdisp;

    if( disp == null ||
        (rdisp = race.getDiscipline(disp.id)) == null ) {
      disp_btn.setVisibility(View.GONE);
    }
    else {
      disp_btn.setVisibility(View.VISIBLE);
      disp_btn.setText(rdisp.name);
    }

    Log.d("wsa-ng-ui", "Table setup");

    if( term == null || race == null || disp == null ) {
      Log.d("wsa-ng-ui", "Table not setuped");
      if( term == null )
        Log.d("wsa-ng-ui", "Table not setuped: term == null");

      if( disp == null )
        Log.d("wsa-ng-ui", "Table not setuped: disp == null");

      if( disp == null )
        Log.d("wsa-ng-ui", "Table not setuped: disp == null");

      return;
    }

    Log.d("wsa-ng-ui", "Table setup continue");

    dataList.clear();
    table.removeAllViews();

    Collections.sort(race.crews);

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
    header.addView(_build_spacer());

    if( disp.startGate ) {
      findViewById(R.id.new_crew).setVisibility(View.VISIBLE);
      TextView start = (TextView)_newDataCol(R.id.start_gate);
      start.setTypeface(null, Typeface.BOLD);
      header.addView(start);
      header.addView(_build_spacer());
    }
    else {
      findViewById(R.id.new_crew).setVisibility(View.GONE);
    }

    for( int i = 0; i < disp.gates.size(); i++ ) {
      TextView gate = (TextView)_newDataCol(R.id.any_gate);

      gate.setTypeface(null, Typeface.BOLD);
      gate.setText(Integer.toString(disp.gates.get(i)));
      header.addView(gate);
      header.addView(_build_spacer());
    }

    if( disp.finishGate ) {
      TextView finish = (TextView)_newDataCol(R.id.finish_gate);
      finish.setTypeface(null, Typeface.BOLD);
      header.addView(finish);
    }

    table.addView(header);

    chrono = new Chrono(MainActivity.this,
                        getSharedPreferences("chrono", Context.MODE_PRIVATE),
                        getSharedPreferences("chrono_data", Context.MODE_PRIVATE),
                        (Vibrator)getSystemService(Context.VIBRATOR_SERVICE));

    table.post(new Runnable() {
      public void run() {
        EventBus.getDefault().post(new EventMessage.Boot(disp.id));
      }
    });
  }
}
