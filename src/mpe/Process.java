package mpe;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Vector;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicBoolean;

//we need to access processing from this class
import processing.core.PApplet;
import processing.core.PConstants;
import processing.core.PGraphics3D;

public class Process extends Thread {
	
	public final static String VERSION = "0.1.1";
	
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
	
	// this is the object on which synchronization is based
	private final FrameLock frameLock_ = new FrameLock();
	
	// the cyclic barrier servers as a barrier synchronization for all render clients
	public final CyclicBarrier barrier_;
	
	// have we notified?
	AtomicBoolean notified_;
	
	// do we have input to send along with the next FE message? (leader)
	boolean sendAttributes_;
	
	// do we have input from the server along with the FE message? (follower)
	boolean receivedAttributes_;
	
	// this is the actual object that will be sent along with the FE message
	String attribute_;
	
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
		//cameraZ_ = (pApplet_.height/2.0f) / PApplet.tan(PConstants.PI * fov_/360.0f);
		cameraZ_ = (config_.getMasterDim()[1]/2.0f) / PApplet.tan(PConstants.PI * fov_/360.0f);
		
		// after the sketch calls draw, it will call the draw method in this class
		pApplet_.registerDraw(this);
		pApplet_.registerPre(this);
		
		barrier_ = new CyclicBarrier(config_.numFollowers_ + 1);
		
		config_.printSettings();
		
		running_ = true;
	}
	
	// this call is made before the draw command
	public void pre()
	{
		if(debug_) print("Trying to acquire framelock!");

		
		// wait on framelock to be unlocked
		frameLock_.acquire();

		if(debug_) print("Acquired framelock!");
		
		placeScreen();
	}
	
	public void draw()
	{
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
					
					// disable Nagle's algorithm, otherwise we get TCP delays of ~40ms
					processSocket_.setTcpNoDelay(true);
					
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
					
					// disable Nagle's algorithm, otherwise we get TCP delays of ~40ms
					followerSocket.setTcpNoDelay(true);
					
					if(debug_) print("Received a client connection: " + followerSocket.getInetAddress());
				} catch (IOException e) {
					System.out.println("Unable to accept connection!");
				}
	    		
	    		// new client, so increment the counter of the number of connected
	    		followerState_.incrementConnected();
	    		
	    		// create a new connection object for this client and run communication in a separate thread
	    		Connection conn = new Connection(followerSocket, followerState_, this);
	    		conn.start();
	    		
	    		// add the client to the clients vectors, so that we can later broadcast, etc.
	    		clients_.add(conn);
	    	}
	    	
	    	if(debug_) print("All clients have connected. Start event loop");
	    	
		}
		
		// calls the run() command based on Java thread semantics
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
				
				try {
					barrier_.await();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (BrokenBarrierException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				barrier_.reset();
				
				// release the framelock so master can render
				frameLock_.release();
									
				// send a FE message to all clients so they render the next scene
				broadcastFE();
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
		return config_.getMasterDim()[1];
	}
	
	/*
	 * Begin private methods
	 */
	
	public void placeScreen()
	{
		if(enable3D_)
			placeScreen3D();
		else
			placeScreen2D();
	}
	
	// computes the correct viewing frustum for the perspective view
	private void placeScreen3D()
	{
		
		/*
		 * This is tough.
		 * 
		 * Processing flips the Y coordinate in OpenGL vs. P3D, first of all.
		 * We wont be using P3D, so this shouldn't matter, the resolutions in question
		 * preclude the use of anything but OpenGL.
		 */
		
		pApplet_.camera(config_.getMasterDim()[0]/2.0f, config_.getMasterDim()[1]/2.0f, cameraZ_,
				config_.getMasterDim()[0]/2.0f, config_.getMasterDim()[1]/2.0f, 0, 
                0, 1, 0);
		
		// Frustum information assuming X = 0 @ top left, and Y = 0 @ top left
        /* from my processing work
		  float left = offsetX - mX/2;
		  float right = (offsetX + lX) - mX/2;
		  float top = offsetY - mY/2;
		  float bottom = (offsetY + lY) - mY/2;
		  float near = cameraZ;
		  float far = 1000;
		*/ 
		float mod = 0.1f;
        float left   = (config_.getOffsets()[0] - config_.getMasterDim()[0]/2)*mod;
        float right  = ((config_.getOffsets()[0] + config_.getLocalDim()[0]) - config_.getMasterDim()[0]/2)*mod;
        float top = (config_.getOffsets()[1] - config_.getMasterDim()[1]/2)*mod;
        float bottom = ((config_.getOffsets()[1] + config_.getLocalDim()[1]) - config_.getMasterDim()[1]/2)*mod;
        float near   = cameraZ_*mod;
        float far    = 10000;
        pApplet_.frustum(left,right,top,bottom,near,far);
        
	}
	
	// simply offsets the screen in space
	private void placeScreen2D()
	{
		pApplet_.translate(config_.getOffsets()[0] * -1, config_.getOffsets()[1] * -1);
	}
	
	// reads the command from the leader, releases the frameLock if the FE command was sent
	private void readCommand(Command c)
	{
		// received a frame event command from server, unlock framelock object
		if(c.command.equals("fe"))
		{
			if(debug_) print("Received FE");
			
			// release the framelock
			frameLock_.release();
			
			if(c.atts != null)
			{
				receivedAttributes_ = true;
				attribute_ = c.atts;
			}
		}
	}
	
	private void broadcastFE()
	{
		// create frame event command
		Command command = new Command();
		command.command = "fe";
		
		if(sendAttributes_)
			command.atts = attribute_;
		else command.atts = null;
		
		// we have appended the attribute to the command, and can now set it back to false such that repeated
		// messages are not sent
		sendAttributes_ = false;
		
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
	
	// the sketch requested that the attributes be sent to all other processes
	// note, interaction intended to occur on leader process, for other interaction use
	// asynchronous client
	public void broadcast(String attribute)
	{
		sendAttributes_ = true;
		attribute_ = attribute;
	}
	
	// returns true if the last FE message was received with non-null atts
	public boolean messageReceived()
	{
		return receivedAttributes_;
	}
	
	// returns the attributes that were received from the leader
	public String getMessage()
	{
		if(receivedAttributes_)
		{
			receivedAttributes_ = false;
			return attribute_;
		}
		else
		{
			print("No attributes were received! Check w/ messageReceived() first!");
			return null;
		}		
	}

}
