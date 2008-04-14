import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import Backend.INetworkPeer;
import Backend.INetworkServer;


public class MazeWarPeer implements INetworkPeer, KeyListener {

	
	private Client ownClient;
	private MazeImpl maze;
	private boolean firstPeer;
	private boolean started;
	private INetworkServer server;
	private Map<String, Client> clients = new HashMap<String, Client>();
	
	public MazeWarPeer(Client client, MazeImpl maze, boolean firstPeer) {
		ownClient = client;
		this.maze = maze;
		this.firstPeer = firstPeer;
		clients.put(client.getName(), client);
		if(firstPeer)
		{
			maze.addClient(client);
			started = true;
		}
	}

	@Override
	public void attachToServer(INetworkServer server) throws IOException,
			ClassNotFoundException {
		this.server = server;
		
	}

	@Override
	public Object getCurrentState() {		
			return NetworkMessage.createStateMessage(ownClient);		
	}

	@Override
	public synchronized void onNetworkJoin() {
		System.out.println("Joined the game");
		this.notify();
		
	}

	@Override
	public void onNewPeerJoin(Object newPeerData) {
		addRemoteClient(newPeerData);
		
	}

	@Override
	public void onReceiveMessage(Object msg) {
		System.out.println("Received new message");
		NetworkMessage m = (NetworkMessage)msg;
		m.event = ClientEvent.convert(m.event);
		Client c = clients.get(m.name);
		if(m.event == ClientEvent.fire)
		{
			c.fire();
		}
		else if(m.event == ClientEvent.moveBackward)
		{
			c.backup();
		}
		else if(m.event == ClientEvent.moveForward)
		{
			c.forward();
		}
		else if(m.event == ClientEvent.turnLeft)
		{
			c.turnLeft();
		}
		else if(m.event == ClientEvent.turnRight)
		{
			c.turnRight();
		}
		maze.clientUpdate(c, m.event);
		
	}

	@Override
	public void onStartMessageProcessing() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onStopMessageProcessing() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Object processInitialPeerState(List<Object> initialPeerData) {
		System.out.println("Placing remote players");
		for(Object i : initialPeerData)
		{
			addRemoteClient(i);
		}
		System.out.println("Placing myself");
		maze.addClient(ownClient);
		return NetworkMessage.createStateMessage(ownClient);
	}

	private void addRemoteClient(Object peerState) {
		NetworkMessage state = (NetworkMessage)peerState;
		state.orientation = Direction.convert(state.orientation);
		RemoteClient client = new RemoteClient(state.name);
		clients.put(client.getName(), client);
		maze.addClient(client, state.point, state.orientation);
	}

	public synchronized void waitForJoin() throws InterruptedException {
		if(!firstPeer) this.wait();
		
	}

	@Override
	public void keyPressed(KeyEvent e) {
        // If the user pressed Q, invoke the cleanup code and quit. 
		NetworkMessage m = null;
        if((e.getKeyChar() == 'q') || (e.getKeyChar() == 'Q')) {
                Mazewar.quit();
        // Up-arrow moves forward.
        } else if(e.getKeyCode() == KeyEvent.VK_UP) {
                m = NetworkMessage.createClientUpdate(ownClient, ClientEvent.moveForward);
        // Down-arrow moves backward.
        } else if(e.getKeyCode() == KeyEvent.VK_DOWN) {
            m = NetworkMessage.createClientUpdate(ownClient, ClientEvent.moveBackward);
        // Left-arrow turns left.
        } else if(e.getKeyCode() == KeyEvent.VK_LEFT) {
            m = NetworkMessage.createClientUpdate(ownClient, ClientEvent.turnLeft);
        // Right-arrow turns right.
        } else if(e.getKeyCode() == KeyEvent.VK_RIGHT) {
            m = NetworkMessage.createClientUpdate(ownClient, ClientEvent.turnRight);
        // Spacebar fires.
        } else if(e.getKeyCode() == KeyEvent.VK_SPACE) {
            m = NetworkMessage.createClientUpdate(ownClient, ClientEvent.fire);
        }
        if(m != null)
        {
        	System.out.println("Sending player action");
        	try {
				server.broadcastMessage(m);
			} catch (IOException e1) {
				e1.printStackTrace();
				System.exit(1);
			} catch (ClassNotFoundException e1) {
				e1.printStackTrace();
				System.exit(1);
			} catch (InterruptedException e1) {
				e1.printStackTrace();
				System.exit(1);
			}
        }
}

	@Override
	public void keyReleased(KeyEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void keyTyped(KeyEvent arg0) {
		// TODO Auto-generated method stub
		
	}

}
