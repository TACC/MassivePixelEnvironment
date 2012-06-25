package mpe.process;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;

//we need to access processing from this class
import processing.core.PApplet;
import processing.core.PConstants;
import processing.core.PGraphics3D;

public class Process extends Thread {
	
	// contains identifier and screen-space info for this process
	Configuration config_; 
	
	/*
	 * A process may either be either a leader or follower.
	 * There is only one leader - designated in configuration file as 'head'
	 * The leader maintains a connection to all followers and issues frame events.
	 * Followers simply maintain a connection to the leader and wait for frame events.
	 */
	
	// if we are the leader, this will contain follower communication channels
	Vector<Connection> clients_;
	
	// if we are a follower, this will be our communication layer to the leader
	Connection leader_;
	
	// the processing applet that is running on this host
	PApplet pApplet_;
	
	// true if the processing sketch uses P3D, OpenGL, or GLGraphics
	boolean enable3D_ = false;
	
	// true when every follower is connected to the leader process
	boolean allConnected_ = false;
	
	// true while thread is executing
	boolean running_ = false;
	
	// keeps track of the state of the followers
	FollowerState followerState_;
	
	// are we in debug mode?
	boolean debug_ = false;
	
	// have we notified?
	AtomicBoolean notified_;
	
	// parameters needed to properly set viewing frustum
	float cameraZ_;
	float fov_ = 60.0f;
	
	// socket if the process is a follower, and complementary streams
	Socket processSocket_;
	ObjectInputStream ois_;
	ObjectOutputStream oos_;
	
	/*
	 * Begin public methods
	 */
	
	// concrete constructor
	public Process(Configuration config)
	{
		pApplet_ = config.getApplet();
		notified_ = new AtomicBoolean(false);
		
		// construct the configuration object
		config_ = config;
		debug_ = config_.getDebug();
		
		// create the followerState, which keeps track of how many renderers have rendered and are waiting
		followerState_ = new FollowerState(config_.getNumFollowers());
		if(debug_) print("Number of followers: " + config_.getNumFollowers());
		
		// is this a 3D (P3D/OpenGL/GLGraphics) or 2D (P2D) sketch?
		enable3D_ = pApplet_.g instanceof PGraphics3D;
		
		// set the Z location of the camera
		cameraZ_ = (pApplet_.height/2.0f) / PApplet.tan(PConstants.PI * fov_/360.0f);
		
		// after the sketch calls draw, it will call the draw method in this class
		pApplet_.registerDraw(this);
		pApplet_.registerPre(this);
		
		if(debug_)
			config_.printSettings();
		
		running_ = true;
		
	}
	
	// this call is made before the draw command
	public void pre()
	{
		placeScreen();
	}
	
