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
public class GeneratePrototypeTask extends DefaultTask {
    /** The prototype of files to manipulate */
    @InputFile
    final RegularFileProperty prototype_template = newInputFile()

    /** The directory containing the spectrum files */
    @OutputFile
    final RegularFileProperty prototype_file = newOutputFile()

    /**
     *  The actual generateion method
     *
     */
    @TaskAction
    public void generate() {


        // Global informations
        def total_nb_states = project.configuration.user_configuration.models.global.nb_emitting_states + 2
        def nb_stream = 0
        def total_vec_size = 0
        def stream_msd_info = ""
        def stream_vec_size = ""
        def sweights = ""
        project.configuration.user_configuration.models.cmp.streams.each { stream ->
            if (stream.is_msd) {
                for (i in 1..stream.winfiles.size()) {
                    stream_msd_info += " 1"
                    stream_vec_size += " 1"
                    sweights += " " + stream.weight
                }
                total_vec_size += (stream.order + 1) * stream.winfiles.size()
                nb_stream += stream.winfiles.size()
            } else {
                stream_msd_info += " 0"
                stream_vec_size += " " + (stream.order + 1) * stream.winfiles.size()
                sweights += " " + stream.weight
                total_vec_size += (stream.order + 1) * stream.winfiles.size()
                nb_stream += 1
            }
        }

        // Now adapt the proto template
        def binding = [
            project:        project,
            SWEIGHTS:       sweights,
            GLOBALVECSIZE:  total_vec_size,
            NBSTREAM:       nb_stream,
            STREAMMSDINFO:  stream_msd_info,
            STREAMVECSIZE:  stream_vec_size,
            NBSTATES:       total_nb_states,
        ]

        // Copy
        project.copy {
            from prototype_template.getAsFile().get()
            into prototype_file.getAsFile().get().getParent()

            expand(binding)
        }
    }
}
