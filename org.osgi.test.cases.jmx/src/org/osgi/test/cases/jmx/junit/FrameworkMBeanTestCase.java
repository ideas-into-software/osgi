package org.osgi.test.cases.jmx.junit;

import java.io.IOException;
import java.net.URL;

import javax.management.openmbean.CompositeData;

import org.osgi.jmx.framework.BundleStateMBean;
import org.osgi.jmx.framework.FrameworkMBean;

public class FrameworkMBeanTestCase extends MBeanGeneralTestCase {

	private FrameworkMBean frameworkMBean;
	private BundleStateMBean bundleStateMBean;
	
	@Override
	public void setUp() throws Exception {
		super.setUp();
		super.waitForRegistering(createObjectName(FrameworkMBean.OBJECTNAME));		
		frameworkMBean = getMBeanFromServer(FrameworkMBean.OBJECTNAME,
											FrameworkMBean.class);
		super.waitForRegistering(createObjectName(BundleStateMBean.OBJECTNAME));		
		bundleStateMBean = getMBeanFromServer(BundleStateMBean.OBJECTNAME,
											BundleStateMBean.class);
		
	}

	public void testFrameworkMBeanExists() {
		assertNotNull(frameworkMBean);
	}
	
	/* Test scenario: Install bundle. Start bundle. Set bundle start level bigger than the framework one. Check that bundle is stopped.
	 * Change framework start level to be bigger than the bundle one. Check that bundle is started. Change framework start level to be 
	 * smaller than the bundle one. Check that bundle is stopped. Un-install bundle.
	 */
	public void testFrameworkStartLevel() {
		long testBundle = -1;
		try {
			//install bundle tb2
			URL entry = getContext().getBundle().getEntry("tb2.jar");
			testBundle = frameworkMBean.installBundleFromURL("tb2.jar", entry.toString());
			
			//get framework start level
			int frameworkStartLevel = frameworkMBean.getFrameworkStartLevel();
			
			//start bundle; assure bundle is started
			frameworkMBean.startBundle(testBundle);
			assertTrue("bundle tb2 could not be started for " +  waitTime + " seconds ", waitBundleStateChange(testBundle, "ACTIVE"));
			
			//set bundle start level bigger than the framework one; bundle should be stopped automatically 
			frameworkMBean.setBundleStartLevel(testBundle, frameworkStartLevel + 2);
			assertTrue("bundle tb2 is not stopped for " +  waitTime + " seconds after setting its bundle start level bigger than framework start level", waitBundleStateChange(testBundle, "RESOLVED"));

			//set framework start level bigger than the bundle start level; bundle should be started
			int bundleStartLevel = bundleStateMBean.getStartLevel(testBundle);
			frameworkMBean.setFrameworkStartLevel(bundleStartLevel + 2);
			assertTrue("bundle tb2 is not started for " +  waitTime + " seconds after setting framework start level bigger than its start level", waitBundleStateChange(testBundle, "ACTIVE"));
			
			//set framework start level less than the bundle start level; bundle should be stopped			
			frameworkMBean.setFrameworkStartLevel(bundleStartLevel - 2);
			assertTrue("bundle tb2 is not stopped for " +  waitTime + " seconds after setting framework start level smaller than its start level", waitBundleStateChange(testBundle, "RESOLVED"));
		} catch(Exception io) {
			io.printStackTrace();
			assertTrue("Excxeption ocurs: " + io.toString(), false);
		} finally {
			if (testBundle >= 0) {
				try {
					frameworkMBean.uninstallBundle(testBundle);
				} catch (IOException io) {}
			}		
		}
	}

