package ru.styxheim.wsang;

import java.util.ArrayList;

public class EventMessage {
  public enum EventType {
    /* ProposeMsg */
    PROPOSE, /* UI proporse new record or change old */

    /* CountDownMsg */
    COUNTDOWN_START,
    COUNTDOWN_STOP,
    COUNTDOWN,      /* notify UI about current countdown */
    COUNTDOWN_END,  /* countdown successfull completed.
                      endAtMs == -1 says what timer is stopped (forced or normal) */
  }

  ;

  public static class ProposeMsg {
    /* look to MainService._event_propose */
    public long time; /* finish time.  */
    public int crewId; /* crewId and lapId used together */
    public int lapId;
    public int rowId = -1;
    public int disciplineId;
    public boolean strike;

    public int gate;
    public int penalty;

    public enum Type {
      UNK,
      STRIKE,   /* update strike flag */
      IDENTIFY, /* update identify */
      START,    /* update start time */
      FINISH,   /* update finish time */
      PENALTY,  /* update penalties */
      CONFIRM   /* send data to server */
    }

    ;

    Type type = Type.UNK;

    public ProposeMsg() {
    }

    public ProposeMsg setStrike(boolean strike) {
      this.type = Type.STRIKE;
      this.strike = strike;
      return this;
    }

    public ProposeMsg(int crewId, int lapId, int disciplineId) {
      this.crewId = crewId;
      this.lapId = lapId;
      this.disciplineId = disciplineId;
      this.type = Type.IDENTIFY;
    }

    public ProposeMsg(long time, Type type) {
      this.time = time;
      this.type = type;
    }

    public ProposeMsg(Type type) {
      this.type = type;
    }

    public ProposeMsg setRowId(int rowId) {
      this.rowId = rowId;
      return this;
    }
  }

  public static class CountDownMsg {
    public int disciplineId;
    public long startAt;
    public long leftMs;
    public int lapId;
    public long endAtMs;

    public CountDownMsg(int lapId, int disciplineId, long leftMs) {
      this.lapId = lapId;
      this.leftMs = leftMs;
      this.disciplineId = disciplineId;
    }

    public CountDownMsg(int lapId, int disciplineId, long startAt, long endAtMs) {
      this.startAt = startAt;
      this.lapId = lapId;
      this.endAtMs = endAtMs;
      this.disciplineId = disciplineId;
    }
  }

  public EventType type;
  public Object obj;

  public EventMessage(EventType type, Object obj) {
    this.type = type;
    this.obj = obj;
  }

  public EventMessage(ProposeMsg obj) {
    this.type = EventType.PROPOSE;
    this.obj = obj;
  }

  public static class Boot {
    /* Request boot */

    public Boot() {
    }
  }

  public static class CountDownCancelled {
  }

  public static class RSyncResult {
    public ServerStatus serverStatus;

    public RSyncResult(ServerStatus ss) {
      this.serverStatus = ss;
    }
  }

  public static class SyncSuccess {
    public ArrayList<StartRow> rows;

    public SyncSuccess(ArrayList<StartRow> rows) {
      this.rows = rows;
    }
  }


  public static class SyncFailure {
    public ArrayList<StartRow> rows;

    public SyncFailure(ArrayList<StartRow> rows) {
      this.rows = rows;
    }
  }

  public static class TimeSync {
    boolean isEmpty = true;
    long T1;
    long T2;
    long T3;
    long T4;

    public TimeSync(long T1, long T2, long T3, long T4) {
      this.isEmpty = false;
      this.T1 = T1;
      this.T2 = T2;
      this.T3 = T3;
      this.T4 = T4;
    }

    public String toString() {
      if (isEmpty)
        return String.format("<%s empty>", this.getClass().getName());
      return String.format("<%s T1=%d, T2=%d, T3=%d, T4=%d>",
          this.getClass().getName(),
          this.T1, this.T2, this.T3, this.T4);
    }
  }
}
