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

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.json.JSONException;
import ucar.nc2.NCdumpW;
import ucar.nc2.NetcdfFile;

/**
 *
 * @author Matthijs Laan
 */
public class NetCDF {
        
    public static String getMetadata(String file) throws IOException, JSONException {

        StringWriter writer = new StringWriter();            
        NetcdfFile nc = null;
        try {
            File f = new File(file);
            nc = NetcdfFile.open(f.toString(), null);

            NCdumpW.print(nc, "", writer, null);

        } catch(Exception e) {
            writer.write("\n");
            PrintWriter pw = new PrintWriter(writer);
            e.printStackTrace(pw);
            pw.close();
        } finally {
            if(nc != null) {
                try {
                    nc.close();
                } catch(IOException e) {
                    // ignore
                }
            }
        }

        return writer.toString();
    }    
}
