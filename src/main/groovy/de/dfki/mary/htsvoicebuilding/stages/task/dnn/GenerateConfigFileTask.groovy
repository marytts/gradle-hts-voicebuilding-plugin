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
public class GenerateConfigFileTask extends DefaultTask {

    @InputFile
    final RegularFileProperty template_file = newInputFile()

    /** The directory containing the spectrum files */
    @OutputFile
    final RegularFileProperty configuration_file = newOutputFile()


    /**
     *  The actual generateion method
     *
     */
    @TaskAction
    public void generate() {
        // train.cfg
        def nbstream = 0
        def cmpstream = []
        def pdfstrkind = []
        def pdfstrorder = []
        def pdfstrwin = []

        project.configuration.user_configuration.models.cmp.streams.each { stream ->
            pdfstrkind << stream.kind
            pdfstrorder << (stream.order + 1).toString()

            pdfstrwin << "StrVec"
            pdfstrwin << stream.winfiles.size().toString()
            stream.winfiles.each {
                pdfstrwin << it
            }

            if (stream.is_msd) {
                cmpstream << stream.winfiles.size().toString()

            } else {
                cmpstream << "1"
            }
            nbstream += 1
        }


        def binding = [
            NB_STREAMS: nbstream,
            MAXEMITER: 20, // FIXME: hardcoded
            CMP_STREAM: cmpstream.join(" "),
            VEC_SIZE: pdfstrorder.join(" "),
            EXT_LIST: pdfstrkind.join(" "),
            WIN_LIST: pdfstrwin.join(" ")

        ]

        project.copy {
            from template_file.getAsFile().get()
            into configuration_file.getAsFile().get().getParent()

            rename { file -> configuration_file.getAsFile().get().getName() }
            expand(binding)
        }
    }
}
