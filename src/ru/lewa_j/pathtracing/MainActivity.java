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
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.options_menu,menu);
		return true;
	}

	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item)
	{
		switch(item.getItemId())
		{
			case R.id.res:
				Toast.makeText(this,"res",Toast.LENGTH_SHORT).show();
				return true;
		}
		return super.onMenuItemSelected(featureId, item);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch(item.getItemId())
		{
			case R.id.res_05:
				Toast.makeText(this,"res_05",Toast.LENGTH_SHORT).show();
				glRenderer.SetResolution(glRenderer.scrW/2);
				break;
			case R.id.res_1:
				Toast.makeText(this,"res_1",Toast.LENGTH_SHORT).show();
				glRenderer.SetResolution(glRenderer.scrW);
				break;
			case R.id.res_64:
				Toast.makeText(this,"res_64",Toast.LENGTH_SHORT).show();
				glRenderer.SetResolution(64);
				break;
			case R.id.res_128:
				Toast.makeText(this,"res_128",Toast.LENGTH_SHORT).show();
				glRenderer.SetResolution(128);
				break;
			case R.id.res_256:
				Toast.makeText(this,"res_256",Toast.LENGTH_SHORT).show();
				glRenderer.SetResolution(256);
				break;
			case R.id.res_512:
				Toast.makeText(this,"res_512",Toast.LENGTH_SHORT).show();
				glRenderer.SetResolution(512);
				break;
		}
		item.setChecked(true);
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onPause()
	{
		glView.onPause();
		super.onPause();
	}

	@Override
	protected void onResume()
	{
		glView.onResume();
		super.onResume();
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent event)
	{
		glRenderer.onTouchEvent(event);

		return super.onTouchEvent(event);
	}
}
