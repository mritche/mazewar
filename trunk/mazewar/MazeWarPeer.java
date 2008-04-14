import java.io.IOException;
import java.io.Serializable;
import java.util.List;

import Backend.INetworkPeer;
import Backend.INetworkServer;


public class MazeWarPeer implements INetworkPeer {

	private static class ClientState implements Serializable
	{
		public ClientState(Client client) {
			name = client.getName();
			location = client.getPoint();
			orientation = client.getOrientation();
		}
		public String name;
		public Point location;
		public Direction orientation;
	}
	
	private Client ownClient;
	private MazeImpl maze;
	private boolean firstPeer;
	private boolean started;
	
	public MazeWarPeer(Client client, MazeImpl maze, boolean firstPeer) {
		ownClient = client;
		this.maze = maze;
		this.firstPeer = firstPeer;
		if(firstPeer)
		{
			maze.addClient(client);
			started = true;
		}
	}

	@Override
	public void attachToServer(INetworkServer server) throws IOException,
			ClassNotFoundException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Object getCurrentState() {
		if(started)
		{
		return new ClientState(ownClient);
		}
		else
		{
			return "Jiberish";
		}
	}

	@Override
	public synchronized void onNetworkJoin() {
		this.notify();
		
	}

	@Override
	public void onNewPeerJoin(Object newPeerData) {
		addRemoteClient(newPeerData);
		
	}

	@Override
	public void onReceiveMessage(Object msg) {
		// TODO Auto-generated method stub
		
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
		return new ClientState(ownClient);
	}

	private void addRemoteClient(Object peerState) {
		ClientState state = (ClientState)peerState;
		state.orientation = Direction.convert(state.orientation);
		RemoteClient client = new RemoteClient(state.name);
		maze.addClient(client, state.location, state.orientation);
	}

	public synchronized void waitForJoin() throws InterruptedException {
		if(!firstPeer) this.wait();
		
	}

}
