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
import processing.core.*;
//import processing.event.KeyEvent;
//import processing.event.MouseEvent;

/**
 * This class facilitates the execution of multiple Processing applications across a distributed environment.
 * @author Brandt Westing TACC
 *
 */

public class Process extends Thread {
	
	public final static String VERSION = "##version##";
	
	// used for timers
	public static long start;
	public static long end;
	public static long elapsed;
	
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
	
	// true if the camera should be placed during placeScreen. Set to false for peasyCam, etc.
	boolean placeCamera_ = true;
	
	// true if auto-start is enabled (Default)
	boolean autostart_ = true;
	
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
	Object attribute_;
	
	// mouse and keyboard events sent every frame
	//MouseEvent mouseEvent_ = null;
	//KeyEvent keyEvent_ = null;
	
	// parameters needed to properly set viewing frustum
	float cameraZ_;
	float fov_ = 45.0f;
	
	// socket if the process is a follower, and complementary streams
	Socket processSocket_;
	ObjectInputStream ois_;
	ObjectOutputStream oos_;
	
	// by default, do not serialize mouse and keyboard events (they are not serializable yet) //todo
	//boolean enableDefaultSerialization_ = false;
	
	/*
	 * Begin public methods
	 */
	
	/**
	 * Constructor.
	 * 
	 * @param config The configuration file for the tile configuration.
	 */
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
		enable3D_ = true;
				
		// set the Z location of the camera
		cameraZ_ = (config_.getMasterDim()[1]/2.0f) / PApplet.tan(PConstants.PI * fov_/180.0f);
		
		// after the sketch calls draw, it will call the draw method in this class
		pApplet_.registerDraw(this);
		pApplet_.registerPre(this);
		
		// by default, automatically serialize mouse and keyboard events
		
		
		barrier_ = new CyclicBarrier(config_.numFollowers_ + 1);
		
		if (debug_)
			config_.printSettings();
		
