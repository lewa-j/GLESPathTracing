package ru.lewa_j.pathtracing;

import static android.opengl.GLES20.*;
import android.opengl.GLSurfaceView;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import java.nio.*;
import android.util.Log;
import android.opengl.Matrix;
import android.view.MotionEvent;
import java.util.Random;

public class GLESRenderer implements GLSurfaceView.Renderer
{
	
	
	final String renderVertexSource =
	"attribute vec4 vertex;" +
	"varying vec2 texCoord;" +
	"void main()" +
	"{" +
		"texCoord = vertex.xy * 0.5 + 0.5;" +
		"gl_Position = vertex;" +
	"}";
	
	final String renderFragmentSource =
	"precision highp float;" +
	"varying vec2 texCoord;" +
	"uniform sampler2D texture;" +
	"void main()" +
	"{" +
		"gl_FragColor = texture2D(texture, texCoord);" +
	"}";
	
	int bounces = 4;
	float epsilon = 0.0001f;
	float infinity = 10000.0f;
	float lightSize = 0.2f;
	float lightVal = 0.5f;
	
	// vertex shader, interpolate ray per-pixel
	String tracerVertexSource =
	"attribute vec4 vertex;" +
	"uniform vec3 eye, ray00, ray01, ray10, ray11;" +
	"varying vec3 initialRay;" +
	"void main()" +
	"{" +
		"vec2 percent = vertex.xy * 0.5 + 0.5;" +
		"initialRay = mix(mix(ray00, ray01, percent.y), mix(ray10, ray11, percent.y), percent.x);" +
		"gl_Position = vertex;" +
	"}";
	
	// start of fragment shader
	String tracerFragmentSourceHeader =
	"precision highp float;" +
	"uniform vec3 eye;" +
	"varying vec3 initialRay;" +
	"uniform float textureWeight;" +
	"uniform float timeSinceStart;" +
	"uniform sampler2D texture;" +
	"uniform float glossiness;" +
	"vec3 roomCubeMin = vec3(-1.0, -1.0, -1.0);" +
	"vec3 roomCubeMax = vec3(1.0, 1.0, 1.0);\n";
	
	// compute the near and far intersections of the cube (stored in the x and y components) using the slab method
	// no intersection means vec.x > vec.y (really tNear > tFar)
	String intersectCubeSource =
	"vec2 intersectCube(vec3 origin, vec3 ray, vec3 cubeMin, vec3 cubeMax)" +
	"{" +
	"   vec3 tMin = (cubeMin - origin) / ray;" +
	"   vec3 tMax = (cubeMax - origin) / ray;" +
	"   vec3 t1 = min(tMin, tMax);" +
	"   vec3 t2 = max(tMin, tMax);" +
	"   float tNear = max(max(t1.x, t1.y), t1.z);" +
	"   float tFar = min(min(t2.x, t2.y), t2.z);" +
	"   return vec2(tNear, tFar);" +
	"}\n";
	
	// given that hit is a point on the cube, what is the surface normal?
	// TODO: do this with fewer branches
	String normalForCubeSource =
	"vec3 normalForCube(vec3 hit, vec3 cubeMin, vec3 cubeMax)" +
	"{" +
	"   if(hit.x < cubeMin.x + " + epsilon + ") return vec3(-1.0, 0.0, 0.0);" +
	"   else if(hit.x > cubeMax.x - " + epsilon + ") return vec3(1.0, 0.0, 0.0);" +
	"   else if(hit.y < cubeMin.y + " + epsilon + ") return vec3(0.0, -1.0, 0.0);" +
	"   else if(hit.y > cubeMax.y - " + epsilon + ") return vec3(0.0, 1.0, 0.0);" +
	"   else if(hit.z < cubeMin.z + " + epsilon + ") return vec3(0.0, 0.0, -1.0);" +
	"   else return vec3(0.0, 0.0, 1.0);" +
	"}\n";
	
