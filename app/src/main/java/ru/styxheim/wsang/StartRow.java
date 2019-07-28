package ru.styxheim.wsang;

import java.io.IOException;
import android.util.*;

public class StartRow
{
  private int rowId;

  public int crewId;
  public int lapId;
  public String startAt;

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
           " startTime='" + this.startAt + "'" +
           ">";
  }

  public void saveJSON(JsonWriter w, boolean system) throws IOException
  {
    w.beginObject();
    /* confusing names: for compatable with old WSA application */
    w.name("lapId").value(this.rowId);
    w.name("raceNumber").value(this.lapId);
    w.name("crewNumber").value(this.crewId);
    w.name("startTime").value(this.startAt);
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
      case "startTime":
        this.startAt = r.nextString();
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

