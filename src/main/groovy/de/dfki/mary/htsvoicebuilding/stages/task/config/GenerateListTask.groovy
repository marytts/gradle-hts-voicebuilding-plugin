package de.dfki.mary.htsvoicebuilding.stages.task.config

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
public class GenerateListTask extends DefaultTask {
    /** The list of files to manipulate */
    @InputFile
    final RegularFileProperty list_basenames = newInputFile()

    @InputDirectory
    final DirectoryProperty lab_dir = newInputDirectory()

    /** The directory containing the spectrum files */
    @OutputFile
    final RegularFileProperty list_file = newOutputFile()

    /**
     *  The actual generateion method
     *
     */
    @TaskAction
    public void generate() {

        // Generate set of labels
        def model_set = new HashSet();
        for (String basename: list_basenames.getAsFile().get().readLines()) {
            (new File(lab_dir.getAsFile().get().toString(), basename + ".lab")).eachLine { line ->
                def line_arr = line =~ /^[ \t]*([0-9]+)[ \t]+([0-9]+)[ \t]+(.+)/
                if (line_arr.size() == 0) {
                    model_set.add(line)
                } else {
                    model_set.add(line_arr[0][3])
                }
            }
        }

        // Save it into the list
        list_file.getAsFile().get().text = model_set.join("\n")
    }
}
