package org.eclipse.cdt.cmake.core.internal;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.eclipse.cdt.core.model.ICProject;
import org.eclipse.cdt.debug.core.CDebugUtils;
import org.eclipse.cdt.debug.core.ICDTLaunchConfigurationConstants;
import org.eclipse.cdt.debug.core.launch.CoreBuildLaunchConfigDelegate;
import org.eclipse.cdt.utils.pty.PTY;
import org.eclipse.cdt.utils.spawner.ProcessFactory;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.osgi.util.NLS;

public abstract class AbstractCMakeCoreBuildLaunchConfigDelegate extends CoreBuildLaunchConfigDelegate {

	/**
	 * Expands and returns the working directory attribute of the given launch
	 * configuration. Returns <code>null</code> if a working directory is not
	 * specified.
	 * 
	 * @param configuration launch configuration
	 * @return an absolute path to a directory, or <code>null</code> if unspecified
	 * @throws CoreException if unable to retrieve the associated launch
	 * configuration attribute or if unable to resolve any variables
	 * 
	 * @since 7.3
	 */
	protected IPath getWorkingDirectoryPath(ILaunchConfiguration config) throws CoreException {
		String location = config.getAttribute(ICDTLaunchConfigurationConstants.ATTR_WORKING_DIRECTORY, (String)null);
		if (location != null) {
			String expandedLocation = VariablesPlugin.getDefault().getStringVariableManager().performStringSubstitution(location);;
			if (!expandedLocation.isEmpty()) {
				return new Path(expandedLocation);
			}
		}
		return null;
	}

	/**
	 * Verifies the working directory specified by the given launch
	 * configuration exists, and returns that working directory, or
	 * <code>null</code> if none is specified.
	 * 
	 * @param configuration
	 *            launch configuration
	 * @return the working directory specified by the given launch
	 *         configuration, or <code>null</code> if none
	 * @exception CoreException
	 *                if unable to retrieve the attribute
	 * @since 7.3
	 */
	protected File verifyWorkingDirectory(ILaunchConfiguration configuration) throws CoreException {
		IPath path = getWorkingDirectoryPath(configuration);
		if (path == null) {
			// default working dir is the project if this config has a project
			ICProject cp = CDebugUtils.getCProject(configuration);
			if (cp == null) {
				return null;
			}
	
			IProject p = cp.getProject();
			return p.getLocation().toFile();
		}
		
		if (path.isAbsolute()) {
			File dir = new File(path.toOSString());
			if (dir.isDirectory()) {
				return dir;
			}
		} else {
			IResource res = ResourcesPlugin.getWorkspace().getRoot().findMember(path);
			if (res instanceof IContainer && res.exists()) {
				return res.getLocation().toFile();
			}
		}
	
		abort("Working directory does not exist",	new FileNotFoundException(
						NLS.bind("The working directory {0} does not exist.",
								 path.toOSString())),
								 ICDTLaunchConfigurationConstants.ERR_WORKING_DIRECTORY_DOES_NOT_EXIST);
		return null;
	}

	/**
	 * Throws a core exception with an error status object built from the given
	 * message, lower level exception, and error code.
	 * 
	 * @param message
	 *            the status message
	 * @param exception
	 *            lower level exception associated with the error, or
	 *            <code>null</code> if none
	 * @param code
	 *            error code
	 */
	protected void abort(String message, Throwable exception, int code) throws CoreException {
		IStatus status;
		if (exception != null) {
			MultiStatus multiStatus = new MultiStatus("", code, message, exception);
			multiStatus.add(new Status(IStatus.ERROR, "", code, exception.getLocalizedMessage(), exception));
			status= multiStatus;
		} else {
			status= new Status(IStatus.ERROR, "", code, message, null);
		}
		throw new CoreException(status);
	}

	/**
	 * Performs a runtime exec on the given command line in the context of the
	 * specified working directory, and returns the resulting process.
	 * 
	 * @param cmdLine
	 *            the command line
	 * @param environ
	 * @param workingDirectory
	 *            the working directory, or <code>null</code>
	 * @return the resulting process or <code>null</code> if the exec is
	 *         cancelled
	 * @see Runtime
	 * @since 4.7
	 */
	protected Process exec(String[] cmdLine, String[] environ, File workingDirectory) throws CoreException {
		try {
			if (PTY.isSupported()) {
				return ProcessFactory.getFactory().exec(cmdLine, environ, workingDirectory, new PTY());
			} else {
				return ProcessFactory.getFactory().exec(cmdLine, environ, workingDirectory);
			}
		} catch (IOException e) {
			abort("Error starting process.", e, ICDTLaunchConfigurationConstants.ERR_INTERNAL_ERROR);
		}
		return null;
	}
}