	/* 
	 * Test scenario: Install one bundle. Check that bundle initial start level is equal to the one returned from framework mBean.
	 * Change initial bundle start level. Install second bundle. Check that second installed bundle initial start level is the new one.
	 * Check that the initial bundle start level of the first bundle is not changed. Start bundles. Set bundles initial start levels bigger
	 * than the framework one. Check that bundles are stopped. Change the bundles initial start level to be smaller than the framework one.
	 * Check that bundles are started. Un-install bundles. 
	 */
	public void testBundleStartLevel() {
		long testBundle1 = -1;	
		long testBundle2 = -1;
		try {
			//install bundle tb2
			URL entry = getContext().getBundle().getEntry("tb2.jar");
			testBundle2 = frameworkMBean.installBundleFromURL("tb2.jar", entry.toString());
						
			//check bundle start level is equal to initial one
			int initialBundleStartLevel = frameworkMBean.getInitialBundleStartLevel();
			int testBundle1StartLeve2 = bundleStateMBean.getStartLevel(testBundle2); 
			assertTrue("bundle start level is different from the initial bundle start level returned from the framework MBean", initialBundleStartLevel == testBundle1StartLeve2);

			//change initial bundle start level
			frameworkMBean.setInitialBundleStartLevel(initialBundleStartLevel + 1);
									  			
			//install bundle tb1
			entry = getContext().getBundle().getEntry("tb1.jar");
			testBundle1 = frameworkMBean.installBundleFromURL("tb1.jar", entry.toString());
						
			//check bundle start level is equal to changed one
			initialBundleStartLevel = frameworkMBean.getInitialBundleStartLevel();
			int testBundle1StartLeve1 = bundleStateMBean.getStartLevel(testBundle1);
			assertTrue("bundle start level is different from the initial bundle start level returned from the framework MBean", initialBundleStartLevel == testBundle1StartLeve1);
			
			//check already installed bundles start level is not changed 
			assertTrue("installed bundle start level is changed after changing inital bundle start level via the framework MBean", testBundle1StartLeve2 == bundleStateMBean.getStartLevel(testBundle2));						
			
			//set framework start level bigger than installed bundles' one
			frameworkMBean.setFrameworkStartLevel(testBundle1StartLeve1 + 2);
			
			//start bundles; assure bundle are started
			frameworkMBean.startBundle(testBundle2);
			frameworkMBean.startBundle(testBundle1);
			assertTrue("bundle tb2 could not be started for " +  waitTime + " seconds", waitBundleStateChange(testBundle2, "ACTIVE"));
			assertTrue("bundle tb1 could not be started for " +  waitTime + " seconds", waitBundleStateChange(testBundle1, "ACTIVE"));
						
			//set bundles start levels bigger than the framework one; bundles should be stopped automatically 
			CompositeData result = frameworkMBean.setBundleStartLevels(new long[] {testBundle1, testBundle2}, new int[] {testBundle1StartLeve1 + 4, testBundle1StartLeve1 + 4});
			assertCompositeDataKeys(result, "BATCH_ACTION_RESULT_TYPE", new String[] { "BundleInError", "Completed", "Error", "Remaining", "Success" });
			assertTrue("setting of bundles start levels doesn't succeed", ((Boolean) result.get("Success")).booleanValue());
			assertTrue("bundle tb1 is not stopped for " +  waitTime + " seconds after setting its bundle start level bigger than framework start level", waitBundleStateChange(testBundle1, "RESOLVED"));
			assertTrue("bundle tb2 is not stopped for " +  waitTime + " seconds after setting its bundle start level bigger than framework start level", waitBundleStateChange(testBundle2, "RESOLVED"));

			//set bundles start levels smaller than the framework one; bundles should be started automatically 
			result = frameworkMBean.setBundleStartLevels(new long[] {testBundle2, testBundle1}, new int[] {testBundle1StartLeve1, testBundle1StartLeve1});
			assertCompositeDataKeys(result, "BATCH_ACTION_RESULT_TYPE", new String[] { "BundleInError", "Completed", "Error", "Remaining", "Success" });
			assertTrue("setting of bundles start levels doesn't succeed", ((Boolean) result.get("Success")).booleanValue());			
			assertTrue("bundle tb2 is not started for " +  waitTime + " seconds after setting its bundle start level smaller than framework start level", waitBundleStateChange(testBundle2, "ACTIVE"));
			assertTrue("bundle tb1 is not started for " +  waitTime + " seconds after setting its bundle start level smaller than framework start level", waitBundleStateChange(testBundle1, "ACTIVE"));

			//set bundle start level bigger than the framework one; bundle should be stopped automatically 
			frameworkMBean.setBundleStartLevel(testBundle1, testBundle1StartLeve1 + 4);
			assertTrue("bundle tb1 is not stopped for " +  waitTime + " seconds after setting its bundle start level bigger than framework start level", waitBundleStateChange(testBundle1, "RESOLVED"));

			//set bundles start level smaller than the framework one; bundle should be started automatically 
			frameworkMBean.setBundleStartLevel(testBundle1, testBundle1StartLeve1);
			assertTrue("bundle tb1 is not started for " +  waitTime + " seconds after setting its bundle start level smaller than framework start level", waitBundleStateChange(testBundle1, "ACTIVE"));
		} catch(Exception io) {
			io.printStackTrace();
			assertTrue("Exception ocurred: " + io.toString(), false);
		} finally {
			if (testBundle1 >= 0) {
				try {
					frameworkMBean.uninstallBundle(testBundle1);
				} catch (IOException io) {}
			}
			if (testBundle2 >= 0) {
				try {
					frameworkMBean.uninstallBundle(testBundle2);
				} catch (IOException io) {}
			}
		}
	}
	
