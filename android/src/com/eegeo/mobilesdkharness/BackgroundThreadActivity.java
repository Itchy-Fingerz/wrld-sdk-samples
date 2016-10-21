//Copyright eeGeo Ltd (2012-2014), All Rights Reserved

package com.eegeo.mobilesdkharness;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowManager;

import com.vuforia.Vuforia;


public class BackgroundThreadActivity extends MainActivity
{
	private EegeoSurfaceView m_surfaceView;
	private SurfaceHolder m_surfaceHolder;
	private long m_nativeAppWindowPtr;
	private ThreadedUpdateRunner m_threadedRunner;
	private Thread m_updater;
	private boolean m_isInVRMode;
	private VRModule m_vrModule;
	private ARModule m_arModule;
	
    public static View m_loadingDialogContainer;
    private int m_screenWidth = 0;
    private int m_screenHeight = 0;
	

	static {
		System.loadLibrary("eegeo-sdk-samples");
	}

    public void storeScreenDimensions()
    {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        m_screenWidth = metrics.widthPixels;
        m_screenHeight = metrics.heightPixels;
    }
    
    public int getScreenWidth()
    {
    	return m_screenWidth;
    }
    
    public int getScreenHeight()
    {
    	return m_screenHeight;
    }
	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_main);

		m_surfaceView = (EegeoSurfaceView)findViewById(R.id.surface);
		m_surfaceView.getHolder().addCallback(this);
		m_surfaceView.setActivity(this);

		m_vrModule = new VRModule(this);
		m_arModule = new ARModule(this);
		DisplayMetrics dm = getResources().getDisplayMetrics();
		final float dpi = dm.ydpi;
		final Activity activity = this;
		
		m_loadingDialogContainer = findViewById(R.id.loading_indicator);
		initApplication();

		m_threadedRunner = new ThreadedUpdateRunner(false);
		m_updater = new Thread(m_threadedRunner);
		m_updater.start();

		m_threadedRunner.blockUntilThreadStartedRunning();

		runOnNativeThread(new Runnable()
		{
			public void run()
			{
				m_nativeAppWindowPtr = NativeJniCalls.createNativeCode(activity, getAssets(), dpi);

				if(m_nativeAppWindowPtr == 0)
				{
					throw new RuntimeException("Failed to start native code.");
				}
			}
		});
	}
	

	@SuppressLint("InlinedApi")
	private void setScreenSettings()
	{
		
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		if(android.os.Build.VERSION.SDK_INT<16)
			getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
		else if(android.os.Build.VERSION.SDK_INT<19)
			getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
		else
			getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN);
		
	}
	
	public void enterVRMode()
	{
		if(!m_isInVRMode)
		{
			this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
			m_isInVRMode = true;
		}
	}
	
	public void exitVRMode()
	{
		if(m_isInVRMode)
		{
			this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
			m_isInVRMode = false;
		}
	}

	public void runOnNativeThread(Runnable runnable)
	{
		m_threadedRunner.postTo(runnable);
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		// Vuforia-specific resume operation
        Vuforia.onResume();
		
		setScreenSettings();
		runOnNativeThread(new Runnable()
		{
			public void run()
			{
				NativeJniCalls.resumeNativeCode();
				m_threadedRunner.start();
				
				if(m_surfaceHolder != null && m_surfaceHolder.getSurface() != null)
				{
					NativeJniCalls.setNativeSurface(m_surfaceHolder.getSurface());
					NativeJniCalls.updateCardboardProfile(m_vrModule.getUpdatedCardboardProfile());
				}
			}
		});
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		
		runOnNativeThread(new Runnable()
		{
			public void run()
			{
				m_threadedRunner.stop();
				NativeJniCalls.pauseNativeCode();
			}
		});
        
        // Vuforia-specific pause operation
        Vuforia.onPause();
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		
		m_vrModule.stopTracker();
		runOnNativeThread(new Runnable()
		{
			public void run()
			{
				m_threadedRunner.stop();
				NativeJniCalls.destroyNativeCode();
				m_threadedRunner.destroyed();
			}
		});

		m_threadedRunner.blockUntilThreadHasDestroyedPlatform();
		m_nativeAppWindowPtr = 0;
		m_arModule.destroy();
	}
	
	@Override
	public void surfaceCreated(SurfaceHolder holder)
	{
        // Call Vuforia function to (re)initialize rendering after first use
        // or after OpenGL ES context was lost (e.g. after onPause/onResume):
        Vuforia.onSurfaceCreated();
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder)
	{

		runOnNativeThread(new Runnable()
		{
			public void run()
			{
				m_threadedRunner.stop();
			}
		});
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, final int width, final int height)
	{
		Vuforia.onSurfaceChanged(width, height);
		final SurfaceHolder h = holder;
		
		runOnNativeThread(new Runnable()
		{
			public void run()
			{
				m_surfaceHolder = h;
				if(m_surfaceHolder != null) 
				{
					NativeJniCalls.updateVuforiaRendering(width, height);
					NativeJniCalls.setNativeSurface(m_surfaceHolder.getSurface());
					m_threadedRunner.start();
					NativeJniCalls.updateCardboardProfile(m_vrModule.getUpdatedCardboardProfile());
				}
			}
		});
	}

	private class ThreadedUpdateRunner implements Runnable
	{
		private long m_endOfLastFrameNano;
		private boolean m_running;
		private Handler m_nativeThreadHandler;
		private float m_frameThrottleDelaySeconds;
		private boolean m_destroyed;

		public ThreadedUpdateRunner(boolean running)
		{
			m_endOfLastFrameNano = System.nanoTime();
			m_running = false;
			m_destroyed = false;

			float targetFramesPerSecond = 30.f;
			m_frameThrottleDelaySeconds = 1.f/targetFramesPerSecond;
		}

		synchronized void blockUntilThreadStartedRunning()
		{
			while(m_nativeThreadHandler == null);
		}

		synchronized void blockUntilThreadHasDestroyedPlatform()
		{
			while(!m_destroyed);
		}

		public void postTo(Runnable runnable)
		{
			m_nativeThreadHandler.post(runnable);
		}

		public void start()
		{
			m_running = true;
		}

		public void stop()
		{
			m_running = false;
		}

		public void destroyed()
		{
			m_destroyed = true;
		}

		public void run()
		{
			Looper.prepare();
			m_nativeThreadHandler = new Handler();

			while(true)
			{
				runOnNativeThread(new Runnable()
				{
					public void run()
					{
						long timeNowNano = System.nanoTime();
						long nanoDelta = timeNowNano - m_endOfLastFrameNano;
						float deltaSeconds = (float)((double)nanoDelta / 1e9);
						
						if(deltaSeconds > m_frameThrottleDelaySeconds)
						{
							if(m_running)
							{
								m_vrModule.updateNativeCode(deltaSeconds);
							}
							else
							{
								SystemClock.sleep(200);
							}

							m_endOfLastFrameNano = timeNowNano;
						}

						runOnNativeThread(this);
					}
				});

				Looper.loop();
			}
		}
	}
	
    private void initApplication()
    {
        storeScreenDimensions();
        
        // As long as this window is visible to the user, keep the device's
        // screen turned on and bright:
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

	@Override
	public void initVuforia()
	{
		this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		m_arModule.initVuforia();
	}

	@Override
	public void deInitVuforia()
	{
		this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED );
		m_arModule.deInitVuforia();
	}
	
}