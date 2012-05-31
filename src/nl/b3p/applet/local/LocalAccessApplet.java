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
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.*;
import netscape.javascript.JSObject;
import org.geotools.data.shapefile.dbf.DbaseFileHeader;
import org.geotools.data.shapefile.shp.ShapefileHeader;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Applet which allows JavaScript to access local resources such as listing 
 * files and reading and writing files.
 *
 * @author Matthijs Laan
 */
public class LocalAccessApplet extends Applet {

    private static final int MAX_FILE_SIZE = 128 * 1024;
    
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
    
    private static class Privileged {
        private static String selectDirectory(Component component, String title) {
            JFileChooser chooser = new JFileChooser(); 
            chooser.setDialogTitle(title);
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if(chooser.showOpenDialog(component) == JFileChooser.APPROVE_OPTION) {
                return chooser.getSelectedFile().toString();
            } else {
                return null;
            }
        }
        
        private static JSONArray listDirectory(String dir) throws JSONException {
            JSONArray ja = new JSONArray();
            for(File f: new File(dir).listFiles()) {
                JSONObject jo = new JSONObject();
                boolean isDir = f.isDirectory();
                jo.put("d", isDir ? 1 : 0);
                jo.put("n", f.getName());
                if(!isDir) {
                    jo.put("s", f.length());
                }
                ja.put(jo);
            }      
            return ja;
        }        
        
        private static String readFileUTF8(String file) throws IOException {
            File f = new File(file);
            if(!f.exists() || !f.canRead()) {
                throw new FileNotFoundException(file);
            }
            if(f.length() > MAX_FILE_SIZE) {
                throw new IOException("Bestand is te groot");
            }
                
            FileInputStream in = new FileInputStream(file);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            
            byte[] buffer = new byte[8192];
            
            IOUtils.copyLarge(in, out, buffer);
            
            return out.toString("UTF-8");                    
        }
        
        private static void writeFileUTF8(String file, String content) throws IOException {
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(content.getBytes("UTF-8"));
            fos.flush();
            fos.close();
        }      
        
        private static String getShapefileMetadata(String file) throws IOException, JSONException {
            
            if(!file.toLowerCase().endsWith(".shp")) {
                throw new IllegalArgumentException("File does not end with .shp: " + file);
            }
            
            FileChannel channel = new FileInputStream(file).getChannel();
            ShapefileHeader header = new ShapefileHeader();
            ByteBuffer bb = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
            header.read(bb, true);
            channel.close();
            channel = null;
            file = file.substring(0, file.length()-4) + ".dbf";
            
            JSONObject j = new JSONObject();
            j.put("type", header.getShapeType().name);
            j.put("version", header.getVersion());
            j.put("minX", header.minX());
            j.put("minY", header.minY());
            j.put("maxX", header.maxX());
            j.put("maxY", header.maxY());
            
            JSONObject dbf = new JSONObject();
            j.put("dbf", dbf);
            try {
                channel = new FileInputStream(file).getChannel();
                DbaseFileHeader dheader = new DbaseFileHeader();
                bb = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
                dheader.readHeader(bb);
                dbf.put("numRecords", dheader.getNumRecords());
                JSONArray fields = new JSONArray();
                dbf.put("fields", fields);
                for(int i = 0; i < dheader.getNumFields(); i++) {
                    JSONObject field = new JSONObject();
                    fields.put(field);
                    field.put("name", dheader.getFieldName(i));
                    field.put("length", dheader.getFieldLength(i));
                    field.put("decimalCount", dheader.getFieldDecimalCount(i));
                    field.put("class", dheader.getFieldClass(i).getName().toString());
                    field.put("type", dheader.getFieldType(i) + "");
                }
            } catch(Exception e) {
                dbf.put("error", e.toString());
            } finally {
                if(channel != null) {
                    channel.close();
                }
            }
            
            file = file.substring(0, file.length()-4) + ".prj";
            File f = new File(file);
            String prj = null;
            if(f.exists()) {
                Scanner s = new Scanner(f);
                prj = "";
                try {
                    while(s.hasNextLine()) {
                        if(prj.length() > 0) {
                            prj += "\n";
                        }
                        prj += s.nextLine();
                    }
                } finally {
                    s.close();
                }
            }
            j.put("prj", prj);
            
            return j.toString();
        }
    }
    
