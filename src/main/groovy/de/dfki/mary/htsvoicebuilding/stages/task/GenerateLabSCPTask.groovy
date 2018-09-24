package de.dfki.mary.htsvoicebuilding.stages.task

// Inject
import javax.inject.Inject;

// Worker import
import org.gradle.workers.*;

// Gradle task related
import org.gradle.api.Action;
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*


/**
 *  Definition of the task type to generate spectrum, f0 and aperiodicity using world vocoder
 *
 */
public class GenerateLabSCPTask extends DefaultTask {
    /** The list of files to manipulate */
    @InputFile
    final RegularFileProperty list_basenames = newInputFile()

    @InputDirectory
    final DirectoryProperty lab_dir = newInputDirectory()

    /** The directory containing the spectrum files */
    @OutputFile
    final RegularFileProperty scp_file = newOutputFile()


    /**
     *  The actual generateion method
     *
     */
    @TaskAction
    public void generate() {
        def output = ""
        for (String basename: list_basenames.getAsFile().get().readLines()) {
            output += "${lab_dir.getAsFile().get().toString()}/${basename}.lab\n"
        }

        scp_file.getAsFile().get().text = output
    }
}
