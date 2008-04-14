import java.io.Serializable;


public class NetworkMessage implements Serializable{
	public enum MessageTypes {ClientAdded, ClientEvent, ClientState};
	
	public MessageTypes type;
	public String name;
	public Point point;
	public Direction orientation;
	public ClientEvent event;
	
	
	public static NetworkMessage createStateMessage(Client c)
	{
		NetworkMessage m = new NetworkMessage(c);
		m.type = MessageTypes.ClientState;
		return m;
	}	


	private  NetworkMessage(Client c) {		
		this.name = c.getName();
		this.point = c.getPoint();
		this.orientation = c.getOrientation();
	}


	public static NetworkMessage createClientUpdate(Client c, ClientEvent clientevent) {
		NetworkMessage m = new NetworkMessage(c);
		m.type = MessageTypes.ClientEvent;
		m.event = clientevent;
		return m;
		
	}
}
