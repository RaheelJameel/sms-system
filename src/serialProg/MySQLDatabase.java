package serialProg;

import java.sql.*;

public class MySQLDatabase {
	
	private String url;
	private int port;
	private String db_name;
	private String db_username;
	private String db_password;
	
	private boolean connected;
	private Connection connection;
	private Statement statement;

	public MySQLDatabase( String url, int port, String db_name, String db_username, String db_password ) {
		connected = false;
		this.url = url;
		this.port = port;
		this.db_name = db_name;
		this.db_username = db_username;
		this.db_password = db_password;
	}
	
	public String getUrl() {
		return url;
	}

	public int getPort() {
		return port;
	}

	public String getDb_name() {
		return db_name;
	}
	
	public boolean getConnected() {
		return connected;
	}

	public Statement getStatement() {
		return statement;
	}
	
	public PreparedStatement prepareStatement(String sql) throws SQLException {
		return connection.prepareStatement(sql);
	}


	public boolean connect() throws ClassNotFoundException, SQLException {
		Class.forName("com.mysql.jdbc.Driver");
			
		connection = DriverManager.getConnection( String.format( "jdbc:mysql://%s:%d/%s", url, port, db_name) , db_username, db_password );
			
		statement = connection.createStatement( ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE );
		
		System.out.println("Database Connected");
			
		connected = true;
		return connected;
	}
	
	public void disconnect() {
		if ( connection!=null ) {
			try {
				connection.close();
				connection = null;
				statement = null;
				
				System.out.println("Database Disconnected");
				
				connected = false;
			}
			catch (SQLException e) {
				e.printStackTrace();
				System.out.println("ERROR: Database Not Disconnected");
			}
		}
	}

}
