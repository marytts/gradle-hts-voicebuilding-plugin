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
public class TrainModelsTask extends DefaultTask {
    /** The options which need to be passed to HERest */
    @Internal
    def options = []

    /** DAEM switch */
    @Internal
    Boolean use_daem = false;

    /** The SCP file containing the list of training files */
    @InputFile
    final RegularFileProperty scp_file = newInputFile()

    /** The list of files to manipulate */
    @InputFile
    final RegularFileProperty list_file = newInputFile()

    @InputFile
    final RegularFileProperty mlf_file = newInputFile()

    /** The init model file generated for the CMP part */
    @InputFile
    final RegularFileProperty init_cmp_file = newInputFile()

    /** The init model file generated for the CMP part */
    @InputFile
    final RegularFileProperty init_dur_file = newInputFile()

    /** The init model file generated for the DUR part */
    @OutputFile
    final RegularFileProperty trained_cmp_file = newOutputFile()

    /** The init model file generated for the DUR part */
    @OutputFile
    final RegularFileProperty trained_dur_file = newOutputFile()

    /**
     *  The actual generateion method
     *
     */
    @TaskAction
    public void train() {
        if (use_daem) {
            for (i in 1..project.configuration.user_configuration.settings.daem.nIte) {
                for (j in 1..project.configuration.user_configuration.settings.training.nIte) {
                    // FIXME: log
                    def k = j + (i-1) ** project.configuration.user_configuration.settings.training.nIte
                    project.logger.info("\n\nIteration $k of Embedded Re-estimation")

                    k = (i / project.configuration.user_configuration.settings.daem.nIte) ** project.configuration.user_configuration.settings.daem.alpha

                    project.configurationVoiceBuilding.hts_wrapper.HERest(scp_file.getAsFile().get().toString(),
                                                                          list_file.getAsFile().get().toString(),
                                                                          mlf_file.getAsFile().get().toString(),
                                                                          init_cmp_file.getAsFile().get().toString(),
                                                                          init_dur_file.getAsFile().get().toString(),
                                                                          trained_cmp_file.getAsFile().get().getParent(),
                                                                          trained_dur_file.getAsFile().get().getParent(),
                                                                          ["-k", k])
                }
            }
        } else {
            for (i in 1..project.configuration.user_configuration.settings.training.nIte) {
                // FIXME: log
                project.logger.info("\n\nIteration $i of Embedded Re-estimation")

                project.configurationVoiceBuilding.hts_wrapper.HERest(scp_file.getAsFile().get().toString(),
                                                                      list_file.getAsFile().get().toString(),
                                                                      mlf_file.getAsFile().get().toString(),
                                                                      init_cmp_file.getAsFile().get().toString(),
                                                                      init_dur_file.getAsFile().get().toString(),
                                                                      trained_cmp_file.getAsFile().get().getParent(),
                                                                      trained_dur_file.getAsFile().get().getParent(),
                                                                      options)
            }
        }
    }
}
