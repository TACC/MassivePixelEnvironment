package mpe.process;

public class FollowerState {

	private int numFollowers_;
	
	// the number of processes not yet ready to render
	private int numReady_;
	
	// the number of processes connected
	private int numConnected_;
	
	// have we been notified?
	private boolean notified_ = false;
		
	public FollowerState(int numFollowers) 
	{
		numFollowers_ = numFollowers;
		
		// no followers connected initially
		numConnected_ = 0;
		
		numReady_ = 0;
		
	}
	
	public synchronized boolean allConnected()
	{
		return numConnected_ == numFollowers_;
	}
	
	public synchronized void incrementConnected()
	{
		numConnected_++;
	}
	
	public synchronized void ready()
	{
		numReady_++;
	}
	
	public synchronized boolean allReady()
	{
		return numReady_ == numFollowers_;
	}
	
	public synchronized void setAllReady()
	{
		numReady_ = numFollowers_;
	}
	
	public synchronized void setNoneReady()
	{
		numReady_ = 0;
	}
	
	public synchronized boolean notified()
	{
		return notified_;
	}
	
	public synchronized void notifiedTrue()
	{
		notified_ = true;
	}
	
	public synchronized void notifiedFalse()
	{
		notified_ = false;
	}
	
}
