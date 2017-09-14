package serialProg;

import gnu.io.*;

import java.io.*;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.TooManyListenersException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class Communicator implements SerialPortEventListener {
	
	//File
	PrintWriter writer1,writer2,writer3,writer4,writer5,writer6;
	int fileCounter = 0;
	
	// Port Name to connect to
	String selectedCOMPort;
	
	
	//Buffer for Collective Input
	byte[] buffer = new byte[1024];
	
	
	//For Modified Collective Read from serial port
	//For input buffer
	byte[] readBuffer = new byte[4096];
	//For counting number of bytes read
	int numBytes;
	
	//passed from main GUI
    //GUI window = null;
	String portNames[] = new String[10];
	int portCount = 0;

    //for containing the ports that will be found
    private Enumeration ports = null;

    //this is the object that contains the opened port
    private CommPortIdentifier selectedPortIdentifier = null;
    private SerialPort serialPort = null;

    //input and output streams for sending and receiving data
    private InputStream input = null;
    private OutputStream output = null;

    //just a boolean flag that i use for enabling
    //and disabling buttons depending on whether the program
    //is connected to a serial port or not
    private boolean bConnected = false;

    //the timeout value for connecting with the port
    final static int TIMEOUT = 2000;

    //some ascii values for for certain things
    final static int LF_ASCII = 10;
    final static int CR_ASCII = 13;
    final static int CTRL_Z_ASCII = 26;

    //a string for recording what goes on in the program
    //this string is written to the GUI
    String logText = "";
    
    //strings for sending sms
    String phoneNumber = "", msg = "";
    
    //strings for saving incoming messages
    String inputNumber = "", inputMsg = "", outputMsg = "";
    String meNumber = "+923223044669";
    
    //int for storing incoming sms status
    //0 for unseen sms, 1 for only number received, 2 for number and msg received, 3 for sending sms
    int isSMS = 0;
    
    //number of tries taken for one message
    int tryCount = 0;
    
    //Counter for number of messages stored in GSM Module
    int msgCounter = 0;
    
    //Counter for unfetched messages in GSM Module
    int unRead = 0;
    
    //String to store header for previous and new messages
    String prevMsgHead = "", newMsgHead = "", msgBody = "";
    
    // Class to store the sms received
    MessageClass messageClass;
    
    // BlockingQueue to send data between threads
	private BlockingQueue<Integer> transferQueue;
    
    private class MessageFetch implements Runnable {

		public void run() {
			System.out.println( Thread.currentThread().getName() +  " New MessageFetchThread Started" );
            writer4.println( Thread.currentThread().getName() +  " New MessageFetchThread Started" );
            
			// Wait 15 seconds before sending command
			try
	    	{
				Thread.sleep(10000);
			}
	    	catch (InterruptedException e1)
	    	{
				e1.printStackTrace();
			}
			
			if ( unRead>0 ) {
				System.out.println( String.format("%s Messages unRead: %d", Thread.currentThread().getName(), unRead) );
                writer4.println( String.format("%s Messages unRead: %d", Thread.currentThread().getName(), unRead) );
				
				writeData( String.format("AT+CMGR=%d,0", msgCounter+1), CR_ASCII);
				
				// Start this process again in a new thread to ensure that there are no unread messages
				Thread nextThread = new Thread( new MessageFetch() );
				nextThread.start();
			}
			else {
				System.out.println( Thread.currentThread().getName() +  " Yay no need for MessageFetchThread" );
	            writer4.println( Thread.currentThread().getName() +  " Yay no need for MessageFetchThread" );
			}
		}
    	
    }
    
    
    public Communicator( MessageClass messageClass, BlockingQueue<Integer> transferQueue, MessageHandlerAbstract handler, String selectedCOMPort ) throws NoSuchPortException, PortInUseException, UnsupportedCommOperationException {
		super();
		this.messageClass = messageClass;
		this.transferQueue = transferQueue;
		handler.setCommunicator(this);
		
		this.selectedCOMPort = selectedCOMPort;

		System.out.println(Thread.currentThread().getName() + " called constructor");
		
		openFiles();
		
		searchForPorts();
		
		connect();
        if (getConnected() == true)
        {
        	if (initIOStream() == true)
            {
            	initListener();
            	
            	try
            	{
        			Thread.sleep(1000);
        		}
            	catch (InterruptedException e1)
            	{
        			e1.printStackTrace();
        		}
            	
            	// Delete All SMS
            	writeData( " AT+CMGDA=\"DEL ALL\" ", 13);
            	
            	try
            	{
        			Thread.sleep(1000);
        		}
            	catch (InterruptedException e1)
            	{
        			e1.printStackTrace();
        		}
            	
            	// Set the GSM module in text mode
            	writeData( "AT+CMGF=1", 13);
            }
        }
	
	}
    
    
	public boolean getConnected() {
		return bConnected;
	}


	public void setConnected(boolean bConnected) {
		this.bConnected = bConnected;
	}
	
	
	
	


	//search for all the serial ports
    //pre style="font-size: 11px;": none
    //post: adds all the found ports to a combo box on the GUI
    public void searchForPorts()
    {
        ports = CommPortIdentifier.getPortIdentifiers();

        while (ports.hasMoreElements())
        {
            CommPortIdentifier curPort = (CommPortIdentifier)ports.nextElement();

            logText = String.format("Available Port %s", curPort.getName());
        	System.out.println(logText);
            
            //get only serial ports
            if (curPort.getPortType() == CommPortIdentifier.PORT_SERIAL)
            {
                //window.cboxPorts.addItem(curPort.getName());
            	portNames[portCount] = curPort.getName();
            	++portCount;
            }
        }
        
        for (int i=0;i<portCount;i++)
        {
        	logText = String.format("Available Serial Port %s", portNames[i]);
        	System.out.println(logText);
        }
    }
    
    
    
    
    //connect to the selected port in the combo box
    //pre style="font-size: 11px;": ports are already found by using the searchForPorts
    //method
    //post: the connected comm port is stored in commPort, otherwise,
    //an exception is generated
    public void connect() throws NoSuchPortException, PortInUseException, UnsupportedCommOperationException
    {
        //String selectedPort = (String)window.cboxPorts.getSelectedItem();
    	
    	//Commenting this due to getting input from file
    	//Raheel One
    	//String selectedPort = portNames[0];
    	
    	
    	
    	
    	//String selectedPort = "COM3";
    	//selectedPortIdentifier = (CommPortIdentifier)portMap.get(selectedPort);
    	
    	
        CommPort commPort = null;

        //try
        //{
        	selectedPortIdentifier = CommPortIdentifier.getPortIdentifier(selectedCOMPort);
            //the method below returns an object of type CommPort
            commPort = selectedPortIdentifier.open(this.getClass().getName(), TIMEOUT);
            //the CommPort object can be casted to a SerialPort object
            serialPort = (SerialPort)commPort;
            
            serialPort.setSerialPortParams(9600,SerialPort.DATABITS_8,SerialPort.STOPBITS_1,SerialPort.PARITY_NONE);

            //for controlling GUI elements
            setConnected(true);

            //logging
            //logText = selectedPort + " opened successfully.";
            //window.txtLog.setForeground(Color.black);
            //window.txtLog.append(logText + "n");
            logText = String.format("Selected Port %s opened successfully.", selectedCOMPort);
            System.out.println(logText);
        /*}
        catch (PortInUseException e)
        {
            //logText = selectedPort + " is in use. (" + e.toString() + ")";
            //window.txtLog.setForeground(Color.RED);
            //window.txtLog.append(logText + "n");
            logText = String.format("Selected Port %s is in use. (%s)", selectedCOMPort, e.toString() );
            System.out.println(logText);
        }
        catch (Exception e)
        {
            //logText = "Failed to open " + selectedPort + "(" + e.toString() + ")";
            //window.txtLog.append(logText + "n");
            //window.txtLog.setForeground(Color.RED);
            logText = String.format("Selected Port %s is in use. (%s)", selectedCOMPort, e.toString() );
            System.out.println(logText);
        }*/
    }
    
    
    
    //open the input and output streams
    //pre style="font-size: 11px;": an open port
    //post: initialized input and output streams for use to communicate data
    public boolean initIOStream()
    {
    	System.out.println(Thread.currentThread().getName() + " called initIOStream");
        //return value for whether opening the streams is successful or not
        boolean successful = false;

        try {
            //
            input = serialPort.getInputStream();
            output = serialPort.getOutputStream();
            
            System.out.println("Initialising connection with GSM Module");
            
            output.write(CTRL_Z_ASCII);
            output.flush();
            
            output.write(CTRL_Z_ASCII);
            output.flush();
            
            TimeUnit.SECONDS.sleep(2);
            output.write( (int)'A' );
            output.flush();
            
            TimeUnit.SECONDS.sleep(2);
            output.write( (int)'T' );
            output.flush();

            TimeUnit.SECONDS.sleep(1);
            output.write(CR_ASCII);
            output.flush();
            
            System.out.println("Initialised connection with GSM Module");

            successful = true;
            return successful;
        }
        catch (IOException e)
        {
            logText = String.format("I/O Streams failed to open. (%s)", e.toString() );
            System.out.println(logText);
            
            return successful;
        }
        catch (InterruptedException e)
        {
            logText = String.format("TimeUnit encountered an Exception. (%s)", e.toString() );
            System.out.println(logText);
            
            return successful;
        }
    }
    
    
    
    //starts the event listener that knows whenever data is available to be read
    //pre style="font-size: 11px;": an open serial port
    //post: an event listener for the serial port that knows when data is received
    public void initListener()
    {
    	System.out.println(Thread.currentThread().getName() + " called initListener");
        try
        {
            serialPort.addEventListener(this);
            serialPort.notifyOnDataAvailable(true);
        }
        catch (TooManyListenersException e)
        {
            //logText = "Too many listeners. (" + e.toString() + ")";
            //window.txtLog.setForeground(Color.red);
            //window.txtLog.append(logText + "n");
            logText = String.format("Too many listeners. (%s)", e.toString() );
            System.out.println(logText);
        }
    }
    
    
    
    //Open Output files
    public void openFiles()
    {
    	closeFiles();
    	
    	try
		{
			FileWriter fw1 = new FileWriter("file1_ascii.txt", true);
			BufferedWriter bw1 = new BufferedWriter(fw1);
			writer1 = new PrintWriter(bw1);

			FileWriter fw2 = new FileWriter("file2_char.txt", true);
			BufferedWriter bw2 = new BufferedWriter(fw2);
			writer2 = new PrintWriter(bw2);

			FileWriter fw3 = new FileWriter("file3_message.txt", true);
			BufferedWriter bw3 = new BufferedWriter(fw3);
			writer3 = new PrintWriter(bw3);
			
			FileWriter fw4 = new FileWriter("file4_trim_message.txt", true);
			BufferedWriter bw4 = new BufferedWriter(fw4);
			writer4 = new PrintWriter(bw4);
			
			FileWriter fw5 = new FileWriter("file5_log_sms_message.txt", true);
			BufferedWriter bw5 = new BufferedWriter(fw5);
			writer5 = new PrintWriter(bw5);
			
			FileWriter fw6 = new FileWriter("file6_sent_sms_message.txt", true);
			BufferedWriter bw6 = new BufferedWriter(fw6);
			writer6 = new PrintWriter(bw6);
			
		} catch (IOException e)
		{
			e.printStackTrace();
		}
    }
    
    public void saveFiles()
    {
    	openFiles();
    }
    
    //Print END OF RUN in files
    public void printEnd()
    {
		writer1.println();
		writer1.println("END OF RUN");
		writer1.println();
		writer1.println();

		writer2.println();
		writer2.println("END OF RUN");
		writer2.println();
		writer2.println();

		writer3.println();
		writer3.println("END OF RUN");
		writer3.println();
		writer3.println();

		writer4.println();
		writer4.println("END OF RUN");
		writer4.println();
		writer4.println();

		writer5.println();
		writer5.println("END OF RUN");
		writer5.println();
		writer5.println();

		writer6.println();
		writer6.println("END OF RUN");
		writer6.println();
		writer6.println();
    }
    
    //Close Output FIles
    public void closeFiles()
    {
    	if (writer1 != null)
    		writer1.close();
    	
    	if (writer2 != null)
    		writer2.close();
    	
    	if (writer3 != null)
    		writer3.close();
    	
    	if (writer4 != null)
    		writer4.close();
    	
    	if (writer5 != null)
    		writer5.close();
    	
    	if (writer6 != null)
    		writer6.close();
    	
    }
    
	
    
    
  //disconnect the serial port
    //pre style="font-size: 11px;": an open serial port
    //post: closed serial port
    public void disconnect()
    {
    	System.out.println("Shutdown Initiated");
    	System.out.println("Deleting All Messages in GSM Module");
    	try
    	{
			Thread.sleep(3000);
		}
    	catch (InterruptedException e1)
    	{
			e1.printStackTrace();
		}
    	
    	// Delete All SMS
    	writeData( " AT+CMGDA=\"DEL ALL\" ", 13);
    	
    	try
    	{
			Thread.sleep(2000);
		}
    	catch (InterruptedException e1)
    	{
			e1.printStackTrace();
		}
        
        try {
			output.write(CTRL_Z_ASCII);
	        output.flush();
	        output.write(CTRL_Z_ASCII);
	        output.flush();
		}
        catch (IOException e1) {
			e1.printStackTrace();
		}
        
    	
        //close the serial port
        try
        {
            //writeData(0, 0);

            serialPort.removeEventListener();
            serialPort.close();
            input.close();
            output.close();
            setConnected(false);
            //window.keybindingController.toggleControls();
            
            //Print END OF RUN
            printEnd();
            //Close Files
            closeFiles();

            logText = "Disconnected.";
            //window.txtLog.setForeground(Color.red);
            //window.txtLog.append(logText + "n");
            System.out.println(logText);
        }
        catch (Exception e)
        {
            //logText = "Failed to close " + serialPort.getName() + "(" + e.toString() + ")";
            //window.txtLog.setForeground(Color.red);
            //window.txtLog.append(logText + "n");
            logText = String.format("Failed to close %s (%s)", serialPort.getName(), e.toString() );
            System.out.println(logText);
        }
    }
    
    
    
    
    //what happens when data is received
    //pre style="font-size: 11px;": serial event is triggered
    //post: processing on the data it reads
    
    public synchronized void serialEvent(SerialPortEvent evt) {
        if (evt.getEventType() == SerialPortEvent.DATA_AVAILABLE)
        {
        	System.out.println(Thread.currentThread().getName() + " called serialEvent");
            try
            {
                Thread.sleep(300);
    			//TimeUnit.SECONDS.sleep(1);
                
                //Get input and convert to String
                numBytes = input.read(readBuffer);
                logText = new String(readBuffer,0,numBytes);
                
                //Print input in files
                for(int i=0;i<logText.length();i++)
                {
                	++fileCounter;
                    writer2.println( String.format("%d: %c",fileCounter, logText.charAt(i) ));
                    writer1.println( String.format("%d: %d",fileCounter, (int)logText.charAt(i) ) );
                }
                
                writer3.println( String.format("%d: %s",fileCounter, logText) );
                
                //Remove Whitespace before and after message
                logText = logText.trim();
                
                writer4.println( String.format("%d: %s",fileCounter, logText) );
                System.out.println( String.format("%d: %s",fileCounter, logText) );
                

                if ( logText.contentEquals("RING") )
                {
                	System.out.println( "RRRRR" );
                	sendCommand("ATH");
                }
                else if ( logText.startsWith("+CMTI:") ||logText.startsWith("+CMGS:") || logText.startsWith("OK") || logText.startsWith(">") )
                {
                	System.out.println( "DDDDD" );
                	
                	//Cannot do this
                	//This causes the next command of fetch message to be ignored
                	//To forward all messages to another number
                	/*
                	if ( logText.startsWith("+CMGS:") )
                	{
            			if ( !(this.phoneNumber.contentEquals(meNumber)) )
            			{
            				sendSMS( meNumber, String.format("FWD\r\n%s",this.msg) );
            			}
                	}
                	*/
                	if ( logText.contains("+CMGS:") ) {
                		transferQueue.offer(1);
                	}
                	else if ( logText.contains("ERROR") ) {
                		System.out.println("GOT ERROR - Informing");
                		transferQueue.offer(-1);
                	}
                	
                	
                	int i=0;
                	while ( logText.contains("+CMTI:") )
                	{
                    	++unRead;
                    	++i;
                    	logText = logText.substring( logText.indexOf("+CMTI:")+2 );
                    	
                    	//Test Ones
                    	//The proper implementation would be to call this when process is idle
                    	//Thread.sleep(300);
                    	writeData( String.format("AT+CMGR=%d,0", msgCounter+i), CR_ASCII);
                    	
                    	Thread messageFetchThread = new Thread( new MessageFetch() );
                    	messageFetchThread.start();
                	}
            		System.out.println( String.format( "unRead: %d", unRead) );
                    writer4.println( String.format( "unRead: %d", unRead) );
            		System.out.println( String.format( "msgCounter: %d", msgCounter) );
                    writer4.println( String.format( "msgCounter: %d", msgCounter) );
                	
                	//logText.indexOf("+CMTI:");
                	//logText.indexOf(str, fromIndex);
                	
                }
                else if ( logText.startsWith("+CMGR:") )
                {
                	System.out.println( "CCCCC" );
                	
                    ++msgCounter;
                    --unRead;
                    
                	if ( msgCounter > 47)
                	{
                		msgCounter = 0;
                		sendDelayCommand( " AT+CMGDA=\"DEL ALL\" ", CR_ASCII, 500);
                		sendDelayCommand( " AT+CMGDA=\"DEL ALL\" ", CR_ASCII, 1000);
                	}
                	
                	newMsgHead = logText.substring(0, logText.indexOf( (char)CR_ASCII ) );
                	
                	//Remove Last OK of GSM Module from SMS
                	msgBody = logText.substring( logText.indexOf( (char)LF_ASCII )+1 );
                	msgBody = msgBody.substring( 0, msgBody.length()-2 ).trim() ;
                	
                	StringBuilder tempSB = new StringBuilder(newMsgHead);
            		tempSB.delete(0, newMsgHead.indexOf(',')+2 );
            		inputNumber = tempSB.toString();
            		
            		inputNumber = inputNumber.substring(0, inputNumber.indexOf("\"") );
            		
            		System.out.println( String.format( "newMsgHead: %s", newMsgHead) );
            		System.out.println( String.format( "inputNumber: %s", inputNumber) );
            		System.out.println( String.format( "msgBody: %s", msgBody) );
            		
            		writer5.println( newMsgHead );
            		writer5.println( msgBody );
            		
            		if ( newMsgHead.contains("REC UNREAD") ) {
            			messageClass.setData( new MessageUnit( inputNumber, msgBody ) );
            		}
            		else {
            			System.out.println("Repeat SMS");
                		writer5.println("Repeat SMS");
            		}
            		writer5.println();
                }
                else if ( logText.contains("+CMGS:") ) {
            		transferQueue.offer(1);
            	}
            	else if ( logText.contains("ERROR") ) {
            		System.out.println("GOT ERROR - Informing");
            		transferQueue.offer(-1);
            	}
                
                
                
                
                
                /*
                if ( logText.contentEquals("OK") || logText.contentEquals(">") )
                {
                	System.out.println( "AAAAAA" );
            		System.out.println( String.format( "unRead: %d", unRead) );
                    writer4.println( String.format( "unRead: %d", unRead) );
            		System.out.println( String.format( "msgCounter: %d", msgCounter) );
                    writer4.println( String.format( "msgCounter: %d", msgCounter) );
                }
                else if ( logText.contentEquals("RING") )
                {
                	System.out.println( "RRRRR" );
                	sendCommand("ATH");
                }
                else if ( logText.contentEquals("ERROR") )
                {
                	System.out.println( "BBBBB" );
                }
                else if ( logText.startsWith("+CMGR:") )
                {
                	System.out.println( "CCCCC" );
                	if ( autoMsgRetrieve == 1 )
                	{
                    	++msgCounter;
                    	--unRead;
                    	autoMsgRetrieve = 0;
                	}
                	if ( msgCounter > 49)
                	{
                		msgCounter = 0;
                		writeData( " AT+CMGDA=\"DEL ALL\" ", CR_ASCII);
                	}
                	
                	newMsgHead = logText.substring(0, logText.indexOf( (char)CR_ASCII ) );
                	
                	//Remove Last OK of GSM Module from SMS
                	msgBody = logText.substring( logText.indexOf( (char)LF_ASCII )+1 );
                	msgBody = msgBody.substring( 0, msgBody.length()-2 ).trim() ;
                	
                	StringBuilder tempSB = new StringBuilder(newMsgHead);
            		tempSB.delete(0, newMsgHead.indexOf(',')+2 );
            		inputNumber = tempSB.toString();
            		
            		inputNumber = inputNumber.substring(0, inputNumber.indexOf("\"") );
            		
            		System.out.println( String.format( "newMsgHead: %s", newMsgHead) );
            		System.out.println( String.format( "inputNumber: %s", inputNumber) );
            		System.out.println( String.format( "msgBody: %s", msgBody) );
            		
            		writer5.println( newMsgHead );
            		writer5.println( msgBody );
            		writer5.println();
            		
            		int i,tempInt,digitCount=0;
                	
                	for (i=1;i<inputNumber.length();i++)
                	{
                		tempInt = (int)inputNumber.charAt(i);
                		if ( tempInt>47 && tempInt<58 )
                			++digitCount;
                	}
                	if (digitCount < 9)
                		return;
            		
            		int offset = msgBody.length() - 100;
            		if ( offset > 0 )
            		{
            			msgBody = msgBody.substring(0, msgBody.length()-offset);
            		}
            		
            		outputMsg = String.format("Your number is:\r\n%s\r\n\r\nAnd your msg was:\r\n%s", inputNumber,  msgBody);
            		
            		sendSMS(inputNumber, outputMsg);
                }
                else if ( logText.startsWith("+CMGS:") || logText.startsWith("OK") || logText.startsWith(">") )
                {
                	System.out.println( "DDDDD" );
                	
                	//Cannot do this
                	//This causes the next command of fetch message to be ignored
                	//To forward all messages to another number
                	///aaaaaaaaaaaaaaaaaa*
                	if ( logText.startsWith("+CMGS:") )
                	{
            			if ( !(this.phoneNumber.contentEquals(meNumber)) )
            			{
            				sendSMS( meNumber, String.format("FWD\r\n%s",this.msg) );
            			}
                	}
                	//*aaaaaaaaaaaaaaaaaaaa/
                	
                	int i=0;
                	while ( logText.contains("+CMTI:") )
                	{
                    	++unRead;
                    	++i;
                    	logText = logText.substring( logText.indexOf("+CMTI:")+2 );
                    	
                    	//Test Ones
                    	//The proper implementation would be to call this when process is idle
                    	writeData( String.format("AT+CMGR=%d,1", msgCounter+i), CR_ASCII);
                    	autoMsgRetrieve = 1;
                	}
            		System.out.println( String.format( "unRead: %d", unRead) );
                    writer4.println( String.format( "unRead: %d", unRead) );
            		System.out.println( String.format( "msgCounter: %d", msgCounter) );
                    writer4.println( String.format( "msgCounter: %d", msgCounter) );
                	
                	//logText.indexOf("+CMTI:");
                	//logText.indexOf(str, fromIndex);
                	
                }
                else
                {
                	System.out.println( "EEEEE" );
                	writeData( String.format("AT+CMGR=%d,1", msgCounter+1), CR_ASCII);
                	++unRead;
                	autoMsgRetrieve = 1;
                }
                */
                logText = "";
            }
            catch (Exception e)
            {
                logText = String.format("Failed to read data. (%s)", e.toString() );
                System.out.println(logText);
            }
        }
    }
    
    //New serialEvent
    /*
    public void serialEvent(SerialPortEvent evt) {
        if (evt.getEventType() == SerialPortEvent.DATA_AVAILABLE)
        {
            try
            {
            	int data;
            	
                int len = 0;
                while ( ( data = input.read()) > -1 )
                {
                	++fileCounter;
                	writer1.println( String.format("%d: %d",fileCounter,data) );
                	writer2.println( String.format("%d: %c",fileCounter,(char)data) );
                    //System.out.println(".");
                    if ( data == '\n')
                    {
                    	//System.out.print(".n");
                    	logText = "";
                    	break;
                    }
                    if ( data == '\r')
                    {
                    	//System.out.print(".r");
                    	logText = "";
                    	break;
                    }
                    if ( data == 62 )
                    {
                    	++fileCounter;
                    	data = input.read();
                    	writer1.println( String.format("%d: %d",fileCounter,data) );
                    	writer2.println( String.format("%d: %c",fileCounter,(char)data) );
                    	logText = "";
                    	break;
                    }
                    buffer[len++] = (byte) data;
                }
                //System.out.println("+");
                logText = new String(buffer,0,len);
                if (logText.trim().length() > 0)
                {
                	System.out.print("Output:");
                	System.out.println(logText);
                	writer3.println( String.format("%d: %s",fileCounter,logText) );
                	//System.out.println("End");
                	if (logText.startsWith("+CMT:"))
                	{
                		isSMS = 1;
                		
                		StringBuilder tempSB = new StringBuilder(logText);
                		tempSB.delete(0, 7);
                		inputNumber = tempSB.toString();
                    	//System.out.println(inputNumber);
                    	
                    	char[] tempArray = new char[16];
                    	tempArray[0] = '+';
                    	int i,tempInt;
                    	
                    	for (i=1;i<inputNumber.length() && i<16;i++)
                    	{
                    		tempInt = (int)inputNumber.charAt(i);
                    		if ( tempInt<48 || tempInt>57 )
                    		{
                    			break;
                    		}
                    		tempArray[i] = (char)tempInt;
                    	}
                    	inputNumber = new String(tempArray,0,i);
                    	if (inputNumber.length()<5)
                    		isSMS = 0;
                		
                    	//inputNumber = inputNumber.substring(0, inputNumber.indexOf("\""));
                    	//System.out.println(inputNumber);
                    	logText = "";
                	}
                	else if (isSMS == 1)
                	{
                		
                		isSMS = 2;
                		
                		inputMsg = logText;
                		
                		int inputLength = inputNumber.length() + inputMsg.length();
                		int offset = inputLength - 110;
                		if ( offset > 0 )
                		{
                			inputMsg = inputMsg.substring(0, offset+1);
                		}
                		
                		outputMsg = String.format("Your number is:\r\n%s\r\n\r\nAnd your msg was:\r\n%s", inputNumber,  inputMsg);
                		
                		//System.out.println(outputMsg);
                		
                		sendSMS(inputNumber, outputMsg);
                		
                	}
                	//else if (isSMS == 3)
                	else if (tryCount>0)
                	{
                		if ( logText.equals(new String("ERROR")) && tryCount<5 )
                		{
                			sendSMS( this.phoneNumber, this.msg);
                		}
                		else if ( logText.startsWith("+CMGS: ") || tryCount>4 )
                		{
                			isSMS = 0;
                			tryCount = 0;
                			if ( !(this.phoneNumber.contentEquals(meNumber)) )
                			{
                				sendSMS( meNumber, String.format("FWD\r\n%s",this.msg) );
                			}
                		}
                	}
                }
            	logText = "";
            }
            catch (Exception e)
            {
                logText = String.format("Failed to read data. (%s)", e.toString() );
                System.out.println(logText);
            }
        }
    }
    */
    
    
    
    //method that can be called to send data
    //pre style="font-size: 11px;": open serial port
    //post: data sent to the other device
    /*
    public void writeData(int leftThrottle, int rightThrottle)
    {
        try
        {
            output.write(leftThrottle);
            output.flush();
            //this is a delimiter for the data
            output.write(DASH_ASCII);
            output.flush();

            output.write(rightThrottle);
            output.flush();
            //will be read as a byte so it is a space key
            output.write(SPACE_ASCII);
            output.flush();
        }
        catch (Exception e)
        {
            //logText = "Failed to write data. (" + e.toString() + ")";
            //window.txtLog.setForeground(Color.red);
            //window.txtLog.append(logText + "n");
            logText = String.format("Failed to write data. (%s)", e.toString() );
            System.out.println(logText);
        }
    }
    */
    
    public void sendChar(char endChar) {
    	try {
			output.write(endChar);
	        output.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
    
    
    
    //New writeData
    public synchronized void writeData(String inputString, int endChar)
    {
    	//try
    	//{
		//	Thread.sleep(600);
		//}
    	//catch (InterruptedException e1)
    	//{
		//	e1.printStackTrace();
		//}
    	int sizeOfInput = inputString.length();
    	int[] command = new int[inputString.length()];
    	
		for(int i=0;i<inputString.length();i++)
		{
			command[i] = (int)(inputString.charAt(i));
		}
    	
        try
        {
        	for(int i=0;i<sizeOfInput;i++)
        	{
        		output.write(command[i]);
                //output.flush();
        	}
        	output.write(endChar);
            output.flush();
        }
        catch (Exception e)
        {
            //logText = "Failed to write data. (" + e.toString() + ")";
            //window.txtLog.setForeground(Color.red);
            //window.txtLog.append(logText + "n");
            logText = String.format("Failed to write data. (%s)", e.toString() );
            System.out.println(logText);
        }
    }
    
    public void sendCommand(String inputString)
    {
    	writeData(inputString, CR_ASCII);
    }
    
    public void sendDelayCommand(String inputString, int endChar, int delay)
    {
    	try
    	{
			Thread.sleep(delay);
		}
    	catch (InterruptedException e1)
    	{
			e1.printStackTrace();
		}
    	writeData(inputString, endChar);
    }
    
    public void sendSMS(String phoneNumber1, String msg1)
    {
    	System.out.println(Thread.currentThread().getName() + " called sendSMS - Now waiting 10 sec");
		try {
			Thread.sleep(10000);
		}
		catch (InterruptedException e) {
			e.printStackTrace();
		}
		sendSMSCommand(phoneNumber1, msg1);
    }
    
    
    private synchronized void sendSMSCommand(String phoneNumber1, String msg1)
    {
    	//AT+CMGF=1
    	//AT+CMGS=\"+YYxxxxxxxxxx\"\r
    	++tryCount;
    	
    	this.phoneNumber = phoneNumber1;
    	this.msg = msg1;
    	
    	if ( isSMS == 2 )
    		isSMS = 3;
    	
    	System.out.println();
		System.out.println(String.format("Recipient: %s", phoneNumber));
		System.out.println(String.format("Message: %s", msg));
		
		writer6.println( String.format("Recipient: %s", phoneNumber) );
		writer6.println("Message:");
		writer6.println(msg);
		writer6.println("-------------------------------");
		writer6.println();
		writer6.println();
		
    	String command = String.format("AT+CMGS=\"%s\"", phoneNumber);
    	
    	/*
    	writeData("AT+CMGF=1", CR_ASCII);
    	
    	try
    	{
			TimeUnit.SECONDS.sleep(1);
		}
    	catch (InterruptedException e)
        {
            logText = String.format("TimeUnit encountered an Exception. (%s)", e.toString() );
            System.out.println(logText);
        }
    	*/
    	writeData(command, CR_ASCII);
    	
    	try
    	{
			//TimeUnit.SECONDS.sleep(1);
            Thread.sleep(200);
		}
    	catch (Exception e)
        {
            logText = String.format("Sleep encountered an Exception. (%s)", e.toString() );
            System.out.println(logText);
        }
    	
    	
    	writeData(msg, CTRL_Z_ASCII);
    	
    	/*
    	try
    	{
			TimeUnit.SECONDS.sleep(1);
		}
    	catch (InterruptedException e)
        {
            logText = String.format("TimeUnit encountered an Exception. (%s)", e.toString() );
            System.out.println(logText);
        }
    	
    	
    	try
    	{
    		output.write(CTRL_Z_ASCII);
            output.flush();
    	}
        catch (Exception e)
        {
            logText = String.format("Failed to write data. (%s)", e.toString() );
            System.out.println(logText);
        }
        */
    }
    
    
	
}