    private static String exceptionMessage(Exception e) {
        e.printStackTrace();
        String s = e.toString();
        StackTraceElement[] bt = e.getStackTrace();
        if(bt.length > 0) {
            s += " in " + bt[0].getFileName() + ":" + bt[0].getLineNumber() + " in " + bt[0].getMethodName();
        }
        return s;
    }
    
    
    /**
     * Show a directory selector dialog.
     * 
     * @param title The title for the dialog.
     * @param callback JavaScript function to call when a directory is selected.
     *   The function is called with a String parameter with the selected 
     *   directory which is null when the dialog is canceled.
     * @param errorCallback JavaScript function to call when an error occurs. Single
     *   String argument is a toString() of the Exception. 
     */
    public void selectDirectory(final String title, final String requestId, final String callback, final String errorCallback) {
        final Applet applet = this;
        executorService.execute(new Runnable() {

            @Override
            public void run() {
                try {
                    progress(true, "Dialoog om map te selecteren wordt getoond");
                    Object ret = AccessController.doPrivileged(new PrivilegedAction() {
                        @Override
                        public Object run() {
                            try {
                                return Privileged.selectDirectory(applet, title);
                            } catch(Exception ex) {
                                return ex;
                            }
                        }
                    });            
                    
                    if(ret instanceof Exception) {
                        JSObject.getWindow(applet).call(errorCallback, new Object[] { requestId, exceptionMessage((Exception)ret) });
                    } else {
                        JSObject.getWindow(applet).call(callback, new Object[] { requestId, ret });
                    }
                    
                } catch(Exception e) {
                    JSObject.getWindow(applet).call(errorCallback, new Object[] { requestId, exceptionMessage(e) });
                } finally {
                    progress(false);
                }
            }
        });
    }   
    
