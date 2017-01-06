package de.dfki.mary.utils

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.OutputDirectory;
import java.io.File;

public class StandardTask extends DefaultTask {
    @OutputDirectory
    File output

    public void setOutput(String output_string)
    {
        output = new File(output_string)
    }
}