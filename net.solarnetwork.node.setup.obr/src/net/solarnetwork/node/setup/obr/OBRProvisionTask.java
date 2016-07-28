/* ==================================================================
 * OBRProvisionTask.java - Apr 24, 2014 8:09:12 PM
 * 
 * Copyright 2007-2014 SolarNetwork.net Dev Team
 * 
 * This program is free software; you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License as 
 * published by the Free Software Foundation; either version 2 of 
 * the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU 
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with this program; if not, write to the Free Software 
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 
 * 02111-1307 USA
 * ==================================================================
 */

package net.solarnetwork.node.setup.obr;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import org.apache.commons.codec.digest.DigestUtils;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;
import org.osgi.framework.wiring.FrameworkWiring;
import org.osgi.service.obr.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;
import net.solarnetwork.node.backup.Backup;
import net.solarnetwork.node.backup.BackupManager;
import net.solarnetwork.node.setup.BundlePlugin;
import net.solarnetwork.node.setup.Plugin;

/**
 * Task to install plugins.
 * 
 * @author matt
 * @version 1.0
 */
public class OBRProvisionTask implements Callable<OBRPluginProvisionStatus> {

	private static final Logger LOG = LoggerFactory.getLogger(OBRProvisionTask.class);

	private final BundleContext bundleContext;
	private final OBRPluginProvisionStatus status;
	private Future<OBRPluginProvisionStatus> future;
	private final File directory;
	private final BackupManager backupManager;

	/**
	 * Construct with a status.
	 * 
	 * @param bundleContext
	 *        the BundleContext to manipulate bundles with
	 * @param status
	 *        the status, which defines the plugins to install
	 * @param directory
	 *        the directory to download plugins to
	 * @param backupManager
	 *        if provided, then a backup will be performed before provisioning
	 *        any bundles
	 */
	public OBRProvisionTask(BundleContext bundleContext, OBRPluginProvisionStatus status, File directory,
			BackupManager backupManager) {
		super();
		this.bundleContext = bundleContext;
		this.status = status;
		this.directory = directory;
		this.backupManager = backupManager;
		this.status.setBackupComplete(backupManager == null);
	}

	@Override
	public OBRPluginProvisionStatus call() throws Exception {
		try {
			status.setStatusMessage("Starting provisioning operation.");
			handleBackupBeforeProvisioningOperation();
			if ( status.getPluginsToInstall() != null && status.getPluginsToInstall().size() > 0 ) {
				downloadPlugins(status.getPluginsToInstall());
			}
			if ( status.getPluginsToRemove() != null && status.getPluginsToRemove().size() > 0 ) {
				removePlugins(status.getPluginsToRemove());
			}
			status.setStatusMessage("Provisioning operation complete.");
			return status;
		} catch ( Exception e ) {
			LOG.warn("Error in provision task: {}", e.getMessage(), e);
			status.setStatusMessage("Error in provisioning operation: " + e.getMessage());
			throw e;
		}
	}

	private void handleBackupBeforeProvisioningOperation() {
		// if we are actually going to provision something, let's make a backup
		if ( backupManager != null && status.getOverallProgress() < 1 ) {
			status.setStatusMessage("Creating backup before provisioning operation.");
			LOG.info("Creating backup before provisioning operation.");
			try {
				Backup backup = backupManager.createBackup();
				if ( backup != null ) {
					LOG.info("Created backup {} (size {})", backup.getKey(), backup.getSize());
					status.setStatusMessage("Backup complete.");
					status.setBackupComplete(Boolean.TRUE);
				}
			} catch ( RuntimeException e ) {
				status.setBackupComplete(Boolean.FALSE);
				LOG.warn("Error creating backup for provisioning operation {}", status.getProvisionID(),
						e);
			}
		}
	}

	/**
	 * Find all installed bundles for a specific ID that are less than or equal
	 * to a specific version.
	 * 
	 * @param symbolicName
	 *        The bundle ID to look for.
	 * @param maxVersion
	 *        The maximum version to include in the result.
	 * @return All found bundles whose symbolic name matches and has a version
	 *         less than {@code maxVersion}, in largest to smallest order, or
	 *         <em>null</em> if none found.
	 */
	private List<Bundle> findBundlesOlderThanVersion(String symbolicName, Version maxVersion) {
		List<Bundle> olderBundles = null;
		Bundle[] bundles = bundleContext.getBundles();
		for ( Bundle b : bundles ) {
			if ( b.getSymbolicName().equals(symbolicName) && b.getVersion().compareTo(maxVersion) < 1 ) {
				if ( olderBundles == null ) {
					olderBundles = new ArrayList<Bundle>(2);
				}
				olderBundles.add(b);
			}
		}
		if ( olderBundles != null ) {
			olderBundles.sort(new Comparator<Bundle>() {

				@Override
				public int compare(Bundle o1, Bundle o2) {
					return o2.getVersion().compareTo(o1.getVersion());
				}
			});
		}
		return olderBundles;
	}

