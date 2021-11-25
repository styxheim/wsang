package ru.styxheim.wsang;

import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;

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
import java.util.Iterator;
import java.util.Arrays;

import org.greenrobot.eventbus.SubscriberExceptionEvent;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import android.widget.RelativeLayout.*;
import android.text.style.*;

import javax.sql.*;

public class MainActivity extends Activity {
  protected int lastCrewId = 0;
  protected SharedPreferences settings;
  protected SharedPreferences settingsRace;
  protected SharedPreferences settingsChrono;
  protected RaceStatus race;
  protected TerminalStatus term;
  protected boolean finishGate;
  protected StartList local_startList;

  protected boolean strict_crewslist = false;
  protected Chrono chrono;

  protected int selectedRowId = -1;
  protected int selectedLapId = -1;
  protected View selectedGate;
  protected View selectedGateHeader;

  protected ArrayList<ViewData> dataList_remote = new ArrayList<ViewData>();
  protected ArrayList<TableData> tableList_remote = new ArrayList<TableData>();
  protected ArrayList<ViewData> dataList_local = new ArrayList<ViewData>();
  protected ArrayList<TableData> tableList_local = new ArrayList<TableData>();


  protected boolean countDownMode = false;
  protected long countDownLap;
  protected long countDownEndAt;
  protected long countDownStartAt;

  public void g(String format, Object... args) {
    Toast.makeText(this, String.format(format, args), Toast.LENGTH_LONG).show();
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onEvent(SubscriberExceptionEvent exceptionEvent) {
    Log.e("wsa-ng-ui", "Catch exception");
    StringWriter psw = new StringWriter();
    PrintWriter pw = new PrintWriter(psw);

    exceptionEvent.throwable.printStackTrace(pw);

    Log.e("wsa-ng-ui", "Exception: " + psw.toString());

    Toast.makeText(MainActivity.this,
        "Look to logcat 'wsa-ng-ui'", Toast.LENGTH_SHORT).show();
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    settings = getSharedPreferences("main", Context.MODE_PRIVATE);
    settingsRace = getSharedPreferences("race", Context.MODE_PRIVATE);
    settingsChrono = getSharedPreferences("chrono", Context.MODE_PRIVATE);
    setContentView(R.layout.main);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      setShowWhenLocked(true);
    } else {
      this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
    }

    local_startList = new StartList();
    local_startList.setOutput("localRows.json");
    local_startList.Load(this);
  }

