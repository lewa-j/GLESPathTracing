package ru.lewa_j.pathtracing;

import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.opengl.GLSurfaceView;

public class MainActivity extends Activity
{
	GLSurfaceView glView;
	GLESRenderer glRenderer;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
	{
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.main);
		
		glView=new GLSurfaceView(this);
		glView.setEGLContextClientVersion(2);
		glRenderer=new GLESRenderer();
		glView.setRenderer(glRenderer);
		setContentView(glView);
    }
	
	@Override
	public boolean onTouchEvent(MotionEvent event)
	{
		glRenderer.onTouchEvent(event);

		return super.onTouchEvent(event);
	}
}
