package mpe.process;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.BrokenBarrierException;

public class Connection extends Thread {
	
	Socket socket_;
	ObjectOutputStream oos_;
	ObjectInputStream ois_;
	
	FollowerState followerState_;
	
	Process process_;
	
	// initialize input/output streams and assign client and ID
	public Connection(Socket s, FollowerState fs, Process p) 
	{
		socket_ = s;
		try {
			oos_ = new ObjectOutputStream(socket_.getOutputStream());
			ois_ = new ObjectInputStream(socket_.getInputStream());
		} catch (IOException e) { 
			e.printStackTrace();
		}
	
		followerState_ = fs;
		
		process_ = p;
		
	}
	
	public void run()
	{
		// read input from follower
		while(true)
		{
			Command command = null;
			try {
				command = (Command) ois_.readObject();
			} catch (IOException e) {
				System.out.println("Client disconnected!");
				//e.printStackTrace();
				System.exit(-1);
			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			if(command == null)
				break; // the leader hung up!
			readCommand(command);
		}
	}
	
	public void sendCommand(Command command)
	{
		try {
			oos_.writeObject(command);
		} catch (IOException e) {
			System.out.println("Unable to write to client!");
			e.printStackTrace();
		}
	}

	private void readCommand(Command command)
	{	
		// the client has rendered a frame and is ready for next
		if(command.command.equals("ef"))
		{
			if(process_.getDebug()) process_.print("Received EF.");

			followerState_.ready();
			
			try {
				process_.barrier_.await();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (BrokenBarrierException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

}
