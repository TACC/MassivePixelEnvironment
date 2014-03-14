package mpe;

import java.net.UnknownHostException;

// XML parser library includes
import xmlcomponents.Jocument;
import xmlcomponents.Jode;

import processing.core.PApplet;

/**
 * An object that encapsulates the configuration context of the display system.
 * @author Brandt Westing TACC
 *
 */
public class Configuration {

	private PApplet applet_;
	
	private String file_;
	
	private String server_ = null;;
	private int port_;
	private int[] localDim_;
	private int[] masterDim_;
	private int[] offsets_;
	private int[] tileRes_;
	private int[] numTiles_;
	private int[] bezels_;
	private int[] windowLocation_;
	private String display_;
	private int rank_;
	private boolean debug_ = true;
	
	// are we the leader process?
	boolean isLeader_ = false;
	
	// the number of follower processes (total num processes - 1)
	int numFollowers_;
	
	// this constructor is in case you forget the file location or just omit it
	public Configuration(PApplet p)
	{
		this("configuration.xml", p);
	}

	// We assume that a head process is launched from the processing GUI, and the rank is not declared
	// if the rank is declared in the environment, we assume the process was launched via command line
	public Configuration(String _file, PApplet _p)
	{
		// the processing applet that we are taking care of!
		applet_ = _p;
		
		file_ = _file;
		
		tileRes_   = new int[2];
		numTiles_  = new int[2];
		bezels_    = new int[2];
		localDim_  = new int[2];
		masterDim_ = new int[2];
		offsets_   = new int[2];
		windowLocation_ = new int[2];
		
		// set up the pipeline for reading XML
		
		Jode root = null;
		root = Jocument.load(_file);
								
		// my DISPLAY identifier
		display_ = System.getenv("DISPLAY");
		
		if(System.getenv("RANK") != null)
			rank_ = Integer.valueOf(System.getenv("RANK"));
		else rank_ = -1; // head node in auto-start
		
		System.out.println("loading XML configuration node");
		Jode config = root.single("configuration");
		System.out.println("loading XML dimensions node");
		Jode dimensions = config.single("dimensions");
		System.out.println("loading XML dimensions node, screenWidth");
		tileRes_[0]   = Integer.parseInt(dimensions.attribute("screenWidth").v);
		tileRes_[1]   = Integer.parseInt(dimensions.attribute("screenHeight").v);
		numTiles_[0]  = Integer.parseInt(dimensions.attribute("numTilesWidth").v);
		numTiles_[1]  = Integer.parseInt(dimensions.attribute("numTilesHeight").v);
		bezels_[0]    = Integer.parseInt(dimensions.attribute("mullionWidth").v);
		System.out.println("loading XML dimensions node, mullionHeight");
		bezels_[1]    = Integer.parseInt(dimensions.attribute("mullionHeight").v);
		System.out.println("loading XML dimensions node, debug");
		debug_ = Integer.parseInt(dimensions.attribute("debug").v) == 1;
		
		numFollowers_ = config.children().getLength() - 3;
		System.out.println("numFollowers_ = " + numFollowers_);
		System.out.println("loading XML dimensions node, head");
		Jode head = config.first("head");
		
		if(head != null)
		{
			server_ = head.attribute("host").v;
			port_ = Integer.parseInt(head.attribute("port").v);
			if(debug_)
				System.out.println("Server: "+ server_ + ":" + Integer.toString(port_));
		}
		else
		{
			System.out.println("Couldn't get head! Setting default as localhost");
			server_ = "localhost";
		}
		
		// we are the head node
		if(rank_ == -1)
		{
			Jode headChild = head.first();
			localDim_[0] = Integer.parseInt(headChild.attribute("width").v);
			localDim_[1] = Integer.parseInt(headChild.attribute("height").v);
			
			masterDim_[0] = localDim_[0];
			masterDim_[1] = localDim_[1];
			
			// offsets
			offsets_[0] = 0;
			offsets_[1] = 0;
			isLeader_ = true;
			return;
		}
		
		Jode child = null;
		
		// find the entry for the correct host
		for(Jode configChild: config.children())
		{
			if(configChild.hasAttribute("rank"))
			{
				if(Integer.parseInt(configChild.attribute("rank").v) == rank_)
				{
					child = configChild;
					break; // we found our xml entry!
				}
			}
		}
		
		if(child == null)
		{
			System.out.println("ERROR: Couldn't find my entry in the configuration. Exiting.");
			System.exit(-1);
		}
		
		// child corresponds to entry with the correct hostName here
		Jode childi;
		childi = child.first();
		int mini = Integer.parseInt(childi.attribute("i").v);
		int maxi = Integer.parseInt(childi.attribute("i").v);
		int minj = Integer.parseInt(childi.attribute("j").v);
		int maxj = Integer.parseInt(childi.attribute("j").v);
		
		
		// note: this won't work generically -- only when addressing multiple screens in one display (ala xinerama) -- add tag for this?
		windowLocation_[0] = (mini)*tileRes_[0];
		windowLocation_[1] = 0;
		
		try
		{
			windowLocation_[0] = Integer.parseInt(childi.attribute("x").v);
			System.out.println("!!!! x: "+childi.attribute("x").v);
		}
		catch (Exception e)
		{
			if (debug_) System.out.println("no x attribute found for rank "+rank_);
		}
		
		try
		{
			windowLocation_[1] = Integer.parseInt(childi.attribute("y").v);
		}
		catch (Exception e)
		{
			if (debug_) System.out.println("no y attribute found for rank "+rank_);
		}
		
		for(int i = 0; i < child.children().getLength(); i++)
		{
			childi = child.children().get(i);
			if(Integer.parseInt(childi.attribute("i").v) < mini)
				mini = Integer.parseInt(childi.attribute("i").v);
			if(Integer.parseInt(childi.attribute("i").v) > maxi)
				maxi = Integer.parseInt(childi.attribute("i").v);
			if(Integer.parseInt(childi.attribute("j").v) < minj)
				minj = Integer.parseInt(childi.attribute("j").v);
			if(Integer.parseInt(childi.attribute("j").v) > maxj)
				maxj = Integer.parseInt(childi.attribute("j").v);
		}
		
		//windowLocation_[0] = 0;
		//windowLocation_[1] = 0;
		
		// this get the size of the monitor array for this host in screens
		int rangei = maxi - mini + 1;
		int rangej = maxj - minj + 1;
		
		if (debug_)
			System.out.println("maxi: "+maxi+" mini: "+mini+" rangei: "+rangei);
		
		int totalMullionsX = (rangei - 1)*bezels_[0];
		int totalMullionsY = (rangej - 1)*bezels_[1];
		
		localDim_[0] = rangei*tileRes_[0] + totalMullionsX;
		localDim_[1] = rangej*tileRes_[1] + totalMullionsY;
		
		masterDim_[0] = numTiles_[0]*tileRes_[0] + (numTiles_[0] - 1)*bezels_[0];
		masterDim_[1] = numTiles_[1]*tileRes_[1] + (numTiles_[1] - 1)*bezels_[1];
		
		if (debug_)
		{
			System.out.println("masterDim_[0]: "+masterDim_[0]+"masterDim_[1]: "+masterDim_[1]);
			System.out.println("localDim_[0]: "+localDim_[0]+"localDim_[1]: "+localDim_[1]);
		}
		
		// offsets
		offsets_[0] = (mini)*tileRes_[0] + mini*bezels_[0];
		offsets_[1] = (minj)*tileRes_[1] + minj*bezels_[1];
		
		//set the initial window location of the processing sketch
		//_p.frame.setLocation(0,0);		
		
		if(debug_)
			printSettings();
	}
	
