package mpe;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;

import xmlcomponents.Jocument;
import xmlcomponents.Jode;

/**
 * AutoLauncher will try to automatically execute MPE processes on remote nodes with SSH.
 * @author Brandt Westing TACC
 *
 */
public class AutoLauncher extends Thread {
	
	String configFile_;
	String sketchPath_;
	
	public AutoLauncher(String configFile, String sketchPath)
	{
		configFile_ = configFile;
		sketchPath_ = sketchPath;
	}
	
	public void run()
	{
		Jode root = null;
		root = Jocument.load(configFile_);
		
		Jode config = root.single("configuration");
		String processingPath = config.first("config").attribute("processingPath").v;
		
		// launch each child process
		for(Jode child : config.children())
		{
			if (child.n.equals("process"))
			{
				String rank = child.attribute("rank").v;
				String hostName = child.attribute("host").v;
				
				String[] envp = {"RANK=" + rank, "PATH=$PATH:" + processingPath};
				
				System.out.println(sketchPath_);
				
				String command = "processing-java.exe --sketch=" + sketchPath_ + " --run --output=" + sketchPath_ + hostName;
				String command2 = "echo";
				String[] command3 = {"C://Program Files//processing-2.0b8//processing-java.exe", 
										"--sketch="+sketchPath_, "--run", "--output="+sketchPath_+"//"+hostName, "--force"};
				
				ProcessBuilder pb = new ProcessBuilder(command3);
				Map<String, String> env = pb.environment();
				env.put("RANK", rank);
				env.put("PATH", "PATH=" + processingPath);
				pb.redirectErrorStream(true);
				//pb.inheritIO();
				try {
					java.lang.Process p = pb.start();
					
					/*
					InputStream errorOutput = new BufferedInputStream(p.getErrorStream(), 10000);
					InputStream consoleOutput = new BufferedInputStream(p.getInputStream(), 10000);

					//int exitCode = p.waitFor();
					Thread.sleep(1000);

					int ch;

					System.out.println("Errors:");
					while ((ch = errorOutput.read()) != -1) {
					    System.out.print((char) ch);
					}

					System.out.println("Output:");
					while ((ch = consoleOutput.read()) != -1) {
					    System.out.print((char) ch);
					}

					//System.out.println("Exit code: " + exitCode);
					*/
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
}
