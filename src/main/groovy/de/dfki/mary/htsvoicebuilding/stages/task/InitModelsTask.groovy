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
public class InitModelsTask extends DefaultTask {
    /** The SCP file containing the list of training files */
    @InputFile
    final RegularFileProperty scp_file = newInputFile()

    /** The prototype file used to bootstrap the models */
    @InputFile
    final RegularFileProperty prototype_file = newInputFile();

    /** The list of files to manipulate */
    @InputFile
    final RegularFileProperty vfloor_dur_template_file = newInputFile()

    @InputFile
    final RegularFileProperty average_dur_template_file = newInputFile()

    /** The vfloor file generated for the CMP part */
    @OutputFile
    final RegularFileProperty vfloor_cmp_file = newOutputFile()

    /** The average file generated for the CMP part */
    @OutputFile
    final RegularFileProperty average_cmp_file = newOutputFile()

    /** The init model file generated for the CMP part */
    @OutputFile
    final RegularFileProperty init_cmp_file = newOutputFile()


    /** The vfloor file generated for the DUR part */
    @OutputFile
    final RegularFileProperty vfloor_dur_file = newOutputFile()

    /** The average file generated for the DUR part */
    @OutputFile
    final RegularFileProperty average_dur_file = newOutputFile()

    /** The init model file generated for the DUR part */
    @OutputFile
    final RegularFileProperty init_dur_file = newOutputFile()

    /**
     *  The actual generateion method
     *
     */
    @TaskAction
    public void generate() {
        // CMP parts
        //   1. Get average model
        project.configurationVoiceBuilding.hts_wrapper.HCompV(scp_file.getAsFile().get().toString(),
                                                              prototype_file.getAsFile().get().toString(),
                                                              average_cmp_file.getAsFile().get().getName(),
                                                              average_cmp_file.getAsFile().get().getParent())

        //   2. Get Init model
        def header = prototype_file.getAsFile().get().readLines()[0]
        init_cmp_file.getAsFile().get().text = header + "\n" + vfloor_cmp_file.getAsFile().get().text


        // DUR part
        //    1. vfloor file
        def engine = new groovy.text.SimpleTemplateEngine()
        def vfloor_template = vfloor_dur_template_file.getAsFile().get().text // FIXME: update template path
        def content = ""
        for (i in 1..project.configuration.user_configuration.models.global.nb_emitting_states) {
            def variance = project.configuration.user_configuration.models.dur.vflr
            variance *= project.configuration.user_configuration.models.dur.initvar

            def binding = [
                STATEID:i,
                VARIANCE:variance
            ]
            content += engine.createTemplate(vfloor_template).make(binding)
        }
        vfloor_dur_file.getAsFile().get().text = content

        //   2. average file (TODO: move that into the template and deal properly with the template !)
        content = ""
        for (i in 1..project.configuration.user_configuration.models.global.nb_emitting_states) {
            content += "\t\t<STREAM> $i\n"
            content += "\t\t\t<MEAN> 1\n"
            content += "\t\t\t\t" + project.configuration.user_configuration.models.dur.initmean + "\n"
            content += "\t\t\t<VARIANCE> 1\n"
            content += "\t\t\t\t" + project.configuration.user_configuration.models.dur.initvar + "\n"

        }

        def binding = [
            NBSTATES:project.configuration.user_configuration.models.global.nb_emitting_states,
            STATECONTENT:content,
            NAME:"average.mmf"
        ]

        def average_template = average_dur_template_file.getAsFile().get().text
        average_dur_file.getAsFile().get().text = engine.createTemplate(average_template).make(binding).toString()
    }
}