	private void downloadPlugins(List<Plugin> plugins) throws InterruptedException {
		assert plugins != null;
		LOG.debug("Starting install of {} plugins", plugins.size());
		if ( !directory.exists() && !directory.mkdirs() ) {
			throw new RuntimeException("Unable to create plugin directory: " + directory.toString());
		}

		// This method will manually download the bundle for each resolved plugin, 
		// then install it and start it in the running OSGi platform. We don't
		// make use of the OBR RepositoryAdmin to do this because on SolarNode
		// the bundle's runtime area is held only in RAM (not persisted to disk)
		// but we want these downloaded bundles to be persisted to disk. Thus we
		// just do a bit of work here to download and start the bundles ourselves.

		boolean refreshNeeded = false;
		List<Bundle> installedBundles = new ArrayList<Bundle>(plugins.size());

		// iterate backwards, to work our way up through deps to requested plugin
		for ( ListIterator<Plugin> itr = plugins.listIterator(plugins.size()); itr.hasPrevious(); ) {
			Plugin plugin = itr.previous();
			assert plugin instanceof OBRResourcePlugin;
			LOG.debug("Starting install of plugin: {}", plugin.getUID());
			status.setStatusMessage("Starting install of plugin " + plugin.getUID());

			OBRResourcePlugin obrPlugin = (OBRResourcePlugin) plugin;
			Resource resource = obrPlugin.getResource();
			URL resourceURL = resource.getURL();
			String pluginFileName = StringUtils.getFilename(resourceURL.getPath());
			File outputFile = new File(directory, pluginFileName);
			String bundleSymbolicName = resource.getSymbolicName();

			// download to tmp file first, then we'll rename
			File tmpOutputFile = new File(directory, "." + pluginFileName);
			LOG.debug("Downloading plugin {} => {}", resourceURL, tmpOutputFile);
			try {
				FileCopyUtils.copy(resourceURL.openStream(), new FileOutputStream(tmpOutputFile));
			} catch ( IOException e ) {
				throw new RuntimeException("Unable to download plugin " + bundleSymbolicName, e);
			}

			moveTemporaryDownloadedPluginFile(resource, outputFile, tmpOutputFile);

			if ( installDownloadedPlugin(resource, outputFile, installedBundles) && !refreshNeeded ) {
				refreshNeeded = true;
			}

			LOG.debug("Installed plugin: {}", plugin.getUID());
			status.markPluginInstalled(plugin);
		}
		for ( ListIterator<Bundle> itr = installedBundles.listIterator(); itr.hasNext(); ) {
			Bundle b = itr.next();
			status.setStatusMessage("Starting plugin: " + b.getSymbolicName());
			try {
				if ( !(b.getState() == Bundle.ACTIVE || b.getState() == Bundle.STARTING) ) {
					b.start();
				}
				// bundles are in reverse order of plugins
				Plugin p = plugins.get(plugins.size() - itr.nextIndex());
				status.markPluginStarted(p);
			} catch ( BundleException e ) {
				throw new RuntimeException(
						"Unable to start plugin " + b.getSymbolicName() + " version " + b.getVersion(),
						e);
			}
		}
		if ( refreshNeeded ) {
			status.setStatusMessage("Refreshing OSGi framework.");
			FrameworkWiring fw = bundleContext.getBundle(0).adapt(FrameworkWiring.class);
			fw.refreshBundles(null);
		}
		LOG.debug("Install of {} plugins complete", plugins.size());
		status.setStatusMessage("Install of " + plugins.size() + " plugins complete");
	}

	private void moveTemporaryDownloadedPluginFile(Resource resource, File outputFile,
			File tmpOutputFile) {
		if ( outputFile.exists() ) {
			// if the file has not changed, just delete tmp file
			InputStream outputFileInputStream = null;
			InputStream tmpOutputFileInputStream = null;
			try {
				outputFileInputStream = new FileInputStream(outputFile);
				tmpOutputFileInputStream = new FileInputStream(tmpOutputFile);
				String outputFileHash = DigestUtils.shaHex(outputFileInputStream);
				String tmpOutputFileHash = DigestUtils.shaHex(tmpOutputFileInputStream);
				if ( tmpOutputFileHash.equals(outputFileHash) ) {
					// file unchanged, so just delete tmp file
					tmpOutputFile.delete();
				} else {
					LOG.debug("Bundle {} version {} content updated", resource.getSymbolicName(),
							resource.getVersion());
					outputFile.delete();
					tmpOutputFile.renameTo(outputFile);
				}
			} catch ( IOException e ) {
				throw new RuntimeException("Error downloading plugin " + resource.getSymbolicName(), e);
			} finally {
				if ( outputFileInputStream != null ) {
					try {
						outputFileInputStream.close();
					} catch ( IOException e ) {
						// ignore;
					}
				}
				if ( tmpOutputFileInputStream != null ) {
					try {
						tmpOutputFileInputStream.close();
					} catch ( IOException e ) {
						// ignore
					}
				}
			}
		} else {
			// rename tmp file
			tmpOutputFile.renameTo(outputFile);
		}
	}

