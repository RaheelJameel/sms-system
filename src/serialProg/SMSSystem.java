package serialProg;

import java.io.*;
import java.sql.SQLException;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.UnsupportedCommOperationException;

public class SMSSystem {

	public static void main(String[] args) throws FileNotFoundException, IOException, NoSuchPortException, PortInUseException, UnsupportedCommOperationException, ClassNotFoundException, SQLException {
		
		String selectedCOMPort, host, databaseName, username, password, devHeadNumber;
		int port, isDevHead, creditMsgProbability;
		String [] fileInput;
		
		BufferedReader inputParmsReader = new BufferedReader(new FileReader(new File("config.cfg")));
		
		fileInput = inputParmsReader.readLine().split("~");
		selectedCOMPort = fileInput[1];
		
		fileInput = inputParmsReader.readLine().split("~");
		host = fileInput[1];
		
		fileInput = inputParmsReader.readLine().split("~");
		port = Integer.parseInt(fileInput[1]);
		
		fileInput = inputParmsReader.readLine().split("~");
		databaseName = fileInput[1];
		
		fileInput = inputParmsReader.readLine().split("~");
		username = fileInput[1];
		
		fileInput = inputParmsReader.readLine().split("~");
		password = fileInput[1];
		
		fileInput = inputParmsReader.readLine().split("~");
		isDevHead = Integer.parseInt(fileInput[1]);
		
		fileInput = inputParmsReader.readLine().split("~");
		devHeadNumber = fileInput[1];
		
		fileInput = inputParmsReader.readLine().split("~");
		creditMsgProbability = Integer.parseInt(fileInput[1]);
		
		inputParmsReader.close();
		inputParmsReader = null;
		
		Scanner scanner = new Scanner(System.in);
		
		
		
		MessageClass messageClass = new MessageClass();
		
		BlockingQueue<Integer> transferQueue = new LinkedBlockingQueue<Integer>();
		
		MessageHandlerClass listener = new MessageHandlerClass( messageClass, transferQueue, host, port, databaseName, username, password, creditMsgProbability );
		
		if ( isDevHead==1 ) {
			listener.enableDevHead( devHeadNumber );
		}
		
		messageClass.addDataEventListener(listener);
		
		Communicator gsmCom = new Communicator( messageClass, transferQueue, listener, selectedCOMPort );
		
        
		
		
        String inputString, phoneNumber, msg;
        
        boolean var1 = true;
        
        while(var1)
        {
    		//scanner.nextLine();
        	inputString = scanner.nextLine();
        	if (inputString.startsWith("exit"))
        	{
        		gsmCom.disconnect();
        		break;
        	}
        	else if (inputString.startsWith("AT"))
        	{
        		System.out.println(String.format("Your msg is %s", inputString));
        		gsmCom.sendCommand(inputString);
        		
        	}
        	else if (inputString.startsWith("sms"))
        	{
        		System.out.print("Phone Number (+92XXX...): ");
        		phoneNumber = scanner.nextLine();
        		
        		if (phoneNumber.startsWith("exit"))
            		continue;
        		
        		System.out.print("Message (Max 160 Char): ");
        		msg = scanner.nextLine();
        		
        		if (msg.startsWith("exit"))
            		continue;
        		else if ( msg.length() > 160 )
        			msg = msg.substring(0,160);
        		
        		gsmCom.sendSMS( phoneNumber, msg);
        	}
        	else if (inputString.startsWith("save"))
        	{
        		System.out.println("Files Saved");
        		gsmCom.saveFiles();
        		
        	}
        	else
        		System.out.println("Incorrect Command");
        }
		
		scanner.close();
	}
	

}