	/* Test scenario: Install bundle from URL and check it is installed and the returned bundle id is useful for operations (check done via starting it). Un-install the bundle.   
	/* Install two bundles from URL and check they are installed and the returned bundle ids are useful	(check done via starting them). Un-install bundles. */
	public void testBundleInstallFromURL() {
		long testBundle = -1;
		try {
			//install single bundle
			URL entry2 = getContext().getBundle().getEntry("tb2.jar");
			testBundle = frameworkMBean.installBundleFromURL("tb2.jar", entry2.toString());
			//check bundle id is the right one
			frameworkMBean.startBundle(testBundle);
			assertTrue("bundle tb2 is not started for " +  waitTime + " seconds", waitBundleStateChange(testBundle, "ACTIVE"));
		} catch(Exception io) {
			io.printStackTrace();
			assertTrue("Exception ocurred: " + io.toString(), false);
		} finally {
			if (testBundle >= 0) {
				try {
					frameworkMBean.uninstallBundle(testBundle);
				} catch (IOException io) {
					assertTrue("Exception ocurred: " + io.toString(), false);				
				}
			}
		}
		
		Long[] bundleIds = null;
		try {			
			//install several bundles
			URL entry1 = getContext().getBundle().getEntry("tb1.jar");
			URL entry2 = getContext().getBundle().getEntry("tb2.jar");			
			CompositeData result = frameworkMBean.installBundlesFromURL(new String[] {"tb2.jar", "tb1.jar"}, new String[] {entry2.toString(), entry1.toString()});
			assertCompositeDataKeys(result, "BATCH_ACTION_RESULT_TYPE", new String[] { "BundleInError", "Completed", "Error", "Remaining", "Success" });
			assertTrue("installing bundles from URL doesn't succeed", ((Boolean) result.get("Success")).booleanValue());
			bundleIds = (Long[]) result.get("Completed");
			assertTrue("installing bundles from URL doesn't return right bundle ids info", (bundleIds != null) && (bundleIds.length == 2));
			frameworkMBean.startBundle(bundleIds[0].longValue());
			frameworkMBean.startBundle(bundleIds[1].longValue());
			assertTrue("bundle tb2 is not started for " +  waitTime + " seconds", waitBundleStateChange(bundleIds[0], "ACTIVE"));
			assertTrue("bundle tb1 is not started for " +  waitTime + " seconds", waitBundleStateChange(bundleIds[1], "ACTIVE"));
			
			//if mentioned as bundleIds[0] and than bundleIds[1] -> there is a problem with bundleIds[0] to be un-installed
			result = frameworkMBean.uninstallBundles(new long[] { bundleIds[1].longValue(), bundleIds[0].longValue() });			
			assertCompositeDataKeys(result, "BATCH_ACTION_RESULT_TYPE", new String[] { "BundleInError", "Completed", "Error", "Remaining", "Success" });
			assertTrue("un-installing bundles from URL doesn't succeed", ((Boolean) result.get("Success")).booleanValue());
		} catch(Exception io) {
			io.printStackTrace();
			assertTrue("Exception ocurred: " + io.toString(), false);
		} 
	}

