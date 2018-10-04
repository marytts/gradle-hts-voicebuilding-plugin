package de.dfki.mary.htsvoicebuilding.stages.task

//
import java.util.Hashtable

// Inject
import javax.inject.Inject;

// Worker import
import org.gradle.workers.*;

// Gradle task related
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.*;


/**
 *  Definition of the task type to generate spectrum, f0 and aperiodicity using world vocoder
 *
 */
public class GenerateMOCCConfigurationFile extends DefaultTask {

    Hashtable<File, Float> mocc_values = new Hashtable<File, Float>();

    /** The list of files to manipulate */
    @OutputFiles
    ConfigurableFileCollection mocc_files = project.files();

    /** The directory containing the spectrum files */
    @InputFile
    final RegularFileProperty template_file = newInputFile();


    /**
     *  The actual generateion method
     *
     */
    @TaskAction
    public void generate() {

        for (File mocc_file: mocc_files.getFiles()) {
            def binding = [mocc : mocc_values.get(mocc_file)]
            project.copy {
                from template_file
                into mocc_file.getParent()

                rename { file -> mocc_file.getName() }

                expand(binding)
            }
        }
    }
}
