package com.gmail.cwatterson.NickReq;

import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class NickReqDAO
{

	private Connection _mysql;
	
	public void connect( String hostname, String database, String username, String password, File dir )
	{ 
		try
		{
			Class.forName( "com.mysql.jdbc.Driver" ).newInstance();
			this._mysql = DriverManager.getConnection( "jdbc:mysql://" + hostname + "/" + database, username, password );
		} catch( Exception e ) { 
			System.err.println( "Cannot connect to database server: " + e.getMessage() );
			return;
		}

		try {
			DatabaseMetaData dbm = this._mysql.getMetaData();
			ResultSet tables = dbm.getTables(null, null, "nickreq_list", null);
			if (!tables.next()){
				//table does not exist
				System.out.println("[NickReq] No nickreq_list table found!  Creating...");
				createListTable();
			}
			DatabaseMetaData dbm2 = this._mysql.getMetaData();
			tables = dbm2.getTables(null, null, "postpone", null);
			if (!tables.next()){
				//nickname table does not exist
				System.out.println("[NickReq] No postpone table found!  Creating...");
				createPostponeTable();
			}
		} catch( Exception e) {
			System.err.println( "Cannot get db metadata: " + e.getMessage() );
			return;
		}

	} 
	
	public void createListTable()
	{
		try {
			PreparedStatement s = this._mysql.prepareStatement("CREATE TABLE nickreq_list (id int(50) not null auto_increment primary key, user varchar(35), nickname varchar(35), approved boolean, denied boolean, staff varchar(35))");
			s.execute();
		}
		catch( Exception e ) {
			System.err.println("Exception creating table: " + e.getMessage());
		}
	}
	
	public void createPostponeTable()
	{
		try {
			PreparedStatement s = this._mysql.prepareStatement("CREATE TABLE postpone (id int(50) not null auto_increment primary key, user varchar(35), nickname varchar(35))");
			s.execute();
		}
		catch( Exception e) {
			System.err.println("Exception creating table: " + e.getMessage());
		}
	}
	
	public void disconnect( File dir )
	{
		if ( this._mysql == null ) return; // Ignore unopened connections
		try {
			this._mysql.close();
		} catch( Exception e ) {
			System.err.println( "Cannot close database connection: " + e.getMessage() );
		}
	}
	
	public String getPendingNickname(String playerName)
	{
		
		try {
			
			PreparedStatement s = this._mysql.prepareStatement("SELECT nickname FROM postpone WHERE user = ? LIMIT 1");
			s.setString(1, playerName);
			ResultSet rs = s.executeQuery();
			if (!rs.last()){
				return "off";
			} else 
			{
				return rs.getString(1);
			}
			
		} catch( Exception e) {
			e.printStackTrace();
		}
		
		return "off";
	
	}
	
	public void removeFromQueue(String playerName)
	{
		try {
			
			PreparedStatement s = this._mysql.prepareStatement("DELETE FROM postpone WHERE user = ? LIMIT 1");
			s.setString(1, playerName);
			s.executeUpdate();
			
		} catch( Exception e) {
			e.printStackTrace();
		}
	}
	
	public boolean addReq(String senderName, String desiredNick)
	{
		try {
			PreparedStatement s = this._mysql.prepareStatement("SELECT * FROM nickreq_list WHERE user = ? AND approved = false AND denied = false LIMIT 1");
			s.setString(1, senderName);
			ResultSet rs = s.executeQuery();
			if (!rs.last())
			{
				//create entry
				PreparedStatement s2 = this._mysql.prepareStatement( "INSERT INTO nickreq_list (user,nickname,approved,denied) VALUES(?,?,false,false)");
				s2.setString(1, senderName);
				s2.setString(2, desiredNick);
				s2.executeUpdate();
				return false;
			}
			else
			{
				//update entry
				PreparedStatement s2 = this._mysql.prepareStatement("UPDATE nickreq_list SET nickname = ? WHERE user = ? AND approved = false AND denied = false LIMIT 1");
				s2.setString(1, desiredNick);
				s2.setString(2, senderName);
				s2.executeUpdate();
				return true;
			}
		} catch( SQLException e) {
			e.printStackTrace();
		}
		return true;  //unreachable except for an exception.
	}
	
	public String getUsernameForID(int id)
	{
		try {
			
			PreparedStatement s = this._mysql.prepareStatement("SELECT user FROM nickreq_list WHERE id = ? LIMIT 1");
			s.setInt(1, id);
			ResultSet rs = s.executeQuery();
			if(rs.last())
			{
				return rs.getString(1);
			}
			else
				return "none";
			
		} catch( Exception e) {
			e.printStackTrace();
		}
		
		return "none";
	}
	
	public List<String> getReqs()
	{
		// do something
		List<String> resultStrings = new ArrayList<String>();
		
		try {
			PreparedStatement s = this._mysql.prepareStatement("SELECT id, user, nickname FROM nickreq_list WHERE approved = false AND denied = false");
			ResultSet rs = s.executeQuery();
			while(rs.next()) 
			{
				resultStrings.add(rs.getInt(1) + " // " + rs.getString(2) + " // " + rs.getString(3));
			}
			
		} catch( SQLException e) {
			e.printStackTrace();
		}
		
		return resultStrings;
		
	}
	
	public boolean approveNick(int requestNum, String staffMember, CommandSender sender)
	{
		try {
			PreparedStatement s = this._mysql.prepareStatement("SELECT user, nickname FROM nickreq_list WHERE id = ? AND approved = false AND denied = false LIMIT 1");
			s.setInt(1, requestNum);
			ResultSet rs = s.executeQuery();
			if (!rs.last())
			{
				return false;
			}
			else
			{
				//update entry
				PreparedStatement s2 = this._mysql.prepareStatement("UPDATE nickreq_list SET approved = true, staff = ? WHERE id = ? AND denied = false LIMIT 1");
				s2.setString(1, staffMember);
				s2.setInt(2, requestNum);
				s2.executeUpdate();
				
				//Check if user is online.  If so, set nick.  Else, add to queue
				String user = rs.getString(1);
				String nickToSet = rs.getString(2);
				for(Player player: Bukkit.getServer().getOnlinePlayers()) 
				{	 
				    if(player.getName().equals(user))
				    {
						String commandString = "nick " + user + " " + nickToSet;
						Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), commandString);
						sender.sendMessage(ChatColor.GREEN + "Nickname set!");
						return true;
				    }
				}
				//user is offline
				    	
				PreparedStatement s3 = this._mysql.prepareStatement( "INSERT INTO postpone (user,nickname) VALUES(?,?)");
				s3.setString(1, user);
				s3.setString(2, nickToSet);
				s3.executeUpdate();
				sender.sendMessage(ChatColor.GREEN + "User is offline!  Queueing nickname");
				return true;
				
				
			}
		} catch( SQLException e) {
			e.printStackTrace();
		}
		
		return false;
	}
	
	public boolean denyNick(int requestNum, String staffMember)
	{
		try {
			PreparedStatement s = this._mysql.prepareStatement("SELECT * FROM nickreq_list WHERE id = ? AND approved = false AND denied = false LIMIT 1");
			s.setInt(1, requestNum);
			ResultSet rs = s.executeQuery();
			if (!rs.last())
			{
				return false;
			}
			else
			{
				//update entry
				PreparedStatement s2 = this._mysql.prepareStatement("UPDATE nickreq_list SET denied = true, staff = ? WHERE id = ? AND approved = false LIMIT 1");
				s2.setString(1, staffMember);
				s2.setInt(2, requestNum);
				s2.executeUpdate();
				
				String username = getUsernameForID(requestNum);
				
				for(Player player: Bukkit.getServer().getOnlinePlayers()) 
				{	 
				    if(player.getName().equals(username))
				    {
				    	player.sendMessage(ChatColor.GREEN + "Your Nickname Request Was Denied!");
				    }
				}
				
				return true;
			}
		} catch( SQLException e) {
			e.printStackTrace();
		}
		
		return false;
	}
}