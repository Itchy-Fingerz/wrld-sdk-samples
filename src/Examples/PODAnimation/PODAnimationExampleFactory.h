// Copyright eeGeo Ltd (2012-2014), All Rights Reserved

#ifndef __ExampleApp__PODAnimationExampleFactory__
#define __ExampleApp__PODAnimationExampleFactory__

#include "IExampleFactory.h"
#include "GlobeCameraExampleBase.h"
#include "EegeoWorld.h"

namespace Examples
{
class PODAnimationExampleFactory : public IExampleFactory
{
	Eegeo::EegeoWorld& m_world;
    DefaultCameraControllerFactory& m_defaultCameraControllerFactory;
    Eegeo::Camera::GlobeCamera::GlobeCameraTouchController& m_globeCameraTouchController;

public:
	PODAnimationExampleFactory(Eegeo::EegeoWorld& world,
	                           DefaultCameraControllerFactory& defaultCameraControllerFactory,
                               Eegeo::Camera::GlobeCamera::GlobeCameraTouchController& globeCameraTouchController);

	std::string ExampleName() const;

	IExample* CreateExample() const;
};
}

#endif /* defined(__ExampleApp__PODAnimationExampleFactory__) */