	public void draw()
	{
		
		if(debug_) print("About to wait on framelock");
		
		synchronized(pApplet_)
		{
			// only wait if we haven't received a FE message yet
			if(!notified_.get())
			{
				if(debug_) print("In a wait");
				try {
					pApplet_.wait();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		
		notified_.set(false);
		
		if(debug_) print("framelock ready!");
		
		// send end-of-frame message if not leader
		if(!config_.isLeader())
			endFrame();
	}
	
	public void start()
	{
		// we are just a follower, just register with leader
		if(!config_.isLeader())
		{
			// set up socket to leader, retrying every two seconds if fail
			boolean notConnected = true;
			while(notConnected)
			{
				notConnected = false;
				try {
					processSocket_ = new Socket(config_.getServer(), config_.getPort());
					oos_ = new ObjectOutputStream(processSocket_.getOutputStream());
		            ois_ = new ObjectInputStream(processSocket_.getInputStream());
				} catch (UnknownHostException e) {
					System.out.println("Can't connect to leader process! Did you specify a 'head' process in config? Retrying!");
					notConnected = true;
				} catch (IOException e) {
					System.out.println("Can't connect to leader process! Did you specify a 'head' process in config? Retrying!");
					notConnected = true;
				}
				
				// sleep for 2 seconds and then retry to connect to leader
				try {
					Thread.sleep(2000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		
		// we are the leader, create connection listener(s)
		if(config_.isLeader())
		{
			
			clients_ = new Vector<Connection>();
			
			// set listener for all connections
	        ServerSocket listener = null;
	    	Socket followerSocket = null;
	    	
	    	try {
				listener = new ServerSocket(config_.getPort());
			} catch (IOException e) {
				System.out.println("Unable to listen on port " + config_.getPort() + " , quitting.");
				System.exit(-1);
			}
	    	    	
	    	// whenever a new connection is made, create a handler thread
	    	while(!followerState_.allConnected())
	    	{
	    		try {
					followerSocket = listener.accept();
					if(debug_) print("Received a client connection: " + followerSocket.getInetAddress());
				} catch (IOException e) {
					System.out.println("Unable to accept connection!");
				}
	    		
	    		// new client, decrement count
	    		followerState_.incrementConnected();
	    		
	    		// create a new connection object for this client and run communication in a separate thread
	    		Connection conn = new Connection(followerSocket, followerState_, this);
	    		conn.start();
	    		clients_.add(conn);
	    	}
	    	
	    	if(debug_) print("All clients have connected. Start event loop");
	    	
	    	// broadcast an initial frame event to the followers
	    	broadcastFE();
	    	
		}
		
		super.start();
	}
	
	// main loop for this thread
	public void run()
	{
		
		while(running_)
		{
			// wait for all followers to be ready and broadcast msg to render
			if(config_.isLeader())
			{
				
				if(debug_) print("Waiting on followerState");
				synchronized(followerState_)
				{
					// wait for followerState notification that all followers have reported
					try {
						followerState_.wait();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				notified_.set(true);
				
				synchronized(pApplet_)
				{
					pApplet_.notify();
				}
				
				notified_.set(false);
				broadcastFE();
				
				/*
				if(followerState_.allReady())
				{
					
					synchronized(pApplet_)
					{
						notified_.set(true);
						pApplet_.notify();
						
						// all clients will be sent the frame event message, so set their state to not ready until they respond
						followerState_.setAllNotReady();
					}
					
					broadcastFE(); // send all followers the command to render
				}
				*/
			}
			
			// we are a follower and we should receive a msg
			else
			{
				Command command = null;
				try {
					command = (Command) ois_.readObject();
				} catch (IOException e) {
					print("Leader disconnected! Exiting.");
					System.exit(-1);
				} catch (ClassNotFoundException e) {
					print("Leader disconnected! Exiting.");
					System.exit(-1);
				} // blocks until new msg is available
				if(command == null)
					break; // remote end hung up
				readCommand(command);
			}
		}
	}
	
	public int getLWidth()
	{
		return config_.getLocalDim()[0];
	}
	
	public int getLHeight()
	{
		return config_.getLocalDim()[1];
	}
	
	public int getMWidth()
	{
		return config_.getMasterDim()[0];
	}
	
	public int getMHeight()
	{
		return config_.getMasterDim()[0];
	}
	
	/*
	 * Begin private methods
	 */
	
	private void placeScreen()
	{
		if(enable3D_)
			placeScreen3D();
		else
			placeScreen2D();
	}
	
	// computes the correct viewing frustum for the perspective view
	private void placeScreen3D()
	{
		
		pApplet_.camera(config_.getMasterDim()[0]/2.0f, config_.getMasterDim()[1]/2.0f, cameraZ_,
				config_.getMasterDim()[0]/2.0f, config_.getMasterDim()[1]/2.0f, 0, 
                0, 1, 0);
        
		
		float mod = 1f/10f;
        float left   = (config_.getOffsets()[0] - config_.getMasterDim()[0]/2)*mod;
        float right  = (config_.getOffsets()[0] + config_.getLocalDim()[0] - config_.getMasterDim()[0]/2)*mod;
        float top = (config_.getOffsets()[1] + config_.getLocalDim()[1]-config_.getMasterDim()[1]/2)*mod;
        float bottom = (config_.getOffsets()[1] - config_.getMasterDim()[1]/2)*mod;
        float near   = cameraZ_*mod;
        float far    = 10000;
        pApplet_.frustum(left,right,top,bottom,near,far);
	}
	
	// simply offsets the screen in space
	private void placeScreen2D()
	{
		pApplet_.translate(config_.getOffsets()[0] * -1, config_.getOffsets()[1] * -1);
	}
	
	// TODO: handle input as well here
	private void readCommand(Command c)
	{
		// received a frame event command from server, unlock framelock object
		if(c.command.equals("fe"))
		{
			if(debug_) print("Received FE");

			notified_.set(true);
			synchronized(pApplet_)
			{
				pApplet_.notify();
				//notified_.set(false);
			}
		}
	}
	
	private void broadcastFE()
	{
		// create frame event command
		Command command = new Command();
		command.command = "fe";
		
		for(int i = 0; i < clients_.size(); i++)
		{
			clients_.elementAt(i).sendCommand(command);
		}
	}
	
	// sends msg to leader indicating the frame has been drawn
	private void endFrame()
	{
		Command command = new Command();
		command.command = "ef";
		
		try {
			oos_.writeObject(command);
		} catch (IOException e) {
			System.out.println("Unable to write to server! Server disconnected.");
			System.exit(-1);
		}
	}
	
	public void print(String msg)
	{
		System.out.println(config_.getRank() + ": " + msg);
	}
	
	public boolean getDebug()
	{
		return debug_;
	}

}
