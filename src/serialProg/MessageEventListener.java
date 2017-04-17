package serialProg;

import java.util.EventListener;

public interface MessageEventListener extends EventListener {
	
	public void eventReceived(MessageEvent event);

}