	private boolean installDownloadedPlugin(Resource resource, File outputFile,
			List<Bundle> installedBundles) {
		final String bundleSymbolicName = resource.getSymbolicName();
		boolean refreshNeeded = false;
		try {
			URL newBundleURL = outputFile.toURI().toURL();
			List<Bundle> oldBundles = findBundlesOlderThanVersion(bundleSymbolicName,
					resource.getVersion());
			Bundle oldBundle = (oldBundles != null && oldBundles.size() > 0 ? oldBundles.get(0) : null);
			Version oldVersion = (oldBundle != null ? oldBundle.getVersion() : null);
			if ( oldVersion != null && oldVersion.compareTo(resource.getVersion()) >= 0 ) {
				LOG.debug("Skipping install of plugin {} as version is unchanged at {}",
						bundleSymbolicName, oldVersion);
			} else if ( oldVersion != null ) {
				LOG.debug("Upgrading plugin {} from {} to {}", bundleSymbolicName, oldVersion,
						resource.getVersion());
				InputStream in = null;
				try {
					in = new BufferedInputStream(new FileInputStream(outputFile));
					oldBundle.update(in);

					// try to delete the old version
					File oldJar = new File(directory, bundleSymbolicName + "-" + oldVersion + ".jar");
					if ( !oldJar.delete() ) {
						LOG.warn("Error deleting old plugin " + oldJar.getName());
					}

					installedBundles.add(oldBundle);
					LOG.info("Upgraded plugin {} from version {} to {}", bundleSymbolicName, oldVersion,
							resource.getVersion());
					refreshNeeded = true;
				} catch ( BundleException e ) {
					throw new RuntimeException("Unable to upgrade plugin " + bundleSymbolicName, e);
				} catch ( FileNotFoundException e ) {
					throw new RuntimeException("Unable to upgrade plugin " + bundleSymbolicName, e);
				} finally {
					if ( in != null ) {
						try {
							in.close();
						} catch ( IOException e ) {
							// ignore
						}
					}
				}
			} else {
				LOG.debug("Installing plugin {} version {}", newBundleURL, resource.getVersion());
				Bundle newBundle = bundleContext.installBundle(newBundleURL.toString());
				LOG.info("Installed plugin {} version {}", newBundle.getSymbolicName(),
						newBundle.getVersion());
				installedBundles.add(newBundle);
			}
		} catch ( BundleException e ) {
			throw new RuntimeException("Unable to install plugin " + bundleSymbolicName, e);
		} catch ( MalformedURLException e ) {
			throw new RuntimeException("Unable to install plugin " + bundleSymbolicName, e);
		}
		return refreshNeeded;
	}

	private void removePlugins(List<Plugin> plugins) {
		assert plugins != null;
		LOG.debug("Starting removal of {} plugins", plugins.size());

		boolean refreshNeeded = false;
		for ( Plugin plugin : plugins ) {
			assert plugin instanceof BundlePlugin;
			LOG.debug("Starting removal of plugin: {}", plugin.getUID());
			status.setStatusMessage("Starting removal of plugin " + plugin.getUID());
			BundlePlugin bundlePlugin = (BundlePlugin) plugin;
			Bundle oldBundle = bundlePlugin.getBundle();
			if ( oldBundle != null ) {
				Version oldVersion = oldBundle.getVersion();
				LOG.debug("Removing plugin {} version {}", oldBundle.getSymbolicName(), oldVersion);
				try {
					oldBundle.uninstall();
					refreshNeeded = true;
				} catch ( BundleException e ) {
					throw new RuntimeException(
							"Unable to uninstall plugin " + oldBundle.getSymbolicName(), e);
				}
				File oldJar = new File(directory,
						oldBundle.getSymbolicName() + "-" + oldVersion + ".jar");
				if ( !oldJar.delete() ) {
					LOG.warn("Error deleting plugin JAR " + oldJar.getName());
				}
			}

			LOG.debug("Removed plugin: {}", plugin.getUID());
			status.setStatusMessage("Removed plugin " + plugin.getUID());
			status.markPluginRemoved(plugin);
		}
		if ( refreshNeeded ) {
			status.setStatusMessage("Refreshing OSGi framework.");
			FrameworkWiring fw = bundleContext.getBundle(0).adapt(FrameworkWiring.class);
			fw.refreshBundles(null);
		}
		LOG.debug("Removal of {} plugins complete", plugins.size());
	}

	public OBRPluginProvisionStatus getStatus() {
		return status;
	}

	Future<OBRPluginProvisionStatus> getFuture() {
		return future;
	}

	void setFuture(Future<OBRPluginProvisionStatus> future) {
		this.future = future;
	}

	public File getDirectory() {
		return directory;
	}

}