		running_ = true;
	}
	
	/**
	 * Registered with the PApplet pre() function and called before each draw loop.
	 */
	public void pre()
	{
		if(debug_) print("Trying to acquire framelock!");
		
		// wait on framelock to be unlocked
		frameLock_.acquire();

		if(debug_) print("Acquired framelock!");
		
		placeScreen();
	}
	
	/**
	 * Registered with the PApplet draw() function and called after each draw loop.
	 */
	public void draw()
	{
		// send end-of-frame message if not leader
		if(!config_.isLeader())
			endFrame();
	}
	
	/**
	 * Registered with the PApplet mouseEvent() function and called when mouse interaction occurs.
	 */
	/*
	public void mouseEvent(MouseEvent e)
	{
		// create mouseEvent object
		mouseEvent_ = e;
	}
	*/
	/**
	 * Registered with the PApplet keyEvent() function and called when keyboard interaction occurs.
	 */
	/*
	public void keyEvent(KeyEvent e)
	{
		// create mouseEvent object
		keyEvent_ = e;
	}
	*/
	/**
	 * Starts the communication thread.
	 */
	public void start()
	{
		/*
		if(enableDefaultSerialization_)
		{
			//pApplet_.registerMouseEvent(this);
			//pApplet_.registerKeyEvent(this);
			
			//pApplet_.registerMethod("mouseEvent", this);
			//pApplet_.registerMethod("keyEvent", this);
		}
		*/
		
		// we are just a follower, register with leader
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
			
			// create thread to launch processes on remote nodes
			if(autostart_)
			{
				AutoLauncher autoLauncher = new AutoLauncher(config_.getFilename());
				autoLauncher.start();
			}
			
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
	
	/**
	 * Called auto-magically by start().
	 */
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
				Process.start = System.currentTimeMillis();
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
	
	/**
	 * Gets the pixel width of the local display.
	 * 
	 * @return The pixel width of the local display.
	 */
	public int getLWidth()
	{
		return config_.getLocalDim()[0];
	}
	
	/**
	 * Gets the pixel height of the local display.
	 * 
	 * @return The pixel height of the local display.
	 */
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
	
	/**
	 * Disables MPE camera placement at each draw. Used for libraries such as PeasyCam, etc.
	 */
	public void disableCameraReset()
	{
		placeCamera_ = false;
	}
	
	/**
	 * Disables autostart of MPE children across cluster. To use MPE you must use mperun script.
	 */
	public void disableAutoStart()
	{
		autostart_ = false;
	}
	
	/**
	 * Disables the automatic serialization of mouse and keyboard events from the head process to the render processes.
	 */
	/*
	public void disableDefaultSerialization()
	{
		enableDefaultSerialization_ = false;
	}
	*/
	
	/**
	 * Allows the user to manually set the field of view of the camera.
	 * @param fov The desired field of view in degrees.
	 */
	public void setFOV(float fov)
	{
		fov_ = fov;
		cameraZ_ = (config_.getMasterDim()[1]/2.0f) / PApplet.tan(PConstants.PI * fov_/180.0f);
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
		
		/*
		 * This is tough.
		 * 
		 * Processing flips the Y coordinate in OpenGL vs. P3D, first of all.
		 * We wont be using P3D, so this shouldn't matter, the resolutions in question
		 * preclude the use of anything but OpenGL.
		 */
		
		if(placeCamera_)
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
        float bottom = (config_.getOffsets()[1] - config_.getMasterDim()[1]/2)*mod;
        float top = ((config_.getOffsets()[1] + config_.getLocalDim()[1]) - config_.getMasterDim()[1]/2)*mod;
        float near   = cameraZ_*mod;
        float far    = 10000;
        pApplet_.frustum(left,right,bottom,top,near,far);
		
		/*
		double near = 0.1;
		double far  = 10000.;
		
		double aspect = getMHeight() / getMWidth() * getLHeight() / getLWidth();
		double winFovY = fov_ * aspect;
		
		double top = PApplet.tan(0.5 * winFovY * PConstants.PI/180.) * near;
		double bottom = -top;
		double left = 1./aspect * bottom;
		double right = 1./aspect * top;
		
		double fleft = left + ()
		*/
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
			
			// receives the attribute if not null
			if(c.att != null)
			{
				receivedAttributes_ = true;
				attribute_ = c.att;
			}
			/*
			// gets mouse and keyboard events from head process
			if(c.k != null)
			{
				pApplet_.keyEvent = c.k;
			}
			if(c.m != null)
			{
				pApplet_.mouseEvent = c.m;
			}
			*/
			// release the framelock
			frameLock_.release();
			
			
		}
	}
	
	private void broadcastFE()
	{
		// create frame event command
		Command command = new Command();
		command.command = "fe";
		
		// set keyboard and mouse
		//command.k = keyEvent_;
		//command.m = mouseEvent_;
		
		if(sendAttributes_)
		{
			command.att = attribute_;
		}
		else command.att = null;
		
		// we have appended the attribute to the command, and can now set it back to false such that repeated
		// messages are not sent
		sendAttributes_ = false;
		
		for(int i = 0; i < clients_.size(); i++)
		{
			clients_.elementAt(i).sendCommand(command);
		}
		
		// set events to null so they are not resent
		//keyEvent_ = null;
		//mouseEvent_ = null;
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
	/**
	 * Broadcasts the object(s) to all client processes.
	 * @param attribute The object to broadcast. Can be casted to any object.
	 */
	public void broadcast(Object attribute)
	{
		sendAttributes_ = true;
		attribute_ = attribute;
	}
	
	// returns true if the last FE message was received with non-null atts
	/**
	 * Determines if a message has been received since last frame draw.
	 * @return True if a message has been received since last frame draw.
	 */
	public boolean messageReceived()
	{
		return receivedAttributes_;
	}
	
	// returns the attributes that were received from the leader
	/**
	 * If a message has been received, returns the message.
	 * @return The message that has been received. Cast to the desired object.
	 */
	public Object getMessage()
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
