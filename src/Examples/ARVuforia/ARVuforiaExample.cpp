// Copyright eeGeo Ltd (2012-2014), All Rights Reserved

#include "ARVuforiaExample.h"
#include "VectorMath.h"
#include "LatLongAltitude.h"
#include "CatmullRomSpline.h"
#include "CameraSplinePlaybackController.h"
#include "ResourceCeilingProvider.h"
#include "GlobeCameraController.h"
#include "EegeoWorld.h"
#include "EarthConstants.h"
#include "ScreenProperties.h"

#include <Vuforia/Vuforia.h>
#include <Vuforia/CameraDevice.h>
#include <Vuforia/Renderer.h>
#include <Vuforia/VideoBackgroundConfig.h>
#include <Vuforia/Trackable.h>
#include <Vuforia/TrackableResult.h>
#include <Vuforia/Tool.h>
#include <Vuforia/Tracker.h>
#include <Vuforia/TrackerManager.h>
#include <Vuforia/ObjectTracker.h>
#include <Vuforia/CameraCalibration.h>
#include <Vuforia/UpdateCallback.h>
#include <Vuforia/DataSet.h>

#include "Logger.h"

#define INTERIOR_NEAR_MULTIPLIER 0.025f
#define EXTERIOR_NEAR_MULTIPLIER 0.1f


using namespace std;

namespace Examples
{

	ARVuforiaExample::ARVuforiaExample(Eegeo::EegeoWorld& eegeoWorld,
                                           Eegeo::Camera::GlobeCamera::GlobeCameraController* pCameraController,
                                           const IScreenPropertiesProvider& initialScreenProperties,
										   Examples::IARTracker& arTracker)
    : m_world(eegeoWorld)
    , m_pARController(NULL)
	, m_arTracker(arTracker)
    {
        Eegeo::m44 projectionMatrix = Eegeo::m44(pCameraController->GetRenderCamera().GetProjectionMatrix());
        m_pCameraController = new Eegeo::AR::ARCameraController(initialScreenProperties.GetScreenProperties().GetScreenWidth(), initialScreenProperties.GetScreenProperties().GetScreenHeight());
        m_pCameraController->GetCamera().SetProjectionMatrix(projectionMatrix);
        m_pARController = Eegeo_NEW(Eegeo::AR::ARVuforiaController)(initialScreenProperties.GetScreenProperties().GetScreenWidth(), initialScreenProperties.GetScreenProperties().GetScreenHeight());
        NotifyScreenPropertiesChanged(initialScreenProperties.GetScreenProperties());
    }
    
	ARVuforiaExample::~ARVuforiaExample()
	{
		delete m_pCameraController;
	    Eegeo_DELETE m_pARController;
    }
    
    void ARVuforiaExample::Start()
    {
        m_arTracker.InitVuforia();
        Eegeo::Space::LatLongAltitude eyePosLla = Eegeo::Space::LatLongAltitude::FromDegrees(40.763647, -73.973468, 150);
        m_pCameraController->SetStartLatLongAltitude(eyePosLla);
    }
    
    void ARVuforiaExample::Suspend()
    {
    	m_pARController->StopCamera();
    	m_arTracker.DeInitVuforia();
    }

    void ARVuforiaExample::Draw()
    {
        
        
        
    }

    void ARVuforiaExample::UpdateWorld(float dt, Eegeo::EegeoWorld& world, Eegeo::Camera::CameraState cameraState, Examples::ScreenPropertiesProvider& screenPropertyProvider, Eegeo::Streaming::IStreamingVolume& streamingVolume)
    {
        
        Eegeo::EegeoUpdateParameters updateParameters(dt,
                                                      cameraState.LocationEcef(),
                                                      cameraState.InterestPointEcef(),
                                                      cameraState.ViewMatrix(),
                                                      cameraState.ProjectionMatrix(),
                                                      streamingVolume,
                                                      screenPropertyProvider.GetScreenProperties());
        world.Update(updateParameters);
        

    }

