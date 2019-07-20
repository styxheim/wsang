package ru.styxheim.wsang;


public class EventMessage
{
  public enum EventType {
    NEW,    /* Need a new record */
    UPDATE,  /* Record info message (Service -> UI) */
    CHANGED, /* row changed (UI -> Service) */
    COUNTDOWN_START,
    COUNTDOWN_STOP,
    COUNTDOWN,      /* notify UI about current countdown */
    COUNTDOWN_END   /* countdown successfull comoleted */
  };

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
