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
public class GenerateTrainingConfigurationTask extends DefaultTask {
    /** The list of files to manipulate */
    @InputFile
    final RegularFileProperty configuration_template = newInputFile()

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
        def vfloorvalues = ""
        project.configuration.user_configuration.models.cmp.streams.each { stream ->
            if (stream.is_msd) {
                nbstream += stream.winfiles.size()
                for (i in 0..(stream.winfiles.size()-1)) {
                    vfloorvalues += " " + stream.vflr
                }
            } else {
                nbstream += 1
                vfloorvalues += " " + stream.vflr
            }
        }

        def binding = [
            VFLOORDUR : project.configuration.user_configuration.models.dur.vflr * 100,
            MAXDEV : project.configuration.user_configuration.settings.training.maxdev,
            MINDUR : project.configuration.user_configuration.settings.training.mindur,
            NBSTREAM : nbstream,
            VFLOORVALUES: vfloorvalues
        ]

        project.copy {
            from configuration_template
            into configuration_file.getParent()

            expand(binding)
        }
    }
}
