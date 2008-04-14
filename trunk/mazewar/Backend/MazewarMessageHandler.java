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

            if (_Message.MessageID == PeerMessageID.GOODBYE)
            {
                // TODO: Implement peers leaving.
            }
            else if (_Message.MessageID == PeerMessageID.HELLO)
            {
                Log.Write("RECV[ " + _Message.toString() + " ]");

                // L-L-L-L-Lamport!
                _Parent.Clock.syncMessageTime(_Message.Timestamp);

                _Parent.processHello(_Message.Source);
            }
            else if (_Message instanceof NewPeerMessage)
            {
                Log.Write("RECV[ " + _Message.toString() + " ]");

                // L-L-L-L-Lamport!
                _Parent.Clock.syncMessageTime(_Message.Timestamp);

                _Parent.processNewPeer((NewPeerMessage)_Message);
            }
            else if ((_Message instanceof PeerClientMessage) || (_Message instanceof PeerClientACK))
            {
                _Parent.queueClientMessageForDispatch(_Message);
                //_Parent.processClientMessage((PeerClientMessage)_Message);
            }
            else if (_Message instanceof NewPeerACK)
            {
                Log.Write("RECV[ " + _Message.toString() + " ]");

                // L-L-L-L-Lamport!
                _Parent.Clock.syncMessageTime(_Message.Timestamp);
                
                _Parent.processNewPeerAck((NewPeerACK)_Message);
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
