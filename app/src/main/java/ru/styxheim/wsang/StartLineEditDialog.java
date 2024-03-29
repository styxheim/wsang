package ru.styxheim.wsang;

import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.content.DialogInterface;
import android.util.Log;

import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;


public class StartLineEditDialog extends DialogFragment
    implements View.OnClickListener,
    NumberPicker.OnValueChangeListener {

  public static int MIN = 0;
  public static int MAX = 200;

  public interface StartLineEditDialogListener {
    void onStartLineEditDialogResult(StartLineEditDialog sled, int crew, int lap, int discipline);
  }

  private StartLineEditDialogListener listener;

  private String title = "";

  private int crew_chosen;
  private int lap_chosen;
  private int disp_choosen;

  private ArrayList<Integer> crew_values = new ArrayList<Integer>();
  private ArrayList<Integer> lap_values = new ArrayList<Integer>();
  private ArrayList<String> disps = new ArrayList<String>();

  boolean editMode = false;

  public StartLineEditDialog(int crew_no, int lap_no, int disp_no) {
    this.crew_chosen = crew_no;
    this.lap_chosen = lap_no;
    this.disp_choosen = disp_no;
  }

  public StartLineEditDialog(int crew_no, int lap_no, int disp_no, boolean is_edit) {
    this.crew_chosen = crew_no;
    this.lap_chosen = lap_no;
    this.disp_choosen = disp_no;
    this.editMode = is_edit;
  }

  public void setStartLineEditDialogListener(StartLineEditDialogListener listener) {
    this.listener = listener;
  }

  public void setLapValues(ArrayList<Integer> values) {
    this.lap_values = values;
  }

  public void setCrewValues(ArrayList<Integer> values) {
    this.crew_values = values;
  }

  public void setDisciplines(ArrayList<String> disps) {
    this.disps = disps;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  @Override
  public void onClick(View v) {
    /* store changed value from all NumberPickers */
    v.requestFocusFromTouch();
    /* remove dialog */
    this.dismiss();
    Log.d("wsa-ng-ui", String.format("sled: crew=%d, lap=%d, discipline=%d", crew_chosen, lap_chosen, disp_choosen));

    if (v.getId() == R.id.start_line_edit_dialog_save && listener != null) {
      if (crew_values.size() != 0 && (crew_chosen >= crew_values.size() ||
          crew_chosen < 0))
        return;
      if (lap_values.size() != 0 && (lap_chosen >= lap_values.size() ||
          lap_chosen < 0))
        return;
      listener.onStartLineEditDialogResult(this,
          this.crew_chosen,
          this.lap_chosen,
          this.disp_choosen);
    }
  }

  @Override
  public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
    switch (picker.getId()) {
      case R.id.start_line_edit_dialog_crew_picker:
        this.crew_chosen = newVal;
        break;
      case R.id.start_line_edit_dialog_lap_picker:
        this.lap_chosen = newVal;
        break;
    }
  }

  protected void setupDisciplineButton(View v) {
    final Button b;

    if (disp_choosen == -1) {
      if (disps.size() == 1) {
        disp_choosen = 0;
      } else {
        disp_choosen = ThreadLocalRandom.current().nextInt(0, disps.size() - 1);
      }
    }

    b = v.findViewById(R.id.discipline_button);
    if (disp_choosen >= 0 && disp_choosen < disps.size()) {
      b.setVisibility(View.VISIBLE);
      b.setText(disps.get(disp_choosen));
    } else {
      b.setVisibility(View.GONE);
    }

    b.setOnClickListener(new Button.OnClickListener() {
      public void onClick(View v) {
        disp_choosen = (disp_choosen + 1) % disps.size();
        b.setText(disps.get(disp_choosen));
      }
    });
  }

  private void updateTitle(View v) {
    TextView textView = (TextView) v.findViewById(R.id.start_line_edit_dialog_title);

    textView.setText(this.title);
  }

  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    View v = inflater.inflate(R.layout.start_line_edit_dialog, container, false);
    NumberPicker np;
    Button b;

    setupDisciplineButton(v);
    updateTitle(v);

    b = (Button) v.findViewById(R.id.start_line_edit_dialog_save);
    b.setOnClickListener(this);
    if (editMode)
      b.setText(R.string.edit_crew_save_title);

    b = (Button) v.findViewById(R.id.start_line_edit_dialog_cancel);
    b.setOnClickListener(this);

    np = (NumberPicker) v.findViewById(R.id.start_line_edit_dialog_crew_picker);
    np.setOnValueChangedListener(this);
    if (crew_values.size() == 0) {
      if (crew_chosen == -1)
        crew_chosen = ThreadLocalRandom.current().nextInt(MIN, MAX);
      try {
        final EditText wheel = (EditText) np.getChildAt(0);
        wheel.post(new Runnable() {
          public void run() {
            wheel.setSelectAllOnFocus(true);
            wheel.setFocusableInTouchMode(true);
            wheel.clearFocus();
            wheel.requestFocus();
          }
        });
      } catch (Exception e) {
        Log.e("wsa-ng-ui", "Error in crew NumberPicker: " + e.getMessage());
      }
      np.setMinValue(MIN);
      np.setMaxValue(MAX);
      np.setWrapSelectorWheel(true);
    } else {
      String[] values = new String[crew_values.size()];

      for (int i = 0; i < crew_values.size(); i++) {
        values[i] = crew_values.get(i).toString();
      }

      if (crew_chosen == -1) {
        if (crew_values.size() == 1) {
          crew_chosen = 0;
        } else {
          crew_chosen = ThreadLocalRandom.current().nextInt(0, crew_values.size() - 1);
        }
      }
      np.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
      np.setMinValue(0);
      np.setMaxValue(crew_values.size() - 1);
      np.setDisplayedValues(values);
      np.setWrapSelectorWheel(true);
    }
    np.setValue(crew_chosen);

    final NumberPicker lnp;

    lnp = (NumberPicker) v.findViewById(R.id.start_line_edit_dialog_lap_picker);
    lnp.setOnValueChangedListener(this);
    if (lap_values.size() == 0) {
      if (lap_chosen == -1)
        lap_chosen = ThreadLocalRandom.current().nextInt(MIN, MAX);
      lnp.setMinValue(MIN);
      lnp.setMaxValue(MAX);
      lnp.setWrapSelectorWheel(true);
    } else {
      String[] values = new String[lap_values.size()];

      for (int i = 0; i < lap_values.size(); i++) {
        values[i] = lap_values.get(i).toString();
      }
      if (lap_chosen == -1) {
        if (lap_values.size() == 1) {
          lap_chosen = 0;
        } else {
          lap_chosen = ThreadLocalRandom.current().nextInt(0, lap_values.size() - 1);
        }
      }
      lnp.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
      lnp.setMinValue(0);
      lnp.setMaxValue(lap_values.size() - 1);
      lnp.setDisplayedValues(values);
      lnp.setWrapSelectorWheel(false);
    }
    lnp.setValue(lap_chosen);
    return v;
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    Dialog dialog = super.onCreateDialog(savedInstanceState);
    if (lap_values.size() == 0 || crew_values.size() == 0)
      dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    return dialog;
  }

}
