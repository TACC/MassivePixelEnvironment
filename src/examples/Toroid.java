package mpe.examples;

import mpe.process.Configuration;
import mpe.process.Process;
import processing.core.PApplet;
import processing.core.PVector;

public class Toroid extends PApplet {
	
	// mpe stuff
	Process process;
	Configuration tileConfig;
	
	// toroid stuff
	int pts = 40; 
	float angle = 0;
	float radius = (float) 60.0;

	// lathe segments
	int segments = 60;
	float latheAngle = 0;
	float latheRadius = (float) 100.0;

	//vertices
	PVector vertices[], vertices2[];

	// for shaded or wireframe rendering 
	boolean isWireFrame = false;

	// for optional helix
	boolean isHelix = false;
	float helixOffset = (float) 5.0;
	
	public static void main(String[] args) {
        PApplet.main(new String[] { "mpe.examples.Toroid" });
	}
	
	public void setup()
	{
		tileConfig = new Configuration(sketchPath("configuration.xml"), this);
		size(tileConfig.getLWidth(), tileConfig.getLHeight(), P3D);
		process = new Process(tileConfig);
				
		process.start();
	}
	
	public void draw()
	{
		background(50, 64, 42);
		  // basic lighting setup
		  lights();
		  // 2 rendering styles
		  // wireframe or solid
		  if (isWireFrame){
		    stroke(255, 255, 150);
		    noFill();
		  } 
		  else {
		    noStroke();
		    fill(150, 195, 125);
		  }
		  //center and spin toroid
		  translate(process.getMWidth()/2, process.getMHeight()/2, -100);

		  rotateX(frameCount*PI/150);
		  rotateY(frameCount*PI/170);
		  rotateZ(frameCount*PI/90);

		  // initialize point arrays
		  vertices = new PVector[pts+1];
		  vertices2 = new PVector[pts+1];

		  // fill arrays
		  for(int i=0; i<=pts; i++){
		    vertices[i] = new PVector();
		    vertices2[i] = new PVector();
		    vertices[i].x = latheRadius + sin(radians(angle))*radius;
		    if (isHelix){
		      vertices[i].z = cos(radians(angle))*radius-(helixOffset* 
		        segments)/2;
		    } 
		    else{
		      vertices[i].z = cos(radians(angle))*radius;
		    }
		    angle+=360.0/pts;
		  }

		  // draw toroid
		  latheAngle = 0;
		  for(int i=0; i<=segments; i++){
		    beginShape(QUAD_STRIP);
		    for(int j=0; j<=pts; j++){
		      if (i>0){
		        vertex(vertices2[j].x, vertices2[j].y, vertices2[j].z);
		      }
		      vertices2[j].x = cos(radians(latheAngle))*vertices[j].x;
		      vertices2[j].y = sin(radians(latheAngle))*vertices[j].x;
		      vertices2[j].z = vertices[j].z;
		      // optional helix offset
		      if (isHelix){
		        vertices[j].z+=helixOffset;
		      } 
		      vertex(vertices2[j].x, vertices2[j].y, vertices2[j].z);
		    }
		    // create extra rotation for helix
		    if (isHelix){
		      latheAngle+=720.0/segments;
		    } 
		    else {
		      latheAngle+=360.0/segments;
		    }
		    endShape();
		  }
		
	}

}