	// compute the near intersection of a sphere
	// no intersection returns a value of +infinity
	String intersectSphereSource =
	"float intersectSphere(vec3 origin, vec3 ray, vec4 sphereCenter)" +
	"{" +
	"   vec3 toSphere = origin - sphereCenter.xyz;" +
	"   float a = dot(ray, ray);" +
	"   float b = 2.0 * dot(toSphere, ray);" +
	"   float c = dot(toSphere, toSphere) - sphereCenter.w*sphereCenter.w;" +
	"   float discriminant = b*b - 4.0*a*c;" +
	"   if(discriminant > 0.0)" +
	"	{" +
	"     float t = (-b - sqrt(discriminant)) / (2.0 * a);" +
	"     if(t > 0.0) return t;" +
	"   }" +
	"   return " + infinity + ";" +
	" }";

	// given that hit is a point on the sphere, what is the surface normal?
	String normalForSphereSource =
	"vec3 normalForSphere(vec3 hit, vec4 sphereCenter)" +
	"{" +
	"   return (hit - sphereCenter.xyz) / sphereCenter.w;" +
	"}";
	
	// use the fragment position for randomness
	String randomSource =
	"float random(vec3 scale, float seed)" +
	"{" +
	"   return fract(sin(dot(gl_FragCoord.xyz + seed, scale)) * 43758.5453 + seed);" +
	"}";
	
	// random cosine-weighted distributed vector
	// from http://www.rorydriscoll.com/2009/01/07/better-sampling/
	String cosineWeightedDirectionSource =
	"vec3 cosineWeightedDirection(float seed, vec3 normal)" +
	"{" +
	"   float u = random(vec3(12.9898, 78.233, 151.7182), seed);" +
	"   float v = random(vec3(63.7264, 10.873, 623.6736), seed);" +
	"   float r = sqrt(u);" +
	"   float angle = 6.283185307179586 * v;" +
	// compute basis from normal
	"   vec3 sdir, tdir;" +
	"   if (abs(normal.x)<.5)" +
	"	{" +
	"     sdir = cross(normal, vec3(1,0,0));" +
	"   }" +
	"	else" +
	"	{" +
	"     sdir = cross(normal, vec3(0,1,0));" +
	"   }" +
	"   tdir = cross(normal, sdir);" +
	"   return r*cos(angle)*sdir + r*sin(angle)*tdir + sqrt(1.-u)*normal;" +
	"}";
	
	// random normalized vector
	String uniformlyRandomDirectionSource =
	"vec3 uniformlyRandomDirection(float seed)" +
	"{" +
		"float u = random(vec3(12.9898, 78.233, 151.7182), seed);" +
		"float v = random(vec3(63.7264, 10.873, 623.6736), seed);" +
		"float z = 1.0 - 2.0 * u;" +
		"float r = sqrt(1.0 - z * z);" +
		"float angle = 6.283185307179586 * v;" +
		"return vec3(r * cos(angle), r * sin(angle), z);" +
	"}\n";
	
	// random vector in the unit sphere
	// note: this is probably not statistically uniform, saw raising to 1/3 power somewhere but that looks wrong?
	String uniformlyRandomVectorSource =
	"vec3 uniformlyRandomVector(float seed)" +
	"{" +
		"return uniformlyRandomDirection(seed) * sqrt(random(vec3(36.7539, 50.3658, 306.2759), seed));" +
	"}\n";
	
	// compute specular lighting contribution
	String specularReflection =
	" vec3 reflectedLight = normalize(reflect(light - hit, normal));" +
	" specularHighlight = max(0.0, dot(reflectedLight, normalize(hit - origin)));";
	
	
	// update ray using normal and bounce according to a diffuse reflection
	String newDiffuseRay =
	"ray = cosineWeightedDirection(timeSinceStart + float(bounce), normal);";
	
	// update ray using normal according to a specular reflection
	String newReflectiveRay =
	"ray = reflect(ray, normal);" +
	specularReflection +
	"specularHighlight = 2.0 * pow(specularHighlight, 20.0);";
	