	/* Test scenario: Install bundle and get it last modified time. Update the bundle. Check that last modified time is bigger. Un-install the bundle.
	/* Install bundles and get their last modified time. Update bundles. Check that new last modified time is bigger for each of them. Un-install bundles.
	*/
	public void testBundleUpdateFromURL() {
		long testBundle = -1;
		try {
			//install single bundle
			URL entry2 = getContext().getBundle().getEntry("tb2.jar");
			testBundle = frameworkMBean.installBundleFromURL("tb2.jar", entry2.toString());
			//get last modification time
			long lastModifiedTime = bundleStateMBean.getLastModified(testBundle);
			//wait some time
			Thread.currentThread().sleep(10);
			//update bundle
			frameworkMBean.updateBundleFromURL(testBundle, entry2.toString());
			//get new last modification time
			long newLastModifiedTime = bundleStateMBean.getLastModified(testBundle);
			//check that newest last modification time is bigger than the previous one
			assertTrue("after update bundle from url the bundle's last modified time is not changed", newLastModifiedTime > lastModifiedTime);
		} catch(Exception io) {
			io.printStackTrace();
			assertTrue("Exception ocurred: " + io.toString(), false);
		} finally {
			if (testBundle >= 0) {
				try {
					frameworkMBean.uninstallBundle(testBundle);
				} catch (IOException io) {
					assertTrue("Exception ocurred: " + io.toString(), false);				
				}
			}
		}
		
		long testBundle1 = -1;
		long testBundle2 = -1;		
		try {
			//install two bundles
			URL entry2 = getContext().getBundle().getEntry("tb2.jar");
			testBundle2 = frameworkMBean.installBundleFromURL("tb2.jar", entry2.toString());
			URL entry1 = getContext().getBundle().getEntry("tb1.jar");
			testBundle1 = frameworkMBean.installBundleFromURL("tb1.jar", entry1.toString());
			//get last modification time
			long lastModifiedTime1 = bundleStateMBean.getLastModified(testBundle1);
			long lastModifiedTime2 = bundleStateMBean.getLastModified(testBundle2);			
			//wait some time
			Thread.currentThread().sleep(10);
			//update bundle
			CompositeData result = frameworkMBean.updateBundlesFromURL(new long[] { testBundle1, testBundle2 }, new String[] {entry1.toString(), entry2.toString() });
			assertCompositeDataKeys(result, "BATCH_ACTION_RESULT_TYPE", new String[] { "BundleInError", "Completed", "Error", "Remaining", "Success" });
			assertTrue("update of bundles from url doesn't succeed", ((Boolean) result.get("Success")).booleanValue());			
			//get new last modification times
			long newLastModifiedTime1 = bundleStateMBean.getLastModified(testBundle1);
			long newLastModifiedTime2 = bundleStateMBean.getLastModified(testBundle2);
			//check that newest last modification time is bigger than the previous one
			assertTrue("after update bundles from url the bundles' last modified time is not changed", (newLastModifiedTime1 > lastModifiedTime1) && (newLastModifiedTime2 > lastModifiedTime2));
		} catch(Exception io) {
			io.printStackTrace();
			assertTrue("Exception ocurred: " + io.toString(), false);
		} finally {
			if (testBundle1 >= 0) {
				try {
					frameworkMBean.uninstallBundle(testBundle1);
				} catch (IOException io) {
					io.printStackTrace();				
				}
			}
			if (testBundle2 >= 0) {
				try {
					frameworkMBean.uninstallBundle(testBundle2);
				} catch (IOException io) {
					io.printStackTrace();				
				}
			}
		}		
	}

