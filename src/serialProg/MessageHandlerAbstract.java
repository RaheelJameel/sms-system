package serialProg;

public abstract class MessageHandlerAbstract implements MessageEventListener {

	protected Communicator gsmCom;

	public MessageHandlerAbstract() {
		this.gsmCom = null;
	}

	public final void setCommunicator(Communicator GsmCom) {
		if ( this.gsmCom == null) {
			this.gsmCom = GsmCom;
		}
	}
}