/*
 * Copyright (c) IBM Corporation (2009). All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.osgi.test.cases.webcontainer.util;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.test.cases.webcontainer.util.validate.Validator;
import org.osgi.test.support.compatibility.DefaultTestBundleControl;
import org.osgi.util.tracker.ServiceTracker;

/**
 * @version $Rev$ $Date$
 * 
 *          abstract test class for webcontainer
 */
public abstract class WebContainerTestBundleControl extends
        DefaultTestBundleControl {
    protected Server server;
    protected boolean debug;
    protected long beforeInstall;
    protected Map<String, Object> options = new HashMap<String, Object>();
    protected String warContextPath;
    protected TimeUtil timeUtil;
    protected int srSize;
    protected Bundle b;
    protected static final String WARSCHEMA = "webbundle:";
    protected static final String WEB_CONTEXT_PATH = Validator.WEB_CONTEXT_PATH;
    protected static final String WEB_JSP_EXTRACT_LOCATION = Validator.WEB_JSP_EXTRACT_LOCATION;
    protected static final String[] IMPORTS_OSGI_FRAMEWORK = {"org.osgi.framework", "org.osgi.service.log"};
    protected static final String[] IMPORTS_JNDI = {"javax.naming"};

    public void setUp() throws Exception {
        // TODO if war file already exists, let's remove it first.
        this.server = new Server();
        this.debug = true;

        // capture a time before install
        this.beforeInstall = System.currentTimeMillis();
		ServiceReference[] sr = getContext().getAllServiceReferences("javax.servlet.ServletContext", null);
		this.srSize = (sr == null ? 0 : sr.length);
    }
    
	public void tearDown() throws Exception {
		if (this.b != null && this.b.getState() != Bundle.UNINSTALLED) {
			this.b.uninstall();
			if (debug) {
				log("uninstalled bundle " + this.b.getSymbolicName() + " "
						+ this.b.getVersion().toString());
			}
		}

		// make sure all war/wab are uninstalled
		ServiceReference[] sr = getContext().getAllServiceReferences("javax.servlet.ServletContext", null);
		assertEquals("service registry size should be the same", this.srSize, sr == null ? 0 : sr.length);
		this.b = null;
	}
	
    /**
     * install the specified war and return the installed bundle
     * @param options
     * @param warName
     * @param start
     * @return
     * @throws Exception
     */
    protected Bundle installWar(Map<String, Object> options, String warName,
            boolean start) throws Exception {
        // install the war file
        log("install and start war file: " + warName);

        try {
            String loc = getWarURL(warName, options);
            if (debug) {
                log("bundleName to be passed into installBundle is " + loc);
            }
            Bundle bundle = installBundle(loc);
            if (start) {
                try {
                    bundle.start();
                } catch (BundleException e) {
                    bundle.uninstall();
                    throw e;
                }
            }
            return bundle;
        } catch (BundleException e) {
            if (debug) {
                log("installWar failed: " + options + " warName: " + warName + "Exception: " + e.getCause());
            }
            throw e;
        } 
    }


    protected void prepare(String wcp) throws Exception {
        this.warContextPath = wcp;
        this.timeUtil = new TimeUtil(this.warContextPath);
        this.options.put(WEB_CONTEXT_PATH, this.warContextPath);
    }

    /*
     * return original manifest from the test war path
     */
    protected Manifest getManifest(String warPath) throws Exception {
        // test bundle manifest is constructed per user's deployment options
        String location = System.getProperty("user.dir");
        
        if (!location.endsWith(File.separator)) {
        	location += File.separator;
        }
        
        location += "generated" + warPath;
        log("jar:file:" + location + "!/");
        URL url = new URL("jar:file:" + location + "!/");
        JarURLConnection conn = (JarURLConnection) url.openConnection();
        JarFile jarfile = conn.getJarFile();
        Manifest originalManifest = jarfile.getManifest();
        return originalManifest;
    }

    /*
     * return original manifest from the test war name
     */
    protected Manifest getManifestFromWarName(String warName) throws Exception {
        return getManifest(getWarPath(warName));
    }

	/*
	 * return the warPath based on the warName, for example tw1.war path is
	 * /tw1.war
	 */
    private String getWarPath(String warName) throws Exception {
		return "/" + warName;
    }

    protected static void cleanupPropertyFile() {
        // clean up the property file.
        boolean success = ConstantsUtil.removeLogFile();
        if (!success) {
            log("Deleting File: " + ConstantsUtil.getLogFile() + " failed.");
        } else {
            log(ConstantsUtil.getLogFile() + " file is deleted.");
        }
    }

    protected String getWarURL(String name, Map<String, Object> options) {
        String query = generateQuery(options);
        if (query != null && query.length() > 0) {
        	if (debug) {
        		log("Install URL is " + WARSCHEMA + getWebServer() + name + "?"
                        + generateQuery(options));
        	}
            return WARSCHEMA + getWebServer() + name + "?"
                    + generateQuery(options);
        } else {
        	if (debug) {
        		log("Install URL is " + WARSCHEMA + getWebServer() + name);
        	}
            return WARSCHEMA + getWebServer() + name;
        }
    }

    protected String getResponse(String path) throws Exception {
        // final String request = warContextPath + "/";
        final URL url = Dispatcher.createURL(path, this.server);
        final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        final String response;
        try {
            assertEquals(200, conn.getResponseCode());
            checkContentType("text/html", conn.getContentType());
            response = Dispatcher.dispatch(conn);
            if (this.debug) {
                log(response);
            }
        } finally {
            conn.disconnect();
        }
        return response;
    }

    protected void checkContentType(String expected, String actual) {
        assertTrue("Unexpected content-type: " + actual, 
                  actual != null  && actual.startsWith(expected));
    }
    
    protected void checkPageContents(String contextPath, String warName) throws Exception {
        // check the correctness of the static home page
        String response = getResponse(contextPath);
        checkHomeResponse(response, warName);
        
        // check the correctness of a dynamic page
        String path = contextPath;
        if (warName.indexOf("tw1.war") > -1) {
            path += "/BasicTest";
        } else if (warName.indexOf("tw2.war") > -1) {
            path += "/ResourceServlet2";
        } else if (warName.indexOf("tw3.war") > -1) {
            path += "/ResourceServlet2";
        } else if (warName.indexOf("tw4.war") > -1) {
            path += "/TestServlet1/TestServlet2";
        } else if (warName.indexOf("tw5.war") > -1) {
            path += "/ClasspathTestServlet";
        }
        
        response = getResponse(path);
        checkDynamicPageResponse(response, warName);
        
        // additional test for tw1.war to make sure jsps are processed correctly
        if (warName.indexOf("tw1.war") > -1) {
            checkWelcomeJSP(contextPath);
        }
    }
    
    protected void checkWelcomeJSP(String contextPath) throws Exception {
        final String request = contextPath + "/welcome.jsp?email=eeg@osgi.org&message=Welcome%20String%20from%20env-entry!";
        String response = getResponse(request);
        // check if content of response is correct
        log("verify content of response is correct");
        assertTrue(response.indexOf(ConstantsUtil.EMAIL + "-"
                + ConstantsUtil.EMAILVALUE) > 0);
        assertTrue(response.indexOf(ConstantsUtil.WELCOMESTRING + "-"
                + ConstantsUtil.WELCOMESTRINGVALUE) > 0);
    }
    
    protected boolean ableAccessPath(String path) throws Exception {
        try {
            getResponse(path);
            return true;
        } catch (Exception e) {
            return false;
        } catch (Error e) {
            return false;
        }
    }

    /*
     * check static content index.html is correct to make sure war is deployed correctly
     */
    protected void checkHomeResponse(String response, String warName)
            throws Exception {
        log("verify content of index.html response is correct");
        if (warName.indexOf("tw1.war") > -1) {
            checkTW1HomeResponse(response);
        } else if (warName.indexOf("tw2.war") > -1) {
            checkTW2HomeResponse(response);
        } else if (warName.indexOf("tw3.war") > -1) {
            checkTW3HomeResponse(response);
        } else if (warName.indexOf("tw4.war") > -1) {
            checkTW4HomeResponse(response);
        } else if (warName.indexOf("tw5.war") > -1) {
            checkTW5HomeResponse(response);
        }
    }

    protected void checkTW1HomeResponse(String response) throws Exception {
        assertEquals(
                "<html><head><title>TestWar1</title></head><body>This is TestWar1.<P><A href=\"BasicTest\">/BasicTest</A><BR><A href=\"404.html\">404.html (static link)</A><BR><A href=\"ErrorTest?target=html\">404.html (through servlet.RequestDispatcher.forward())</A><BR><A href=\"ErrorTest\">404.jsp (through servlet.RequestDispatcher.forward())</A><BR><A href=\"aaa\">Broken Link (for call ErrorPage)</A><BR><A href=\"image.html\">image.html</A></P><BR></body></html>",
                response);
    }
    
    protected void checkTW2HomeResponse(String response) throws Exception {
        assertEquals(
                "<html><head><title>TestWar2</title></head><body>This is TestWar2.<P><A href=\"PostConstructPreDestroyServlet1\">PostConstructPreDestroyServlet1</A><BR><A href=\"PostConstructPreDestroyServlet2\">PostConstructPreDestroyServlet2</A><BR><A href=\"PostConstructPreDestroyServlet3\">PostConstructPreDestroyServlet3</A><BR><A href=\"ResourceServlet1\">ResourceServlet1</A><BR><A href=\"ResourceServlet2\">ResourceServlet2</A><BR><A href=\"ResourceServlet3\">ResourceServlet3</A><BR><A href=\"ResourceServlet4\">ResourceServlet4</A><BR><A href=\"PostConstructErrorServlet1\">PostConstructErrorServlet1</A><BR><A href=\"PostConstructErrorServlet2\">PostConstructErrorServlet2</A><BR><A href=\"PostConstructErrorServlet3\">PostConstructErrorServlet3</A><BR><A href=\"PreDestroyErrorServlet1\">PreDestroyErrorServlet1</A><BR><A href=\"PreDestroyErrorServlet2\">PreDestroyErrorServlet2</A><BR><A href=\"PreDestroyErrorServlet3\">PreDestroyErrorServlet3</A><BR><A href=\"ServletContextListenerServlet\">ServletContextListenerServlet</A><BR><A href=\"SecurityTestServlet\">SecurityTestServlet</A><BR><A href=\"RequestListenerServlet\">RequestListenerServlet</A><BR><A href=\"HTTPSessionListenerServlet\">HTTPSessionListenerServlet</A><BR></P></body></html>",
                response);
    }

    protected void checkTW3HomeResponse(String response) throws Exception {
        assertEquals(
                "<html><head><title>TestWar3</title></head><body>This is TestWar3.<P><A href=\"PostConstructPreDestroyServlet1\">PostConstructPreDestroyServlet1</A><BR><A href=\"PostConstructPreDestroyServlet2\">PostConstructPreDestroyServlet2</A><BR><A href=\"PostConstructPreDestroyServlet3\">PostConstructPreDestroyServlet3</A><BR><A href=\"ResourceServlet1\">ResourceServlet1</A><BR><A href=\"ResourceServlet2\">ResourceServlet2</A><BR><A href=\"ResourceServlet3\">ResourceServlet3</A><BR><A href=\"ResourceServlet4\">ResourceServlet4</A><BR><A href=\"ServletContextListenerServlet\">ServletContextListenerServlet</A><BR><A href=\"RequestListenerServlet\">RequestListenerServlet</A><BR><A href=\"HTTPSessionListenerServlet\">HTTPSessionListenerServlet</A><BR></P></body></html>",
                response);
    }

    protected void checkTW4HomeResponse(String response) throws Exception {
        assertEquals(
                "<html><head><title>TestWar4</title></head><body>This is TestWar4.<P><a href=\"TestServlet1\">TestServlet1</a><br/><a href=\"TestServlet1/TestServlet2?tc=1&param1=value1&param2=abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz\">TestServlet2?tc=1</a><BR><br/><a href=\"TestServlet1/TestServlet2/TestServlet3\">TestServlet3</a><br/><a href=\"TestServlet1/TestServlet2/TestServlet3/TestServlet4?type=plain\">TestServlet4 plain</a><br/><a href=\"TestServlet1/TestServlet2/TestServlet3/TestServlet4?type=html\">TestServlet4 html</a><br/><a href=\"TestServlet1/TestServlet2/TestServlet3/TestServlet4?type=jpg\">TestServlet4 jpg</a><br/></P></body></html>",
                response);    
    }

    protected void checkTW5HomeResponse(String response) throws Exception {
        assertEquals(
                "<html><head><title>TestWar5</title></head><body>This is TestWar5.<P><A href=\"BundleTestServlet\">/BundleTestServlet</A><BR><A href=\"ClasspathTestServlet\">/ClasspathTestServlet</A><BR></P></body></html>",
                response);
    }
    
    /* 
     * check one dynamic page is correct to make sure classes are loaded correctly
     */
    protected void checkDynamicPageResponse(String response, String warName) 
        throws Exception {
        log("verify content of one dynamic page response of each war file is correct");
        if (warName.indexOf("tw1.war") > -1) {
            checkTW1BasicTestServletResponse(response);
        } else if (warName.indexOf("tw2.war") > -1) {
            checkTW2ResourceServlet2Response(response);
        } else if (warName.indexOf("tw3.war") > -1) {
            checkTW3ResourceServlet2Response(response);
        } else if (warName.indexOf("tw4.war") > -1) {
            checkTW4TestServletResponse(response);
        } else if (warName.indexOf("tw5.war") > -1) {
            checkTW5ClassPathTestServletResponse(response);
        }
    }
    
    /*
     * check the response of BasicTestServlet of tw1
     */
    protected void checkTW1BasicTestServletResponse(String response) throws Exception {
        log("verify content of BasicTestServlet response from tw1 is correct");
        assertEquals(response, ConstantsUtil.BASICTESTWAR1);
    }
    
    /*
     * check the response of ResourceServlet2 of tw2
     */
    protected void checkTW2ResourceServlet2Response(String response) throws Exception {
        // check if content of response is correct
        log("verify content of ResourceServlet2 response from tw2 is correct");
        assertTrue(response.indexOf("ResourceServlet2") > 0);
        assertTrue(response.indexOf("Welcome String from env-entry!") > 0);
        assertTrue(response.indexOf("5 + 5 = 10 that is true") > 0);
        assertEquals(-1, response.indexOf("null"));    
    }
    
    /*
     * check the response of ResourceServlet2 of tw3
     */
    protected void checkTW3ResourceServlet2Response(String response) throws Exception {
        // check if content of response is correct
        log("verify content of ResourceServlet2 response from tw3 is correct");
        assertTrue(response.indexOf("ResourceServlet2") > 0);
        assertTrue(response.indexOf(ConstantsUtil.NULL + " "
                + ConstantsUtil.NULL) > 0);
        assertTrue(response.indexOf(ConstantsUtil.NULL + " + "
                + ConstantsUtil.NULL + " = " + ConstantsUtil.NULL + " that is "
                + ConstantsUtil.NULL) > 0); 
    }
    
    /*
     * check the response of TestServlet1 of tw4
     */
    protected void checkTW4TestServletResponse(String response) throws Exception {
        assertEquals("<html><head><title>TestWar4-TestServlet</title></head><body>no test case is specified</body></html>", response);
    }
    
    /*
     * check the response of ClasspathTestServlet of tw5
     */
    protected void checkTW5ClassPathTestServletResponse(String response) throws Exception {
        assertEquals("checking response content", "<html><head><title>ClasspathTestServlet</title></head><body>" 
                + ConstantsUtil.ABLEGETLOG + "<br/>" +  ConstantsUtil.ABLEGETSIMPLEHELLO + "<br/></body></html>", response);
    }

    private String generateQuery(Map<String, Object> options) {
        String symbolicName = options.get(Constants.BUNDLE_SYMBOLICNAME) == null ? null
                : (String) options.get(Constants.BUNDLE_SYMBOLICNAME);
        String version = options.get(Constants.BUNDLE_VERSION) == null ? null
                : (String) options.get(Constants.BUNDLE_VERSION);
        String manifestVersion = options.get(Constants.BUNDLE_MANIFESTVERSION) == null ? null
                : (String) options.get(Constants.BUNDLE_MANIFESTVERSION);
        String[] importPackage = options.get(Constants.IMPORT_PACKAGE) == null ? null
                : (String[]) options.get(Constants.IMPORT_PACKAGE);
        String[] exportPackage = options.get(Constants.EXPORT_PACKAGE) == null ? null
                : (String[]) options.get(Constants.EXPORT_PACKAGE);
        String[] classpath = options.get(Constants.BUNDLE_CLASSPATH) == null ? null
                : (String[]) options.get(Constants.BUNDLE_CLASSPATH);
        String contextPath = options.get(WEB_CONTEXT_PATH) == null ? null
                : (String) options.get(WEB_CONTEXT_PATH);
        String jspExtractLoc = options.get(WEB_JSP_EXTRACT_LOCATION) == null ? null
                : (String) options.get(WEB_JSP_EXTRACT_LOCATION);
        String query = "";

        if (symbolicName != null) {
            query += "&" + Constants.BUNDLE_SYMBOLICNAME + "=" + symbolicName;
        }
        if (version != null) {
            query += "&" + Constants.BUNDLE_VERSION + "=" + version;
        }
        if (manifestVersion != null) {
            query += "&" + Constants.BUNDLE_MANIFESTVERSION + "=" + manifestVersion;
        }
        if (importPackage != null) {
            query += "&" + Constants.IMPORT_PACKAGE + "="
                    + getStringValue(importPackage);
        }
        if (exportPackage != null) {
            query += "&" + Constants.EXPORT_PACKAGE + "="
                    + getStringValue(exportPackage);
        }
        if (classpath != null) {
            query += "&" + Constants.BUNDLE_CLASSPATH + "="
                    + getStringValue(classpath);
        }
        if (contextPath != null) {
            query += "&" + WEB_CONTEXT_PATH + "=" + contextPath;
        }
        if (jspExtractLoc != null) {
            query += "&" + WEB_JSP_EXTRACT_LOCATION + "=" + jspExtractLoc;
        }
        if (query != null && query.startsWith("&")) {
            query = query.substring(1);
        }
        return query;
    }

    private String getStringValue(String[] a) {
        String value = "";
        for (int i = 0; i < a.length; i++) {
            value += a[i];
            if (i < a.length - 1) {
                value += ",";
            }
        }
        return value;
    }

    @Override
    public Bundle installBundle(String bundleName, boolean start)
            throws Exception {
        try {
            if (bundleName.indexOf(getWebServer()) < 0) {
                bundleName = getWebServer() + bundleName;
            }

            Bundle bundle = getContext().installBundle(bundleName);
            if (start) {
                try {
                    bundle.start();
                } catch (BundleException e) {
                    bundle.uninstall();
                }
            }
            return bundle;
        } catch (Exception e) {
            log("Not able to install testbundle " + bundleName);
            log("Nested " + e.getCause());
            e.printStackTrace();
            throw e;
        }
    }
    
    @Override
    public Bundle installBundle(String bundleName)
            throws Exception {
        try {
            if (bundleName.indexOf(getWebServer()) < 0) {
                bundleName = getWebServer() + bundleName;
            }
            
            Bundle b = getContext().installBundle(bundleName);

            return b;
        } catch (Exception e) {
            log("Not able to install testbundle " + bundleName);
            log("Nested " + e.getCause());
            e.printStackTrace();
            throw e;
        }
    }
    
	/**
	 * this method checks if the servlet context associated with the web context path 
	 * is registered in the service registry.  This method will return false if unable
	 * to find the registered servlet context within 10 seconds.
	 * @param webContextPath
	 * @return
	 * @throws InterruptedException 
	 * @throws InvalidSyntaxException 
	 */
	public boolean checkServiceRegistered(String webContextPath) throws InterruptedException, InvalidSyntaxException {
		boolean toReturn = false;
		// open a service tracker
	    StringBuilder filterString = new StringBuilder();
	    filterString.append("(&(" + Constants.OBJECTCLASS + "=javax.servlet.ServletContext)");
        filterString.append("(osgi.web.contextpath" + "=" + webContextPath + "))");
	    Filter filter = getContext().createFilter(filterString.toString());
	    ServiceTracker st = new ServiceTracker(getContext(), filter, null);
	    st.open();
	    
	    Object obj = st.waitForService(10000);
	    if (obj != null) {
	    	toReturn = true;
	    }
	    
	    // close the tracker
	    if (st != null) {
	    	st.close();
	    }
	    
	    return toReturn;
	}
}