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
public class GenerateCMPTreeConversionScriptTask extends DefaultTask {

    @InputFile
    final RegularFileProperty list_file = newInputFile()

    /** The directory containing the spectrum files */
    @OutputFile
    final RegularFileProperty script_file = newOutputFile()


    /**
     *  The actual generateion method
     *
     */
    @TaskAction
    public void generate() {

        def output = "TR 2\n"

        project.configuration.user_configuration.models.cmp.streams.each { stream ->
            output += "LT \"$project.tree_dir/${stream.kind}.${project.configuration.user_configuration.settings.training.nb_clustering-1}.inf\"\n" // FIXME: hardcoded
        }

        output += "AU \"${list_file.getAsFile().get()}\"\n"
        output += "CO \"${project.list_dir}/tiedlist_cmp\"\n" // FIXME: hardcoded

        script_file.getAsFile().get().text = output
    }
}