	/* Test scenario: Install bundle and resolve it. Check their state is RESOLVED. Un-install the bundle. 
	 * Install two bundles and resolve them. Check their state is RESOLVED. Check operation is successful. Un-install bundles.
	*/
	public void testBundleResolve() {
		long testBundle = -1;
		try {
			//install single bundle
			URL entry2 = getContext().getBundle().getEntry("tb2.jar");
			testBundle = frameworkMBean.installBundleFromURL("tb2.jar", entry2.toString());
			//resolve bundle2
			assertTrue("bundle tb2 could not be resolved", frameworkMBean.resolveBundle(testBundle));
		} catch(Exception io) {
			io.printStackTrace();
			assertTrue("Exception ocurred: " + io.toString(), false);
		} finally {
			if (testBundle >= 0) {
				try {
					frameworkMBean.uninstallBundle(testBundle);
				} catch (IOException io) {
					assertTrue("Exception ocurred: " + io.toString(), false);				
				}
			}
		}
	
		long testBundle1 = -1;
		long testBundle2 = -1;		
		try {
			//install bundle2
			URL entry2 = getContext().getBundle().getEntry("tb2.jar");
			testBundle2 = frameworkMBean.installBundleFromURL("tb2.jar", entry2.toString());

			//install bundle1
			URL entry1 = getContext().getBundle().getEntry("tb1.jar");
			testBundle1 = frameworkMBean.installBundleFromURL("tb1.jar", entry1.toString());

			//resolve bundles
			assertTrue("bundle tb1 and tb2 could not be resolved", frameworkMBean.resolveBundles(new long[] {testBundle1, testBundle2}));
		} catch(Exception io) {
			io.printStackTrace();
			assertTrue("Exception ocurred: " + io.toString(), false);
		} finally {
			if (testBundle1 >= 0) {
				try {
					frameworkMBean.uninstallBundle(testBundle1);
				} catch (IOException io) {}
			}
			if (testBundle2 >= 0) {
				try {
					frameworkMBean.uninstallBundle(testBundle2);
				} catch (IOException io) {}
			}
		}				
	}

	/* Test scenario: Install bundles tb1 and tb2. Start  each of them. Stop both. Start both. Stop each of them.
	 * After execution of each operation check their state was changed correctly.  
	*/
	public void testBundleStartStop() {
		long testBundle1 = -1;
		long testBundle2 = -1;		
		try {
			//install bundle2
			URL entry2 = getContext().getBundle().getEntry("tb2.jar");
			testBundle2 = frameworkMBean.installBundleFromURL("tb2.jar", entry2.toString());

			//install bundle1
			URL entry1 = getContext().getBundle().getEntry("tb1.jar");
			testBundle1 = frameworkMBean.installBundleFromURL("tb1.jar", entry1.toString());
			
			frameworkMBean.startBundle(testBundle2);
			assertTrue("bundle tb2 is not started for " +  waitTime + " seconds", waitBundleStateChange(testBundle2, "ACTIVE"));
			
			frameworkMBean.startBundle(testBundle1);
			assertTrue("bundle tb1 is not started for " +  waitTime + " seconds", waitBundleStateChange(testBundle1, "ACTIVE"));
			
			CompositeData result = frameworkMBean.stopBundles(new long[] {testBundle2, testBundle1});
			assertCompositeDataKeys(result, "BATCH_ACTION_RESULT_TYPE", new String[] { "BundleInError", "Completed", "Error", "Remaining", "Success" });
			assertTrue("stop of bundles doesn't succeed", ((Boolean) result.get("Success")).booleanValue());
			assertTrue("bundle tb2 is not stopped for " +  waitTime + " seconds", waitBundleStateChange(testBundle2, "RESOLVED"));			
			assertTrue("bundle tb1 is not stopped for " +  waitTime + " seconds", waitBundleStateChange(testBundle1, "RESOLVED"));
									
			result = frameworkMBean.startBundles(new long[] {testBundle2, testBundle1});
			assertCompositeDataKeys(result, "BATCH_ACTION_RESULT_TYPE", new String[] { "BundleInError", "Completed", "Error", "Remaining", "Success" });
			assertTrue("start of bundles doesn't succeed", ((Boolean) result.get("Success")).booleanValue());
			assertTrue("bundle tb2 is not started for " +  waitTime + " seconds", waitBundleStateChange(testBundle2, "ACTIVE"));			
			assertTrue("bundle tb1 is not started for " +  waitTime + " seconds", waitBundleStateChange(testBundle1, "ACTIVE"));

			frameworkMBean.stopBundle(testBundle2);
			assertTrue("bundle tb2 is not stopped for " +  waitTime + " seconds", waitBundleStateChange(testBundle2, "RESOLVED"));
			
			frameworkMBean.stopBundle(testBundle1);						
			assertTrue("bundle tb1 is not stopped for " +  waitTime + " seconds", waitBundleStateChange(testBundle1, "RESOLVED"));
		} catch(Exception io) {
			io.printStackTrace();
			assertTrue("Exception ocurred: " + io.toString(), false);
		} finally {
			if (testBundle1 >= 0) {
				try {
					frameworkMBean.uninstallBundle(testBundle1);
				} catch (IOException io) {}
			}
			if (testBundle2 >= 0) {
				try {
					frameworkMBean.uninstallBundle(testBundle2);
				} catch (IOException io) {}
			}
		}						
	}
	
