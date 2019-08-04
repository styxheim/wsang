package ru.styxheim.wsang;

import android.app.*;
import android.os.*;
import android.widget.*;
import android.content.*;
import android.view.*;
import android.util.Log;
import java.util.ArrayList;

import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.ThreadMode;
import org.greenrobot.eventbus.Subscribe;

public class FinishActivity extends StartFinish
{
  protected final int times_max = 1000;
  /* list of all chronometer results */
  protected ArrayList<Long> times = new ArrayList<Long>();
  /* list of selected chrono results (used in finish list) */
  protected ArrayList<Long> sel_times = new ArrayList<Long>();
  protected SharedPreferences chrono_cfg;

  /* TOREMOVE */
  protected int lastCrewId = 0;
  protected int lastLapId = 0;
  /* */

  public FinishActivity() {

  }

  protected void _save_chrono()
  {
    SharedPreferences.Editor ed = chrono_cfg.edit();
    for( int i = 0; i < times.size(); i++ ) {
      ed.putLong(Integer.toString(i), times.get(i));
    }
    ed.apply();
  }

  protected void _load_chrono()
  {
    times.clear();
    for( int i = 0; i < times_max; i++ ) {
      if( !chrono_cfg.contains(Integer.toString(i)) )
        break;
      times.add(chrono_cfg.getLong(Integer.toString(i), 0));
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    Log.d("wsa-ng", "Launcher:onCreate()");
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_finish);
    chrono_cfg = getSharedPreferences("chrono", Context.MODE_PRIVATE);
  }

  @Override
  public void onStart()
  {
    Log.d("wsa-ng", "Launcher:onStart()");

    _load_chrono();

    super.onStart();
    EventBus.getDefault().register(this);
  }

