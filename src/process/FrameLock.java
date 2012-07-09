package mpe.process;

import java.util.concurrent.Semaphore;

public class FrameLock {
	
	Semaphore frameLock_;
	
	public FrameLock()
	{
		frameLock_ = new Semaphore(1);
	}
	
	public void acquire()
	{
		try {
			frameLock_.acquire();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void release()
	{
		frameLock_.release();
	}

}
