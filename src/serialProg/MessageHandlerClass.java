package serialProg;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class MessageHandlerClass extends MessageHandlerAbstract {
	
	private MessageClass messageClass;
	private BlockingQueue<Integer> transferQueue;
	private String currentTime;
	
	// Var to count total number of Sent SMS
	int sentSMSCount;
	
	// Var to count total number of recived SMS
	int recievedSMSCount;
	
	// Var to count total number of failed SMS
	int failedSentSMS;
	
	// Var to count admin cancel commands
	int adminCancelCount;
	
	// Var to count admin complete commands
	int adminCompleteCount;
	
	// Database Vars
	private MySQLDatabase database;
	private Statement statement;
	
	// Developer Mode / Debugging Mode
	private boolean isDevHead;
	private String devHeadPhoneNumber;
	private int creditMsgProbability;
	
	// Information Messages
	private final String infoMsg = "NETRONiX Complaint System\n\nReply\n\"info\" or \"help\" to see this message\n\"format\" for message format\n\"status\" to view complaint status\n\"cancel\" to cancel complaint";
	
	private final String formatMsg = "Message Format:\nHostel # Room #\n\nExample:\nHostel 1 Room 1\nor\nHostel 11 Room A1\n\nFor more info reply with \"help\"\n\nNETRONiX";
	
	private final String creditsMsg = "This Complaint System was created by:\n\nScarecrow - Batch 24\n(Raheel Jameel)\n\nDuring Spring 2017\nIt is written in Java\n\n\nReply \"credits\" to view this\n\n\nNETRONiX";
	
	private final String studentIncorrectMsg = "ERROR Incorrect Format\n\nMessage Format:\nHostel # Room #\n\nExample:\nHostel 1 Room 1\nor\nHostel 11 Room A1\n\nFor more info reply with \"help\"\n\nNETRONiX";
	
	private final String memberIncorrectMsg = "Incorrect Command\n\nReply with \"commands\" to view list of commands available to you\n\n\nNETRONiX";
	
	private final String [] memberCommandMsg = new String[]{
			"NETRONiX Commands:\n\n\"view all\"\nto view all complaints assigned to you\n\n\"view [compliant#]\"\nto view details of a complaint assigned to you\n...",
			"...\n\"done [complaint#]\"\nto mark complaint as resolved"};

	public MessageHandlerClass( MessageClass messageClass, BlockingQueue<Integer> transferQueue, String host, int port, String dbName, String username, String password, int creditMsgProbability ) throws ClassNotFoundException, SQLException {
		isDevHead = false;
		devHeadPhoneNumber = null;
		this.creditMsgProbability = creditMsgProbability;
		
		this.messageClass = messageClass;
		this.transferQueue = transferQueue;
		
		database = new MySQLDatabase(host,port,dbName,username,password);
		
		sentSMSCount = 0;
		recievedSMSCount = 0;
		failedSentSMS = 0;
		adminCancelCount = 0;
		adminCompleteCount = 0;
		
		scheduleStatusSMS();
		
		sendStartupSMS();
	}
	
	public void enableDevHead(String devHeadPhoneNumber) {
		isDevHead = true;
		this.devHeadPhoneNumber = devHeadPhoneNumber;
	}
	
	public void disableDevHead() {
		isDevHead = false;
		devHeadPhoneNumber = null;
	}
	
	private void databaseConnect() {
		// Starting Connection to Database
		try {
			database.connect();
		}
		catch (ClassNotFoundException | SQLException e2) {
			e2.printStackTrace();
			System.out.println("ERROR-------1");
			System.exit(0);
		}
		statement = database.getStatement();
	}
	
	private void databaseDisconnect() {
		// Closing Connection to Database
		try {
			statement.close();
		}
		catch (SQLException e) {
			e.printStackTrace();
		}
		database.disconnect();
		statement = null;
	}

	public synchronized void eventReceived( MessageEvent event ) {
		
		if (event.getEventType() == MessageEvent.DATA_AVAILABLE) {
			
			System.out.println(Thread.currentThread().getName() + " called Class's eventReceived");

			String studentMessage = null;
			String memberMessage = null;
			int memberReg = 0;
			String firstName = null;
			String lastName = null;
			int batch = 0;
			String memberNumber = null;
			String hostelHeadMessage = null;
			String hostelHeadNumber = null;
			String comments = "none";
			String complaintTime = null;
			
			ResultSet resultSet = null;
			
			int hostel = -1;
			int room = -1;
			String roomString = null;
			
			int sms_id = 0;
			int complaint_id = 0;
			
			int complaint_status = -1;
			
			int solved = -1;
			int assigned = -1;
			int pending = -1;
			
			
			// Fetch 1st message from class
			MessageUnit messageUnit = messageClass.getData();
			
			if ( messageUnit!=null ){
				recievedSMSCount++;
				System.out.println(Thread.currentThread().getName() + " Number: " + messageUnit.msgNumber);
				System.out.println(Thread.currentThread().getName() + " Message: " + messageUnit.msgBody);
				
				// Starting Connection to Database
				databaseConnect();
				
				try {
					currentTime = getCurrentMySQLTime();
					
					PreparedStatement preparedStatement = database.prepareStatement("INSERT INTO `sms_received` VALUES (NULL, ?, ?, ?)");
					preparedStatement.setString(1, messageUnit.msgNumber);
					preparedStatement.setString(2, messageUnit.msgBody);
					preparedStatement.setString(3, currentTime);
					preparedStatement.executeUpdate();
					
					resultSet = statement.executeQuery("SELECT LAST_INSERT_ID();");
					resultSet.next();
					sms_id = resultSet.getInt(1);
					resultSet.close();
				}
				catch (SQLException e) {
					e.printStackTrace();
					System.out.println("ERROR-------2");
					databaseDisconnect();
					return;
				}
				complaintTime = currentTime;
				
				System.out.println(Thread.currentThread().getName() + " Message ID: " + sms_id);
				
				if ( messageUnit.msgNumber.length()<10 ) {
					databaseDisconnect();
					return;
				}
				else {
					try {
						Double.parseDouble( messageUnit.msgNumber.substring(1) );
					}
					catch (NumberFormatException e) {
						databaseDisconnect();
						return;
					}
				}
				
				String msgTemp = messageUnit.msgBody.trim().toLowerCase();
				
				
				if ( msgTemp.contentEquals("help") || msgTemp.contentEquals("info") || msgTemp.contentEquals("information") )  {
					studentMessage = infoMsg;
					sendSMS( messageUnit.msgNumber, studentMessage );
					databaseDisconnect();
					return;
				}
				else if ( msgTemp.contentEquals("format") ) {
					studentMessage = formatMsg;
					sendSMS( messageUnit.msgNumber, studentMessage );
					databaseDisconnect();
					return;
				}
				else if ( msgTemp.contentEquals("credit") || msgTemp.contentEquals("credits") ) {
					studentMessage = creditsMsg;
					sendSMS( messageUnit.msgNumber, studentMessage );
					databaseDisconnect();
					return;
				}
				else if ( msgTemp.contentEquals("command") || msgTemp.contentEquals("commands") ) {
					try {
						studentMessage = studentIncorrectMsg;
						resultSet = statement.executeQuery( String.format("SELECT reg_number FROM `member` WHERE phone_number=\'%s\';",messageUnit.msgNumber) );
						if ( resultSet.next() ) {
							resultSet.close();
							sendSplitSMS(messageUnit.msgNumber, memberCommandMsg);
							databaseDisconnect();
							return;
						}
						sendSMS( messageUnit.msgNumber, studentMessage );
					}
					catch (SQLException e) {
						e.printStackTrace();
						System.out.println("ERROR-------3");
					}
					databaseDisconnect();
					return;
				}
				else if ( msgTemp.contentEquals("status") ) {
					try {
						resultSet = statement.executeQuery( String.format("SELECT complaint_id,hostel_number,room,comment,timestamp,status FROM `complaint` NATURAL JOIN `sms_received` WHERE phone_number=\'%s\' ORDER BY status LIMIT 0,1;",messageUnit.msgNumber) );
						if ( resultSet.next() ) {
							complaint_id = resultSet.getInt(1);
							hostel = resultSet.getInt(2);
							roomString = resultSet.getString(3);
							comments = resultSet.getString(4);
							complaintTime = resultSet.getString(5);
							complaint_status = resultSet.getInt(6);
							resultSet.close();
							
							if (complaint_status<2) {
								if (complaint_status==0) {
									studentMessage = String.format( "Complaint #%d\nIs In Progress\n\nHostel %d Room %s\nComments: %s\nTimestamp: %s\n\n\nNETRONiX", complaint_id, hostel, roomString, comments, complaintTime );
								}
								else {
									studentMessage = String.format( "Complaint #%d\nIs Resolved\n\nReply with \"yes\" to confirm, else \"no\"\n\nHostel %d Room %s\nComments: %s\nTimestamp: %s\n\n\nNETRONiX", complaint_id, hostel, roomString, comments, complaintTime );
								}
								
								sendSMS( messageUnit.msgNumber, studentMessage );
								databaseDisconnect();
								return;
							}
						}
						studentMessage = "You have no Ongoing Complaint\n\n\nNETRONiX";
						sendSMS( messageUnit.msgNumber, studentMessage );
					}
					catch (SQLException e) {
						e.printStackTrace();
						System.out.println("ERROR-------4");
					}
					databaseDisconnect();
					return;
				}
				else if ( msgTemp.contentEquals("cancel") || msgTemp.contentEquals("yes") || msgTemp.contentEquals("no") ) {
					boolean isCompleted = false;
					try {
						resultSet = statement.executeQuery( String.format("SELECT complaint_id,hostel_number,room,comment,timestamp,member_reg_number,status FROM `complaint` NATURAL JOIN `sms_received` WHERE status<2 AND phone_number=\'%s\' ORDER BY status LIMIT 0,1;",messageUnit.msgNumber) );
						if ( resultSet.next() ) {
							complaint_id = resultSet.getInt(1);
							hostel = resultSet.getInt(2);
							roomString = resultSet.getString(3);
							comments = resultSet.getString(4);
							complaintTime = resultSet.getString(5);
							memberReg = resultSet.getInt(6);
							complaint_status = resultSet.getInt(7);
							resultSet.close();
							
							resultSet = statement.executeQuery( String.format("SELECT first_name,last_name,batch,phone_number,solved,assigned,pending FROM `member` WHERE reg_number=%d;",memberReg) );
							resultSet.next();
							firstName = resultSet.getString(1);
							lastName = resultSet.getString(2);
							batch = resultSet.getInt(3);
							memberNumber = resultSet.getString(4);
							solved = resultSet.getInt(5);
							assigned = resultSet.getInt(6);
							pending = resultSet.getInt(7);
							resultSet.close();
							
							String studentText = null;
							String netronixText = null;
							
							if ( msgTemp.contentEquals("cancel") ) {
								assigned--;
								if (complaint_status==1) {
									pending--;
									solved++;
								}
								complaint_status=3;
								studentText = "Has Been Cancelled by You";
								netronixText = "Cancelled by Complainant";
							}
							else if ( complaint_status==1 ) {
								if ( msgTemp.contentEquals("yes") ) {
									assigned--;
									pending--;
									solved++;
									complaint_status=2;
									studentText = "Is Confirmed Complete by You";
									netronixText = "Confirmed Complete by Complainant";
									isCompleted = true;
								}
								else if ( msgTemp.contentEquals("no") ) {
									pending--;
									complaint_status=0;
									studentText = "Has Been Registered Again";
									netronixText = "REPEAT";
								}
							}
							else {
								studentMessage = "Your Complaint is Pending\nYou cannot reply \"yes\" or \"no\" to it right now\n\n\nNETRONiX";
								sendSMS( messageUnit.msgNumber, studentMessage );
								databaseDisconnect();
								return;
							}
							studentMessage = String.format( "Complaint #%d\n%s\n\nHostel %d Room %s\nComments: %s\nTimestamp: %s\n\n\nNETRONiX", complaint_id, studentText, hostel, roomString, comments, complaintTime );
							memberMessage = String.format( "NETRONiX Complaint #%d\n%s\n\nHostel %d Room %s\nNumber: %s\nComments: %s\nTimestamp: %s", complaint_id, netronixText, hostel, roomString, messageUnit.msgNumber, comments, complaintTime );
							hostelHeadMessage = String.format( "NETRONiX Complaint #%d\n%s\n\nHostel %d Room %s\nNumber: %s\nComments: %s\nTimestamp: %s\n\nAssigned: %s %s B%d\nNumber: %s", complaint_id, netronixText, hostel, roomString, messageUnit.msgNumber, comments, complaintTime, firstName, lastName, batch, memberNumber );
							
							sendSMS( memberNumber, memberMessage );

							char gender = 'm';
							int[] girlHostels = {7,80};
							if ( getIntArrayIndex(girlHostels,hostel) > -1) {
								gender = 'f';
							}
							
							resultSet = statement.executeQuery( String.format("SELECT phone_number FROM member NATURAL JOIN hostel_head WHERE hostel_id=%d;",hostel) );
							if (resultSet.next()==false) {
								resultSet.close();
								resultSet = statement.executeQuery(
									String.format(
										"SELECT phone_number FROM member NATURAL JOIN hostel_head WHERE gender=\'%c\' ORDER BY rand() limit 0,1;",
										gender
									)
								);
								if (resultSet.next()==false) {
									resultSet.close();
									resultSet = statement.executeQuery("SELECT phone_number FROM member NATURAL JOIN hostel_head ORDER BY rand() limit 0,1;");
									resultSet.next();
								}
							}
							
							// This loop send messages to all Heads of that Hostel
							ArrayList<String> hostelHeadNumberList = new ArrayList<String>();
							do {
								hostelHeadNumberList.add(resultSet.getString(1));
							} while (resultSet.next());
							resultSet.close();

							for(String phoneNumber:hostelHeadNumberList) {
								sendSMS( phoneNumber, hostelHeadMessage );
							}
							
							if (isDevHead) {
								sendSMS( devHeadPhoneNumber, "DevHead\n"+hostelHeadMessage );
							}
							
							statement.executeUpdate( String.format("UPDATE `member` SET solved=%d,assigned=%d,pending=%d WHERE reg_number=%d",solved,assigned,pending,memberReg) );
							
							statement.executeUpdate( String.format("UPDATE `complaint` SET status=%d WHERE complaint_id=%d",complaint_status,complaint_id) );
						}
						else if ( msgTemp.contentEquals("cancel") ) {
							studentMessage = "You have no Ongoing Complaint to cancel\n\n\nNETRONiX";
						}
						else {
							studentMessage = "You have no Ongoing Complaint to reply Yes/No to\n\n\nNETRONiX";
						}
						sendSMS( messageUnit.msgNumber, studentMessage );

						if (isCompleted) {
							int randomNum = (int)(Math.random() * creditMsgProbability + 1);
							System.out.println("creditMsgProbability: " + creditMsgProbability + " randomNum: " + randomNum);
							if (randomNum == creditMsgProbability) {
								System.out.println("credit msg sent");
								sendSMS( messageUnit.msgNumber, creditsMsg );
								isCompleted = false;
							}
						}
					}
					catch (SQLException e) {
						e.printStackTrace();
						System.out.println("ERROR-------5");
					}
					databaseDisconnect();
					return;
				}
				else if ( msgTemp.startsWith("view") ) {
					String msgTemp2 = msgTemp.substring( msgTemp.indexOf("view")+4 ).trim();
					try {
						complaint_id = Integer.parseInt(msgTemp2);
					}
					catch (NumberFormatException e) {
						complaint_id = -1;
					}
					try {
						studentMessage = studentIncorrectMsg;
						resultSet = statement.executeQuery( String.format("SELECT reg_number FROM `member` WHERE phone_number=\'%s\';",messageUnit.msgNumber) );
						if ( resultSet.next() ) {
							memberReg = resultSet.getInt(1);
							resultSet.close();
							studentMessage = memberIncorrectMsg;
							if ( complaint_id>0 || msgTemp2.contentEquals("all") ) {
								if ( msgTemp2.contentEquals("all") ) {
									studentMessage = "NETRONiX Ongoing Complaints:\n";
									resultSet = statement.executeQuery( String.format("SELECT complaint_id,hostel_number,room,status FROM `complaint` WHERE status<2 AND member_reg_number=%d ORDER BY status",memberReg) );
									boolean checkEmpty = true;
									while ( resultSet.next() ) {
										studentMessage = studentMessage + "\n\nID:" + resultSet.getInt(1) + "  H" + resultSet.getInt(2) + "R" + resultSet.getString(3) + "\nStatus:" + getStatusString(resultSet.getInt(4));
										checkEmpty = false;
									}
									resultSet.close();
									if (checkEmpty) {
										studentMessage = "No Ongoing Complaints assigned to you\n\n\nNETRONiX";
									}
								}
								else {
									resultSet = statement.executeQuery( String.format( "SELECT `complaint`.status,`complaint`.hostel_number,`complaint`.room,`complaint`.phone_number,`complaint`.comment,`sms_received`.timestamp FROM `complaint` JOIN `sms_received` WHERE `complaint`.sms_id=`sms_received`.sms_id AND `complaint`.member_reg_number=%d AND `complaint`.complaint_id=%d;", memberReg, complaint_id ) );
									if ( resultSet.next() ) {
										complaint_status = resultSet.getInt(1);
									    hostel = resultSet.getInt(2);
									    roomString = resultSet.getString(3);
									    String tempPhoneNumber = resultSet.getString(4);
									    comments = resultSet.getString(5);
									    complaintTime = resultSet.getString(6);
										resultSet.close();
									    studentMessage = String.format( "NETRONiX Complaint #%d\nStatus: %s\n\nHostel %d Room %s\nNumber: %s\nComments: %s\nTimestamp: %s", complaint_id, getStatusString( complaint_status ), hostel, roomString, tempPhoneNumber, comments, complaintTime );
									}
									else {
										studentMessage = "Invalid Complaint Number\n\n\nNETRONiX";
									}
								}
							}
						}
						sendSMS( messageUnit.msgNumber, studentMessage );
					}
					catch (SQLException e) {
						e.printStackTrace();
						System.out.println("ERROR-------6");
					}
					databaseDisconnect();
					return;
				}
				else if ( msgTemp.startsWith("done") ) {
					String msgTemp2 = msgTemp.substring( msgTemp.indexOf("done")+4 ).trim();
					try {
						complaint_id = Integer.parseInt(msgTemp2);
					}
					catch (NumberFormatException e) {
						complaint_id = -1;
					}
					studentMessage = studentIncorrectMsg;
					try {
						resultSet = statement.executeQuery( String.format("SELECT reg_number,pending FROM `member` WHERE phone_number=\'%s\';",messageUnit.msgNumber) );
						if ( resultSet.next() ) {
							memberReg = resultSet.getInt(1);
							pending = resultSet.getInt(2);
							resultSet.close();
							studentMessage = memberIncorrectMsg;
							if ( complaint_id>0 ) {
								studentMessage = "Invalid Complaint Number\n\n\nNETRONiX";
								resultSet = statement.executeQuery( String.format( "SELECT `complaint`.status,`complaint`.hostel_number,`complaint`.room,`complaint`.phone_number,`complaint`.comment,`sms_received`.timestamp FROM `complaint` JOIN `sms_received` WHERE `complaint`.sms_id=`sms_received`.sms_id AND `complaint`.status<2 AND `complaint`.member_reg_number=%d AND `complaint`.complaint_id=%d;", memberReg, complaint_id ) );
								if ( resultSet.next() ) {
									complaint_status = resultSet.getInt(1);
									hostel = resultSet.getInt(2);
									roomString = resultSet.getString(3);
									String tempPhoneNumber = resultSet.getString(4);
									comments = resultSet.getString(5);
									complaintTime = resultSet.getString(6);
									resultSet.close();
									if (complaint_status==1){
										studentMessage = String.format( "NETRONiX Complaint #%d\nIs Already In Waiting State\n\nHostel %d Room %s\nNumber: %s\nComments: %s\nTimestamp: %s", complaint_id, hostel, roomString, tempPhoneNumber, comments, complaintTime );
									}
									else {
										complaint_status = 1;
										pending++;
										studentMessage = String.format( "NETRONiX Complaint #%d\nStatus changed to Waiting\n\nHostel %d Room %s\nNumber: %s\nComments: %s\nTimestamp: %s", complaint_id, hostel, roomString, tempPhoneNumber, comments, complaintTime );
										statement.executeUpdate( String.format("UPDATE `complaint` SET status=%d WHERE complaint_id=%d",complaint_status,complaint_id) );
										statement.executeUpdate( String.format("UPDATE `member` SET pending=%d WHERE reg_number=%d",pending,memberReg) );
										
										String tempMessage = String.format( "Complaint #%d\nIs Resolved\n\nReply with \"yes\" to confirm, else \"no\"\n\nHostel %d Room %s\nComments: %s\nTimestamp: %s\n\n\nNETRONiX", complaint_id, hostel, roomString, comments, complaintTime );

										sendSMS( tempPhoneNumber, tempMessage );
									}
								}
							}
						}
						sendSMS( messageUnit.msgNumber, studentMessage );
					}
					catch (SQLException e) {
						e.printStackTrace();
						System.out.println("ERROR-------7");
					}
					databaseDisconnect();
					return;
				}
				else if ( msgTemp.startsWith("admin") ) {
					String msgTemp2 = msgTemp.substring( msgTemp.indexOf("admin")+5 ).trim();
					try {
						studentMessage = studentIncorrectMsg;
						resultSet = statement.executeQuery( String.format("SELECT * FROM `member` NATURAL JOIN `hostel_head` WHERE phone_number=\'%s\';",messageUnit.msgNumber) );
						if ( resultSet.next() ) {
							resultSet.close();
							studentMessage = memberIncorrectMsg;
							if ( msgTemp2.startsWith("view") ) {
								String msgTemp3 = msgTemp2.substring( msgTemp2.indexOf("view")+4 ).trim();
								try {
									complaint_id = Integer.parseInt(msgTemp3);
								}
								catch (NumberFormatException e) {
									complaint_id = -1;
								}
								if ( complaint_id>0 || msgTemp3.contentEquals("all") || msgTemp3.startsWith("hostel") ) {
									if ( msgTemp3.contentEquals("all") ) {
										studentMessage = "NETRONiX Ongoing Complaints:\n";
										resultSet = statement.executeQuery( "SELECT complaint_id,hostel_number,room,status FROM `complaint` WHERE status<2 ORDER BY status" );
										boolean checkEmpty = true;
										while ( resultSet.next() ) {
											studentMessage = studentMessage + "\n\nID:" + resultSet.getInt(1) + "  H" + resultSet.getInt(2) + "R" + resultSet.getString(3) + "\nStatus:" + getStatusString(resultSet.getInt(4));
											checkEmpty = false;
										}
										resultSet.close();
										if (checkEmpty) {
											studentMessage = "No Ongoing Complaints\n\n\nNETRONiX";
										}
									}
									else if ( msgTemp3.startsWith("hostel") ) {
										String msgTemp4 = msgTemp3.substring( msgTemp3.indexOf("hostel")+6 ).trim();
										try {
											hostel = Integer.parseInt(msgTemp4);
										}
										catch (NumberFormatException e) {
											hostel = -1;
										}
										if (hostel>0) {
											studentMessage = String.format("NETRONiX Ongoing H%d Complaints:\n",hostel);
											resultSet = statement.executeQuery( String.format("SELECT complaint_id,room,status FROM `complaint` WHERE status<2 AND hostel_number=%d ORDER BY status",hostel) );
											boolean checkEmpty = true;
											while ( resultSet.next() ) {
												studentMessage = studentMessage + "\n\nID:" + resultSet.getInt(1) + "  R" + resultSet.getString(2) + "\nStatus:" + getStatusString(resultSet.getInt(3));
												checkEmpty = false;
											}
											resultSet.close();
											if (checkEmpty) {
												studentMessage = String.format("No Ongoing H%d Complaints\n\n\nNETRONiX",hostel);
											}
										}
									}
									else {
										resultSet = statement.executeQuery( String.format( "SELECT `complaint`.status,`complaint`.hostel_number,`complaint`.room,`complaint`.phone_number,`complaint`.comment,`sms_received`.timestamp,`member`.first_name,`member`.last_name,`member`.batch,`member`.phone_number FROM `complaint` JOIN `sms_received` JOIN `member` WHERE `complaint`.sms_id=`sms_received`.sms_id AND `complaint`.member_reg_number=`member`.reg_number AND `complaint`.complaint_id=%d;", complaint_id ) );
										if ( resultSet.next() ) {
											complaint_status = resultSet.getInt(1);
										    hostel = resultSet.getInt(2);
										    roomString = resultSet.getString(3);
										    String tempPhoneNumber = resultSet.getString(4);
										    comments = resultSet.getString(5);
										    complaintTime = resultSet.getString(6);
										    firstName = resultSet.getString(7);
										    lastName = resultSet.getString(8);
										    batch = resultSet.getInt(9);
										    memberNumber = resultSet.getString(10);
											resultSet.close();
										    studentMessage = String.format( "NETRONiX Complaint #%d\nStatus: %s\n\nHostel %d Room %s\nNumber: %s\nComments: %s\nTimestamp: %s\n\nAssigned: %s %s B%d\nNumber: %s", complaint_id, getStatusString( complaint_status ), hostel, roomString, tempPhoneNumber, comments, complaintTime, firstName, lastName, batch, memberNumber );
										}
										else {
											studentMessage = "Invalid Complaint Number\n\n\nNETRONiX";
										}
									}
								}
							}
							else if ( msgTemp2.startsWith("done") ) {
								String msgTemp3 = msgTemp2.substring( msgTemp2.indexOf("done")+4 ).trim();
								try {
									complaint_id = Integer.parseInt(msgTemp3);
								}
								catch (NumberFormatException e) {
									complaint_id = -1;
								}
								if ( complaint_id>0 ) {
									resultSet = statement.executeQuery( String.format( "SELECT `complaint`.status,`complaint`.hostel_number,`complaint`.room,`complaint`.phone_number,`complaint`.comment,`sms_received`.timestamp, `member`.reg_number, `member`.first_name, `member`.last_name, `member`.batch,`member`.phone_number,`member`.pending FROM `complaint` JOIN `sms_received` JOIN `member` WHERE `complaint`.sms_id=`sms_received`.sms_id AND `complaint`.member_reg_number=`member`.reg_number AND `complaint`.status<2 AND `complaint`.complaint_id=%d;", complaint_id ) );
									if ( resultSet.next() ) {
										complaint_status = resultSet.getInt(1);
										hostel = resultSet.getInt(2);
										roomString = resultSet.getString(3);
										String tempPhoneNumber = resultSet.getString(4);
										comments = resultSet.getString(5);
										complaintTime = resultSet.getString(6);
										memberReg = resultSet.getInt(7);
										firstName = resultSet.getString(8);
										lastName = resultSet.getString(9);
										batch = resultSet.getInt(10);
										memberNumber = resultSet.getString(11);
										pending = resultSet.getInt(12);
										resultSet.close();
										if (complaint_status==1){
											studentMessage = String.format( "Complaint #%d\nIs Already In Waiting State\n\nHostel %d Room %s\nNumber: %s\nComments: %s\nTimestamp: %s\n\nAssigned: %s %s B%d\nNumber: %s", complaint_id, hostel, roomString, tempPhoneNumber, comments, complaintTime, firstName, lastName, batch, memberNumber );
										}
										else {
											complaint_status = 1;
											pending++;
											studentMessage = String.format( "Complaint #%d\nStatus changed to Waiting\n\nHostel %d Room %s\nNumber: %s\nComments: %s\nTimestamp: %s\n\nAssigned: %s %s B%d\nNumber: %s", complaint_id, hostel, roomString, tempPhoneNumber, comments, complaintTime, firstName, lastName, batch, memberNumber );
											statement.executeUpdate( String.format("UPDATE `complaint` SET status=%d WHERE complaint_id=%d",complaint_status,complaint_id) );
											statement.executeUpdate( String.format("UPDATE `member` SET pending=%d WHERE reg_number=%d",pending,memberReg) );
											
											String tempMessage = String.format( "Complaint #%d\nIs Resolved\n\nReply with \"yes\" to confirm, else \"no\"\n\nHostel %d Room %s\nComments: %s\nTimestamp: %s\n\n\nNETRONiX", complaint_id, hostel, roomString, comments, complaintTime );
											sendSMS( tempPhoneNumber, tempMessage );
											
											memberMessage = String.format( "NETRONiX Complaint #%d\nStatus changed to Waiting\n\nHostel %d Room %s\nNumber: %s\nComments: %s\nTimestamp: %s", complaint_id, hostel, roomString, tempPhoneNumber, comments, complaintTime );
											sendSMS( memberNumber, memberMessage );
										}
									}
									else {
										studentMessage = "Invalid Complaint Number\n\n\nNETRONiX";
									}
								}
							}
							else if ( msgTemp2.startsWith("cancel") ) {
								String msgTemp3 = msgTemp2.substring( msgTemp2.indexOf("cancel")+6 ).trim();
								try {
									complaint_id = Integer.parseInt(msgTemp3);
								}
								catch (NumberFormatException e) {
									complaint_id = -1;
								}
								if ( complaint_id>0 ) {
									resultSet = statement.executeQuery( String.format( "SELECT `complaint`.status,`complaint`.hostel_number,`complaint`.room,`complaint`.phone_number,`complaint`.comment,`sms_received`.timestamp, `member`.reg_number, `member`.first_name, `member`.last_name, `member`.batch, `member`.phone_number, `member`.solved, `member`.assigned, `member`.pending FROM `complaint` JOIN `sms_received` JOIN `member` WHERE `complaint`.sms_id=`sms_received`.sms_id AND `complaint`.member_reg_number=`member`.reg_number AND `complaint`.status<2 AND `complaint`.complaint_id=%d;", complaint_id ) );
									if ( resultSet.next() ) {
										complaint_status = resultSet.getInt(1);
										hostel = resultSet.getInt(2);
										roomString = resultSet.getString(3);
										String tempPhoneNumber = resultSet.getString(4);
										comments = resultSet.getString(5);
										complaintTime = resultSet.getString(6);
										memberReg = resultSet.getInt(7);
										firstName = resultSet.getString(8);
										lastName = resultSet.getString(9);
										batch = resultSet.getInt(10);
										memberNumber = resultSet.getString(11);
										solved = resultSet.getInt(12);
										assigned = resultSet.getInt(13);
										pending = resultSet.getInt(14);
										resultSet.close();
										assigned--;
										if (complaint_status==1){
											pending--;
											studentMessage = String.format("Complaint #%d \nChanged Waiting to Canceled\n\nHostel %d Room %s\nNumber: %s\nComments: %s\nTimestamp: %s\n\nAssigned: %s %s B%d\nNumber: %s", complaint_id, hostel, roomString, tempPhoneNumber, comments, complaintTime, firstName, lastName, batch, memberNumber );
										}
										else {
											studentMessage = String.format("Complaint #%d \nChanged In Progress to Canceled\n\nHostel %d Room %s\nNumber: %s\nComments: %s\nTimestamp: %s\n\nAssigned: %s %s B%d\nNumber: %s", complaint_id, hostel, roomString, tempPhoneNumber, comments, complaintTime, firstName, lastName, batch, memberNumber );
										}
										complaint_status = 3;
										statement.executeUpdate( String.format("UPDATE `complaint` SET status=%d WHERE complaint_id=%d",complaint_status,complaint_id) );
										statement.executeUpdate( String.format("UPDATE `member` SET solved=%s,assigned=%s,pending=%d WHERE reg_number=%d",solved,assigned,pending,memberReg) );
										
										memberMessage = String.format( "NETRONiX Complaint #%d\nStatus changed to Canceled\n\nHostel %d Room %s\nNumber: %s\nComments: %s\nTimestamp: %s", complaint_id, hostel, roomString, tempPhoneNumber, comments, complaintTime );
										sendSMS( memberNumber, memberMessage );
										adminCancelCount++;
									}
									else {
										studentMessage = "Invalid Complaint Number\n\n\nNETRONiX";
									}
								}
							}
							else if ( msgTemp2.startsWith("complete") ) {
								String msgTemp3 = msgTemp2.substring( msgTemp2.indexOf("complete")+8 ).trim();
								try {
									complaint_id = Integer.parseInt(msgTemp3);
								}
								catch (NumberFormatException e) {
									complaint_id = -1;
								}
								if ( complaint_id>0 ) {
									resultSet = statement.executeQuery( String.format( "SELECT `complaint`.status,`complaint`.hostel_number,`complaint`.room,`complaint`.phone_number,`complaint`.comment,`sms_received`.timestamp, `member`.reg_number, `member`.first_name, `member`.last_name, `member`.batch, `member`.phone_number, `member`.solved, `member`.assigned, `member`.pending FROM `complaint` JOIN `sms_received` JOIN `member` WHERE `complaint`.sms_id=`sms_received`.sms_id AND `complaint`.member_reg_number=`member`.reg_number AND `complaint`.status<2 AND `complaint`.complaint_id=%d;", complaint_id ) );
									if ( resultSet.next() ) {
										complaint_status = resultSet.getInt(1);
										hostel = resultSet.getInt(2);
										roomString = resultSet.getString(3);
										String tempPhoneNumber = resultSet.getString(4);
										comments = resultSet.getString(5);
										complaintTime = resultSet.getString(6);
										memberReg = resultSet.getInt(7);
										firstName = resultSet.getString(8);
										lastName = resultSet.getString(9);
										batch = resultSet.getInt(10);
										memberNumber = resultSet.getString(11);
										solved = resultSet.getInt(12);
										assigned = resultSet.getInt(13);
										pending = resultSet.getInt(14);
										resultSet.close();
										solved++;
										assigned--;
										if (complaint_status==1){
											pending--;
											studentMessage = String.format("Complaint #%d \nChanged Waiting to Completed\n\nHostel %d Room %s\nNumber: %s\nComments: %s\nTimestamp: %s\n\nAssigned: %s %s B%d\nNumber: %s", complaint_id, hostel, roomString, tempPhoneNumber, comments, complaintTime, firstName, lastName, batch, memberNumber );
										}
										else {
											studentMessage = String.format("Complaint #%d \nChanged In Progress to Completed\n\nHostel %d Room %s\nNumber: %s\nComments: %s\nTimestamp: %s\n\nAssigned: %s %s B%d\nNumber: %s", complaint_id, hostel, roomString, tempPhoneNumber, comments, complaintTime, firstName, lastName, batch, memberNumber );
										}
										complaint_status = 2;
										statement.executeUpdate( String.format("UPDATE `complaint` SET status=%d WHERE complaint_id=%d",complaint_status,complaint_id) );
										statement.executeUpdate( String.format("UPDATE `member` SET solved=%s,assigned=%s,pending=%d WHERE reg_number=%d",solved,assigned,pending,memberReg) );
										
										memberMessage = String.format( "NETRONiX Complaint #%d\nStatus changed to Completed\n\nHostel %d Room %s\nNumber: %s\nComments: %s\nTimestamp: %s", complaint_id, hostel, roomString, tempPhoneNumber, comments, complaintTime );
										sendSMS( memberNumber, memberMessage );
										adminCompleteCount++;
									}
									else {
										studentMessage = "Invalid Complaint Number\n\n\nNETRONiX";
									}
								}
							}
							else if ( msgTemp2.startsWith("status") ) {
								studentMessage = getStatusSMS();
							}
						}
						sendSMS( messageUnit.msgNumber, studentMessage );
					}
					catch (SQLException e) {
						e.printStackTrace();
						System.out.println("ERROR-------8");
					}
					databaseDisconnect();
					return;
				}
				
				
				String tempMsgBody = msgTemp;
				String tempString = null;
				
				String[] matches = new String[] {"hostel", "h"};
				
				for (String s : matches)
				{
				  if ( tempMsgBody.contains(s) )
				  {
					tempString = tempMsgBody.substring( tempMsgBody.indexOf(s) + s.length() ).trim();
					
					boolean letterFound = false;
					for (int i=0; i<tempString.length(); i++) {
						int tempInt = (int)tempString.charAt(i);
						if ( tempInt>96 && tempInt<123 ) {
							letterFound = true;
							break;
						} else if ( tempInt>48 && tempInt<58 ) {
							letterFound = false;
							break;
						}
					}
					
					if (!letterFound) {
						hostel = getFirstInt(tempString);
					}
					else {
						hostel = -1;
					}
					
					System.out.println(Thread.currentThread().getName() + " HostelString:" + tempString);
					System.out.println(Thread.currentThread().getName() + " Hostel#:" + hostel);
				    break;
				  }
				}
				
				char specialRoom = 'x';
				boolean validSpecialRoom = false;
				
				if (hostel>0) {
					matches = new String[] {"room", "r"};

					for (String s : matches)
					{
					  if ( tempMsgBody.contains(s) )
					  {
						tempString = tempMsgBody.substring( tempMsgBody.indexOf(s) + s.length() ).trim();
						
						
						boolean letterFound = true;
						boolean runOnce = false;
						for (int i=0; i<tempString.length(); i++) {
							if (!runOnce) {
								if ( tempString.charAt(i) == 'a' || tempString.charAt(i) == 'b' || tempString.charAt(i) == 'c' ) {
									specialRoom = tempString.charAt(i);
									validSpecialRoom = true;
									runOnce = true;
									continue;
								}
							}
							
							int tempInt = (int)tempString.charAt(i);
							
							if ( tempInt>96 && tempInt<123 ) {
								letterFound = true;
								break;
							}
							
							if ( tempInt>48 && tempInt<58 ) {
								letterFound = false;
								break;
							}
						}
						
						if (!letterFound) {
							room = getFirstInt(tempString);
						}
						else {
							room = -1;
							validSpecialRoom = false;
						}
						
					    break;
					  }
					}
					System.out.println(Thread.currentThread().getName() + " RoomString:" + tempString);
					if (validSpecialRoom) {
						System.out.println(Thread.currentThread().getName() + " Room#:" + specialRoom + room);
					}
					else {
						System.out.println(Thread.currentThread().getName() + " Room#:" + room);
					}
				}
				
				if ( ( (hostel>0 && hostel<13) || hostel == 80 ) && (room>0 && room<150) ) {
					
					// To read comments in complaint about problem

					if ( msgTemp.contains("dc") ) {
						comments = "DC++";
					}
					else if ( msgTemp.contains("wifi") || msgTemp.contains("wi-fi") ) {
						comments = "Wi-Fi";
					}
					else if ( msgTemp.contains("lan") || msgTemp.contains("ethernet") || msgTemp.contains("crimping") ) {
						comments = "Crimping";
					}
					else if ( msgTemp.contains("corridor") || msgTemp.contains("corry") || msgTemp.contains("corri") ) {
						comments = "Corridor";
					}
					
					
					
					try {
						resultSet = statement.executeQuery( String.format("SELECT * FROM `complaint` WHERE status<2 AND phone_number=%s;",messageUnit.msgNumber) );
						if ( resultSet.next() ) {
							resultSet.close();
							studentMessage = "You already have an Ongoing Complaint\n\nMultiple Ongoing Complaints are not allowed\n\nFor more info reply with \"help\"\n\n\nNETRONiX";
							sendSMS( messageUnit.msgNumber, studentMessage );
							databaseDisconnect();
							return;
						}
					}
					catch (SQLException e1) {
						e1.printStackTrace();
						System.out.println("ERROR-------9");
						databaseDisconnect();
						return;
					}
					
					if (validSpecialRoom) {
						specialRoom = (char)( (int)specialRoom - 32 );
						roomString = String.format("%c%d",specialRoom, room);
					}
					else {
						roomString = Integer.toString(room);
					}
					
					try {
						char gender = 'm';
						int[] girlHostels = {7,80};
						if ( getIntArrayIndex(girlHostels,hostel) > -1) {
							gender = 'f';
						}
						// Query for the specific gender members who are not hostel heads
						resultSet = statement.executeQuery(
							String.format(
								"SELECT reg_number,first_name,last_name,batch,phone_number,assigned,(assigned - pending) FROM `member` WHERE reg_number NOT IN (SELECT reg_number FROM hostel_head) AND (assigned - pending)=(SELECT min(assigned - pending) FROM `member` WHERE reg_number NOT IN (SELECT reg_number FROM hostel_head) AND gender=\'%c\') AND gender=\'%c\' ORDER BY rand();",
								gender,
								gender
							)
						);
						if ( resultSet.next() == false){
							resultSet.close();
							// Query for all specific gender members
							resultSet = statement.executeQuery(
								String.format(
									"SELECT reg_number,first_name,last_name,batch,phone_number,assigned,(assigned - pending) FROM `member` WHERE (assigned - pending)=(SELECT min(assigned - pending) FROM `member` WHERE gender=\'%c\') AND gender=\'%c\' ORDER BY rand();",
									gender,
									gender
								)
							);
							if ( resultSet.next() == false){
								resultSet.close();
								// Query for members who are not hostel heads
								resultSet = statement.executeQuery(
									"SELECT reg_number,first_name,last_name,batch,phone_number,assigned,(assigned - pending) FROM `member` WHERE reg_number NOT IN (SELECT reg_number FROM hostel_head) AND (assigned - pending)=(SELECT min(assigned - pending) FROM `member` WHERE reg_number NOT IN (SELECT reg_number FROM hostel_head)) ORDER BY rand();"
								);
								if ( resultSet.next() == false){
									resultSet.close();
									// Query for all members
									resultSet = statement.executeQuery(
										"SELECT reg_number,first_name,last_name,batch,phone_number,assigned,(assigned - pending) FROM `member` WHERE (assigned - pending)=(SELECT min(assigned - pending) FROM `member`) ORDER BY rand();"
									);
									resultSet.next();
								}
							}
						}
						memberReg = resultSet.getInt(1);
						firstName = resultSet.getString(2);
						lastName = resultSet.getString(3);
						batch = resultSet.getInt(4);
						memberNumber = resultSet.getString(5);
						assigned = resultSet.getInt(6);
						resultSet.close();
						statement.executeUpdate( String.format("INSERT INTO `complaint` VALUES (NULL,%d,\'%s\',\'%s\',\'%s\',%d,%d,%d);",hostel,roomString,messageUnit.msgNumber,comments,memberReg,0,sms_id) );
						resultSet = statement.executeQuery("SELECT LAST_INSERT_ID();");
						resultSet.next();
						complaint_id = resultSet.getInt(1);
						resultSet.close();
						assigned++;
						statement.executeUpdate( String.format("UPDATE `member` SET assigned=%d WHERE reg_number=%d",assigned,memberReg) );
						resultSet = statement.executeQuery( String.format("SELECT phone_number FROM member NATURAL JOIN hostel_head WHERE hostel_id=%d;",hostel) );
						if (resultSet.next()==false) {
							resultSet.close();
							resultSet = statement.executeQuery(
								String.format(
									"SELECT phone_number FROM member NATURAL JOIN hostel_head WHERE gender=\'%c\' ORDER BY rand() limit 0,1;",
									gender
								)
							);
							if (resultSet.next()==false) {
								resultSet.close();
								resultSet = statement.executeQuery("SELECT phone_number FROM member NATURAL JOIN hostel_head ORDER BY rand() limit 0,1;");
								resultSet.next();
							}
						}
						hostelHeadMessage = String.format( "NETRONiX Complaint #%d\n\nHostel %d Room %s\nNumber: %s\nComments: %s\nTimestamp: %s\n\nAssigned: %s %s B%d\nNumber: %s", complaint_id, hostel, roomString, messageUnit.msgNumber, comments, complaintTime, firstName, lastName, batch, memberNumber );
						
						// This loop send messages to all Heads of that Hostel
						ArrayList<String> hostelHeadNumberList = new ArrayList<String>();
						do {
							hostelHeadNumberList.add(resultSet.getString(1));
						} while (resultSet.next());
						resultSet.close();

						for(String phoneNumber:hostelHeadNumberList) {
							sendSMS( phoneNumber, hostelHeadMessage );
						}
					}
					catch (SQLException e) {
						e.printStackTrace();
						System.out.println("ERROR-------10");
						databaseDisconnect();
						return;
					}
					
					studentMessage = String.format( "Complaint #%d Registered\n\nHostel %d Room %s\nComments: %s\nTimestamp: %s\n\n\nNETRONiX", complaint_id, hostel, roomString, comments, complaintTime );
					memberMessage = String.format( "NETRONiX Complaint #%d\n\nHostel %d Room %s\nNumber: %s\nComments: %s\nTimestamp: %s", complaint_id, hostel, roomString, messageUnit.msgNumber, comments, complaintTime );
					
					
					
					sendSMS( messageUnit.msgNumber, studentMessage );
					
					sendSMS( memberNumber, memberMessage );
					
					if (isDevHead) {
						sendSMS( devHeadPhoneNumber, "DevHead\n"+hostelHeadMessage );
					}
				}
				else {
					studentMessage = studentIncorrectMsg;
					sendSMS( messageUnit.msgNumber, studentMessage );
				}
				
				databaseDisconnect();
			}
	    }
	}
	
	private synchronized void sendSMS(String sendNumber, String sendMsg) {
		try {
			while (sendMsg.length()>160) {
				int tempIndex = sendMsg.lastIndexOf('\n', 157);
				String tempMessage = sendMsg.substring( 0, tempIndex ) + "\n...";
				if (tempMessage.length()>160) {
					tempMessage = sendMsg.substring(0, 157) + "...";
					sendMsg = "..."+sendMsg.substring(157);
				}
				else {
					sendMsg = "..."+sendMsg.substring(tempIndex);
				}
				sendConfirmSMS( sendNumber, tempMessage );
				currentTime = getCurrentMySQLTime();
				if (statement != null) {
					statement.executeUpdate( String.format("INSERT INTO `sms_sent` VALUES (NULL,\'%s\',\'%s\',\'%s\');",sendNumber,tempMessage,currentTime) );
				}
			}
			sendConfirmSMS( sendNumber, sendMsg );
			currentTime = getCurrentMySQLTime();
			if (statement != null) {
				statement.executeUpdate( String.format("INSERT INTO `sms_sent` VALUES (NULL,\'%s\',\'%s\',\'%s\');",sendNumber,sendMsg,currentTime) );
			}
		}
		catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	private synchronized void sendSplitSMS(String sendNumber, String [] msgList) {
		try {
			for (String sendMsg: msgList) {
				sendConfirmSMS( sendNumber, sendMsg );
				currentTime = getCurrentMySQLTime();
				statement.executeUpdate( String.format("INSERT INTO `sms_sent` VALUES (NULL,\'%s\',\'%s\',\'%s\');",sendNumber,sendMsg,currentTime) );
			}
		}
		catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	private synchronized void sendConfirmSMS(String sendNumber, String sendMsg) {
		int count = 1;
		boolean failed = true, error = false;
		Integer testInteger = null;
		
		try {
			while ( failed == true && count<5 ) {
				failed = false;
				error = false;
				
				if (count!=1)
					System.out.println("SMS Failed - Resending");
				
				transferQueue.clear();
				gsmCom.sendSMS( sendNumber, sendMsg );
				
				testInteger = transferQueue.poll(45,TimeUnit.SECONDS);
				if ( testInteger == null) {
					System.out.println("SMS timed out");
					failed = true;
				}
				else if ( testInteger ==  -1) {
					System.out.println("SMS got ERROR");
					failed = true;
					error = true;
				}
				else {
					break;
				}
				
				if (!error) {
					transferQueue.clear();
					gsmCom.sendChar((char)26);
					gsmCom.sendChar((char)26);
					
					testInteger = transferQueue.poll(45,TimeUnit.SECONDS);
					if ( testInteger != null) {
						if ( testInteger !=  -1) {
							break;
						}
						System.out.println("SMS got ERROR");
						error = true;
					}
					else {
						System.out.println("SMS timed out");
					}
				}
				count++;
			}
			
		}
		catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		if (count<5) {
			sentSMSCount++;
			System.out.println(String.format("SMS Sending Successful After %d tries",count));
			System.out.println(String.format("Total %d SMS Sent",sentSMSCount));
		}
		else {
			failedSentSMS++;
			System.out.println("SMS Failed After 4 tries");
		}
	}
	
	
	
	private String getStatusSMS() {
		return "Status OK"
				+ "\n\nRecieved: " + recievedSMSCount
				+ "\nSent: " + sentSMSCount
				+ "\nFailed: " + failedSentSMS
				+ "\n\nAdmin Cancel: " + adminCancelCount
				+ "\nAdmin Complete: " + adminCompleteCount
				+ "\n\nNETRONiX Complaint System\n" + getCurrentMySQLTime();
	}
	
	
	

	private class StatusSMS implements Runnable {
		public void run() {
			System.out.println( Thread.currentThread().getName() +  " Sending StatusSMS to admin" );
			Thread nextThread = new Thread( new SendStatusSMS("+923223044669", getStatusSMS()) );
			nextThread.start();
		}
		
		private class SendStatusSMS implements Runnable {
			String number;
			String message;

			SendStatusSMS(String number, String message) {
				this.number = number;
				this.message = message;
			}

			public void run() {
				sendSMS(number, message);
			}
	    }
    }
	
	private void scheduleSMS(int hours, Runnable runnable) {
		LocalDateTime localNow = LocalDateTime.now();
        ZoneId currentZone = ZoneId.of("Asia/Karachi");
        ZonedDateTime zonedNow = ZonedDateTime.of(localNow, currentZone);
        
        ZonedDateTime zonedNext1 = zonedNow.withHour(hours).withMinute(0).withSecond(0);
        if(zonedNow.compareTo(zonedNext1) > 0)
        	zonedNext1 = zonedNext1.plusDays(1);

        Duration duration1 = Duration.between(zonedNow, zonedNext1);
        long initalDelay1 = duration1.getSeconds();
        
        System.out.println( Thread.currentThread().getName() +  " 			Scheduling with " + hours + " hours\n									"
        		+ "initalDelay1:" + initalDelay1 + " Next:" +24*60*60);

        ScheduledExecutorService scheduler1 = Executors.newScheduledThreadPool(1);            
        scheduler1.scheduleAtFixedRate(runnable, initalDelay1, 24*60*60, TimeUnit.SECONDS);
	}
	
	private void scheduleStatusSMS() {
		StatusSMS statusSMS = new StatusSMS();
		scheduleSMS(0, statusSMS);
		scheduleSMS(6, statusSMS);
		scheduleSMS(12, statusSMS);
		scheduleSMS(15, statusSMS);
		scheduleSMS(18, statusSMS);
		scheduleSMS(21, statusSMS);
	}
	
	
	private void sendStartupSMS() {
		Thread nextThread = new Thread( new StartupSMS("+923223044669") );
		nextThread.start();
	}
	
	private class StartupSMS implements Runnable {
		String number;

		StartupSMS(String number) {
			this.number = number;
		}

		public void run() {
			try {
				Thread.sleep(20000);
			} catch (InterruptedException e) {
				System.out.println("Class StartupSMS got exception");
				e.printStackTrace();
			}
			sendSMS(number, "Started\nNETRONiX Complaint System\n" + getCurrentMySQLTime());
		}
    }
	
	
	
	// Code from Stackoverflow user icza
	// http://stackoverflow.com/posts/25379180/revisions
	public static boolean containsIgnoreCase(String src, String what) {
	    final int length = what.length();
	    if (length == 0)
	        return true; // Empty string is contained

	    final char firstLo = Character.toLowerCase(what.charAt(0));
	    final char firstUp = Character.toUpperCase(what.charAt(0));

	    for (int i = src.length() - length; i >= 0; i--) {
	        // Quick check before calling the more expensive regionMatches() method:
	        final char ch = src.charAt(i);
	        if (ch != firstLo && ch != firstUp)
	            continue;

	        if (src.regionMatches(true, i, what, 0, length))
	            return true;
	    }

	    return false;
	}
	
	public static int getFirstInt(String input) {
		int ans = -1;
		Matcher matcher = Pattern.compile("[^0-9]*([0-9]+).*").matcher(input);
		//matcher.find();
		if (matcher.matches()) {
			ans = Integer.valueOf(matcher.group(1));
		}
		return ans;
	}
	
	public static int getIntFromChar(char inputChar) {
		return ( (int)inputChar - 48 );
	}
	
	public static String getCurrentMySQLTime() {
		java.util.Date dt = new java.util.Date();

		java.text.SimpleDateFormat sdf = 
		     new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

		return sdf.format(dt);
	}
	
	public static String getStatusString(int input) {
		switch(input) {
		case 0:
			return "In Progress";
			
		case 1:
			return "Waiting";
			
		case 2:
			return "Completed";
			
		case 3:
			return "Cancelled";

		default:
			return null;
		}
	}

	public int getIntArrayIndex(int[] arr,int value) {
        int k=-1;
        for(int i=0;i<arr.length;i++){

            if(arr[i]==value){
                k=i;
                break;
            }
        }
        return k;
	}
}