  @Override
  public void onStop()
  {
    Log.d("wsa-ng", "Launcher:onStop()");

    EventBus.getDefault().unregister(this);
    super.onStop();
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event)
  {
    long timeInMillis;
    long off = System.currentTimeMillis() - SystemClock.uptimeMillis();
    timeInMillis = event.getEventTime() + off;

    timeInMillis -= settings.getLong("chrono_offset", Default.chrono_offset);

    /*if (keyCode == settings.getInt("chrono_key", Default.chrono_key))*/
    if( keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
        keyCode == KeyEvent.KEYCODE_VOLUME_UP )
    {
      times.add(0, timeInMillis);
      if( times.size() > times_max )
        times.remove(times.size() - 1);

      int vtime = settings.getInt("chrono_vibro", Default.chrono_vibro);
      if( vtime > 0 ) {
        Vibrator v = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
        v.vibrate(VibrationEffect.createOneShot(vtime, VibrationEffect.DEFAULT_AMPLITUDE));
      }
      _save_chrono();
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onServiceMessageUpdate(EventMessage ev)
  {
    switch( ev.type ) {
    case UPDATE:
      publishFinishRow((StartRow)ev.obj);
      break;
    }
  }

  protected void _startRowSetTime(final int rowId, final long time, boolean askForReplace)
  {
    if( !askForReplace ) {
      EventMessage.ProposeMsg req;

      req = new EventMessage.ProposeMsg(time, EventMessage.ProposeMsg.Type.FINISH);
      req.setRowId(rowId);
      EventBus.getDefault().post(new EventMessage(EventMessage.EventType.PROPOSE, req));
    }
    else {
      AlertDialog.Builder builder = new AlertDialog.Builder(this);
      builder.setMessage("Заменить финишное время?");
      builder.setPositiveButton("Да", new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int id) {
          _startRowSetTime(rowId, time, false);
        }
      });
      builder.setNegativeButton("Нет", new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int id) {
        }
      });
      builder.create().show();

    }
  }

  protected void _showMoreItems(final int rowId, final boolean askForReplace)
  {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);

    String[] Stimes = new String[times.size() + 1];
    long offset = times.get(0);

    for( int i = 0; i < times.size(); i++ ) {
      Stimes[i] = String.format("%3d. %s %s%s%s",
                                times.size() - i,
                                Default.millisecondsToString(times.get(i)),
                                offset >= times.get(i) ? "+" : "-",
                                Default.millisecondsToString(offset - times.get(i)),
                                ((sel_times.indexOf(times.get(i)) == -1) ? "" :
                                 " *")
                                );
    }
    Stimes[times.size()] = String.format("%3d. %s (сбросить)",
                                         0,
                                         Default.time_empty);

    builder.setItems(Stimes, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int item)
      {
        if( item == times.size() ) {
          _startRowSetTime(rowId, 0, askForReplace);
        }
        else {
          _startRowSetTime(rowId, times.get(item), askForReplace);
        }
      }
    });
    builder.create().show();
  }

  public void selectFinishTimeOnClick(final View v)
  {
    final Drawable row_drw;
    final TableRow row;

    if( times.size() == 0 ) {
      Toast.makeText(FinishActivity.this,
                     "Используйте кнопку секундомера для отсечки времени",
                     Toast.LENGTH_SHORT).show();

      return;
    }

    row = (TableRow)v.getParent();
    row_drw = row.getBackground();
    row.setBackgroundResource(R.color.selected_row);

    final boolean askForReplace;

    if( Default.time_empty.compareTo(((TextView)v).getText().toString()) == 0 )
      askForReplace = false;
    else
      askForReplace = true;


    PopupMenu pmenu = new PopupMenu(this, v);

    /* bottom magic */
    int _c = 0;
    long offset = times.get(0);

    for( int i = 0; i < times.size() && i < 5; i++, _c++) {
      String title;
      title = String.format("%2d. %s %s%s",
                            times.size() - i,
                            Default.millisecondsToString(times.get(i)),
                            offset >= times.get(i) ? "+" : "-",
                            Default.millisecondsToString(offset - times.get(i))
                            );
      pmenu.getMenu().add(1, i, i, title);
      if( sel_times.indexOf(times.get(i)) != -1 ) {
        /* disable already selected values */
        pmenu.getMenu().getItem(i).setEnabled(false);
      }
    }

    pmenu.getMenu().add(1, _c, _c, "  ... ещё ... ");

    final int c = _c;

    pmenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
      @Override
      public boolean onMenuItemClick(MenuItem item)
      {
        try {
          if( item.getItemId() == c ) {
            _showMoreItems(row.getTag(), askForReplace);
          }
          else {
            _startRowSetTime(row.getTag(),
                             times.get(item.getItemId()),
                             askForReplace);
          }
          return true;
        }
        finally {
          row.setBackground(row_drw);
        }
      }
    });

    pmenu.setOnDismissListener(new PopupMenu.OnDismissListener() {
      @Override
      public void onDismiss(PopupMenu menu) {
        row.setBackground(row_drw);
      }
    });

    pmenu.show();
  }

  private void publishFinishRow(StartRow startRow)
  {
    final ScrollView sv = findViewById(R.id.finish_scroll);
    LayoutInflater inflater;
    TableLayout table;
    TableRow row;

    TextView vlap;
    TextView vcrew;
    TextView vtime;
    TextView syncer;

    boolean visible = false;

    if( startRow.crewId > this.lastCrewId )
      this.lastCrewId = startRow.crewId;

    if( startRow.lapId > this.lastLapId )
      this.lastLapId = startRow.lapId;


    table = (TableLayout)findViewById(R.id.finish_table);
    row = (TableRow)table.findViewWithTag(startRow.getRowId());
    if( row != null ) {
      visible = true;
    }
    else {
      inflater = getLayoutInflater();
      row = (TableRow)inflater.inflate(R.layout.finish_row, null);
      row.setTag(startRow.getRowId());
    }

    vlap = (TextView)row.findViewById(R.id.row_lap_id);
    vcrew = (TextView)row.findViewById(R.id.row_crew_id);
    vtime = (TextView)row.findViewById(R.id.row_time);
    syncer = (TextView)row.findViewById(R.id.row_synced);

    vcrew.setText("C" + Integer.toString(startRow.crewId));
    vlap.setText("L" + Integer.toString(startRow.lapId));

    if( visible ) {
      /* remove old chrono value when it different */
      Long prev_time = (Long)vtime.getTag(R.id.tag_milliseconds);
      int index;
      if( !prev_time.equals(startRow.finishAt) ) {
        index = sel_times.indexOf(prev_time);
        if( index != -1 ) {
          if( startRow.finishAt != 0 ) {
            sel_times.set(index, startRow.finishAt);
          }
          else {
            sel_times.remove(index);
          }
        }
        else {
          sel_times.add(startRow.finishAt);
        }
      }
    }
    else {
      /* add new value */
      if( startRow.finishAt != 0 )
        sel_times.add(startRow.finishAt);
    }

    if( startRow.finishAt == 0 ) {
      vtime.setTypeface(null, Typeface.BOLD);
    }
    else {
      vtime.setTypeface(null, Typeface.NORMAL);
    }

    vtime.setText(Default.millisecondsToString(startRow.finishAt));
    vtime.setTag(R.id.tag_milliseconds, new Long(startRow.finishAt));

    switch( startRow.state ) {
    case PENDING:
      syncer.setBackgroundResource(R.color.Pending);
      break;
    case SYNCED:
      syncer.setBackgroundResource(R.color.Synced);
      break;
    case ERROR:
      syncer.setBackgroundResource(R.color.errSync);
      break;
    default:
      syncer.setBackgroundResource(R.color.notSynced);
      break;
    }

    table.addView(row);

    if( table.getChildCount() % 2 == 0 ) {
      row.setBackgroundResource(R.color.rowEven);
    }

    if( !visible ) {
      row.post(new Runnable() {
        @Override
        public void run()
        {
          sv.scrollTo(0, sv.getBottom());
        }
      });
    }
  }

  /* TOREMOVE */
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
}