    void ARVuforiaExample::DrawWorld(Eegeo::EegeoWorld& world,  Eegeo::Camera::CameraState cameraState, Examples::ScreenPropertiesProvider& screenPropertyProvider)
    {
        
        // Get the state from Vuforia and mark the beginning of a rendering section
        Vuforia::State state = Vuforia::Renderer::getInstance().begin();
        
        // Did we find any trackables this frame?
        for(int tIdx = 0; tIdx < state.getNumTrackableResults(); tIdx++)
        {
            // Get the trackable:
            const Vuforia::TrackableResult* result = state.getTrackableResult(tIdx);
            const Vuforia::Trackable& trackable = result->getTrackable();
            Vuforia::Matrix44F modelViewMatrix =
            Vuforia::Tool::convertPose2GLMatrix(result->getPose());
            
            // Choose the texture based on the target name:
            int textureIndex;
            if (strcmp(trackable.getName(), "chips") == 0)
            {
                EXAMPLE_LOG("Chips Detected");
            }
            else if (strcmp(trackable.getName(), "stones") == 0)
            {
                EXAMPLE_LOG("Stons Detected");
            }
            else
            {
                EXAMPLE_LOG("Default Detected");
            }
        }
        
        // Explicitly render the Video Background
        Vuforia::Renderer::getInstance().drawVideoBackground();
        
        Eegeo::EegeoDrawParameters drawParameters(cameraState.LocationEcef(),
                                                  cameraState.InterestPointEcef(),
                                                  cameraState.ViewMatrix(),
                                                  cameraState.ProjectionMatrix(),
                                                  screenPropertyProvider.GetScreenProperties());
        
        world.Draw(drawParameters);
        
        Vuforia::Renderer::getInstance().end();
    }
    
    void ARVuforiaExample::EarlyUpdate(float dt)
    {
        m_pCameraController->Update(dt);
        m_pCameraController->SetNearMultiplier(EXTERIOR_NEAR_MULTIPLIER);
    }
    
    void ARVuforiaExample::NotifyScreenPropertiesChanged(const Eegeo::Rendering::ScreenProperties& screenProperties)
    {
    	//m_pARController->NotifyScreenPropertiesChanged(screenProperties);
    }
    
    Eegeo::Camera::CameraState ARVuforiaExample::GetCurrentCameraState() const
    {
        return m_pCameraController->GetCameraState();
    }

    
    void ARVuforiaExample::SetVRCameraState(const float headTransform[])
    {
        
        Eegeo::m33 orientation;
        Eegeo::v3 right = Eegeo::v3(headTransform[0],headTransform[4],headTransform[8]);
        Eegeo::v3 up = Eegeo::v3(headTransform[1],headTransform[5],headTransform[9]);
        Eegeo::v3 forward = Eegeo::v3(-headTransform[2],-headTransform[6],-headTransform[10]);
        orientation.SetRow(0, right);
        orientation.SetRow(1, up);
        orientation.SetRow(2, forward);
        
        m_pCameraController->UpdateFromPose(orientation, 0.0f);
        
    }
    
    const Eegeo::m33& ARVuforiaExample::GetCurrentCameraOrientation()
    {
        return m_pCameraController->GetOrientation();
    }
    
    const Eegeo::m33& ARVuforiaExample::GetBaseOrientation()
    {
        return m_pCameraController->GetCameraOrientation();
    }
    
    const Eegeo::m33& ARVuforiaExample::GetHeadTrackerOrientation()
    {
        return m_pCameraController->GetHeadTrackerOrientation();
    }
    
    
	int ARVuforiaExample::InitTracker()
	{
		return m_pARController->InitTracker();
	}

	int ARVuforiaExample::LoadTrackerData()
	{
		return m_pARController->LoadTrackerData();
	}

	void ARVuforiaExample::OnVuforiaInitializedNative()
	{
		m_pARController->OnVuforiaInitializedNative();
	}

	void ARVuforiaExample::InitVuforiaRendering()
	{
		m_pARController->InitRendering();
	}

	void ARVuforiaExample::UpdateVuforiaRendering(int width, int height)
	{
		m_pARController->UpdateRendering(width, height);
	}
}
