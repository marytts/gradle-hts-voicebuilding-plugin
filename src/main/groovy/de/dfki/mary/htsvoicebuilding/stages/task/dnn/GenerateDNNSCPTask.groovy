package de.dfki.mary.htsvoicebuilding.stages.task.dnn

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
public class GenerateDNNSCPTask extends DefaultTask {
    /** The list of files to manipulate */
    @InputFile
    final RegularFileProperty list_file = newInputFile()

    @InputDirectory
    final DirectoryProperty ffo_dir = newInputDirectory()

    @InputDirectory
    final DirectoryProperty ffi_dir = newInputDirectory()

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
        for (String basename: list_file.getAsFile().get().readLines()) {
            output += "${ffi_dir.getAsFile().get()}/${basename}.ffi ${ffo_dir.getAsFile().get()}/${basename}.ffo\n"
        }

        scp_file.getAsFile().get().text = output
    }
}
