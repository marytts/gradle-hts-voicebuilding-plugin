// package de.dfki.mary.htsvoicebuilding

import groovy.json.* // To load the JSON configuration file

public class DataFileFinder {
    def static project_path
    
    static String getFilePath(String filename) {
        // Absolute path
        if (filename.startsWith("/")){
            return filename
        }

        
        return project_path + "/" + filename
    }
    
    static String getFilePath(String filename, String ext) {
        if (ext.startsWith(".")){
            return getFilePath(filename + ext)
        }
        
        return getFilePath(filename + "." + ext)
    }
}
