package ru.styxheim.wsang;

import java.io.IOException;
import android.util.*;

public class StartRow
{
  private int rowId;

  public int crewId;
  public int lapId;
  public long startAt = 0;
  public long finishAt = 0;

  public static enum SyncState {
    NONE,
    PENDING,
    SYNCING,
    ERROR,
    SYNCED,
  };

  public SyncState state = SyncState.NONE;
  /* hack for old-style server expectation (`start` mode without lapId)  */
  public SyncState state_start = SyncState.NONE;

  public StartRow(int rowId)
  {
    this.rowId = rowId;
  }

  public int getRowId()
  {
    return rowId;
  }

  public String toString()
  {
    return "<Start " + 
           " id='" + Integer.toString(this.rowId) + "'" +
           " lapId='" + Integer.toString(this.lapId) + "'" +
           " crewId='" + Integer.toString(this.crewId) + "'" +
           " startTime='" + Default.millisecondsToString(this.startAt) + "'" +
           " finishTime='" + Default.millisecondsToString(this.finishAt) + "'" +
           ">";
  }

  public void saveJSONStart(JsonWriter w) throws IOException
  {
    w.beginObject();
    w.name("LapNumber").value(this.lapId);
    w.name("CrewNumber").value(this.crewId);
    w.name("StartTime").value(Default.millisecondsToString(this.startAt));
    w.name("isStarted").value(true);
    w.endObject();
  }

  public void saveJSONFinish(JsonWriter w) throws IOException
  {
    w.beginObject();
    w.name("LapId").value(this.rowId);
    w.name("FinishTime").value(Default.millisecondsToString(this.finishAt));
    w.name("isFinished").value(true);
    w.endObject();
  }

  public void saveJSON(JsonWriter w, boolean system) throws IOException
  {
    SyncState state = this.state;
    SyncState state_start = this.state_start;

    if( state == SyncState.SYNCING )
      state = SyncState.PENDING;

    if( state_start == SyncState.SYNCING )
      state = SyncState.PENDING;

    w.beginObject();
    /* confusing names: for compatable with old WSA application */
    w.name("lapId").value(this.rowId);
    w.name("raceNumber").value(this.lapId);
    w.name("crewNumber").value(this.crewId);
    w.name("startTimeMs").value(this.startAt);
    w.name("finishTimeMs").value(this.finishAt);
    if( system ) {
      w.name("_state_name").value(state.name());
      w.name("_state_name_start").value(state_start.name());
    }
    w.endObject();
  }

  public void loadJSON(JsonReader r, boolean system) throws IOException
  {
    r.beginObject();
    while( r.hasNext() ) {
      switch( r.nextName() ) {
      case "lapId":
        this.rowId = r.nextInt();
        break;
      case "raceNumber":
        this.lapId = r.nextInt();
        break;
      case "crewNumber":
        this.crewId = r.nextInt();
        break;
      case "startTimeMs":
        this.startAt = r.nextLong();
        break;
      case "finishTimeMs":
        this.finishAt = r.nextLong();
        break;
      case "_state_name":
        if( system ) {
          this.state = SyncState.valueOf(r.nextString());
        }
        else {
          r.skipValue();
        }
        break;
      case "_state_name_start":
        if( system ) {
          this.state_start = SyncState.valueOf(r.nextString());
        }
        else {
          r.skipValue();
        }
        break;

      default:
        r.skipValue();
      }
    }
    r.endObject();
  }
}

