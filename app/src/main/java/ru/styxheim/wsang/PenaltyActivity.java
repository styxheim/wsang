package ru.styxheim.wsang;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.RadioGroup;
import android.widget.RadioButton;
import android.widget.GridLayout;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.util.Log;

import java.util.ArrayList;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class PenaltyActivity extends Activity
{
  protected int rowId;
  protected int crew;
  protected int lap;
  protected ArrayList<Integer> gates = new ArrayList<Integer>();
  protected ArrayList<Integer> penalties = new ArrayList<Integer>();
  protected ArrayList<Integer> values = new ArrayList<Integer>();

  protected SharedPreferences settingsChrono;

  protected long race_timestamp;
  protected long term_timestamp;

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    Bundle extras;
    super.onCreate(savedInstanceState);

    settingsChrono = getSharedPreferences("chrono", Context.MODE_PRIVATE);
    setContentView(R.layout.gate_penalties);

    if( (extras = getIntent().getExtras()) != null ) {
      this.rowId = extras.getInt("rowId");
      this.crew = extras.getInt("crew");
      this.lap = extras.getInt("lap");
      this.gates = extras.getIntegerArrayList("gates");
      this.penalties = extras.getIntegerArrayList("penalties");
      this.values = extras.getIntegerArrayList("values");
      this.race_timestamp = extras.getLong("race_timestamp");
      this.term_timestamp = extras.getLong("term_timestamp");
    }

    _setupPenalties();
  }

  protected void _setupPenalties()
  {
    int rb_id = 1;
    LayoutInflater inflater = LayoutInflater.from(this);
    TextView title = findViewById(R.id.title);
    GridLayout gate_container = findViewById(R.id.container);

    gate_container.removeAllViews();

    for( int gi = 0; gi < gates.size(); gi++ ) {
      Integer gateId = gates.get(gi);
      View tGate = inflater.inflate(R.layout.gate_penalties_elem, null, false);
      TextView gate_title = tGate.findViewById(R.id.gate_title);
      RadioGroup penalty_container = tGate.findViewById(R.id.penalty_container);

      gate_title.setText(gateId.toString());
      penalty_container.removeAllViews();

      for( int i = 1; i < penalties.size(); i++ ) {
        /* i == 0 for `not setted` */
        View _v = inflater.inflate(R.layout.gate_penalties_elem, null, false);
        RadioButton rb = _v.findViewById(R.id.penalty);

        rb.setId(rb_id++);
        rb.setTag(R.id.tag_gate_id, gi);
        rb.setTag(R.id.tag_penalty_id, i);

        ((ViewGroup)rb.getParent()).removeView(rb);
        rb.setText(penalties.get(i).toString());
        if( i == values.get(gi)) {
          rb.setChecked(true);
        }
        else {
          rb.setChecked(false);
        }
        penalty_container.addView(rb);
      }

      gate_container.addView(tGate);
    }

    title.setText(String.format("Заезд %d Команда %d", lap, crew));
  }

  @Override
  public void onStart()
  {
    super.onStart();
    EventBus.getDefault().register(this);
  }

  @Override
  public void onStop()
  {
    EventBus.getDefault().unregister(this);
    super.onStop();
  }

  public void onClickCancel(View v)
  {
    v.setEnabled(false);
    finish();
  }

  public void onClickSave(View v)
  {
    EventMessage.ProposeMsg msg;
    v.setEnabled(false);

    for( int i = 0; i < gates.size(); i++ ) {
      msg = new EventMessage.ProposeMsg(EventMessage.ProposeMsg.Type.PENALTY);
      msg.rowId = rowId;
      msg.gate = gates.get(i);
      msg.penalty = values.get(i);
      EventBus.getDefault().post(new EventMessage(msg));
    }
    /* TODO: send new data */
    finish();
  }

  public void onClickPenaltyRadioButton(View v)
  {
    int gate = v.getTag(R.id.tag_gate_id);
    int penalty = v.getTag(R.id.tag_penalty_id);

    values.set(gate, penalty);
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void _event_reloadSettings(TerminalStatus term)
  {
    if( term.timestamp != term_timestamp ) {
      /* move to launcher for apply new settings */
      Intent intent = new Intent(PenaltyActivity.this, Launcher.class);
      startActivity(intent);
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void _event_reloadSettings(RaceStatus race)
  {
    if( race.timestamp != race_timestamp ) {
      /* move to launcher for apply new settings */
      Intent intent = new Intent(PenaltyActivity.this, Launcher.class);
      startActivity(intent);
    }
  }

}
