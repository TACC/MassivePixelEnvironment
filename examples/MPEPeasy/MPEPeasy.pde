import processing.opengl.*;

import peasy.*;
PeasyCam cam;

// MPE includes
import mpe.Process;
import mpe.Configuration;

// MPE Process thread
Process process;

// MPE Configuration object
Configuration tileConfig;

void setup() {
  
  // create a new configuration object and specify the path to the configuration file
  tileConfig = new Configuration(dataPath("configuration.xml"), this);
  
  // set the size of the sketch based on the configuration file
  size(tileConfig.getLWidth(), tileConfig.getLHeight(), OPENGL);
  
  // create a new process
  process = new Process(tileConfig);
  
  // disable camera placement by MPE, because it interferes with PeasyCam
  process.disableCameraReset();
 
  // initialize the peasy cam
  cam = new PeasyCam(this, 3000);
  cam.setMinimumDistance(0);
  cam.setMaximumDistance(5000);
  
  // start the MPE process
  process.start();
}
void draw() {
  
  // synchronize this process' camera with the headnode
  if(process.messageReceived())
  {
    // set the animation time to 0, otherwise we get weird behavior
    cam.setState((CameraState) process.getMessage(), 0);
  }
  
  // draw a couple boxes
  scale(5);
  rotateX(-.5);
  rotateY(-.5);
  background(0);
  fill(255,0,0);
  box(200);
  pushMatrix();
  translate(0,0,200);
  fill(0,0,255);
  box(50);
  popMatrix();
}

// when the master process receives a mouse event, broadcast the update camera state to the other processes
void mouseDragged()
{
  process.broadcast(cam.getState());
}
