/*
 * Copyright (C) 2012 B3Partners B.V.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package nl.b3p.applet.local;

import java.applet.Applet;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.*;
import netscape.javascript.JSObject;

/**
 * Applet which allows JavaScript to access local resources such as listing 
 * files and reading and writing files.
 * 
 * Usage: 
 * <code>
 * var appletDir = "/myapp/applet/";
 * var appletContainterElementId = "applet-container";
 * 
 * function loadApplet(appletCaller) {
 *     if(this.local == null) {
 *         if(confirm("Load Java applet?")) {
 *             this.local = new LocalAccess();
 *             this.local.initApplet(appletDir, appletContainerElementId, function() {
 *                 appletCaller(this.local);
 *             });
 *          }
 *     } else {
 *         appletCaller(this.local);
 *     }
 * }
 * 
 * loadApplet(function(local) {
 *     local.callApplet("selectDirectory", console.log, console.log);
 * });
 * </code>
 *
 * @author Matthijs Laan
 */
public class LocalAccessApplet extends Applet {

    private static final String TOOLTIP_DEFAULT = "Java applet voor het openen van lokale mappen";
    private final JLabel status = new JLabel("");
    private Icon java, spinner;
    
    private ExecutorService executorService;
    
    @Override
    public void init() {

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());        
        } catch(Exception e) {
        }
        
        setLayout(null);
        try {
            spinner = new ImageIcon(LocalAccessApplet.class.getResource("spinner.gif"));
            java = new ImageIcon(LocalAccessApplet.class.getResource("java.gif"));
            status.setIcon(java);
            add(status);
            status.setToolTipText(TOOLTIP_DEFAULT);
            status.setBounds(getWidth()-16-1,0,16,16);
        } catch(Exception e) {
            e.printStackTrace();
        }
        
        executorService = Executors.newSingleThreadExecutor();
        
        JSObject.getWindow(this).call("localAccessAppletLoaded", new Object[] { });
    }
    
    private void progress(boolean busy) {    
        progress(busy, null);
    }
    
    private void progress(boolean busy, String tooltip) {
        if(busy) {
            status.setIcon(spinner);
            status.setToolTipText(tooltip == null ? TOOLTIP_DEFAULT : tooltip);
        } else {
            status.setIcon(java);
            status.setToolTipText(TOOLTIP_DEFAULT);
        }
    }
    
    @Override
    public void destroy() {
        executorService.shutdown();
    }
    
    private static String exceptionMessage(Exception e) {
        Throwable t = e;
        while(t instanceof InvocationTargetException && t.getCause() != null) {
            t = t.getCause();
        }
        String s = t.toString();
        StackTraceElement[] bt = t.getStackTrace();
        if(bt.length > 0) {
            s += " in " + bt[0].getFileName() + ":" + bt[0].getLineNumber() + " in " + bt[0].getMethodName();
        }
        return s;
    }
    
    private void doCallback(String callback, Object[] params) {
        Object[] params2 = new Object[params.length+1];
        params2[0] = callback;
        System.arraycopy(params, 0, params2, 1, params.length);
        JSObject.getWindow(this).call("localAccessAppletCallback", params2);
    }
    
    private void doPrivileged(final String status, final Method method, final Object[] params, final String callback, final String errorCallback) {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    progress(true, status);
                    
                    Object ret = AccessController.doPrivileged(new PrivilegedAction() {
                        @Override
                        public Object run() {
                            try {
                                return method.invoke(null, params);
                            } catch (Exception ex) {
                                return ex;
                            }
                        }
                    });        
                    if(ret instanceof Exception) {
                        doCallback(errorCallback, new Object[] { exceptionMessage((Exception)ret) });
                    } else {                       
                        doCallback(callback, new Object[] { ret });
                    } 
                } catch(Exception e) {
                    doCallback(errorCallback, new Object[] { exceptionMessage(e) });
                } finally {
                    progress(false);
                }
            }
        });       
    }
    
    /* All methods callable through JavaScript LiveConnect below: */

    /* These functions are explicitly defined here (instead of calling methods
     * dynamically using reflection) for reasons of simplicity. The actual 
     * implementations are split out in separate classes. The methods below 
     * are the only entry points for LiveConnect JavaScript calls.
     */
    
    /* === File functions === */
    
    public void selectDirectory(final String title, final String callback, final String errorCallback) throws NoSuchMethodException {
        doPrivileged("Dialoog om map te selecteren wordt getoond",
                Files.class.getMethod("selectDirectory", Component.class, String.class),
                new Object[] {this, title},
                callback, errorCallback);
    }
    
    public void listDirectory(final String dir, final String callback, final String errorCallback) throws NoSuchMethodException { 
        doPrivileged("Ophalen lijst met bestanden in directory \"" + dir + "\"",
                Files.class.getMethod("listDirectory", String.class),
                new Object[] { dir },
                callback, errorCallback);
    }
    
    public void readFileUTF8(final String file, final String callback, final String errorCallback) throws NoSuchMethodException { 
        doPrivileged("Lezen bestand \"" + file + "\"...",
                Files.class.getMethod("readFileUTF8", String.class),
                new Object[] { file },
                callback, errorCallback);
    }
    
    public void writeFileUTF8(final String file, final String content, final String callback, final String errorCallback) throws NoSuchMethodException { 
        doPrivileged("Schrijven bestand \"" + file + "\"...",
                Files.class.getMethod("writeFileUTF8", String.class, String.class),
                new Object[] { file, content },
                callback, errorCallback);
    }
    
    /* === Shapefile functions === */    
    
    public void getShapefileMetadata(final String file, final String callback, final String errorCallback) throws NoSuchMethodException {
        doPrivileged("Lezen shapefile \"" + file + "\"...",
                Shapefiles.class.getMethod("getMetadata", String.class),
                new Object[] { file },
                callback, errorCallback);
    }
    
    /* === NetCDF functions === */
    
    public void getNCDump(final String file, final String callback, final String errorCallback) throws NoSuchMethodException {
        doPrivileged("Lezen NetCDF informatie \"" + file + "\"...",
                NetCDF.class.getMethod("getNCDump", String.class),
                new Object[] { file },
                callback, errorCallback);
    }    
    
    public void getNCML(final String file, final String callback, final String errorCallback) throws NoSuchMethodException {
        doPrivileged("Lezen NetCDF informatie \"" + file + "\"...",
                NetCDF.class.getMethod("getNCML", String.class),
                new Object[] { file },
                callback, errorCallback);
    }      
}

