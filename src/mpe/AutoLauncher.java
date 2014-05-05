package mpe;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Map;
import java.util.Vector;

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
	Vector<java.lang.Process> processVector_;
	
	public AutoLauncher(String configFile, String sketchPath)
	{
		configFile_ = configFile;
		sketchPath_ = sketchPath;
		processVector_ = new Vector<java.lang.Process>();
	}
	
	public void run()
	{
		Jode root = null;
		root = Jocument.load(configFile_);
		
		System.out.println("AutoLauncher:loading XML configuration node");
		Jode config = root.single("configuration");
		System.out.println("AutoLauncher:loading XML processingPath node");
		String processingPath = config.first("config").attribute("processingPath").v;
		System.out.println("processingPath = "+processingPath);
		// launch each child process
		for(Jode child : config.children())
		{
			if (child.n.equals("process"))
			{
				if(!child.hasAttribute("rank"))
				{
					System.out.println("Rank not specified in configuration for all processes. Aborting.");
					
					// kill all previously launched process'
					for(java.lang.Process p : processVector_)
						p.destroy();
					System.exit(-1);
				}
				else
				{
					final String rank = child.attribute("rank").v;
					final String hostName = child.attribute("host").v;
					
					String display=":0.0";
					if(child.hasAttribute("display"))
						display = child.attribute("display").v;
					
					String[] command = {"ssh",
											hostName, 
											"export RANK="+rank, 
											"export PATH=$PATH:"+processingPath,
											"export DISPLAY="+display,
											";", // seperate the exports with the executable
											"processing-java", 
											 "--sketch="+sketchPath_, 
											 "--run", 
											 "--output="+sketchPath_+"/"+hostName+"_"+rank, 
											 "--force"};

					// the ProcessBuilder will launch the process external to the VM with specified env. parameters
					ProcessBuilder pb = new ProcessBuilder(command);
					Map<String, String> env = pb.environment();
					//env.put("RANK", rank);
					//env.put("PATH", "PATH=" + processingPath);
					//System.out.println(Arrays.toString(command));

					// start the process and merge output streams of child process' with head stream
					try {
						
						pb.redirectErrorStream(true);
						java.lang.Process p = pb.start();
						processVector_.add(p);
						final InputStream is = p.getInputStream();
						
						new Thread(new Runnable() {
						    public void run() {
						        try {
						            BufferedReader reader =
						                new BufferedReader(new InputStreamReader(is));
						            String line;
						            while ((line = reader.readLine()) != null) {
						                System.out.println(hostName + "-rank" + rank + ": " + line);
						            }
						        } catch (IOException e) {
						            e.printStackTrace();
						        } finally {
						            try {
										is.close();
									} catch (IOException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}
						        }
						    }
						}).start();

						//System.out.println("Exit code: " + exitCode);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
	}
	
	/*
	public void replace() {
	      String oldFileName = "~/.processing/preferences.txt";
	      String tmpFileName = "~/.processing/tmp_preferences.txt";

	      BufferedReader br = null;
	      BufferedWriter bw = null;
	      try {
	         br = new BufferedReader(new FileReader(oldFileName));
	         bw = new BufferedWriter(new FileWriter(tmpFileName));
	         String line;
	         while ((line = br.readLine()) != null) {
	            if (line.contains("run.display"))
	               line = line.replaceAll(":0.0", "");
	            bw.write(line+"\n");
	         }
	      } catch (Exception e) {
	         return;
	      } finally {
	         try {
	            if(br != null)
	               br.close();
	         } catch (IOException e) {
	            //
	         }
	         try {
	            if(bw != null)
	               bw.close();
	         } catch (IOException e) {
	            //
	         }
	      }
	      // Once everything is complete, delete old file..
	      File oldFile = new File(oldFileName);
	      oldFile.delete();

	      // And rename tmp file's name to old file name
	      File newFile = new File(tmpFileName);
	      newFile.renameTo(oldFile);

	}
	*/
	
	public void shutDown()
	{
		// kill all previously launched process'
		for(java.lang.Process p : processVector_)
			p.destroy();
		System.exit(0);
	}
}
