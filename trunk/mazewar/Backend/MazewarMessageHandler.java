package Backend;

public class MazewarMessageHandler implements Runnable
{
    private final MazewarServer _Parent;
    private final PeerMessage _Message;

    public MazewarMessageHandler (MazewarServer parent, PeerMessage msg)
    {
        _Parent = parent;
        _Message = msg;
    }

    public void run ()
    {
        try
        {
            Log.Write("RECV[ " + _Message.toString() + " ]");
            
            // L-L-L-L-Lamport!
            _Parent.Clock.syncMessageTime(_Message.Timestamp);

            if (_Message.MessageID == PeerMessageID.GOODBYE)
            {
                // TODO: Implement peers leaving.
            }
            else if (_Message.MessageID == PeerMessageID.HELLO)
            {
                _Parent.processHello(_Message.Source);
            }
            else if (_Message instanceof NewPeerMessage)
            {
                _Parent.processNewPeer((NewPeerMessage)_Message);
            }
            else if (_Message instanceof PeerClientMessage)
            {
                _Parent.processClientMessage((PeerClientMessage)_Message);
            }
            else if (_Message instanceof NewPeerACK)
            {
                _Parent.processNewPeerAck((NewPeerACK)_Message);
            }
            else if (_Message instanceof PeerClientACK)
            {
                _Parent.processClientAck((PeerClientACK)_Message);
            }
        }
        catch (Exception e)
        {
            synchronized (Log.class)
            {
                Log.Write("Exception when processing message " + _Message.toString() + ":" + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