    /**
     * List files in a directory. The JavaScript callback function is called
     * with a single String argument which is stringified and minified JSON 
     * instead of a JSObject to minimize LiveConnect marshalling time.
     * <p>
     * Example:
     * <pre>
     * [ { d: 0, n: "foo.txt", s: 1024 }, { d: 1, n: "subdir"} ]
     * </pre>
     * Where:<br>
     * <i>d</i> is non-zero if the file is a directory<br>
     * <i>n</i> is the filename<br>
     * <i>s</i> is the filesize<br>
     * 
     * @param title The title for the dialog.
     * @param callback JavaScript function to call with the list of files.
     *   The function is called with a String parameter with stringified JSON
     *   specified above.
     * @param errorCallback JavaScript function to call when an error occurs. Single
     *   String argument is a toString() of the Exception. 
     */    
    public void listDirectory(final String dir, final String requestId, final String callback, final String errorCallback) {       
        final Applet applet = this;
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    progress(true, "Ophalen lijst met bestanden in directory \"" + dir + "\"");
                    
                    Object ret = AccessController.doPrivileged(new PrivilegedAction() {
                        @Override
                        public Object run() {
                            try {
                                return Privileged.listDirectory(dir);
                            } catch (Exception ex) {
                                return ex;
                            }
                        }
                    });            
                    if(ret instanceof Exception) {
                        JSObject.getWindow(applet).call(errorCallback, new Object[] { requestId, exceptionMessage((Exception)ret) });
                    } else {                       
                        JSObject.getWindow(applet).call(callback, new Object[] { requestId, ret.toString()});
                    } 
                } catch(Exception e) {
                    JSObject.getWindow(applet).call(errorCallback, new Object[] { requestId,  exceptionMessage(e) });
                } finally {
                    progress(false);
                }
            }
        });       
    }
    
    /**
     * Read a file and call a JavaScript function with the file content as a String
     * assuming the file is UTF-8.
     * 
     * @param file the file to read
     * @param callback JavaScript function to call with the file content.
     * @param errorCallback JavaScript function to call when an error occurs. Single
     *   String argument is a toString() of the Exception. 
     */    
    public void readFileUTF8(final String file, final String requestId, final String callback, final String errorCallback) {
        final Applet applet = this;
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    progress(true, "Lezen bestand \"" + file + "\"...");
                    
                    Object ret = AccessController.doPrivileged(new PrivilegedAction() {
                        @Override
                        public Object run() {
                            try {
                                return Privileged.readFileUTF8(file);
                            } catch (Exception ex) {
                                return ex;
                            }
                        }
                    });            
                    if(ret instanceof Exception) {
                        JSObject.getWindow(applet).call(errorCallback, new Object[] { requestId, exceptionMessage((Exception)ret) });
                    } else {                       
                        JSObject.getWindow(applet).call(callback, new Object[] { requestId, ret.toString()});
                    } 
                } catch(Exception e) {
                    JSObject.getWindow(applet).call(errorCallback, new Object[] { requestId, exceptionMessage(e) });
                } finally {
                    progress(false);
                }
            }
        });       
    }    
    
    /**
     * Read a file and call a JavaScript function with the file content as a String
     * assuming the file is UTF-8. 
     * 
     * @param file the file to read
     * @param callback JavaScript function to call with the file content.
     * @param notFoundCallback JavaScript function to call if the file doest not exist
     * @param errorCallback JavaScript function to call when an error occurs. Single
     *   String argument is a toString() of the Exception. 
     */    
    public void readFileIfExistsUTF8(final String file, final String notFoundCallback, final String requestId, final String callback, final String errorCallback) {
        final Applet applet = this;
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    progress(true, "Lezen bestand \"" + file + "\"...");
                    
                    Object ret = AccessController.doPrivileged(new PrivilegedAction() {
                        @Override
                        public Object run() {
                            try {
                                return Privileged.readFileUTF8(file);
                            } catch (Exception ex) {
                                return ex;
                            }
                        }
                    });        
                    if(ret instanceof FileNotFoundException) {
                        JSObject.getWindow(applet).call(notFoundCallback, new Object[] { requestId });
                    } else if(ret instanceof Exception) {
                        JSObject.getWindow(applet).call(errorCallback, new Object[] { requestId, exceptionMessage((Exception)ret) });
                    } else {                       
                        JSObject.getWindow(applet).call(callback, new Object[] { requestId, ret.toString()});
                    } 
                } catch(Exception e) {
                    JSObject.getWindow(applet).call(errorCallback, new Object[] { requestId, exceptionMessage(e) });
                } finally {
                    progress(false);
                }
            }
        });       
    }      
    
    /**
     * Write a String to a file in UTF-8 encoding.
     * 
     * @param file the file to write
     * @param content String to write to the file in UTF-8.
     * @param callback JavaScript function to call when successfully written.
     * @param errorCallback JavaScript function to call when an error occurs. Single
     *   String argument is a toString() of the Exception. 
     */    
    public void writeFileUTF8(final String file, final String content, final String requestId, final String callback, final String errorCallback) {
        final Applet applet = this;
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    progress(true, "Schrijven bestand \"" + file + "\"...");
                    
                    Object ret = AccessController.doPrivileged(new PrivilegedAction() {
                        @Override
                        public Object run() {
                            try {
                                Privileged.writeFileUTF8(file, content);
                                return null;
                            } catch (Exception ex) {
                                return ex;
                            }
                        }
                    });        
                    if(ret instanceof Exception) {
                        JSObject.getWindow(applet).call(errorCallback, new Object[] { requestId, exceptionMessage((Exception)ret) });
                    } else {                       
                        JSObject.getWindow(applet).call(callback, new Object[] { requestId });
                    } 
                } catch(Exception e) {
                    JSObject.getWindow(applet).call(errorCallback, new Object[] { requestId, exceptionMessage(e) });
                } finally {
                    progress(false);
                }
            }
        });       
    }       
    
    public void getShapefileMetadata(final String file, final String requestId, final String callback, final String errorCallback) {
        final Applet applet = this;
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    progress(true, "Lezen shapefile \"" + file + "\"...");
                    
                    Object ret = AccessController.doPrivileged(new PrivilegedAction() {
                        @Override
                        public Object run() {
                            try {
                                return Privileged.getShapefileMetadata(file);
                            } catch (Exception ex) {
                                return ex;
                            }
                        }
                    });        
                    if(ret instanceof Exception) {
                        JSObject.getWindow(applet).call(errorCallback, new Object[] { requestId, exceptionMessage((Exception)ret) });
                    } else {                       
                        JSObject.getWindow(applet).call(callback, new Object[] { requestId, ret });
                    } 
                } catch(Exception e) {
                    JSObject.getWindow(applet).call(errorCallback, new Object[] { requestId, exceptionMessage(e) });
                } finally {
                    progress(false);
                }
            }
        });       
    }
}

