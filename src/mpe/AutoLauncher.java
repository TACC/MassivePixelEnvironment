package mpe;

import xmlcomponents.Jocument;
import xmlcomponents.Jode;

/**
 * AutoLauncher will try to automatically execute MPE processes on remote nodes with SSH.
 * @author Brandt Westing TACC
 *
 */
public class AutoLauncher extends Thread {
	
	String configFile_;
	
	public AutoLauncher(String configFile)
	{
		configFile_ = configFile;
	}
	
	public void run()
	{
		System.out.println("In launcher");
		Jode root = null;
		root = Jocument.load(configFile_);
		
		Jode config = root.single("configuration");
		
		// launch each child process
		for(Jode child : config.children())
		{
			if (child.n.equals("process"))
			{
				String rank = child.attribute("rank").v;
				String hostname = child.attribute("host").v;
				
				String command = "ssh " + hostname + " processing-java --sketch=/blah --run --output=/blah/";
				
				System.out.println(command);
			}
		}
	}
}
