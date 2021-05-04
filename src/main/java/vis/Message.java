package vis;

/**
 * Every message should implement this interface
 * in order for vis to get the sender's key and find
 * the actor ref of the sender of the message.
 * @author Siddhanth Venkateshwaran
 */
public interface Message {
    long getSenderKey();
}
