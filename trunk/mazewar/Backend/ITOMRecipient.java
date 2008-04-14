package Backend;

import java.io.IOException;

interface ITOMRecipient
{
    public abstract void dispatchMessage (PeerMessage msg)
        throws IOException, ClassNotFoundException;
}
