package ru.styxheim.wsang;

import java.io.IOException;
import android.util.*;
import java.util.*;

public class StartRow
{
  final static String CLASS_NAME = "Lap";

  private int rowId = -1;

  public long timestamp = 0;

  public int disciplineId = -1;
  public int crewId = -1;
  public int lapId = -1;
  public long startAt = 0;
  public long finishAt = 0;
  public ArrayList<Gate>gates = new ArrayList<Gate>();

  public boolean strike = false;

  public static class Gate {
    public int gate;
    public int penalty;

    public Gate()
    {
    }

    public Gate clone()
    {
      Gate n = new Gate();
      n.gate = this.gate;
      n.penalty = this.penalty;
      return n;
    }

    public Gate(int gate, int penalty)
    {
      this.gate = gate;
      this.penalty = penalty;
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
    }

    public String toString()
    {
      String r;
      r = String.format("<Gate #%d penalty=%d>", gate, penalty);
      return r;
    }
  };

  public static enum SyncState {
    NONE,
    PENDING,
    SYNCING,
    ERROR,
    SYNCED,
  };

  protected static class SyncData {
    public Long timestamp;

    public Boolean strike;
    public Integer disciplineId;
    public Integer rowId;
    public Integer crewId;
    public Integer lapId;
    public Long finishTime;
    public Long startTime;
    public ArrayList<Gate> gates = new ArrayList<Gate>();

    public SyncData()
    {
    }

    public SyncData(Gate gate)
    {
      this.gates.add(gate.clone());
    }

