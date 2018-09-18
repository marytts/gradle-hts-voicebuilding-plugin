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
public class GenerateMLFTask extends DefaultTask {
    /** The list of files to manipulate */
    @InputDirectory
    final DirectoryProperty lab_dir = newInputDirectory()

    /** The directory containing the spectrum files */
    @OutputFile
    final RegularFileProperty mlf_file = newOutputFile()

    /**
     *  The actual generateion method
     *
     */
    @TaskAction
    public void generate() {
        mlf_file.getAsFile().get().text = "#!MLF!#\n" + '"*/*.lab" -> "' + lab_dir.getAsFile().get() +'"'
    }
}