  @Override
  public void onStart() {
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

        if (current > countDownEndAt) {
          if (current < countDownEndAt + 1000) {
            tv.setText(Default.millisecondsToStringShort(0));
            tv.setBackgroundResource(R.color.selected_row);
          } else {
            tv.setBackground(drw);
          }
        }

        if (countDownMode) {
          if (current > countDownEndAt) {
            _disableCountDownMode();
          } else {
            tv.setText(Default.millisecondsToStringShort(countDownEndAt - current + 1000));
            pb.setProgress((int) (countDownEndAt - current));
          }
        } else {
          tv.setText(Default.millisecondsToStringShort(current - offsetMillis));
        }
        tv.postDelayed(this, 300);
      }
    };

    tv.post(cron);

    findViewById(R.id.settings_button).setEnabled(true);

    boot();
  }

  protected void boot() {
    /* FIXME: sleep for wait service - bad idea */
    findViewById(R.id.bottom_spacer).postDelayed(new Runnable() {
      public void run() {
        EventMessage.Boot boot_msg;

        boot_msg = new EventMessage.Boot();
        EventBus.getDefault().post(boot_msg);
      }
    }, 500);
  }

  @Override
  public void onStop() {
    EventBus.getDefault().unregister(this);
    super.onStop();
  }

  public void disciplineOnClick(View v) {
    /* TODO: to remove */
  }

  public void settingsOnClick(View v) {
    v.setEnabled(false);

    Intent intent = new Intent(this, SettingsActivity.class);
    startActivity(intent);
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (chrono != null && finishGate) {
      if (chrono.onKeyDown(keyCode, event))
        return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  public void cancelOnClick(View v) {
    EventBus.getDefault().post(new EventMessage(EventMessage.EventType.COUNTDOWN_STOP, null));
  }

  protected int _fillDisciplinesArray(ArrayList<Integer> disp_ids, ArrayList<String> disp_names,
                                      int lastDisciplineId) {
    int selectedDisciplineNum = -1;
    for (TerminalStatus.Discipline tdisp : term.disciplines) {
      if (tdisp.startGate) {
        RaceStatus.Discipline rdisp = race.getDiscipline(tdisp.id);
        if (lastDisciplineId == tdisp.id)
          selectedDisciplineNum = disp_ids.size();
        disp_ids.add(tdisp.id);
        if (rdisp != null) {
          disp_names.add(rdisp.name);
        } else {
          disp_names.add(String.format("id %d", tdisp.id));
        }
      }
    }

    if (selectedDisciplineNum == -1 && race.disciplines.size() > 1) {
      selectedDisciplineNum = 0;
    }

    return selectedDisciplineNum;
  }

  protected int _fillDisciplinesArrayLocal(ArrayList<Integer> disp_ids, ArrayList<String> disp_names,
                                           int lastDisciplineId) {
    int selectedDisciplineNum = -1;
    for (TerminalStatus.Discipline tdisp : term.disciplines) {
      if (!tdisp.startGate &&
          (tdisp.gates.size() != 0 || tdisp.finishGate)) {
        RaceStatus.Discipline rdisp = race.getDiscipline(tdisp.id);
        if (lastDisciplineId == tdisp.id)
          selectedDisciplineNum = disp_ids.size();
        disp_ids.add(tdisp.id);
        if (rdisp != null) {
          disp_names.add(rdisp.name);
        } else {
          disp_names.add(String.format("id %d", tdisp.id));
        }
      }
    }

    if (selectedDisciplineNum == -1 && race.disciplines.size() > 1) {
      selectedDisciplineNum = 0;
    }

    return selectedDisciplineNum;
  }

  public void startLocalOnClick(View v) {
    StartLineEditDialog sled;
    final ArrayList<Integer> lap_values = new ArrayList<Integer>();
    final ArrayList<String> disp_names = new ArrayList<String>();
    final ArrayList<Integer> disp_ids = new ArrayList<Integer>();
    int lastDisciplineId = -1;
    int selectedDisciplineNum = 0;

    if (dataList_local.size() > 0) {
      ViewData vd = dataList_local.get(dataList_local.size() - 1);
      lastDisciplineId = vd.disciplineId;
    } else if (dataList_remote.size() > 0) {
      ViewData vd = dataList_remote.get(dataList_remote.size() - 1);
      lastDisciplineId = vd.disciplineId;
    }

    selectedDisciplineNum = _fillDisciplinesArrayLocal(disp_ids, disp_names, lastDisciplineId);

    lap_values.add(0);
    sled = new StartLineEditDialog(-1, -1, selectedDisciplineNum, false);
    sled.setLapValues(lap_values);
    sled.setDisciplines(disp_names);
    sled.setStartLineEditDialogListener(new StartLineEditDialog.StartLineEditDialogListener() {
      @Override
      public void onStartLineEditDialogResult(StartLineEditDialog sled, int crewNum, int lapNum, int disciplineNum) {
        StartRow row = local_startList.addRecord(crewNum, 0, disp_ids.get(disciplineNum));
        row.setState(StartRow.SyncState.ERROR);
        local_startList.Save(MainActivity.this);
        _localTableLoad();
        _scrollToBottom(dataList_local);
      }
    });
    sled.show(getFragmentManager(), sled.getClass().getCanonicalName());
  }

  public void startOnClick(View v) {
    StartLineEditDialog sled;
    final ArrayList<Integer> lap_values = new ArrayList<Integer>();
    final ArrayList<String> disp_names = new ArrayList<String>();
    final ArrayList<Integer> disp_ids = new ArrayList<Integer>();
    ViewData vd;
    int lastLapId = 0;
    int lastDisciplineId = -1;
    int selectedDisciplineNum = 0;

    if (race == null)
      Log.e("wsa-ng-ui", "RaceStatus is empty, cannot add new row");
    if (term == null)
      Log.e("wsa-ng-ui", "TerminalStatus is empty, cannot add new row");

    if (term == null || race == null) {
      Toast.makeText(MainActivity.this,
          "Look to logcat 'wsa-ng-ui'",
          Toast.LENGTH_SHORT).show();
      return;
    }

    if (dataList_remote.size() > 0) {
      vd = dataList_remote.get(dataList_remote.size() - 1);
      lastLapId = vd.lap;
      lastDisciplineId = vd.disciplineId;
      // allow attach to last lap in 2 cases:
      // lap not started
      // parallel start is allowed: TODO
      if (vd.start == 0) {
        lap_values.add(vd.lap);
      }
    }

    lap_values.add(lastLapId + 1);

    /* fill disciplines */
    selectedDisciplineNum = _fillDisciplinesArray(disp_ids, disp_names, lastDisciplineId);

    if (race.crews.size() != 0) {
      sled = new StartLineEditDialog(-1, lap_values.size() - 1, selectedDisciplineNum);
      sled.setCrewValues(race.crews);
    } else {
      sled = new StartLineEditDialog(this.lastCrewId + 1, lap_values.size() - 1, selectedDisciplineNum);
    }

    Log.d("wsa-ng-ui", "show sled");
    sled.setLapValues(lap_values);
    sled.setDisciplines(disp_names);
    sled.setStartLineEditDialogListener(new StartLineEditDialog.StartLineEditDialogListener() {
      @Override
      public void onStartLineEditDialogResult(StartLineEditDialog sled, int crewNum, int lapNum, int dispNum) {
        EventMessage.ProposeMsg req;
        int crewId;
        int lapId = lap_values.get(lapNum);
        int disciplineId = disp_ids.get(dispNum);

        if (race.crews.size() != 0)
          crewId = race.crews.get(crewNum);
        else
          crewId = crewNum;

        lastCrewId = crewId;

        req = new EventMessage.ProposeMsg(crewId, lapId, disciplineId);

        Log.d("wsa-ng-ui", "Propose new: crew=" + Integer.toString(crewId) +
            " lap=" + Integer.toString(lapId) +
            " discipline=" + Integer.toString(disciplineId));
        EventBus.getDefault().post(new EventMessage(req));
      }
    });
    sled.show(getFragmentManager(), sled.getClass().getCanonicalName());
  }

  protected TableData _getTableDataByDisciplineId(ArrayList<TableData> tableList,
                                                  LinearLayout tableListLayout,
                                                  int disciplineId) {
    TableData td = null;
    /* get and check last table data by disciplineId */
    if (tableList.size() > 0) {
      td = tableList.get(tableList.size() - 1);
      if (td.disciplineId != disciplineId) {
        td = null;
      }
      // FIXME: this code make load slowly
      for (TableData ctd : tableList) {
        if (td == ctd)
          break;
        if (ctd.disciplineId == disciplineId) {
          if (ctd.tableDataList.size() == 0) {
            tableList.remove(ctd);
            tableListLayout.removeView(ctd.layout);
          }
          break;
        }
      }
    }

    return td;
  }

  protected ViewData _getViewDataById(ArrayList<ViewData> dataList, int rowId) {
    for (ViewData vd : dataList) {
      if (vd.rowId == rowId)
        return vd;
    }

    return null;
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void _event_receive(EventMessage.RSyncResult rstatus) {
    final View errorView = findViewById(R.id.error_layout);
    final TextView errorText = findViewById(R.id.error_text);
    final ServerStatus status = rstatus.serverStatus;

    if ((status != null && status.error == null) || status == null) {
      errorView.setVisibility(View.GONE);
      return;
    }

    errorText.setText(status.error.text);
    errorView.setVisibility(View.VISIBLE);
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void _event_terminalStatus(TerminalStatus new_term) {
    Log.d("wsa-ng-ui", "Receive new TerminalStatus: " + new_term.toString());
    if (term == null || term.timestamp != new_term.timestamp) {
      Log.d("wsa-ng-ui", "Apply new TerminalStatus");
      term = new_term;

      _buttonsSetup();
      _tablesSetup();
      _zeroTablesSetup();
      _localTableLoad();
    }
  }

  protected void _zeroTablesSetup() {
    TableData td;
    LinearLayout tableListLayout = findViewById(R.id.table_list);

    if (race == null || term == null)
      return;

    for (int i = 0; i < race.disciplines.size(); i++) {
      boolean found = false;
      RaceStatus.Discipline rdisp = race.disciplines.get(i);

      for (TableData _td : tableList_remote) {
        if (rdisp.id == _td.disciplineId) {
          found = true;
        }
      }
      if (!found) {
        td = new TableData(rdisp.id, tableList_remote);
        tableList_remote.add(td);
        tableListLayout.addView(td.getView());
      }
    }
  }

  protected void _localTableLoad() {
    LinearLayout tableListLayout = findViewById(R.id.table_list_local);

    if (race == null || term == null)
      return;

    for (StartRow row : local_startList) {
      _update_StartRow_fast(row, tableListLayout, dataList_local, tableList_local, true);
    }

    if (tableList_local.size() == 0) {
      findViewById(R.id.notebook_title).setVisibility(View.GONE);
    } else {
      findViewById(R.id.notebook_title).setVisibility(View.VISIBLE);
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void _event_raceStatus(RaceStatus new_race) {
    boolean race_is_chanded = false;

    Log.d("wsa-ng-ui", "Receive new RaceStatus");
    if (race == null || (race.timestamp != new_race.timestamp ||
        race.competitionId != new_race.competitionId)) {
      Log.d("wsa-ng-ui", "Apply new RaceStatus");
      if (race != null) {
        if (!race.gates.equals(new_race.gates)) {
          race_is_chanded = true;
        }
      }

      if (!race_is_chanded) {
        race = new_race;
        _buttonsSetup();
        _tablesSetup();
        _zeroTablesSetup();
        _localTableLoad();

        if (chrono != null)
          chrono.reload();
      } else {
        race = null;
        ((ViewGroup) findViewById(R.id.table_list)).removeAllViews();
        ((ViewGroup) findViewById(R.id.table_list_local)).removeAllViews();
        dataList_remote.clear();
        tableList_remote.clear();
        dataList_local.clear();
        tableList_local.clear();
        boot();
      }
    }
  }

  protected void _scrollToBottom(final ArrayList<ViewData> vdl) {
    final View sv = findViewById(R.id.vscroll);

    if (vdl.size() != 0) {
      sv.post(new Runnable() {
        public void run() {
          View v = vdl.get(vdl.size() - 1).tRow;
          int toTop = 0;

          do {
            toTop += v.getTop();
          } while ((v = (View) v.getParent()) != sv);

          sv.scrollTo(0, toTop);
        }
      });
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void _event_StartRowList(final ArrayList<StartRow> rows) {
    final LinearLayout load = findViewById(R.id.load_container);
    final TextView load_text = ((ViewGroup) load).findViewById(R.id.load_title);
    final LinearLayout view = findViewById(R.id.scroll_container);
    final Button settings_btn = findViewById(R.id.settings_button);
    int to_insert = 0;

    if (race == null || term == null)
      return;

    for (StartRow row : rows) {
      if (_getViewDataById(dataList_remote, row.getRowId()) == null) {
        to_insert++;
      }
    }

    Log.d("wsa-ng-ui", String.format("Update %d rows, to insert: %d",
        rows.size(), to_insert));

    final long bulk_load_start = System.currentTimeMillis();
    final Iterator<StartRow> iter = rows.iterator();
    final boolean scrollToBottom = (to_insert != 0);
    final LinearLayout tableListLayout = findViewById(R.id.table_list);
    final int to_insert_f = to_insert;

    Runnable upd = new Runnable() {
      private int count = 0;

      public void run() {
        for (int i = 0; i < 13; i++) {
          if (iter.hasNext()) {
            StartRow nrow = iter.next();

            _update_StartRow_fast(nrow, tableListLayout, dataList_remote, tableList_remote, false);
          } else {
            long bulk_load_time = System.currentTimeMillis() - bulk_load_start;
            long load_ms = bulk_load_time % 1000;
            long load_seconds = bulk_load_time / 1000;

            load.setVisibility(View.GONE);
            view.setVisibility(View.VISIBLE);
            settings_btn.setEnabled(true);
            if (scrollToBottom) {
              _scrollToBottom(dataList_remote);
            }
            if (rows.size() > 13) {
              g("Bulk load: %d.%ds (%d all, %d new)",
                  load_seconds, load_ms, rows.size(), to_insert_f);
            }
            return;
          }
          count++;
        }
        load_text.setText(String.format("Loading %d/%d",
            count,
            rows.size()));
        load_text.post(this);
      }
    };

    settings_btn.setEnabled(false);

    if (to_insert > 3) {
      view.setVisibility(View.GONE);
      load.setVisibility(View.VISIBLE);
      load_text.setText(String.format("Loading %d lines...", to_insert));
    }

    upd.run();
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void _update_StartRow(StartRow row) {
    final LinearLayout tableListLayout = findViewById(R.id.table_list);
    boolean scrollToBottom = (_getViewDataById(dataList_remote, row.getRowId()) == null);

    if (race == null || term == null)
      return;

    _update_StartRow_fast(row, tableListLayout, dataList_remote, tableList_remote, false);
    if (scrollToBottom) {
      _scrollToBottom(dataList_remote);
    }
  }

  protected void _update_StartRow_fast(StartRow row,
                                       final LinearLayout tableListLayout,
                                       ArrayList<ViewData> dataList,
                                       ArrayList<TableData> tableList,
                                       boolean is_local) {
    /* Update or add new data */
    ViewData vd = _getViewDataById(dataList, row.getRowId());

    Log.d("wsa-ng-ui",
        "got " + row.toString() +
            ", visible=" + (vd == null ? "false" : "true"));

    if (vd == null) {
      /* try to add new row */
      TableData td = _getTableDataByDisciplineId(tableList,
          tableListLayout,
          row.disciplineId);

      if (td == null) {
        /* setup new table */
        td = new TableData(row.disciplineId, tableList);
        tableList.add(td);
        tableListLayout.addView(td.getView());
      }

      vd = new ViewData(row.getRowId(), row.disciplineId, td, dataList, is_local);
      td.addData(vd);
      dataList.add(vd);
    }

    /* auto-confirm */
    if (!row.isQueueEmpty() &&
        row.state == StartRow.SyncState.NONE) {
      EventMessage.ProposeMsg confirm;

      confirm = new EventMessage.ProposeMsg(EventMessage.ProposeMsg.Type.CONFIRM);
      confirm.setRowId(row.getRowId());
      EventBus.getDefault().post(new EventMessage(EventMessage.EventType.PROPOSE, confirm));
    }

    vd.update(row);
  }

  protected void _enableCountDownMode(int lapId, long startAt, long endAt) {
    ProgressBar pb = findViewById(R.id.start_progress);
    TextView chronometer = findViewById(R.id.chronometer);

    if (countDownMode)
      return;

    countDownMode = true;
    countDownLap = lapId;
    countDownEndAt = endAt;
    countDownStartAt = startAt;
    if (VERSION.SDK_INT >= VERSION_CODES.O) {
      pb.setMin(0);
    }
    pb.setMax((int) (endAt - startAt));
    pb.setProgress(0);

    findViewById(R.id.countdown_cancel_button).setVisibility(View.VISIBLE);
    findViewById(R.id.settings_button).setVisibility(View.GONE);
    chronometer.setTypeface(null, Typeface.BOLD);
  }

  protected void _disableCountDownMode() {
    ProgressBar pb = findViewById(R.id.start_progress);
    TextView chronometer = findViewById(R.id.chronometer);

    countDownMode = false;
    pb.setProgress(0);

    findViewById(R.id.countdown_cancel_button).setVisibility(View.GONE);
    findViewById(R.id.settings_button).setVisibility(View.VISIBLE);
    chronometer.setTypeface(null, Typeface.NORMAL);
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void _event_countdown(EventMessage.CountDownMsg msg) {
    _enableCountDownMode(msg.lapId, msg.startAt, msg.endAtMs);
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void _event_countDownCanceled(EventMessage.CountDownCancelled msg) {
    _disableCountDownMode();
  }

  private Boolean isGateIdPermittedForTerminal(Integer gateId,
                                               RaceStatus.Discipline rdisp,
                                               TerminalStatus.Discipline tdisp) {
    for (int rgateId : rdisp.gates) {
      if (gateId.compareTo(rgateId) == 0) {
        for (int tgateId : tdisp.gates) {
          if (gateId.compareTo(tgateId) == 0) {
            return true;
          }
        }
        break;
      }
    }

    return false;
  }

  private class TableData {
    public int disciplineId;
    public ArrayList<ViewData> tableDataList = new ArrayList<ViewData>();
    LinearLayout layout;
    TableLayout table_layout;
    TableRow header;
    public int tableId;
    public TextView title;
    String disp_name;
    boolean hidden = false;
    ArrayList<TableData> tableList;

    public TableData(int disciplineId, ArrayList<TableData> tableList) {
      this.disciplineId = disciplineId;
      this.tableList = tableList;
    }

    public View getView() {
      table_layout = new TableLayout(MainActivity.this);
      header = (TableRow) _newDataCol(R.layout.data_row);
      TextView anyGate;
      int index;
      title = (TextView) LayoutInflater.from(MainActivity.this).inflate(R.layout.table_title, null);

      tableId = View.generateViewId();
      table_layout.setId(tableId);
      layout = new LinearLayout(MainActivity.this);
      layout.setOrientation(LinearLayout.VERTICAL);
      layout.addView(title);
      layout.addView(table_layout);

      /* add table header */
      header.findViewById(R.id.start_gate).setTag(R.id.tag_gate_id, RaceStatus.GATE_START);
      header.findViewById(R.id.finish_gate).setTag(R.id.tag_gate_id, RaceStatus.GATE_FINISH);
      index = header.indexOfChild((View) header.findViewById(R.id.finish_gate)) - 1;
      anyGate = header.findViewById(R.id.any_gate);
      header.removeView(anyGate);
      for (int gateId : race.gates) {
        anyGate = (TextView) _newDataCol(R.id.any_gate);
        anyGate.setText(Integer.toString(gateId));
        anyGate.setTag(R.id.tag_gate_id, gateId);
        anyGate.setTag(R.id.tag_selected, false);
        header.addView(anyGate, index);
        index++;
      }
      table_layout.addView(header);

      header.setBackgroundResource(R.color.rowHeader);
      //title.setBackgroundResource(R.color.rowHeader);
      title.setOnClickListener(new TextView.OnClickListener() {
        @Override
        public void onClick(View v) {
          int size = tableList.size();
          if (size > 1 && tableList.get(size - 1) != TableData.this) {
            toggleHide();
          }
        }
      });

      update();
      return layout;
    }

    public void toggleHide() {
      hidden = !hidden;
      table_layout.setVisibility(b2v(!hidden));
      table_layout.post(new Runnable() {
        public void run() {
          _updateTitle();
        }
      });
    }

    private int b2v(boolean val) {
      if (!val)
        return View.GONE;
      return View.VISIBLE;
    }

    protected void _updateTitle() {
      String title_text = disp_name;
      if (hidden) {
        title_text += String.format(" (%d)", tableDataList.size());
      }
      title.setText(title_text);
    }

    public View getGateView(int gateId) {
      for (int i = 0; i < header.getChildCount(); i++) {
        View v = header.getChildAt(i);
        if (v instanceof TextView) {
          Integer _gateId = (Integer) v.getTag(R.id.tag_gate_id);

          if (_gateId == null)
            continue;

          if (_gateId.compareTo(gateId) == 0) {
            return v;
          }
        }
      }
      return null;
    }

    public void update() {
      RaceStatus.Discipline rdisp = null;
      TerminalStatus.Discipline tdisp = null;

      if (term != null)
        tdisp = term.getDiscipline(disciplineId);

      if (race != null)
        rdisp = race.getDiscipline(disciplineId);

      if (rdisp == null) {
        disp_name = "discipline id #" + Integer.toString(disciplineId);
      } else {
        disp_name = rdisp.name;
      }
      _updateTitle();

      if (tdisp == null)
        return;

      for (int i = 0; i < header.getChildCount(); i++) {
        View v = header.getChildAt(i);
        if (v instanceof TextView) {
          boolean found = false;
          Integer gateId = (Integer) v.getTag(R.id.tag_gate_id);
          ((TextView) v).setTypeface(null, Typeface.BOLD);

          if (gateId == null)
            continue;

          if (gateId.compareTo(RaceStatus.GATE_START) == 0) {
            v.setVisibility(b2v(tdisp.startGate));
            continue;
          }

          if (gateId.compareTo(RaceStatus.GATE_FINISH) == 0) {
            v.setVisibility(b2v(tdisp.finishGate));
            continue;
          }

          v.setVisibility(b2v(isGateIdPermittedForTerminal(gateId, rdisp, tdisp)));
        }
      }
    }

    public void _remove() {
      for (ViewData vd : tableDataList) {
        vd._remove();
      }
      tableList.remove(this);
      ((ViewGroup) layout.getParent()).removeView(layout);
    }

    public void removeData(ViewData vd) {
      tableDataList.remove(vd);
      if (table_layout != null) {
        table_layout.removeView(vd.getView());
      }
      if (tableDataList.size() == 0) {
        _remove();
      }
    }

    public void addData(ViewData vd) {
      tableDataList.add(vd);
      if (table_layout != null) {
        table_layout.addView(vd.getView());
      }
      if (hidden) {
        toggleHide();
      }
    }
  }

  private class ViewData {
    public int rowId;

    public StartRow.SyncState state;
    public int lap;
    public int disciplineId;
    public int crew;
    public long finish;
    public long start;
    public boolean strike;
    public int[] gates;
    public TableData parent;
    public ArrayList<ViewData> dataList;

    protected Context context;
    public boolean is_local = false;

    protected View tSyncer;
    protected TableRow tRow;
    protected TextView tLap;
    protected TextView tCrew;
    protected TextView tStart;
    protected TextView tFinish;
    protected TextView[] tGates;

    public ViewData(int id, int disciplineId,
                    TableData td,
                    ArrayList<ViewData> dataList,
                    boolean is_local) {
      this.rowId = id;
      this.disciplineId = disciplineId;
      this.context = MainActivity.this;
      this.parent = td;
      this.dataList = dataList;
      this.is_local = is_local;
    }

    public void select(final View v, final int resource) {
      boolean selected = (boolean) v.getTag(R.id.tag_selected);

      if (selected)
        return;

      v.setTag(R.id.tag_selected, true);
      v.setTag(R.id.tag_background, v.getBackground());
      v.setBackgroundResource(resource);
    }

    public void deselect(final View v) {
      boolean selected = (boolean) v.getTag(R.id.tag_selected);

      if (!selected)
        return;

      v.setTag(R.id.tag_selected, false);
      v.setBackground((Drawable) v.getTag(R.id.tag_background));
    }

    protected void _strikeTextView(TextView v) {
      if (strike) {
        v.setPaintFlags(v.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        v.setTextColor(R.color.strike_text);
      } else {
        v.setPaintFlags(v.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
      }
    }

    protected String numpad3(int n) {
      if (n < 10) {
        return String.format(" %d ", n);
      } else if (n < 100) {
        return String.format(" %d", n);
      }
      return Integer.toString(n);
    }

    protected void _update() {
      switch (state) {
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

      tLap.setText(numpad3(lap));
      _strikeTextView(tLap);

      tCrew.setText(numpad3(crew));
      _strikeTextView(tCrew);

      tStart.setText(Default.millisecondsToString(start));
      _strikeTextView(tStart);

      for (int i = 0; i < gates.length; i++) {
        int gatePenaltyId = gates[i];
        int pvalue = 0;
        TextView tGate = tGates[i];

        _strikeTextView(tGate);
        if (gatePenaltyId >= race.penalties.size() || gatePenaltyId < 0)
          Log.e("wsa-ng-ui", String.format("Invalid penalty id#%d", gatePenaltyId));
        else
          pvalue = race.penalties.get(gatePenaltyId);

        tGate.setTag(R.id.tag_gate_value, gatePenaltyId);
        if (gatePenaltyId == 0)
          tGate.setText("  ");
        else
          tGate.setText(Integer.toString(pvalue));
      }

      String finishText = Default.millisecondsToString(finish);
      if (finish > start || finish == 0) {
        finishText = "⚠ " + finishText;
      }
      tFinish.setText(finishText);
      _strikeTextView(tFinish);
    }

    public void update(StartRow row) {
      Chrono.Record r;

      if (chrono != null && finish != row.finishAt) {
        if ((r = chrono.getRecord(finish)) != null)
          r.deselect();
        if ((r = chrono.getRecord(row.finishAt)) != null)
          r.select();
      }

      state = row.state;
      lap = row.lapId;
      crew = row.crewId;
      finish = row.finishAt;
      start = row.startAt;
      strike = row.strike;
      disciplineId = row.disciplineId;

      gates = new int[race.gates.size()];
      for (int i = 0; i < gates.length; i++) {
        int gateId = race.gates.get(i);
        for (StartRow.Gate lgate : row.gates) {
          if (lgate.gate == gateId) {
            gates[i] = lgate.penalty;
            break;
          }
        }
      }

      if (tRow != null) {
        tRow.post(new Runnable() {
          public void run() {
            if (selectedRowId == rowId || selectedLapId == lap) {
              select(tRow, R.color.selected_row);
            } else {
              deselect(tRow);
            }

            _update();
          }
        });
      }
    }

    protected void _deselectGate() {
      if (selectedGate != null) {
        deselect(selectedGate);
        selectedGate = null;
      }
      if (selectedGateHeader != null) {
        deselect(selectedGateHeader);
        selectedGateHeader = null;
      }
    }

    protected void _selectByLapId() {
      for (ViewData vd : dataList) {
        if (vd.lap == lap) {
          vd.select(vd.tRow, R.color.selected_row);
        } else {
          vd.deselect(vd.tRow);
        }
      }
      selectedRowId = -1;
      selectedLapId = lap;

      _deselectGate();
    }

    protected void _selectByRowId() {
      for (ViewData vd : dataList) {
        if (vd.rowId == rowId)
          vd.select(vd.tRow, R.color.selected_row);
        else
          vd.deselect(vd.tRow);
      }
      selectedRowId = rowId;
      selectedLapId = -1;

      _deselectGate();
    }

    protected void _selectByGateId(final View v, final int gateId) {
      final View vh = parent.getGateView(gateId);

      _selectByRowId();
      v.post(new Runnable() {
        public void run() {
          selectedGate = v;
          select(v, R.color.selected_col);

          selectedGateHeader = vh;
          select(vh, R.color.selected_col);
        }
      });
    }

    public void _show_row_remove_dialog() {
      if (!is_local)
        return;

      AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
      builder.setMessage("Удалить запись?");
      builder.setPositiveButton("Да", new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int id) {
          _remove();
        }
      });
      builder.setNegativeButton("Нет", new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int id) {
        }
      });
      builder.create().show();
    }

    public void _remove() {
      Chrono.Record r;

      if (!is_local)
        return;

      parent.removeData(this);
      dataList.remove(this);

      local_startList.removeRecord(rowId);
      local_startList.Save(getApplicationContext());

      if (chrono != null && finish != 0) {
        if ((r = chrono.getRecord(finish)) != null)
          r.deselect();
      }

      _localTableLoad();
    }

    protected void _onLapCrewClick(View v) {
      TerminalStatus.Discipline disp = term.getDiscipline(disciplineId);
      PopupMenu popup;

      _selectByRowId();

      if (!is_local && disp.startGate && ((disp.gates.size() == 0) ||
          (disp.gates.size() != 0 && strike))) {
        // instand dialog: when only start gate
        // or not striked on linear judge
        _show_edit_dialog();
        return;
      }

      popup = new PopupMenu(MainActivity.this, v);

      if (disp.startGate || is_local) {
        popup.getMenu().add(1, 3, 3, R.string.edit_lapcrew);
      }
      if (!strike) {
        popup.getMenu().add(1, 4, 4, R.string.set_strike);
      } else {
        popup.getMenu().add(1, 5, 5, R.string.set_unstrike);
      }

      if (is_local) {
        popup.getMenu().add(1, 6, 6, R.string.remove_row);
        popup.getMenu().add(1, 7, 7, R.string.merge_row);
      }

      popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem item) {
          switch (item.getItemId()) {
            case 3:
              _show_edit_dialog();
              break;
            case 4:
              _show_strike_dialog(true);
              break;
            case 6:
              _show_row_remove_dialog();
              break;
            case 5:
              _show_strike_dialog(false);
              break;
            case 7:
              _show_merge_dialog();
              break;
            default:
              return false;
          }
          return true;
        }
      });

      popup.show();
    }

    protected void _onFinishClick(View v) {
      int i = 0;
      int size = chrono.getSize();
      long offset = 0;
      PopupMenu pmenu = new PopupMenu(MainActivity.this, v);

      _selectByRowId();

      if (finish == 0) {
        if (size == 0) {
          Toast.makeText(MainActivity.this,
              "Используйте кнопку секундомера для отсечки времени",
              Toast.LENGTH_SHORT).show();
          return;
        }

        for (Chrono.Record r : chrono) {
          String title;

          if (i == 0) {
            offset = r.getValue();
          }
          title = String.format("%2d. %s %s%s",
              size - i,
              Default.millisecondsToString(r.getValue()),
              ((offset >= r.getValue()) ? ("+") : ("-")),
              Default.millisecondsToString(offset - r.getValue()));

          pmenu.getMenu().add(1, i, i, title);
          if (r.isSelected()) {
            pmenu.getMenu().getItem(i).setEnabled(false);
          }

          i++;
        }
      } else {
        pmenu.getMenu().add(1, 1, 1, "Отменить финиш");
      }

      pmenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem item) {
          if (finish == 0) {
            Chrono.Record r = chrono.getRecord(item.getItemId());
            EventMessage.ProposeMsg req;

            if (r == null)
              return false;

            if (!is_local) {
              req = new EventMessage.ProposeMsg(r.getValue(), EventMessage.ProposeMsg.Type.FINISH);
              req.setRowId(rowId);
              EventBus.getDefault().post(new EventMessage(EventMessage.EventType.PROPOSE, req));
            } else {
              StartRow row = local_startList.getRecord(rowId);
              row.setFinishData(r.getValue());
              row.setState(StartRow.SyncState.ERROR);
              local_startList.Save(getApplicationContext());
              _localTableLoad();
            }
          } else {
            switch (item.getItemId()) {
              case 1:
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setMessage("Обнулить финишное время?");
                builder.setPositiveButton("Да", new DialogInterface.OnClickListener() {
                  @Override
                  public void onClick(DialogInterface dialog, int id) {
                    if (!is_local) {
                      EventMessage.ProposeMsg req;

                      req = new EventMessage.ProposeMsg(0, EventMessage.ProposeMsg.Type.FINISH);
                      req.setRowId(rowId);
                      EventBus.getDefault().post(new EventMessage(EventMessage.EventType.PROPOSE, req));
                    } else {
                      StartRow row = local_startList.getRecord(rowId);
                      row.setFinishData(0);
                      row.setState(StartRow.SyncState.ERROR);
                      local_startList.Save(getApplicationContext());
                      _localTableLoad();
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
            }
          }

          return true;
        }
      });
      pmenu.show();
    }

    protected void _onStartClick(View v) {
      PopupMenu popup = new PopupMenu(MainActivity.this, v);

      _selectByLapId();

      if (is_local) {
        /* local data has no `Start Time` field because this terminal
         * can create new rows at network layer
         */
        return;
      }

      if (start != 0) {
        /* reset start time */
        popup.getMenu().add(1, 3, 3, R.string.false_start);
      } else if (!countDownMode) {
        popup.getMenu().add(1, 10, 10, R.string.ten_seconds_button);
        popup.getMenu().add(1, 30, 30, R.string.thirty_seconds_button);
        popup.getMenu().add(1, 60, 60, R.string.sixty_seconds_button);
      }

      if ((countDownMode && (countDownLap == lap || start == 0))) {
        popup.getMenu().add(1, 1, 1, R.string.start_cancel_stop);
      }

      popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem item) {
          AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);

          switch (item.getItemId()) {
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
                  for (ViewData vd : dataList) {
                    if (vd.lap == lap) {
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

              msg = new EventMessage.CountDownMsg(lap, disciplineId, seconds * 1000);
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

    protected void _setupGateListener(View v) {
      /*
      View.OnClickListener gate2activityListener = new View.OnClickListener() {
        @Override
        public void onClick(View v)
        {
          Bundle extras = new Bundle();
          Intent intent = new Intent(MainActivity.this, PenaltyActivity.class);
          TerminalStatus.Discipline disp = term.getDiscipline(disciplineId);

          _selectByRowId();

          extras.putInt("rowId", rowId);
          extras.putInt("lap", lap);
          extras.putInt("crew", crew);

          extras.putIntegerArrayList("gates", disp.gates);
          extras.putIntegerArrayList("penalties", race.penalties);
          extras.putIntArray("values", gates);

          extras.putLong("term_timestamp", term.timestamp);
          extras.putLong("race_timestamp", race.timestamp);

          intent.putExtras(extras);
          startActivity(intent);
        }
      };
      v.setOnClickListener(gate2activityListener);
      */
      View.OnClickListener gate2menuListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          final int gateId = (int) v.getTag(R.id.tag_gate_id);
          PopupMenu popup = new PopupMenu(MainActivity.this, v);

          if (((Integer) v.getTag(R.id.tag_gate_value)).compareTo(0) == 0) {
            for (int i = 1; i < race.penalties.size(); i++) {
              popup.getMenu().add(1, i, i, Integer.toString(race.penalties.get(i)));
            }
          } else {
            popup.getMenu().add(1, 0, 0, "Отменить");
          }
          popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
              final EventMessage.ProposeMsg msg;
              int penaltyId = item.getItemId();

              Log.d("wsa-ng", String.format("Emit Penalty gate %d, value %d (%d)",
                  gateId,
                  item.getItemId(),
                  race.penalties.get(item.getItemId())));
              msg = new EventMessage.ProposeMsg(EventMessage.ProposeMsg.Type.PENALTY);
              msg.rowId = rowId;
              msg.gate = gateId;
              msg.penalty = penaltyId;
              if (penaltyId == 0) {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);

                builder.setTitle(R.string.false_start);
                builder.setMessage("Отменить результат?");
                builder.setPositiveButton("Да", new DialogInterface.OnClickListener() {
                  @Override
                  public void onClick(DialogInterface dialog, int id) {
                    if (!is_local) {
                      EventBus.getDefault().post(new EventMessage(msg));
                    } else {
                      StartRow row = local_startList.getRecord(rowId);
                      row.setGateData(msg.gate, msg.penalty);
                      row.setState(StartRow.SyncState.ERROR);
                      local_startList.Save(getApplicationContext());
                      _localTableLoad();
                    }
                  }
                });
                builder.setNegativeButton("Нет", new DialogInterface.OnClickListener() {
                  @Override
                  public void onClick(DialogInterface dialog, int id) {
                  }
                });
                builder.create().show();
              } else {
                if (!is_local) {
                  EventBus.getDefault().post(new EventMessage(msg));
                } else {
                  StartRow row = local_startList.getRecord(rowId);
                  row.setGateData(msg.gate, msg.penalty);
                  row.setState(StartRow.SyncState.ERROR);
                  local_startList.Save(getApplicationContext());
                  _localTableLoad();
                }
              }
              return true;
            }
          });
          _selectByGateId(v, gateId);
          popup.show();
        }
      };
      v.setOnClickListener(gate2menuListener);
    }

    protected void _show_strike_dialog(final boolean striked) {
      AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
      if (striked) {
        builder.setMessage(String.format("Закрыть заезд %d экипажа %d?", lap, crew));
      } else {
        builder.setMessage(String.format("Открыть заезд %d экипажа %d?", lap, crew));
      }
      builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int id) {
          if (!is_local) {
            EventMessage.ProposeMsg req;

            req = new EventMessage.ProposeMsg().setRowId(rowId).setStrike(striked);
            EventBus.getDefault().post(new EventMessage(req));
          } else {
            StartRow row = local_startList.getRecord(rowId);
            row.setStrike(striked);
            row.setState(StartRow.SyncState.ERROR);
            local_startList.Save(getApplicationContext());
            _localTableLoad();
          }
        }
      });
      builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int id) {
        }
      });
      builder.create().show();
    }

    protected void _start_row_to_event(StartRow row, int target_rowId) {
      StartRow.SyncData to_event = row.getSyncData();

      for (StartRow.Gate g : to_event.gates) {
        EventMessage.ProposeMsg msg;

        if (g.penalty == 0)
          continue;

        msg = new EventMessage.ProposeMsg(EventMessage.ProposeMsg.Type.PENALTY);
        msg.rowId = target_rowId;
        msg.gate = g.gate;
        msg.penalty = g.penalty;
        EventBus.getDefault().post(new EventMessage(msg));
      }

      if (to_event.strike != null && to_event.strike != false) {
        EventMessage.ProposeMsg msg;

        msg = new EventMessage.ProposeMsg().setRowId(target_rowId).setStrike(to_event.strike);
        EventBus.getDefault().post(new EventMessage(msg));
      }

      if (to_event.startTime != null && to_event.startTime != 0) {
        EventMessage.ProposeMsg msg;

        msg = new EventMessage.ProposeMsg(to_event.startTime, EventMessage.ProposeMsg.Type.START);
        msg.setRowId(target_rowId);
        EventBus.getDefault().post(new EventMessage(msg));
      }

      if (to_event.finishTime != null && to_event.finishTime != 0) {
        EventMessage.ProposeMsg msg;

        msg = new EventMessage.ProposeMsg(to_event.finishTime, EventMessage.ProposeMsg.Type.FINISH);
        msg.setRowId(target_rowId);
        EventBus.getDefault().post(new EventMessage(msg));
      }
    }

    protected void _show_merge_dialog() {
      final ArrayList<Integer> rowIds = new ArrayList<Integer>();
      final ArrayList<String> rowTitles = new ArrayList<String>();
      final StartRow row = local_startList.getRecord(rowId);
      StartRow.SyncData to_merge = row.getSyncData();

      for (ViewData vd : dataList_remote) {
        // match by discipline id and crew id
        // local data has no lapId info
        if (vd.disciplineId == disciplineId &&
            vd.crew == crew) {
          boolean merge_possible = true;
          // check for empty
          // compares only: finish time, start time and gates penalty
          if (vd.finish != 0 && to_merge.finishTime != null &&
              to_merge.finishTime != 0 && vd.finish != to_merge.finishTime) {
            /* disallow merging */
            continue;
          }
          if (vd.start != 0 && to_merge.startTime != null &&
              to_merge.startTime != 0 && vd.start != to_merge.startTime) {
            /* disallow merging */
            continue;
          }
          for (StartRow.Gate g : to_merge.gates) {
            int gateIndex = race.gates.indexOf(g.gate);

            if (gateIndex == -1) {
              continue;
            }

            if (vd.gates[gateIndex] != 0 && g.penalty != 0 &&
                g.penalty != vd.gates[gateIndex]) {
              merge_possible = false;
              break;
            }
          }
          if (!merge_possible) {
            continue;
          }
          // fill arrays
          rowIds.add(vd.rowId);
          rowTitles.add(String.format("Заезд %d", vd.lap));
        }
      }

      if (rowIds.size() == 0) {
        g("Совпадений не найдено");
        return;
      }

      AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
      builder.setItems(rowTitles.toArray(new String[0]),
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, final int item) {
              tRow.post(new Runnable() {
                public void run() {
                  _start_row_to_event(row, rowIds.get(item));
                  _remove();
                }
              });
            }
          });
      builder.create().show();
    }

    protected void _show_edit_dialog() {
      StartLineEditDialog sled;
      final ArrayList<Integer> lap_values = new ArrayList<Integer>();
      RaceStatus.Discipline rdisp = race.getDiscipline(disciplineId);
      ArrayList<String> disps = new ArrayList<String>();

      if (rdisp == null)
        disps.add(String.format("id #%d", disciplineId));
      else
        disps.add(rdisp.name);

      if (!is_local) {
        /* pass previous and next lap data */
        int cpos = dataList.indexOf(ViewData.this);
        int pos = cpos;

        if (start == 0) {
          /* previous lap */
          while (--cpos >= 0) {
            ViewData prev = dataList.get(cpos);

            if (prev.lap != lap && lap_values.indexOf(prev.lap) == -1) {
              if (prev.start == 0 && (rdisp == null || rdisp.parallel)) {
                /* not add when prev started */
                lap_values.add(prev.lap);
              }
              break;
            }
          }
        }

        lap_values.add(lap);
        cpos = pos;

        if (start == 0) {
          int lastLapId = lap;
          boolean found = false;
          /* next lap */
          while (++cpos < dataList.size()) {
            ViewData next = dataList.get(cpos);

            if (!found) {
              if (lap_values.indexOf(next.lap) == -1) {
                if (next.start == 0 && (rdisp == null || rdisp.parallel)) {
                  lap_values.add(next.lap);
                }
                found = true;
                ;
              }
            }
            lastLapId = next.lap;
          }
          lap_values.add(lastLapId + 1);
        }
      } else {
        lap_values.add(0);
      }

      Log.d("wsa-ng-ui", String.format("<LAPS[%d] %s>", lap_values.size(), lap_values.toString()));

      if (strict_crewslist && race.crews.size() != 0) {
        sled = new StartLineEditDialog(race.crews.indexOf(crew), lap_values.indexOf(lap), -1, true);
        sled.setCrewValues(race.crews);
      } else {
        sled = new StartLineEditDialog(crew, lap_values.indexOf(lap), -1, true);
      }
      sled.setLapValues(lap_values);
      sled.setDisciplines(disps);

      if (!is_local && countDownMode && countDownLap == lap) {
        Toast.makeText(MainActivity.this,
            "Идёт отсчёт",
            Toast.LENGTH_SHORT).show();
        return;
      }

      sled.setStartLineEditDialogListener(new StartLineEditDialog.StartLineEditDialogListener() {
        @Override
        public void onStartLineEditDialogResult(StartLineEditDialog sled, int crewNum, int lapNum, int disciplineId) {
          int crewId;
          int lapId = lap_values.get(lapNum);

          if (race.crews.size() != 0)
            crewId = race.crews.get(crewNum);
          else
            crewId = crewNum;

          Log.i("wsa-ng-ui", "Set new lap/crew for row #" + Integer.toString(rowId) +
              " crew=" + Integer.toString(crewId) + " lap=" + Integer.toString(lapId));
          if (!is_local) {
            EventMessage.ProposeMsg req;

            req = new EventMessage.ProposeMsg(crewId, lapId, ViewData.this.disciplineId);
            req.setRowId(rowId);
            EventBus.getDefault().post(new EventMessage(EventMessage.EventType.PROPOSE, req));
          } else {
            StartRow row = local_startList.getRecord(rowId);
            row.setIdentify(crewId, lapId, row.disciplineId);
            row.setState(StartRow.SyncState.ERROR);
            local_startList.Save(getApplicationContext());
            _localTableLoad();
          }
        }
      });
      sled.show(getFragmentManager(), sled.getClass().getCanonicalName());
    }

    private int b2v(boolean val) {
      if (!val)
        return View.GONE;
      return View.VISIBLE;
    }

    public void updateVisibilityByDisp() {
      TerminalStatus.Discipline disp = term.getDiscipline(disciplineId);
      RaceStatus.Discipline raceDisp = race.getDiscipline(disciplineId);

      if (raceDisp == null || disp == null) {
        tStart.setVisibility(b2v(false));
        for (TextView gateView : tGates) {
          gateView.setVisibility(b2v(false));
        }
        tFinish.setVisibility(b2v(false));
        return;
      }

      tStart.setVisibility(b2v(disp != null && disp.startGate));
      for (TextView gateView : tGates) {
        int viewGateId = (int) gateView.getTag(R.id.tag_gate_id);

        gateView.setVisibility(b2v(isGateIdPermittedForTerminal((Integer) viewGateId, raceDisp, disp)));
      }
      tFinish.setVisibility(b2v(disp != null && disp.finishGate));
    }

    public View getView() {
      TextView anyGate = null;
      int anyGateIndex = 0;

      if (tRow != null) {
        return tRow;
      }

      tRow = (TableRow) LayoutInflater.from(this.context).inflate(R.layout.data_row, null);
      for (int i = 0; i < tRow.getChildCount(); i++) {
        View v = tRow.getChildAt(i);
        switch (v.getId()) {
          case R.id.syncer:
            tSyncer = v;
            break;
          case R.id.crew:
            tCrew = (TextView) v;
            break;
          case R.id.lap:
            tLap = (TextView) v;
            break;
          case R.id.start_gate:
            tStart = (TextView) v;
            break;
          case R.id.finish_gate:
            tFinish = (TextView) v;
            break;
          case R.id.any_gate:
            anyGate = (TextView) v;
            anyGateIndex = i;
            break;
        }
      }
      tRow.setTag(R.id.tag_selected, false);
      tRow.setTag(R.id.tag_background, null);

      if ((parent.tableDataList.indexOf(this) % 2) == 0) {
        tRow.setBackgroundResource(R.color.rowEven);
      } else {
        //tRow.setBackgroundResource(R.color.rowOdd);
      }

      TableRow.OnClickListener rowClickListener = new TableRow.OnClickListener() {
        @Override
        public void onClick(View v) {
          _selectByRowId();
        }
      };

      tRow.setOnClickListener(rowClickListener);

      View.OnClickListener lapcrewListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          _onLapCrewClick(v);
        }
      };

      tCrew.setOnClickListener(lapcrewListener);
      tLap.setOnClickListener(lapcrewListener);

      tGates = new TextView[race.gates.size()];
      for (int i = 0; i < race.gates.size(); i++) {
        if (i > 0) {
          anyGate = (TextView) _newDataCol(R.id.any_gate);
        }

        anyGate.setTag(R.id.tag_selected, false);
        anyGate.setTag(R.id.tag_gate_id, race.gates.get(i));
        _setupGateListener(anyGate);
        tGates[i] = anyGate;
        if (i > 0) {
          tRow.addView(anyGate, anyGateIndex);
        }
        anyGateIndex++;
      }

      if (race.gates.size() == 0) {
        tRow.removeView(anyGate);
      }

      tStart.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          _onStartClick(v);
        }
      });
      tFinish.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          _onFinishClick(v);
        }
      });

      updateVisibilityByDisp();
      return tRow;
    }
  }

  protected View _newDataCol(int id) {
    View v = LayoutInflater.from(this).inflate(R.layout.data_row, null);
    if (id != R.layout.data_row) {
      v = v.findViewById(id);
      ((ViewGroup) v.getParent()).removeView(v);
    }
    v.setId(v.generateViewId());
    return v;
  }

  protected void _tablesSetup() {
    for (TableData td : tableList_remote) {
      td.update();
    }

    for (ViewData vd : dataList_remote) {
      vd.updateVisibilityByDisp();
    }

    for (TableData td : tableList_local) {
      td.update();
    }

    for (ViewData vd : dataList_local) {
      vd.updateVisibilityByDisp();
    }
  }

  protected void _buttonsSetup() {
    Button disp_btn = findViewById(R.id.discipline_title);
    ImageButton new_crew_btn = findViewById(R.id.new_crew);
    ImageButton new_crew_local_btn = findViewById(R.id.new_crew_local);

    disp_btn.setVisibility(View.GONE);

    Log.d("wsa-ng-ui", "Table setup");

    if (term == null || race == null) {
      Log.d("wsa-ng-ui", "Table not setuped");
      if (term == null)
        Log.d("wsa-ng-ui", "Table not setuped: term == null");

      if (race == null)
        Log.d("wsa-ng-ui", "Table not setuped: race == null");

      return;
    }

    Log.d("wsa-ng-ui", "Table setup continue");

    finishGate = false;
    new_crew_local_btn.setVisibility(View.GONE);
    new_crew_btn.setVisibility(View.GONE);
    for (TerminalStatus.Discipline tdisp : term.disciplines) {
      if (tdisp.startGate) {
        new_crew_btn.setVisibility(View.VISIBLE);
      }
      if (!tdisp.startGate &&
          (tdisp.gates.size() != 0 ||
              tdisp.finishGate)) {
        new_crew_local_btn.setVisibility(View.VISIBLE);
      }
      if (tdisp.finishGate) {
        finishGate = true;
      }
    }

    Collections.sort(race.crews);

    chrono = new Chrono(MainActivity.this,
        getSharedPreferences("chrono", Context.MODE_PRIVATE),
        getSharedPreferences("chrono_data", Context.MODE_PRIVATE),
        (Vibrator) getSystemService(Context.VIBRATOR_SERVICE));
  }
}
