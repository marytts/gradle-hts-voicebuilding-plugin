package de.dfki.mary.utils

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.OutputFile;
import java.io.File;

public class StandardFileTask extends DefaultTask {
    @OutputFile
    File output

    public void setOutput(String output_string)
    {
        output = new File(output_string)
    }
}