	public PApplet getApplet()
	{
		return applet_;
	}
		
	public String getServer() {
		return server_;
	}

	public int getPort() {
		return port_;
	}

	public int[] getLocalDim() {
		return localDim_;
	}
	
	// special for processing
	public int getLWidth()
	{
		return localDim_[0];
	}
	
	// special for processing
	public int getLHeight()
	{
		return localDim_[1];
	}

	public int[] getMasterDim() {
		return masterDim_;
	}

	public int[] getOffsets() {
		return offsets_;
	}
	
	// Returns an array containing the (x,y) location of the sketch window 
	
	public int[] getWindowLocation() {
		return windowLocation_;
	}

	public int getNumFollowers()
	{
		return numFollowers_;
	}

	public boolean isLeader()
	{
		return isLeader_;
	}
	
	public boolean getDebug()
	{
		return debug_;
	}
	
	public int getRank()
	{
		return rank_;
	}
	
	public String getFilename()
	
	{
		return file_;
	}
	
	public void printSettings()
	{
		System.out.println("Settings: Rank: " + rank_ + 
					", offsets: " + offsets_[0] + "," + offsets_[1] +
					", lDims: " + localDim_[0] + "," + localDim_[1] +
					", mDims: " + masterDim_[0] + "," + masterDim_[1] +
					", windowLocation: " + windowLocation_[0] + "," + windowLocation_[1]);
	}
	
}
