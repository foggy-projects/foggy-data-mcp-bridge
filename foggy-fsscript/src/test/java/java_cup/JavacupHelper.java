/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package java_cup;

import java.io.IOException;

public class JavacupHelper {
    // public static void main(final String[] args) {
    // long x = Long.parseLong("1375694267597");
    // // Systemx.out.println(new Date(x));
    //
    // // Systemx.out.println(new SimpleDateFormat("yyyy-MM-dd").format(x));
    // // Systemx.out.println(new SimpleDateFormat("yyyy-mm-dd")
    // .format(new Date(x)));
    // }

    public static void main(final String[] args) {
        try {
            Main.main(
                    new String[]{"-expect", "2", "-package", "com.foggyframework.fsscript.parser", "-parser", "ExpParser",
                    "-symbols", "ExpSymbols", "-destdir", "D:\\foggy-projects\\java-data-mcp-bridge\\foggy-fsscript\\src\\main\\java\\com\\foggyframework\\fsscript\\parser",
                    "D:\\foggy-projects\\java-data-mcp-bridge\\foggy-fsscript\\src\\main\\resources\\datasetexp.cup"});

//					"/Users/fengjianguang/workspaces/v3-foggy/work/foggy-framework-fsscript/src/main/resources/datasetexp.cup");
        } catch (final internal_error e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (final IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (final Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