	// update ray using normal and bounce according to a glossy reflection
	String newGlossyRay =
	"ray = normalize(reflect(ray, normal)) + uniformlyRandomVector(timeSinceStart + float(bounce)) * glossiness;" +
	specularReflection +
	"specularHighlight = pow(specularHighlight, 3.0);";
	
	
	String redGreenCornellBox =
	"if(hit.x < -0.9999) surfaceColor = vec3(1.0, 0.3, 0.1);" + // red
	"else if(hit.x > 0.9999) surfaceColor = vec3(0.3, 1.0, 0.1);"; // green
	
	
	final float[] vertices={
		-1f,-1f,
		-1f, 1f,
		 1f,-1f,
		 1f, 1f};
		 
	int vertexBuffer;
	int framebuffer;
	int renderProgram;
	int vertAttr;
	int tracerProgram=0;
	int lightHandle;
	int eyeHandle;
	int[] rayHandles;
	int textureWeightHandle;
	int timeSinceStartHandle;
	int glossinessHandle;
	int sphere1Handle;
	int[] textures=new int[2];
	
	int scrW;
	int scrH;
	float aspect;
	int texSize=256;
	int maxSamples=64;
	int sampleCount=0;
	int samplePerFrame=1;
	
	float angleX = 0f;
	float angleY = 0f;
	float zoomZ=2.5f;
	float[] eye={0f,0f,0f};
	float[] light={0.4f,0.5f,-0.6f};
	float[] sphere1={0f,-0.75f,0f,0.25f};
	
	float glossines = 0.6f;
	
	float[] viewMatrix;
	float[] projectionMatrix;
	float[] viewProjectionMatrix;
	
	float touchX;
	float touchY;
	
	Random rand;

