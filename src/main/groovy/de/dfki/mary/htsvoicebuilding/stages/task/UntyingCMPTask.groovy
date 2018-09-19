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
public class UntyingCMPTask extends DefaultTask {

    @InputFile
    final RegularFileProperty list_file = newInputFile()

    @InputFile
    final RegularFileProperty input_model_file = newInputFile()

    @OutputFile
    final RegularFileProperty output_model_file = newOutputFile()

    @OutputFile
    final RegularFileProperty untying_script_file = newOutputFile()


    /**
     *  The actual generateion method
     *
     */
    @TaskAction
    public void generate() {

        def cmp_untying_file = new File(project.hhed_script_dir + "/untying_cmp.hhed")

        //  1. Generate hhed script file
        def cur_stream = 1
        def script_content = "// untie parameter sharing structure\n"

        project.configuration.user_configuration.models.cmp.streams.each { stream ->

            def end_stream = cur_stream
            if (stream.is_msd) {
                end_stream += stream.winfiles.size() - 1
            }

            if (project.configuration.user_configuration.models.cmp.streams.size() > 1) {
                for (i in 2..project.configuration.user_configuration.models.global.nb_emitting_states+1) {
                    script_content += "UT {*.state[$i].stream[$cur_stream-$end_stream]}\n"
                }
            }  else {
                for (i in 2..project.configuration.user_configuration.models.global.nb_emitting_states+1) {
                    script_content += "UT {*.state[$i]\n}"
                }

            }
            cur_stream = end_stream + 1

        }
        untying_script_file.getAsFile().get().text = script_content

        //  2. untying
        project.configurationVoiceBuilding.hts_wrapper.HHEdOnMMF(untying_script_file.getAsFile().get().toString(),
                                                                 list_file.getAsFile().get().toString(),
                                                                 input_model_file.getAsFile().get().toString(),
                                                                 output_model_file.getAsFile().get().toString(),
                                                                 [])
    }
}
