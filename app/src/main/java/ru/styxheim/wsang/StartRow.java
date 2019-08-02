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
    ERROR,
    SYNCED
  };

  public SyncState state = SyncState.NONE;

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

  public void saveJSON(JsonWriter w, boolean system) throws IOException
  {
    w.beginObject();
    /* confusing names: for compatable with old WSA application */
    w.name("lapId").value(this.rowId);
    w.name("raceNumber").value(this.lapId);
    w.name("crewNumber").value(this.crewId);
    w.name("startTimeMs").value(this.startAt);
    w.name("finishTimeMs").value(this.finishAt);
    w.name("isStarted").value(true);
    if( system ) {
      w.name("_state").value(this.state.ordinal());
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
      case "_state":
        if( system ) {
          this.state = SyncState.values()[r.nextInt()];
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

