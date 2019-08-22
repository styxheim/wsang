package ru.styxheim.wsang;

import java.util.ArrayList;

public class EventMessage
{
  public enum EventType {
    /* ProposeMsg */
    PROPOSE, /* UI proporse new record or change old */

    /* CountDownMsg */
    COUNTDOWN_START,
    COUNTDOWN_STOP,
    COUNTDOWN,      /* notify UI about current countdown */
    COUNTDOWN_END,  /* countdown successfull completed.
                      endAtMs == -1 says what timer is stopped (forced or normal) */
  };

  public static class ProposeMsg
  {
    /* look to MainService._event_propose */
    public long time; /* finish time.  */
    public int crewId; /* crewId and lapId used together */
    public int lapId;
    public int rowId = -1;
    public ArrayList<Integer> penalty;

    public enum Type {
      UNK,
      IDENTIFY, /* update identify */
      START,    /* update start time */
      FINISH,   /* update finish time */
      PENALTY,  /* update penalties */
      CONFIRM   /* send data to server */
    };

    Type type = Type.UNK;

    public ProposeMsg(int crewId, int lapId)
    {
      this.crewId = crewId;
      this.lapId = lapId;
      this.type = Type.IDENTIFY;
    }

    public ProposeMsg(long time, Type type)
    {
      this.time = time;
      this.type = type;
    }

    public ProposeMsg(Type type)
    {
      this.type = type;
    }

    public void setRowId(int rowId)
    {
      this.rowId = rowId;
    }
  }

  public static class CountDownMsg
  {
    public long startAt;
    public long leftMs;
    public int lapId;
    public long endAtMs;
    
    public CountDownMsg(int lapId, long leftMs)
    {
      this.lapId = lapId;
      this.leftMs = leftMs;
    }

    public CountDownMsg(int lapId, long startAt, long endAtMs)
    {
      this.startAt = startAt;
      this.lapId = lapId;
      this.endAtMs = endAtMs;
    }
  }

  public EventType type;
  public Object obj;

  public EventMessage(EventType type, Object obj)
  {
    this.type = type;
    this.obj = obj;
  }

  public EventMessage(ProposeMsg obj)
  {
    this.type = EventType.PROPOSE;
    this.obj = obj;
  }

  public static class ReloadSettings
  {
    /* Request screen reset
    */
  }
  
  public static class Boot
  {
    /* Request boot */
  }
  
  public static class CountDownCancelled
  {
  }
}