    public SyncData(int crewId, int lapId, int disciplineId)
    {
      this.crewId = new Integer(crewId);
      this.lapId = new Integer(lapId);
      this.disciplineId = new Integer(disciplineId);
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

    public String toString()
    {
      String s = new String();

      s += "<SyncData";
      if( rowId != null )
        s += " id=" + rowId.toString();
      if( disciplineId != null )
        s += " discipline=" + disciplineId.toString();
      if( crewId != null )
        s += " crew=" + crewId.toString();
      if( lapId != null )
        s += " lap=" + lapId.toString();
      if( strike != null )
        s += " strike=" + ( strike == true ? "true" : "false" );
      if( startTime != null )
        s += " start=" + startTime.toString();
      if( gates.size() > 0 ) {
        s += " gates={";
        for( Gate g : gates ) {
          s += String.format(" <id=%d penalty=%d>", g.gate, g.penalty);
        }
        s += " }";
      }
      if( finishTime != null )
        s += " finish=" + finishTime.toString();
      s += ">";
      return s;
    }

    public SyncData(JsonReader jr) throws IOException
    {
      this.gates.clear();

      jr.beginObject();
      while( jr.hasNext() ) {
        String name = jr.nextName();
        switch( name ) {
          case "LapId":
            this.rowId = new Integer(jr.nextInt());
            break;
          case "LapNumber":
            this.lapId = new Integer(jr.nextInt());
            break;
          case "CrewId":
            this.crewId = new Integer(jr.nextInt());
            break;
          case "FinishTime":
            this.finishTime = new Long(jr.nextLong());
            break;
          case "StartTime":
            this.startTime = new Long(jr.nextLong());
            break;
          case "DisciplineId":
            this.disciplineId = new Integer(jr.nextInt());
            break;
          case "Gates":
            jr.beginArray();
            while( jr.hasNext() ) {
              this.gates.add(new Gate(jr));
            }
            jr.endArray();
            break;
          case RaceStatus.TIMESTAMP:
            this.timestamp = new Long(jr.nextLong());
            break;
          case "Strike":
            this.strike = new Boolean(jr.nextBoolean());
            break;
          default:
            Log.d("wsa-ng", "StartRow.SyncData: Unknown field '" + name + "'");
            jr.skipValue();
        }
      }
      jr.endObject();
    }

    public boolean isEmpty()
    {
      return ( timestamp == null &&
               disciplineId == null && rowId == null &&
               crewId == null && lapId == null &&
               finishTime == null && startTime == null &&
               strike == null &&
               gates.size() == 0 );
    }

    public void toJSON(JsonWriter jw) throws IOException
    {
      jw.beginObject();

      if( this.timestamp != null ) {
        jw.name(RaceStatus.TIMESTAMP).value(this.timestamp);
      }

      if( this.disciplineId != null ) {
        jw.name("DisciplineId").value(this.disciplineId);
      }

      if( this.rowId != null ) {
        jw.name("LapId").value(this.rowId);
      }

      if( this.crewId != null ) {
        jw.name("CrewId").value(this.crewId);
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

      if( this.strike != null ) {
        jw.name("Strike").value(this.strike);
      }

      if( this.gates.size() != 0 ) {
        jw.name("Gates");
        jw.beginArray();
        for( Gate gate : this.gates ) {
          gate.toJSON(jw);
        }
        jw.endArray();
      }

      jw.endObject();
    }

    public void inprint(SyncData other)
    {
      if( other.timestamp != null )
        this.timestamp = other.timestamp;

      if( other.disciplineId != null )
        this.disciplineId = other.disciplineId;

      if( other.rowId != null )
        this.rowId = other.rowId;

      if( other.crewId != null )
        this.crewId = other.crewId;

      if( other.lapId != null )
        this.lapId = other.lapId;

      if( other.finishTime != null )
        this.finishTime = other.finishTime;

      if( other.startTime != null )
        this.startTime = other.startTime;

      if( other.strike != null )
        this.strike = other.strike;

      for( Gate ogate : other.gates ) {
        boolean found = false;

        for( Gate lgate : this.gates ) {
          if( lgate.gate == ogate.gate ) {
            lgate.penalty = ogate.penalty;
            found = true;
            break;
          }
        }
        if( !found )
          this.gates.add(ogate.clone());
      }
    }

    protected Gate _getGate(int gateId)
    {
      for( Gate g : gates ) {
        if( g.gate == gateId ) {
          return g;
        }
      }

      return null;
    }

    public SyncData clone()
    {
      SyncData r = new SyncData();
      r.inprint(this);
      return r;
    }
  };

  protected ArrayList<SyncData> syncList = new ArrayList<SyncData>();
  protected ArrayList<SyncData> syncedList = new ArrayList<SyncData>();

  public SyncState state = SyncState.NONE;

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

  public void setStrike(boolean strike)
  {
    SyncData sd = new SyncData();

    this.strike = strike;
    sd.strike = strike;
    this.state = SyncState.NONE;
    this.syncList.add(sd);
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

  public void setIdentify(int crewId, int lapId, int disciplineId)
  {
    this.crewId = crewId;
    this.lapId = lapId;
    this.disciplineId = disciplineId;
    this.state = SyncState.NONE;
    this.syncList.add(new SyncData(crewId, lapId, disciplineId));
  }

  public void setGateData(int gate, int penalty)
  {
    Gate rgate = new Gate(gate, penalty);

    if( !updateGate(rgate) )
      this.gates.add(rgate);

    this.state = SyncState.NONE;
    this.syncList.add(new SyncData(rgate));
  }

  public void updateNotPendingFields(SyncData received,
                                     SyncData previous,
                                     SyncData diff)
  {
    // update StartRow for fields what not pending send
    // by data present in syncList
    // return syncData with fields whant not updated
    // when `previous` is null, no changes in `this`
    SyncData pending = new SyncData();

    for( SyncData ndata : syncList ) {
      pending.inprint(ndata);
    }

    // timestamp not used in this case
    // rowId not used in this case
    // disciplineId not used in this case

    if( received.strike != null ) {
      if( pending.strike != null ) {
        if( received.strike.compareTo(pending.strike) != 0 ) {
          if( diff != null )
            diff.strike = pending.strike;
        }
      }
      else if( previous != null && received.strike.compareTo(this.strike) != 0 ) {
        previous.strike = this.strike;
        this.strike = received.strike;
      }
    }
    else if( diff != null ) {
      diff.strike = pending.strike;
    }

    if( received.crewId != null ) {
      if( pending.crewId != null ) {
        if( received.crewId.compareTo(pending.crewId) != 0 ) {
          if( diff != null )
            diff.crewId = pending.crewId;
        }
      }
      else if( previous != null && received.crewId.compareTo(this.crewId) != 0 ) {
        previous.crewId = this.crewId;
        this.crewId = received.crewId;
      }
    }
    else if( diff != null ) {
      diff.crewId = pending.crewId;
    }

    if( received.lapId != null ) {
      if( pending.lapId != null ) {
        if( received.lapId.compareTo(pending.lapId) != 0 ) {
          if( diff != null )
            diff.lapId = pending.lapId;
        }
      }
      else if( previous != null && received.lapId.compareTo(this.lapId) != 0 ) {
        previous.lapId = this.lapId;
        this.lapId = pending.lapId;
      }
    }
    else if( diff != null ) {
      diff.lapId = pending.lapId;
    }

    if( received.finishTime != null ) {
      if( pending.finishTime != null ) {
        if( received.finishTime.compareTo(pending.finishTime) != 0 ) {
          if( diff != null )
            diff.finishTime = pending.finishTime;
        }
      }
      else if( previous != null && received.finishTime.compareTo(this.finishAt) != 0 ) {
        previous.finishTime = this.finishAt;
        this.finishAt = received.finishTime;
      }
    }
    else if( diff != null ) {
      diff.finishTime = pending.finishTime;
    }

    if( received.startTime != null ) {
      if( pending.startTime != null ) {
        // found in pending
        if( received.startTime.compareTo(pending.startTime) != 0 ) {
          // store differ value
          if( diff != null )
            diff.startTime = pending.startTime;
        }
        // value is equal to pending: nothing
      }
      else if( previous != null ) {
        previous.startTime = this.startAt;
        this.startAt = received.startTime;
      }
    }
    else if( diff != null ) {
      // value is pending but received not contain this field
      diff.startTime = pending.startTime;
    }

    if( received.gates.size() != 0 ) {
      for( Gate rgate : received.gates ) {
        Gate pgate = pending._getGate(rgate.gate);
        if( pgate == null ) {
          /* not found: simple set gate data */
          Gate g = _getGate(rgate.gate);
          if( g == null ) {
            gates.add(rgate.clone());
          } else {
            g.penalty = rgate.penalty;
            if( previous != null )
              previous.gates.add(rgate.clone());
          }
        } else if( pgate.penalty == rgate.penalty ) {
          /* gate found in pending queue: value is equal */
          pending.gates.remove(pgate);
        }
      }
      if( diff != null ) {
        /* set list of reaming gates from pending to diff */
        diff.gates = pending.gates;
      }
    }
    else if( diff != null ) {
      // received not have pending values
      diff.gates = pending.gates;
    }
  }

  public void setState(SyncState state)
  {
    if( state == SyncState.SYNCED ) {
      for( SyncData sd : syncList ) {
        syncedList.add(sd);
      }
      syncList.clear();
    }
    this.state = state;
  }

  public String toString()
  {
    return "<Start " + 
           " disciplineId='" + Integer.toString(this.disciplineId) + "'" +
           " id='" + Integer.toString(this.rowId) + "'" +
           " ts='" + Long.toString(this.timestamp) + "'" +
           " lapId='" + Integer.toString(this.lapId) + "'" +
           " crewId='" + Integer.toString(this.crewId) + "'" +
           " startTime='" + Default.millisecondsToString(this.startAt) + "'" +
           " finishTime='" + Default.millisecondsToString(this.finishAt) + "'" +
           " gates=" + Integer.toString(this.gates.size()) +
           "[ " + (this.strike ? " strike" : "") + " ]" +
           " syncPending=" + Integer.toString(this.syncList.size()) +
           " synced=" + Integer.toString(this.syncedList.size()) +
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
    r.disciplineId = disciplineId;
    r.strike = strike;
    for( Gate gate : gates ) {
      r.gates.add(gate.clone());
    }
    for( SyncData sdata : syncList ) {
      r.syncList.add(sdata.clone());
    }
    for( SyncData sdata : syncedList ) {
      r.syncedList.add(sdata.clone());
    }
    r.state = state;
    return r;
  }

  public void update(SyncData newData)
  {
    if( newData.timestamp != null )
      this.timestamp = newData.timestamp;
    if( newData.strike != null )
      this.strike = newData.strike;
    if( newData.lapId != null )
      this.lapId = newData.lapId;
    if( newData.crewId != null ) {
      this.crewId = newData.crewId;
    }
    if( newData.disciplineId != null ) {
      this.disciplineId = newData.disciplineId;
    }
    if( newData.startTime != null ) {
      this.startAt = newData.startTime;
    }
    if( newData.finishTime != null ) {
      this.finishAt = newData.finishTime;
    }
    for( Gate rgate : newData.gates )
    {
      if( !updateGate(rgate) )
        this.gates.add(rgate.clone());
    }
  }

  /* get JSON for server
     return: inprintCount value for setState()
     */
  public int prepareJSON(JsonWriter w) throws IOException
  {
    int i;
    SyncData data = new SyncData();

    data.rowId = new Integer(rowId);

    for( i = 0; i < syncList.size(); i++ ) {
      data.inprint(syncList.get(i));
    }

    data.toJSON(w);
    return i;
  }

  public void saveJSON(JsonWriter w) throws IOException
  {
    SyncState _state = this.state;

    /* SYNCING and PENDING state is equal:
     *  SYNCING for UI
     *  PENDING for configs
     */
    if( _state == SyncState.SYNCING )
      _state = SyncState.PENDING;

    w.beginObject();
    /* confusing names: for compatable with old WSA application */
    w.name("disciplineId").value(this.disciplineId);
    w.name("lapId").value(this.rowId);
    w.name("lapNumber").value(this.lapId);
    w.name("crewId").value(this.crewId);
    w.name("startTimeMs").value(this.startAt);
    w.name("finishTimeMs").value(this.finishAt);
    w.name("strike").value(this.strike);
    w.name("_state_name").value(_state.name());

    if( this.gates.size() != 0 ) {
      w.name("Gates");
      w.beginArray();
      for( Gate gate : this.gates ) {
        gate.toJSON(w);
      }
      w.endArray();
    }

    if( this.syncList.size() != 0 ) {
      w.name("syncList");
      w.beginArray();
      for( SyncData sd : this.syncList ) {
        sd.toJSON(w);
      }
      w.endArray();
    }
    if( this.syncedList.size() != 0 ) {
      w.name("syncedList");
      w.beginArray();
      for( SyncData sd : this.syncedList ) {
        sd.toJSON(w);
      }
      w.endArray();
    }
    w.endObject();
  }

  protected Gate _getGate(int gateId)
  {
    for( Gate g : gates ) {
      if( g.gate == gateId ) {
        return g;
      }
    }
    return null;
  }

  boolean updateGate(Gate rgate) {
    for( Gate lgate : gates ) {
      if( lgate.gate == rgate.gate ) {
        lgate.penalty = rgate.penalty;
        return true;
      }
    }
    return false;
  }

  public void loadJSON(JsonReader r) throws IllegalStateException, IOException
  {
    this.rowId = -1;

    this.strike = false;
    this.timestamp = 0;
    this.disciplineId = -1;
    this.crewId = -1;
    this.lapId = -1;
    this.startAt = 0;
    this.finishAt = 0;
    this.gates.clear();

    this.syncList.clear();
    this.syncedList.clear();

    if( !r.hasNext() )
      return;

    r.beginObject();
    while( r.hasNext() ) {
      String name = r.nextName();
      switch( name ) {
      case "strike":
        this.strike = r.nextBoolean();
        break;
      case "disciplineId":
        this.disciplineId = r.nextInt();
        break;
      case "lapId":
        this.rowId = r.nextInt();
        break;
      case "lapNumber":
        this.lapId = r.nextInt();
        break;
      case "crewId":
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
          syncList.add(new SyncData(r));
        }
        r.endArray();
        break;
      case "syncedList":
        r.beginArray();
        while( r.hasNext() ) {
          syncedList.add(new SyncData(r));
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