	@Override
	public void onSurfaceCreated(GL10 p1, EGLConfig conf)
	{
		rand=new Random();
		
		viewMatrix=new float[16];
		projectionMatrix=new float[16];
		viewProjectionMatrix=new float[16];
		
		int[]ids=new int[1];
		//create vertex buffer
		glGenBuffers(1,ids,0);
		vertexBuffer=ids[0];
		glBindBuffer(GL_ARRAY_BUFFER,vertexBuffer);
		FloatBuffer buff=ByteBuffer.allocateDirect(vertices.length*4)
		.order(ByteOrder.nativeOrder()).asFloatBuffer();
		buff.put(vertices).position(0);
		glBufferData(GL_ARRAY_BUFFER,vertices.length*4,buff,GL_STATIC_DRAW);
		CheckGLError("Create vertex buffer");
		
		//create framebuffer
		glGenRenderbuffers(1,ids,0);
		framebuffer=ids[0];
		
		//create textures
		glGenTextures(2,textures,0);
		for(int i=0;i<2;i++)
		{
			glBindTexture(GL_TEXTURE_2D,textures[i]);
			
			//glPixelStorei(GL_UNPACK_ALIGNMENT,4);
			//glPixelStorei(GL_PACK_ALIGNMENT,1);
			
			glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR); //GL_NEAREST);
			glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);
			glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);
			glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
			
			glTexImage2D(GL_TEXTURE_2D,0,GL_RGB,texSize,texSize,0,GL_RGB,GL_UNSIGNED_BYTE,null);
		}
		glBindTexture(GL_TEXTURE_2D, 0);
		
		//create render shader
		renderProgram=CompileProgram(renderVertexSource,renderFragmentSource);
		vertAttr=glGetAttribLocation(renderProgram,"vertex");
		Log.e("Dbg","vertAttr: "+vertAttr);
		CheckGLError("Create render shader");
		
		glClearColor(0f,0f,0.5f,1);
		glClear(GL_COLOR_BUFFER_BIT);
		
		SetObjects();
		
		sampleCount=0;
	}
	
	void SetObjects()
	{
		if(tracerProgram!=0)
			glDeleteProgram(tracerProgram);
		
		tracerProgram=CompileProgram(tracerVertexSource,makeTracerFragmentSource());
		
		lightHandle=glGetUniformLocation(tracerProgram,"light");
		eyeHandle=glGetUniformLocation(tracerProgram,"eye");
		rayHandles=new int[4];
		rayHandles[0]=glGetUniformLocation(tracerProgram,"ray00");
		rayHandles[1]=glGetUniformLocation(tracerProgram,"ray01");
		rayHandles[2]=glGetUniformLocation(tracerProgram,"ray10");
		rayHandles[3]=glGetUniformLocation(tracerProgram,"ray11");
		
		textureWeightHandle=glGetUniformLocation(tracerProgram,"textureWeight");
		timeSinceStartHandle=glGetUniformLocation(tracerProgram,"timeSinceStart");
		glossinessHandle=glGetUniformLocation(tracerProgram,"glossiness");
		
		sphere1Handle=glGetUniformLocation(tracerProgram,"sphereCenter1");
		glEnableVertexAttribArray(vertAttr);
		
		sampleCount=0;
	}

	@Override
	public void onSurfaceChanged(GL10 p1, int w, int h)
	{
		scrW=w;
		scrH=h;
		aspect=(float)h/w;
		//glViewport(0,0,w,h);
		glViewport(0,0,texSize,texSize);
	}

	@Override
	public void onDrawFrame(GL10 p1)
	{
		eye[0]=(float)(zoomZ*Math.sin(angleY)*Math.cos(angleX));
		eye[1]=(float)(zoomZ*Math.sin(angleX));
		eye[2]=(float)(zoomZ*Math.cos(angleY)*Math.cos(angleX));
		
		glClear(GL_COLOR_BUFFER_BIT);
		
		if(sampleCount<maxSamples)
			for(int i=0;i<samplePerFrame;i++)
				RendererUpdate();
		
		//glViewport(0,0,Math.min(scrW,scrH),Math.min(scrW,scrH));
		glViewport(0,0,scrW,scrH);
		glUseProgram(renderProgram);
		glBindTexture(GL_TEXTURE_2D,textures[0]);
		glBindBuffer(GL_ARRAY_BUFFER,vertexBuffer);
		glEnableVertexAttribArray(vertAttr);
		glVertexAttribPointer(vertAttr,2,GL_FLOAT,false,0,0);
		glDrawArrays(GL_TRIANGLE_STRIP,0,4);
		CheckGLError("Draw");
		
		
	}
	
	void RendererUpdate()
	{
		Matrix.setLookAtM(viewMatrix,0,
						  eye[0],eye[1],eye[2],
						  0,0,0,
						  0,1,0);
		Matrix.perspectiveM(projectionMatrix,0,45,aspect,0.1f,100);
		Matrix.multiplyMM(viewProjectionMatrix,0,projectionMatrix,0,viewMatrix,0);
		//Matrix.multiplyMM(viewProjectionMatrix,0,viewMatrix,0,projectionMatrix,0);
		
		//float[] jitter=new float[16];
		//Matrix.setIdentityM(jitter,0);
		//Matrix.translateM(jitter,0,(rand.nextFloat()*2f-1f)/(float)texSize,(rand.nextFloat()*2f-1f)/(float)texSize,0);
		
		//Matrix.multiplyMM(jitter,0,jitter,0,viewProjectionMatrix,0);
		
		PTUpdate(viewProjectionMatrix);
	}
	
	void PTUpdate(float[] matrix)
	{
		
		glUseProgram(tracerProgram);
		
		glUniform3fv(lightHandle,1,light,0);
		glUniform4fv(sphere1Handle,1,sphere1,0);
		
		glUniform3fv(eyeHandle,1,eye,0);
		float[] tRay=GetEyeRay(matrix,-1,-1);
		glUniform3fv(rayHandles[0],1,tRay,0);
		tRay=GetEyeRay(matrix,-1,1);
		glUniform3fv(rayHandles[1],1,tRay,0);
		tRay=GetEyeRay(matrix,1,-1);
		glUniform3fv(rayHandles[2],1,tRay,0);
		tRay=GetEyeRay(matrix,1,1);
		glUniform3fv(rayHandles[3],1,tRay,0);
		
		glUniform1f(timeSinceStartHandle,rand.nextFloat()-0.1573f);
		glUniform1f(textureWeightHandle,(float)sampleCount/(sampleCount+1));
		glUniform1f(glossinessHandle,glossines);
		
		glBindTexture(GL_TEXTURE_2D,textures[0]);
		glBindBuffer(GL_ARRAY_BUFFER,vertexBuffer);
		glBindFramebuffer(GL_FRAMEBUFFER,framebuffer);
		glViewport(0,0,texSize,texSize);
		glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,textures[1],0);
		glVertexAttribPointer(vertAttr,2,GL_FLOAT,false,0,0);
		glDrawArrays(GL_TRIANGLE_STRIP,0,4);
		glBindFramebuffer(GL_FRAMEBUFFER,0);
		
		int t=textures[0];
		textures[0]=textures[1];
		textures[1]=t;
		sampleCount++;
	}

	
	int CompileProgram(String vtxSrc, String fragSrc)
	{
		int id=glCreateProgram();
		int vs=CompileShader(vtxSrc,GL_VERTEX_SHADER);
		int fs=CompileShader(fragSrc,GL_FRAGMENT_SHADER);
		glAttachShader(id,vs);
		glAttachShader(id,fs);
		glLinkProgram(id);
		Log.e("ShaderProgram","Log: "+glGetProgramInfoLog(id));
		return id;
	}
	
	int CompileShader(String src, int type)
	{
		int id=glCreateShader(type);
		glShaderSource(id,src);
		glCompileShader(id);
		Log.e("Shader","Log: "+glGetShaderInfoLog(id));
		return id;
	}
	
	String makeTracerFragmentSource()
	{
		return tracerFragmentSourceHeader+
		"uniform vec3 light;"+
		"uniform vec4 sphereCenter1;" +
		intersectCubeSource+
		normalForCubeSource+
		intersectSphereSource+
		normalForSphereSource+
		randomSource+
		cosineWeightedDirectionSource+
		uniformlyRandomDirectionSource+
		uniformlyRandomVectorSource+
		MakeShadow()+
		makeCalculateColor()+
		makeMain();
	}
	
	String makeCalculateColor()
	{
		return
			"vec3 calculateColor(vec3 origin, vec3 ray, vec3 light)" +
			"{" +
			"   vec3 colorMask = vec3(1.0);" +
			"   vec3 accumulatedColor = vec3(0.0);" +

			// main raytracing loop
			"   for(int bounce = 0; bounce < " + bounces + "; bounce++)" +
			"	{" +
			// compute the intersection with everything
			"     vec2 tRoom = intersectCube(origin, ray, roomCubeMin, roomCubeMax);" +
//			concat(objects, function(o){ return o.getIntersectCode(); }) +
			//=======
			"float tSphere1 = intersectSphere(origin, ray, sphereCenter1);"+
			//=======

			// find the closest intersection
			"     float t = " + infinity + ";" +
			"     if(tRoom.x < tRoom.y) t = tRoom.y;" +
//			concat(objects, function(o){ return o.getMinimumIntersectCode(); }) +
			//========
			"if(tSphere1 < t) t = tSphere1;"+
			//========

			// info about hit
			"     vec3 hit = origin + ray * t;" +
			"     vec3 surfaceColor = vec3(0.75);" +
			"     float specularHighlight = 0.0;" +
			"     vec3 normal;" +

			// calculate the normal (and change wall color)
			"     if(t == tRoom.y)" +
			"	  {" +
			"       normal = -normalForCube(hit, roomCubeMin, roomCubeMax);" +
//			[yellowBlueCornellBox, redGreenCornellBox][environment] +
			redGreenCornellBox+
		//	"if(hit.y < -0.9999)"+
		//	"{"+
		//	newReflectiveRay+
		//	"}"+
		//	"else if(hit.x > 0.9999)"+
		//	"{"+
			newDiffuseRay +
		//	"}"+
			"     }" +
			"	  else if(t == " + infinity + ")" +
			"	  {" +
			"       break;" +
			"     }" +
			"	  else" +
			"	  {" +
			"       if(false) ;" + // hack to discard the first 'else' in 'else if'
//			concat(objects, function(o){ return o.getNormalCalculationCode(); }) +
//			[newDiffuseRay, newReflectiveRay, newGlossyRay][material] +
			//=========
			"else if(t == tSphere1) normal = normalForSphere(hit, sphereCenter1);"+
//			newReflectiveRay+
//			newDiffuseRay+
			newGlossyRay+
			//=========
			"     }" +

				// compute diffuse lighting contribution
			"     vec3 toLight = light - hit;" +
			"     float diffuse = max(0.0, dot(normalize(toLight), normal));" +

				// trace a shadow ray to the light
			"     float shadowIntensity = shadow(hit + normal * " + epsilon + ", toLight);" +
//			"     float shadowIntensity = 1.0;" +
			
				// do light bounce
			"     colorMask *= surfaceColor;" +
			//"if(bounce>0)"+
			//"{"+
			"     accumulatedColor += colorMask * (" + lightVal + " * diffuse * shadowIntensity);" +
			"     accumulatedColor += colorMask * specularHighlight * shadowIntensity;" +
			//"}"+

			// calculate next origin
			"     origin = hit;" +
			"   }" +
			
			"   return accumulatedColor;" +
			"}\n";
	}
	
	String MakeShadow()
	{
		return
			"float shadow(vec3 origin, vec3 ray)" +
			"{" +
//			concat(objects, function(o){ return o.getShadowTestCode(); }) +
			//==========
			"float tSphere1 = intersectSphere(origin, ray, sphereCenter1);"+
			"if(tSphere1 < 1.0) return 0.0;"+
			//==========
			"   return 1.0;" +
			"}";
	}
	
	String makeMain()
	{
		return 
		"void main()\n" +
		"{" +
			"vec3 newLight = light + uniformlyRandomVector(timeSinceStart - 53.0) * " + lightSize + ";" +
			"vec3 textureCol = texture2D(texture, gl_FragCoord.xy / "+texSize+".0).rgb;" +
			"gl_FragColor = vec4(mix(calculateColor(eye, initialRay, newLight), textureCol, textureWeight), 1.0);" +
		"}\n";
	}
	
	float[] GetEyeRay(float[] matrix,float x, float y)
	{
		float out[]=new float[4];
		Matrix.multiplyMV(out,0,matrix,0,new float[]{x, y, 0, 1},0);
		
		out[0]/=out[3];
		out[1]/=out[3];
		out[2]/=out[3];
		
		out[0]-=eye[0];
		out[1]-=eye[1];
		out[2]-=eye[2];
		
		return new float[]{out[0],out[1],out[2]};
	}
	
	public static void CheckGLError(String command)
	{
		int id = glGetError();
		String error="";
		if(id!=GL_NO_ERROR)
		{
			switch(id)
			{
				case GL_INVALID_ENUM:
					error="GL_INVALID_ENUM";
					break;
				case GL_INVALID_FRAMEBUFFER_OPERATION:
					error="GL_INVALID_FRAMEBUFFER_OPERATION";
					break;
				case GL_INVALID_OPERATION:
					error="GL_INVALID_OPERATION";
					break;
				case GL_INVALID_VALUE:
					error="GL_INVALID_VALUE";
					break;
				case GL_OUT_OF_MEMORY:
					error="GL_OUT_OF_MEMORY";
					break;
				default:
					error="GL_...";
			}
			Log.e("GLError","Func: "+command+" Id: "+id+" Name: "+error);
		}
	}
	
	public void onTouchEvent(MotionEvent event)
	{
		touchX=event.getX();
		touchY=event.getY();
		//Log.e("Input","x "+touchX+" y "+touchY);
		
		light[0]=(touchX/scrW)*2f-1f;
		light[1]=1f-(touchY/scrH)*2f;
		//sphere1[0]=(touchX/scrW)*2f-1f;
		//sphere1[1]=1f-(touchY/scrH)*2f;
		//angleY=(touchX/scrW)*6-3f;
		//angleX=3f-(touchY/scrH)*6f;
		
		sampleCount=0;
	}
}