	/* Test scenario: Install 2 bundles; first one export second one import packages; Un-install the first one, than install it.
	*  Check that the second one is in INSTALLED state. Call refresh bundle. Check that second one moved to RESOLVED state.
	*  Repeat the same test using the refresh bundles method. 
	*/
	public void testBundleRefresh() {
		long testBundle1 = -1;
		long testBundle2 = -1;		
		try {
			//install bundle2
			URL entry2 = getContext().getBundle().getEntry("tb2.jar");
			testBundle2 = frameworkMBean.installBundleFromURL("tb2.jar", entry2.toString());

			//install bundle1
			URL entry1 = getContext().getBundle().getEntry("tb1.jar");
			testBundle1 = frameworkMBean.installBundleFromURL("tb1.jar", entry1.toString());

			//test refresh bundle
			frameworkMBean.uninstallBundle(testBundle2);
			testBundle2 = frameworkMBean.installBundleFromURL("tb2.jar", entry2.toString());
			assertTrue("bundle tb1 is not moved to installed state for " +  waitTime + " seconds", waitBundleStateChange(testBundle1, "INSTALLED"));			
			frameworkMBean.refreshBundle(testBundle2);						
			assertTrue("resolve of bundle tb1 doesn't succeed for " + waitTime + " seconds", waitBundleStateChange(testBundle1, "RESOLVED"));

			//test refresh bundles
			frameworkMBean.uninstallBundle(testBundle2);
			frameworkMBean.refreshBundle(testBundle1);
			assertTrue("bundle tb1 is not moved to installed state for " +  waitTime + " seconds", waitBundleStateChange(testBundle1, "INSTALLED"));
			testBundle2 = frameworkMBean.installBundleFromURL("tb2.jar", entry2.toString());
			frameworkMBean.refreshBundles(new long[] {testBundle1, testBundle2});						
			assertTrue("resolve of bundle tb1 doesn't succeed for " + waitTime + " seconds", waitBundleStateChange(testBundle1, "RESOLVED"));
		} catch(Exception io) {
			io.printStackTrace();
			assertTrue("Exception ocurred: " + io.toString(), false);
		} finally {
			if (testBundle1 >= 0) {
				try {
					frameworkMBean.uninstallBundle(testBundle1);
				} catch (Exception io) {}
			}
			if (testBundle2 >= 0) {
				try {
					frameworkMBean.uninstallBundle(testBundle2);
				} catch (Exception io) {}
			}
		}						
	}
	
	@Override
	protected void tearDown() throws Exception {
		super.tearDown();
		super.waitForUnRegistering(createObjectName(FrameworkMBean.OBJECTNAME));
	}
	
	private boolean waitBundleStateChange(long bundleId, String state) {
		boolean equal = false;
		int count = waitTime*10;
		try {		
			while (count-- > 0) { 
				if (bundleStateMBean.getState(bundleId).equals(state)) {
					equal = true;
					break;
				} else {
					synchronized (this) {
						this.wait(100);
					}
				}
			}
		} catch(InterruptedException e) {
			e.printStackTrace();
		} catch(IOException e) {
			e.printStackTrace();		
		}
		return equal;
	}
	
	private void assertCompositeDataKeys(CompositeData cd, String type, String[] keys) {
		for (int i = 0; i < keys.length; i++) {
			assertTrue("composite data from type " + type + " doesn't contain key " + keys[i], cd.containsKey(keys[i]));
		}
	}
	private final int waitTime = 20;
}