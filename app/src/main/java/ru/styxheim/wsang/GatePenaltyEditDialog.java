package ru.styxheim.wsang;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.RadioGroup;
import android.widget.RadioButton;
import android.widget.GridLayout;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.app.Dialog;
import android.app.DialogFragment;
import android.util.Log;

import java.util.ArrayList;
import android.widget.*;

public class GatePenaltyEditDialog extends DialogFragment
{
  protected int crew;
  protected int lap;
  protected ArrayList<Integer> gates;
  protected ArrayList<Integer> penalties;
  protected ArrayList<Integer> values;

  public interface onChangeListener {
    void onChange(GatePenaltyEditDialog dialog);
  }

  public GatePenaltyEditDialog(int lap, int crew,
                               ArrayList<Integer> gates,
                               ArrayList<Integer> penalties,
                               ArrayList<Integer> values)
  {
    this.lap = lap;
    this.crew = crew;

    this.gates = (ArrayList<Integer>)gates.clone();
    this.penalties = (ArrayList<Integer>)penalties.clone();
    this.values = (ArrayList<Integer>)values.clone();
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState)
  {
    final View v = inflater.inflate(R.layout.gate_penalties, container, false);

    TextView title = v.findViewById(R.id.title);
    Button save = v.findViewById(R.id.save_button);
    GridLayout gate_container = v.findViewById(R.id.container);

    gate_container.removeAllViews();

    for( int gi = 0; gi < gates.size(); gi++ ) {
      Integer gateId = gates.get(gi);
      View tGate = inflater.inflate(R.layout.gate_penalties_elem, null, false);
      TextView gate_title = tGate.findViewById(R.id.gate_title);
      RadioGroup penalty_container = tGate.findViewById(R.id.penalty_container);

      gate_title.setText(gateId.toString());
      penalty_container.removeAllViews();

      for( int i = 0; i < penalties.size(); i++ ) {
        View _v = inflater.inflate(R.layout.gate_penalties_elem, null, false);
        RadioButton rb = _v.findViewById(R.id.penalty);

        rb.setId(i);

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
    save.setWidth(title.getWidth());
    save.setHeight(title.getHeight());

    return v;
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState)
  {
    Dialog dialog = super.onCreateDialog(savedInstanceState);

    return dialog;
  }
}
