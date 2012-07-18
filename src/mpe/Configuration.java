package mpe;

import java.net.UnknownHostException;

import processing.core.PApplet;
import processing.xml.XMLElement;

public class Configuration {

	private PApplet applet_;
	
	private String server_ = null;;
	private int port_;
	private int[] localDim_;
	private int[] masterDim_;
	private int[] offsets_;
	private int[] tileRes_;
	private int[] numTiles_;
	private int[] bezels_;
	private String display_;
	private int rank_;
	private boolean debug_ = false;
	
	// are we the leader process?
	boolean isLeader_ = false;
	
	// the number of follower processes (total num processes - 1)
	int numFollowers_;
	
	// this constructer is in case you forget the file location or just omit it
	public Configuration(PApplet p)
	{
		this("configuration.xml", p);
	}

	public Configuration(String file, PApplet p)
	{
		// the processing applet that we are taking care of!
		applet_ = p;
		
		tileRes_   = new int[2];
		numTiles_  = new int[2];
		bezels_    = new int[2];
		localDim_  = new int[2];
		masterDim_ = new int[2];
		offsets_   = new int[2];
		XMLElement xml, child;
		
		// my DISPLAY identifier
		display_ = System.getenv("DISPLAY");
		
		if(System.getenv("RANK") != null)
			rank_ = Integer.valueOf(System.getenv("RANK"));
		else rank_ = -1;
		
		// get my hostname to identify me in the config file
		String hostname = "";
		try {
			hostname = java.net.InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			System.out.println("I can't determine my hostname!");
		}

		xml = new XMLElement(p, file);
		child = xml.getChild("dimensions");
		tileRes_[0]   = child.getInt("screenWidth");
		tileRes_[1]   = child.getInt("screenHeight");
		numTiles_[0]  = child.getInt("numTilesWidth");
		numTiles_[1]  = child.getInt("numTilesHeight");
		bezels_[0]    = child.getInt("mullionWidth");
		bezels_[1]    = child.getInt("mullionHeight");
		debug_ = child.getInt("debug") == 1;
		
		// subtract 2 because there are entries for the head and the description
		numFollowers_ = xml.getChildCount() - 2;
		
		// get the server
		for(int i = 0; i < xml.getChildCount(); i++)
		{
			child = xml.getChild(i);
			String nodeName = child.getName();
			if(nodeName.equals("head"))
			{
				server_ = child.getString("host");
				port_ = Integer.valueOf(child.getString("port"));
				System.out.println("Server: "+server_ + ":" + Integer.toString(port_));
			}
		}
		
		
		if(server_ == null)
		{
			System.out.println("Couldn't get head! Setting default.");
			server_ = "localhost";
		}
		
		// our rank is defined in the environment, so we should search for that
		if(rank_ != -1)
		{
			// Find out if this process is the head process
			for(int i = 0; i < xml.getChildCount(); i++)
			{
				child = xml.getChild(i);
				String nodeName = child.getName();
				
				// found the head child
				if(nodeName.equals("head"))
				{
					// we are the head node
					if(child.getInt("rank") == rank_)
					{
						XMLElement headChild = child.getChild(0); 
						localDim_[0] = headChild.getInt("width");
						localDim_[1] = headChild.getInt("height");
						
						masterDim_[0] = localDim_[0];
						masterDim_[1] = localDim_[1];
						
						// offsets
						offsets_[0] = 0;
						offsets_[1] = 0;
						isLeader_ = true;
						return;
					}
				}
			}
			
			// find the entry for the correct host
			for(int i = 0; i < xml.getChildCount(); i++)
			{
				child = xml.getChild(i);
				
				if(child.getInt("rank") == rank_)
					break; // we found our xml entry!
			}
		}
		
		// RANK env. variable was not set, resort to hostname lookup
		else
		{
			System.out.println("RANK was not found in the environment, using hostname lookup");
			// Find out if this process is the head process
			for(int i = 0; i < xml.getChildCount(); i++)
			{
				child = xml.getChild(i);
				String nodeName = child.getName();
				
				// found the head child
				if(nodeName.equals("head"))
				{
					// we are the head node
					if(child.getString("host").equals(hostname))
					{
						XMLElement headChild = child.getChild(0); 
						localDim_[0] = headChild.getInt("width");
						localDim_[1] = headChild.getInt("height");
						
						masterDim_[0] = localDim_[0];
						masterDim_[1] = localDim_[1];
						
						// offsets
						offsets_[0] = 0;
						offsets_[1] = 0;
						isLeader_ = true;

						return;
					}
				}
			}
			
			// find the entry for the correct host
			for(int i = 0; i < xml.getChildCount(); i++)
			{
				child = xml.getChild(i);
				String host = child.getString("host");
				String display = child.getString("display");
				if(host != null)
				{
					if(host.equals(hostname) && display.equals(display_))
					{
						break;
					}
				}
			}
		}
		
		// child corresponds to entry with the correct hostname here
		XMLElement childi;
		childi = child.getChild(0);
		int mini = childi.getInt("i");
		int maxi = childi.getInt("i");
		int minj = childi.getInt("j");
		int maxj = childi.getInt("j");
		
		for(int i = 0; i < child.getChildCount(); i++)
		{
			childi = child.getChild(i);
			if(childi.getInt("i") < mini)
				mini = childi.getInt("i");
			if(childi.getInt("i") > maxi)
				maxi = childi.getInt("i");
			if(childi.getInt("j") < minj)
				minj = childi.getInt("j");
			if(childi.getInt("j") > maxj)
				maxj = childi.getInt("j");
		}
		
		// this get the size of the monitor array for this host in screens
		int rangei = maxi - mini + 1;
		int rangej = maxj - minj + 1;
		
		int totalMullionsX = (rangei - 1)*bezels_[0];
		int totalMullionsY = (rangej - 1)*bezels_[1];
		
		localDim_[0] = rangei*tileRes_[0] + totalMullionsX;
		localDim_[1] = rangej*tileRes_[1] + totalMullionsY;
		
		masterDim_[0] = numTiles_[0]*tileRes_[0] + (numTiles_[0] - 1)*bezels_[0];
		masterDim_[1] = numTiles_[1]*tileRes_[1] + (numTiles_[1] - 1)*bezels_[1];
		
		// offsets
		offsets_[0] = (mini)*tileRes_[0] + mini*bezels_[0];
		offsets_[1] = (minj)*tileRes_[1] + mini*bezels_[1];

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
	
	public void printSettings()
	{
		System.out.println("Settings: Rank: " + rank_ + 
					", offsets: " + offsets_[0] + "," + offsets_[1] +
					", lDims: " + localDim_[0] + "," + localDim_[1] +
					", mDims: " + masterDim_[0] + "," + masterDim_[1]);
	}
	
}
