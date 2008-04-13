import java.io.IOException;

public interface ITOMRecipient
{
    public abstract void dispatchMessage (PeerMessage msg)
        throws IOException, ClassNotFoundException;
}
