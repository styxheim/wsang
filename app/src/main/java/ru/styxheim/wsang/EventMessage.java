package ru.styxheim.wsang;


public class EventMessage
{
  public enum EventType {
    /* ProposeMsg */
    PROPOSE, /* UI proporse new record or change old */

    /* StartRow */
    UPDATE,  /* Record info message (Service -> UI) */

    /* CountDownMsg */
    COUNTDOWN_START,
    COUNTDOWN_STOP,
    COUNTDOWN,      /* notify UI about current countdown */
    COUNTDOWN_END   /* countdown successfull comoleted */
  };

  public static class ProposeMsg
  {
    public int crewId;
    public int lapId;
    public int rowId = -1;

    public ProposeMsg(int crewId, int lapId)
    {
      this.crewId = crewId;
      this.lapId = lapId;
    }

    public void setRowId(int rowId)
    {
      this.rowId = rowId;
    }
  }

  public static class CountDownMsg
  {
    public long leftMs;
    public int lapId;
    public long endAtMs;
    
    public CountDownMsg(int lapId, long leftMs, long endAtMs)
    {
      this.leftMs = leftMs;
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
}
