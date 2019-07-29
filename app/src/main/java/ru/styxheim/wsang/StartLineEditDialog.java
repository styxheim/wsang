package ru.styxheim.wsang;
import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.content.DialogInterface;

public class StartLineEditDialog extends DialogFragment
  implements View.OnClickListener,
             NumberPicker.OnValueChangeListener
{
  public interface StartLineEditDialogListener {
    void onStartLineEditDialogResult(StartLineEditDialog sled, int crew, int lap);
  }

  private StartLineEditDialogListener listener;

  private int crew_chosen;
  private int lap_chosen;

  private int lap_min = 1;
  private int lap_max = 999;

  private int crew_min = 1;
  private int crew_max = 999;

  public StartLineEditDialog(int crew_no, int lap_no)
  {
    this.crew_chosen = crew_no;
    this.lap_chosen = lap_no;
  }

  public void setStartLineEditDialogListener(StartLineEditDialogListener listener)
  {
    this.listener = listener;
  }

  public void setLapMinMax(int min, int max)
  {
    this.lap_min = min;
    this.lap_max = max;
  }

  public void setCrewMinMax(int min, int max)
  {
    this.crew_min = min;
    this.crew_max = max;
  }

  @Override
  public void onClick(View v)
  {
    /* store changed value from all NumberPickers */
    v.requestFocusFromTouch();
    /* remove dialog */
    this.dismiss();
    if( v.getId() == R.id.start_line_edit_dialog_save && listener != null) {
      listener.onStartLineEditDialogResult(this,
                                           this.crew_chosen,
                                           this.lap_chosen);
    }
  }

  @Override
  public void onValueChange(NumberPicker picker, int oldVal, int newVal)
  {
    switch( picker.getId() ) {
    case R.id.start_line_edit_dialog_crew_picker:
      this.crew_chosen = newVal;
      break;
    case R.id.start_line_edit_dialog_lap_picker:
      this.lap_chosen = newVal;
      break;
    }
  }

  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState)
  {
    View v = inflater.inflate(R.layout.start_line_edit_dialog, container, false);
    NumberPicker np;
    Button b;

    if( this.crew_chosen == -1 )
      this.crew_chosen = java.util.concurrent.ThreadLocalRandom.current().nextInt(this.crew_min, this.crew_max);

    if( this.lap_chosen == -1 )
      this.lap_chosen = java.util.concurrent.ThreadLocalRandom.current().nextInt(this.lap_min, this.lap_max);

    b = (Button)v.findViewById(R.id.start_line_edit_dialog_save);
    b.setOnClickListener(this);

    b = (Button)v.findViewById(R.id.start_line_edit_dialog_cancel);
    b.setOnClickListener(this);

    np = (NumberPicker)v.findViewById(R.id.start_line_edit_dialog_crew_picker);
    np.setOnValueChangedListener(this);
    np.setMinValue(this.crew_min);
    np.setMaxValue(this.crew_max);
    np.setValue(this.crew_chosen);

    np = (NumberPicker)v.findViewById(R.id.start_line_edit_dialog_lap_picker);
    np.setOnValueChangedListener(this);
    np.setMinValue(this.lap_min);
    np.setMaxValue(this.lap_max);
    np.setValue(this.lap_chosen);

    return v;
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState)
  {
    Dialog dialog = super.onCreateDialog(savedInstanceState);
    return dialog;
  }

}
