package xyz.jordanplayz158.vpkextractor;

import javafx.application.Application;
import xyz.jordanplayz158.vpkextractor.gui.GUI;


public class VPKExtractor {
    public static void main(String[] args) {
        /*if(args.length > 0) {
            switch (args[0]) {
                case "extract":
                    new CommandLine(new ExtractVPK()).execute(args);
                    break;
            }

            return;
        }*/

        // Initialize GUI
        Application.launch(GUI.class);
    }
}