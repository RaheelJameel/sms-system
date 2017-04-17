package serialProg;

import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.event.EventListenerList;

public class MessageClass {
	
	private LinkedList<MessageUnit> list;
	private EventListenerList eventListeners;

	public MessageClass() {
		list = new LinkedList<MessageUnit>();
		eventListeners = new EventListenerList();
	}
	
	public synchronized void addDataEventListener( MessageEventListener listener ){
		
		eventListeners.add( MessageEventListener.class, listener );
	}
	
	public synchronized void removeDataEventListener( MessageEventListener listener ){
		
		eventListeners.remove( MessageEventListener.class, listener );
	}
	
	
	//Class to be used in new threads
	//Class is to be used in fireDataEvent()
	private class MessageClassRunnable implements Runnable {
		
		private MessageEventListener listener;
		private MessageEvent event;
		
		public MessageClassRunnable( MessageEventListener listener, MessageEvent event ) {
			this.listener = listener;
			this.event = event;
		}

		public void run() {
			listener.eventReceived(event);
		}
	}
	
	
	protected synchronized void fireDataEvent(MessageEvent dataEvent6728){
		
		System.out.println(Thread.currentThread().getName() + " called fireDataEvent");
		
	    Object[] listeners = eventListeners.getListenerList();
	    
	    // get number of listeners
	    int numListeners = listeners.length;
	    
	    // create a pool of threads, max numListeners jobs will execute in parallel
	    ExecutorService threadPool = null;
	    if( numListeners>0 ) {
	    	threadPool = Executors.newFixedThreadPool(numListeners);
	    }

	    // loop through each listener and pass on the event if needed
	    for (int i = 0; i<numListeners; i+=2) {
	         if (listeners[i]==MessageEventListener.class) {
	             // pass the event to the listeners event dispatch method
	        	 
	             //((MessageEventListener)listeners[i+1]).eventReceived(dataEvent6728);
	        	 
	        	 // submit jobs to be executing by the pool
	             threadPool.submit( new MessageClassRunnable( (MessageEventListener)listeners[i+1], dataEvent6728 ) );
	         }
	    }
	    
	    // once last job has been submitted to the service it should be shut down
	    if( threadPool!=null ) {
	    	threadPool.shutdown();
	    }
	}

	public MessageUnit getData() {
		MessageUnit messageUnit = null;
		try {
			messageUnit = list.removeFirst();
		}
		catch (NoSuchElementException exception) {
			messageUnit = null;
		}
		return messageUnit;
	}

	public synchronized void setData( MessageUnit messageUnit ) {
		
		System.out.println(Thread.currentThread().getName() + " started setData");
		
		list.add( messageUnit );
		
		//Thread newThread = new Thread(this);
		//newThread.start();
		
		fireDataEvent( new MessageEvent( this, MessageEvent.DATA_AVAILABLE ) );
		
		System.out.println(Thread.currentThread().getName() + " ended setData");
	}
}
