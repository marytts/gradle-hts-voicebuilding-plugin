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

import de.dfki.mary.htsvoicebuilding.HTSWrapper;

/**
 *  Definition of the task type to generate spectrum, f0 and aperiodicity using world vocoder
 *
 */
public class ComputeVarTask extends DefaultTask {
    @InputDirectory
    final DirectoryProperty ffo_dir = newInputDirectory()

    /** The directory containing the spectrum files */
    @OutputDirectory
    final DirectoryProperty var_dir = newOutputDirectory()


    /**
     *  The actual generateion method
     *
     */
    @TaskAction
    public void generate() {
        // Get dimension
        def ffodim = 0
        for (def stream: project.configuration.user_configuration.models.ffo.streams) {
            ffodim += (stream.order + 1) * stream.winfiles.size()
        }

        // Compute global variance
        def command_global_var = "cat ${ffo_dir.getAsFile().get()}/*.ffo | vstat -l $ffodim -d -o 2 > ${var_dir.getAsFile().get()}/global.var"
        HTSWrapper.executeOnShell(command_global_var)


        // Extract global variance per stream
        def start = 0
        for (def stream: project.configuration.user_configuration.models.ffo.streams) {
            def dim = (stream.order + 1) * stream.winfiles.size()
            if (stream.stats)  {
                def command_stream_var = "bcut +f -s $start -e ${start+dim-1} -l 1 ${var_dir.getAsFile().get()}/global.var > ${var_dir.getAsFile().get()}/${stream.kind}.var"
                HTSWrapper.executeOnShell(command_stream_var)
            }
            start += dim
        }
    }
}
