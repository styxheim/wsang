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

  protected boolean finishChanged = false;
  protected boolean startChanged = false;

  public static enum SyncState {
    NONE,
    PENDING,
    SYNCING,
    ERROR,
    SYNCED,
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

  public boolean changePossible()
  {
    return this.state != StartRow.SyncState.SYNCING;
  }

  public void setStartData(long startAt)
  {
    if( this.state != SyncState.SYNCING ) {
      this.startAt = startAt;
      this.state = SyncState.NONE;
      this.startChanged = true;
    }
  }

  public void setFinishData(long finishAt)
  {
    if( this.state != SyncState.SYNCING ) {
      this.finishAt = finishAt;
      this.state = SyncState.NONE;
      this.finishChanged = true;
    }
  }

  public void setIdentify(int crewId, int lapId)
  {
    this.crewId = crewId;
    this.lapId = lapId;
    this.state = SyncState.NONE;
  }

  public void setState(SyncState state)
  {
    if( state == SyncState.SYNCED ) {
      this.finishChanged = false;
      this.startChanged = false;
    }

    this.state = state;
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

  public void prepareJSON(JsonWriter w) throws IOException
  {
    w.beginObject();
    w.name("LapId").value(this.rowId);
    /* identify data always send to server */
    w.name("LapNumber").value(this.lapId);
    w.name("CrewNumber").value(this.crewId);

    if( this.startChanged ) {
      w.name("StartTime").value(this.startAt);
    }

    if( this.finishChanged ) {
      w.name("FinishTime").value(this.finishAt);
    }
    w.endObject();
  }

  public void saveJSON(JsonWriter w) throws IOException
  {
    SyncState state = this.state;

    /* SYNCING and PENDING state is equal:
     *  SYNCING for UI
     *  PENDING for configs
     */
    if( state == SyncState.SYNCING )
      state = SyncState.PENDING;

    w.beginObject();
    /* confusing names: for compatable with old WSA application */
    w.name("lapId").value(this.rowId);
    w.name("lapNumber").value(this.lapId);
    w.name("crewNumber").value(this.crewId);
    w.name("startTimeMs").value(this.startAt);
    w.name("finishTimeMs").value(this.finishAt);
    w.name("_state_name").value(this.state.name());
    w.name("_upd_finish").value(this.finishChanged);
    w.name("_upd_start").value(this.startChanged);
    w.endObject();
  }

  public void loadJSONServer(JsonReader r) throws IOException
  {
    r.beginObject();
    while( r.hasNext() ) {
      String name = r.nextName();
      switch( name ) {
      case "LapId":
        this.rowId = r.nextInt();
        break;
      case "LapNumber":
        this.lapId = r.nextInt();
        break;
      case "CrewNumber":
        this.crewId = r.nextInt();
        break;
      default:
        Log.d("wsa-ng", "StartRow: Unknown field '" + name + "'");
        r.skipValue();
      }
    }
    r.endObject();
  }

  public void loadJSON(JsonReader r) throws IOException
  {
    r.beginObject();
    while( r.hasNext() ) {
      String name = r.nextName();
      switch( name ) {
      case "lapId":
        this.rowId = r.nextInt();
        break;
      case "lapNumber":
        this.lapId = r.nextInt();
        break;
      case "crewNumber":
        this.crewId = r.nextInt();
        break;
      case "startTime":
        /* TODO: convert from String to ms */
        break;
      case "finishTime":
        /* TODO: convert from String to ms */
        break;
      case "startTimeMs":
        this.startAt = r.nextLong();
        break;
      case "finishTimeMs":
        this.finishAt = r.nextLong();
        break;
      case "_state_name":
        this.state = SyncState.valueOf(r.nextString());
        break;
      case "_upd_finish":
        this.finishChanged = r.nextBoolean();
        break;
      case "_upd_start":
        this.startChanged = r.nextBoolean();
        break;
      default:
        Log.d("wsa-ng", "StartRow: Unknown field '" + name + "'");
        r.skipValue();
      }
    }
    r.endObject();
  }
}

