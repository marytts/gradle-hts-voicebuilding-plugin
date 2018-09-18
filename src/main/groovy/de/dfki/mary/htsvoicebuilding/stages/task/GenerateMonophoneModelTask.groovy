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
public class GenerateMonophoneModelTask extends DefaultTask {

    @InputFile
    final RegularFileProperty script_template_cmp_file = newInputFile()

    @InputFile
    final RegularFileProperty list_file = newInputFile()

    @InputFile
    final RegularFileProperty vfloor_cmp_file = newInputFile()

    @InputFile
    final RegularFileProperty vfloor_dur_file = newInputFile()

    @InputDirectory
    final DirectoryProperty cmp_hrest_dir  = newInputDirectory()

    @InputDirectory
    final DirectoryProperty dur_hrest_dir  = newInputDirectory()

    @OutputFile
    final RegularFileProperty script_cmp_file = newOutputFile()

    @OutputFile
    final RegularFileProperty cmp_mmf_file = newOutputFile()

    @OutputFile
    final RegularFileProperty script_dur_file = newOutputFile()

    @OutputFile
    final RegularFileProperty dur_mmf_file = newOutputFile()

    /**
     *  The actual generateion method
     *
     */
    @TaskAction
    public void generate() {

        // CMP model conversion
        //  1. Generate HHEd script
        project.copy {
            from script_template_cmp_file.getAsFile().get().getParent()
            into script_cmp_file.getAsFile().get().getParent()

            include script_template_cmp_file.getAsFile().get().getName()
            rename { file -> script_cmp_file.getAsFile().get().getName()}

            def binding = [
                STARTSTATE:2,
                ENDSTATE:project.configuration.user_configuration.models.global.nb_emitting_states+1,
                VFLOORFILE: vfloor_cmp_file.getAsFile().get().toString(),
                NB_STREAMS: project.configuration.user_configuration.models.cmp.streams.size()
            ]

            expand(binding)
        }

        //  2. Model conversion
        project.configurationVoiceBuilding.hts_wrapper.HHEdOnDir(script_cmp_file.getAsFile().get().toString(),
                                                                 list_file.getAsFile().get().toString(),
                                                                 cmp_hrest_dir.getAsFile().get().toString(),
                                                                 cmp_mmf_file.getAsFile().get().toString())


        // Duration model conversion
        //  1. Generate HHEd script
        def content = "// Load variance flooring macro\n"
        content += "FV \"${vfloor_dur_file.getAsFile().get().toString()}\""
        script_dur_file.getAsFile().get().text = content


        //  2. Model conversion
        project.configurationVoiceBuilding.hts_wrapper.HHEdOnDir(script_dur_file.getAsFile().get().toString(),
                                                                 list_file.getAsFile().get().toString(),
                                                                 dur_hrest_dir.getAsFile().get().toString(),
                                                                 dur_mmf_file.getAsFile().get().toString())


    }
}
