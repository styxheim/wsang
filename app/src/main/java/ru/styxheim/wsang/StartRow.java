package ru.styxheim.wsang;

import java.io.IOException;
import android.util.*;
import java.util.*;

public class StartRow
{
  final static String CLASS_NAME = "Lap";

  private int rowId;

  public long timestamp = 0;

  public int crewId;
  public int lapId;
  public long startAt = 0;
  public long finishAt = 0;
  public ArrayList<Gate>gates = new ArrayList<Gate>();

  public static class Gate {
    public int gate;
    public int penalty;

    public Gate(Gate other)
    {
      this.update(other);
    }

    public Gate(int gate, int penalty)
    {
      this.gate = gate;
      this.penalty = penalty;
    }

    public void update(Gate other)
    {
      this.gate = other.gate;
      this.penalty = other.penalty;
    }

    public Gate(JsonReader jr) throws IOException, IllegalStateException
    {
      fromJSON(jr);
    }

    public void toJSON(JsonWriter jw) throws IOException, IllegalStateException
    {
      jw.beginObject();
      jw.name("Gate").value(this.gate);
      jw.name("Penalty").value(this.penalty);
      jw.endObject();
    }

    public void fromJSON(JsonReader jr) throws IOException, IllegalStateException
    {
      if( !jr.hasNext() )
        return;

      this.gate = 0;
      this.penalty = 0;

      jr.beginObject();
      while( jr.hasNext() ) {
        switch( jr.nextName() ) {
        case "Gate":
          this.gate = jr.nextInt();
          break;
        case "Penalty":
          this.penalty = jr.nextInt();
          break;
        default:
          jr.skipValue();
          break;
        }
      }
      jr.endObject();
      Log.i("wsa-ng", "[GATE] parsed gate: " + toString());
    }

    public String toString()
    {
      String r;
      r = String.format("<Gate #%d penalty=%d>", gate, penalty);
      return r;
    }
  };

  public boolean updateLapId;
  public boolean updateCrewId;
  public boolean updateStartAt;
  public boolean updateFinishAt;

  public static enum SyncState {
    NONE,
    PENDING,
    SYNCING,
    ERROR,
    SYNCED,
  };

  protected class SyncData {
    public Integer crewId;
    public Integer lapId;
    public Long finishTime;
    public Long startTime;
    public Gate gate;

    public SyncData(Gate gate)
    {
      /* save copy */
      this.gate = new Gate(gate);
    }

    public SyncData(int crewId, int lapId)
    {
      this.crewId = new Integer(crewId);
      this.lapId = new Integer(lapId);
    }

    public SyncData(long startTime, long finishTime)
    {
      if( startTime != -1 ) {
        this.startTime = new Long(startTime);
      }

      if( finishTime != -1 ) {
        this.finishTime = new Long(finishTime);
      }
    }

    public SyncData(JsonReader jr) throws IOException
    {
      while( jr.hasNext() ) {
        String name = jr.nextName();
        switch( name ) {
          case "LapNumber":
            this.lapId = new Integer(jr.nextInt());
            break;
          case "CrewNumber":
            this.crewId = new Integer(jr.nextInt());
            break;
          case "FinishTime":
            this.finishTime = new Long(jr.nextLong());
            break;
          case "StartTime":
            this.startTime = new Long(jr.nextLong());
            break;
          default:
            Log.d("wsa-ng", "StartRow.SyncData: Unknown field '" + name + "'");
            jr.skipValue();
        }
      }
    }

    public void inprintJSON(JsonWriter jw) throws IOException
    {
      if( this.crewId != null ) {
        jw.name("CrewNumber").value(this.crewId);
      }

      if( this.lapId != null ) {
        jw.name("LapNumber").value(this.lapId);
      }

      if( this.finishTime != null ) {
        jw.name("FinishTime").value(this.finishTime);
      }

      if( this.startTime != null ) {
        jw.name("StartTime").value(this.startTime);
      }
    }
  };

  protected ArrayList<SyncData> syncList = new ArrayList<SyncData>();

  public SyncState state = SyncState.NONE;

  public StartRow(JsonReader jr) throws IllegalStateException, IOException
  {
    loadJSONServer(jr);
  }

  public StartRow(int rowId)
  {
    this.rowId = rowId;
  }

  public int getRowId()
  {
    return rowId;
  }

  public boolean isQueueEmpty()
  {
    return this.syncList.size() == 0;
  }

  public boolean changePossible()
  {
    return this.state != StartRow.SyncState.SYNCING;
  }

  public void setStartData(long startAt)
  {
    this.startAt = startAt;
    this.state = SyncState.NONE;
    this.syncList.add(new SyncData(startAt, -1));
  }

  public void setFinishData(long finishAt)
  {
    this.finishAt = finishAt;
    this.state = SyncState.NONE;
    this.syncList.add(new SyncData(-1, finishAt));
  }

  public void setIdentify(int crewId, int lapId)
  {
    this.crewId = crewId;
    this.lapId = lapId;
    this.state = SyncState.NONE;
    this.syncList.add(new SyncData(crewId, lapId));
  }

  public void setGateData(int gate, int penalty)
  {
    Gate rgate = new Gate(gate, penalty);

    if( !updateGate(rgate) )
      this.gates.add(rgate);

    this.syncList.add(new SyncData(rgate));
  }

