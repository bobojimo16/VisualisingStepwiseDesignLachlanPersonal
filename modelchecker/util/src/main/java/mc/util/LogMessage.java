package mc.util;

/**
 * A message to send back to the client
 */
public class LogMessage {
  private String message;
  /**
   * Clear the log window
   */
  private boolean clear = false;
  /**
   * Tell the client this is an error
   */
  private boolean error = false;
  private Location location = null;
  private int clearAmt = -1;

  private Thread thread;

  public LogMessage(String message) {
    this.message = message;
    this.thread = Thread.currentThread();
  }

/*  public LogMessage(String message, boolean clear, boolean error) {
    this(message, clear, error, null, -1, Thread.currentThread());
  }*/

  public LogMessage(String message, int clearAmt) {
    this(message);
    this.clearAmt = clearAmt;
    this.clear = true;
  }

  public LogMessage(String message, boolean clear, boolean error, Location location, int clearAmt, Thread thread) {
    this.message = message;
    this.clear = clear;
    this.error = error;
    this.location = location;
    this.clearAmt = clearAmt;
    this.thread = thread;
  }

  protected static String formatLocation(Location location) {
    return "(" + location.getLineStart() + ":" + location.getColStart() + ")";
  }

  public void printToConsole() {
    message = message.replace("@|black", "@|white");
    System.out.println(message);
  }

  public boolean hasExpired() {
    return thread.isInterrupted();
  }

  public String getMessage() {
    return this.message;
  }

  public boolean isClear() {
    return this.clear;
  }

  public boolean isError() {
    return this.error;
  }

  public Location getLocation() {
    return this.location;
  }

  public int getClearAmt() {
    return this.clearAmt;
  }

  public Thread getThread() {
    return this.thread;
  }
}
