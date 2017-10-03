package org.eclipse.cdt.cmake.core.internal;

import org.eclipse.cdt.debug.core.launch.CoreBuildLaunchConfigDelegate;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;

public class CMakeCoreBuildLocalNullLaunchDelegate extends CoreBuildLaunchConfigDelegate {

	@Override
	public void launch(ILaunchConfiguration configuration, String mode, ILaunch launch, IProgressMonitor monitor)
			throws CoreException {
		// Do Nothing
	}

}
