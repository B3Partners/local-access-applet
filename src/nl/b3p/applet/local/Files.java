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

import java.awt.Component;
import java.io.*;
import javax.swing.JFileChooser;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Methods to interact with files.
 * 
 * @author Matthijs Laan
 */
public class Files {

    private static final int MAX_FILE_SIZE = 128 * 1024;
    
    /**
     * Show a directory selector dialog.
     * 
     * @param title The title for the dialog.
     * @return the selected directory or null if the dialog was canceled
     */    
    public static String selectDirectory(Component component, String title) {
        JFileChooser chooser = new JFileChooser(); 
        chooser.setDialogTitle(title);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if(chooser.showOpenDialog(component) == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile().toString();
        } else {
            return null;
        }
    }

    /**
     * List files in a directory. The return value is a String with stringified 
     * and minified JSON instead of a JSObject to minimize LiveConnect 
     * marshalling time.
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
     * @return stringified JSON specified above.
     */        
    public static JSONArray listDirectory(String dir) throws JSONException {
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

    /**
     * Read a file return the contents as a String assuming the file is UTF-8.
     * 
     * @param file the file to read
     * @return the file contents read as UTF-8 or null if the file does not exist
     *   or is not readable
     * @throws IOException thrown when reading the file throws an IOException and
     *   also when the file is too large to marshal through LiveConnect (currently
     *   128KB, see MAX_FILE_SIZE final)
     */    
    public static String readFileUTF8(String file) throws IOException {
        File f = new File(file);
        if(!f.exists() || !f.canRead()) {
            return null;
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

    /**
     * Write a String to a file in UTF-8 encoding. Do not call with too large a
     * content parameter, as this may crash LiveConnect.
     * 
     * @param file the file to write
     * @param content String to write to the file in UTF-8.
     */    
    public static void writeFileUTF8(String file, String content) throws IOException {
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(content.getBytes("UTF-8"));
        fos.flush();
        fos.close();
    }      
}
