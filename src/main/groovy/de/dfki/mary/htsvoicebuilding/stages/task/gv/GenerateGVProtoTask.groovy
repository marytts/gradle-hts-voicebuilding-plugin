package de.dfki.mary.htsvoicebuilding.stages.task.gv

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
public class GenerateGVProtoTask extends DefaultTask {

    @InputFile
    final RegularFileProperty template_file = newInputFile()

    /** The directory containing the spectrum files */
    @OutputFile
    final RegularFileProperty proto_file = newOutputFile()


    /**
     *  The actual generateion method
     *
     */
    @TaskAction
    public void generate() {

        def nb_stream = 0
        def total_vec_size = 0
        def stream_msd_info = ""
        def stream_vec_size = ""

        project.configuration.user_configuration.models.cmp.streams.each { stream ->
            stream_msd_info += " 0"
            stream_vec_size += " " + (stream.order + 1)
            total_vec_size += (stream.order + 1)
            nb_stream += 1
        }

        def binding = [
            project:project,
            GLOBALVECSIZE:total_vec_size,
            NBSTREAM:nb_stream,
            STREAMMSDINFO:stream_msd_info,
            STREAMVECSIZE: stream_vec_size
        ]

        // Now adapt the proto template
        project.copy {
            from template_file.getAsFile().get().getParent()
            into proto_file.getAsFile().get().getParent()

            include template_file.getAsFile().get().getName()
            rename { file -> proto_file.getAsFile().get().getName() }


            expand(binding)
        }
    }
}
