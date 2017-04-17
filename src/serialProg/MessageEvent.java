package serialProg;

import java.util.EventObject;

public class MessageEvent extends EventObject {

	public static final int DATA_AVAILABLE = 1;
	
	private int eventType;

	public MessageEvent(Object source,int eventtype) {
		super(source);
		eventType = eventtype;
	}
	
	public int getEventType(){
		return eventType;
	}

}