  public void setState(SyncState state, int inprintCount)
  {
    if( state == SyncState.SYNCED ) {
      for( int i = 0; i < inprintCount; i++ ) {
        syncList.remove(0);
      }

      if( syncList.size() != 0 ) {
        /* set state to pending when queue is not empty */
        state = SyncState.PENDING;
      }
    }
    else if( state == SyncState.ERROR && inprintCount != syncList.size() ) {
      /* retry when not all changes applied */
      state = SyncState.PENDING;
    }

    this.state = state;
  }

  public String toString()
  {
    return "<Start " + 
           " id='" + Integer.toString(this.rowId) + "'" +
           " ts='" + Long.toString(this.timestamp) + "'" +
           " lapId='" + Integer.toString(this.lapId) + "'" +
           " crewId='" + Integer.toString(this.crewId) + "'" +
           " startTime='" + Default.millisecondsToString(this.startAt) + "'" +
           " finishTime='" + Default.millisecondsToString(this.finishAt) + "'" +
           " gates=" + Integer.toString(this.gates.size()) +
           " " + state.name() +
           ">";
  }

  public StartRow clone()
  {
    StartRow r = new StartRow(this.getRowId());
    r.crewId = crewId;
    r.lapId = lapId;
    r.startAt = startAt;
    r.finishAt = finishAt;
    r.timestamp = timestamp;
    /* todo clone gate */
    for( Gate gate : gates ) {
      r.gates.add(new Gate(gate));
    }
    r.state = state;
    return r;
  }

  public void update(StartRow newData)
  {
    this.timestamp = newData.timestamp;

    if( newData.updateLapId ) {
      this.lapId = newData.lapId;
    }
    if( newData.updateCrewId ) {
      this.crewId = newData.crewId;
    }
    if( newData.updateStartAt ) {
      this.startAt = newData.startAt;
    }
    if( newData.updateFinishAt) {
      this.finishAt = newData.finishAt;
    }
    for( Gate rgate : newData.gates )
    {
      if( !updateGate(rgate) )
        this.gates.add(new Gate(rgate));
    }
  }

  /* get JSON for server
     return: inprintCount value for setState()
     */
  public int prepareJSON(JsonWriter w) throws IOException
  {
    int i;
    w.beginObject();
    w.name("LapId").value(this.rowId);

    for( i = 0; i < syncList.size(); i++ ) {
      syncList.get(i).inprintJSON(w);
    }
    w.endObject();
    return i;
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
    w.name("Gates");
    w.beginArray();
    for( Gate gate : this.gates ) {
      gate.toJSON(w);
    }
    w.endArray();
    w.name("syncList");
    w.beginArray();
    for( SyncData sd : this.syncList ) {
      w.beginObject();
      sd.inprintJSON(w);
      w.endObject();
    }
    w.endArray();
    w.endObject();
  }

  public void saveJSONServer(JsonWriter w) throws IOException
  {
    w.beginObject();
    /* confusing names: for compatable with old WSA application */
    w.name(RaceStatus.TIMESTAMP).value(this.timestamp);
    w.name("lapId").value(this.rowId);
    w.name("lapNumber").value(this.lapId);
    w.name("crewNumber").value(this.crewId);
    w.name("startTimeMs").value(this.startAt);
    w.name("finishTimeMs").value(this.finishAt);
    w.name("Gates");
    w.beginArray();
    for( Gate gate : this.gates ) {
      gate.toJSON(w);
    }
    w.endArray();
    w.endObject();
  }

  boolean updateGate(Gate rgate) {
    for( Gate lgate : gates ) {
      if( lgate.gate == rgate.gate ) {
        lgate.update(rgate);
        return true;
      }
    }
    return false;
  }

  public void loadJSONServer(JsonReader r) throws IllegalStateException, IOException
  {
    if( !r.hasNext() )
      return;

    r.beginObject();
    while( r.hasNext() ) {
      String name = r.nextName();
      switch( name ) {
      case RaceStatus.TIMESTAMP:
        this.timestamp = r.nextLong();
        break;
      case "LapId":
        this.rowId = r.nextInt();
        break;
      case "LapNumber":
        this.updateLapId = true;
        this.lapId = r.nextInt();
        break;
      case "CrewNumber":
        this.updateCrewId = true;
        this.crewId = r.nextInt();
        break;
      case "StartTime":
        this.updateStartAt = true;
        this.startAt = r.nextLong();
        break;
      case "FinishTime":
        this.updateFinishAt = true;
        this.finishAt = r.nextLong();
        break;
      case "Gates":
        r.beginArray();
        while( r.hasNext() ) {
          Gate gate = new Gate(r);
          if( !updateGate(gate) )
            this.gates.add(gate);
        }
        r.endArray();
        break;
      default:
        Log.d("wsa-ng", "StartRow: Unknown field '" + name + "'");
        r.skipValue();
      }
    }
    r.endObject();
  }

  public void loadJSON(JsonReader r) throws IllegalStateException, IOException
  {
    this.syncList.clear();
    this.gates.clear();

    if( !r.hasNext() )
      return;

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
      case "syncList":
        r.beginArray();
        while( r.hasNext() ) {
          r.beginObject();
          syncList.add(new SyncData(r));
          r.endObject();
        }
        r.endArray();
        break;
      case "Gates":
        r.beginArray();
        while( r.hasNext() ) {
          this.gates.add(new Gate(r));
        }
        r.endArray();
        break;
      default:
        Log.d("wsa-ng", "StartRow: Unknown field '" + name + "'");
        r.skipValue();
      }
    }
    r.endObject();
  }
}

