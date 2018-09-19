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


import de.dfki.mary.htsvoicebuilding.HTSWrapper;

/**
 *  Definition of the task type to generate spectrum, f0 and aperiodicity using world vocoder
 *
 */
public class ClusteringDURTask extends DefaultTask {

    /** The list of labels file */
    @InputFile
    final RegularFileProperty list_file = newInputFile();

    @InputFile
    final RegularFileProperty config_file = newInputFile();

    @InputFile
    final RegularFileProperty question_file = newInputFile();

    @InputFile
    final RegularFileProperty script_template_file = newInputFile();

    @InputFile
    final RegularFileProperty fullcontext_model_file = newInputFile();

    @InputFile
    final RegularFileProperty stats_cmp_file = newInputFile();

    @OutputFile
    final RegularFileProperty stats_dur_file = newOutputFile();

    @OutputFile
    final RegularFileProperty script_file = newOutputFile();

    @OutputFile
    final RegularFileProperty tree_file = newOutputFile();

    @OutputFile
    final RegularFileProperty clustered_model_file = newOutputFile();


    /**
     *  The actual initialization method
     *
     */
    @TaskAction
    public void cluster() {

        def content = ""
        stats_cmp_file.getAsFile().get().eachLine { line ->
            def array = line.split()
            content += sprintf("%4d %14s %4d %4d\n",
                               Integer.parseInt(array[0]), array[1],
                               Integer.parseInt(array[2]), Integer.parseInt(array[2]))
        }
        stats_dur_file.getAsFile().get().text = content

        //   2. generate HHEd scripts
        project.copy {

            from script_template_file.getAsFile().get().getParent()
            into script_file.getAsFile().get().getParent()

            include script_template_file.getAsFile().get().getName()

            rename { file -> script_file.getAsFile().get().getName() }

            def questions = question_file.getAsFile().get().text
            def streamline = "TB " + project.configuration.user_configuration.models.dur.thr + " dur_s2_ {*.state[2].stream[1-5]}"
            def binding = [
                GAM: sprintf("%03d", project.configuration.user_configuration.models.dur.gam),
                STATSFILE: stats_dur_file.getAsFile().get().toString(),
                QUESTIONS: questions,
                STREAMLINE: streamline,
                OUTPUT: tree_file.getAsFile().get().toString()
            ]

            expand(binding)
        }

        //   3. build the decision tree
        def params = ["-C", config_file.getAsFile().get().toString()]
        if (project.configuration.user_configuration.models.dur.thr == 0) {
            params += ["-m", "-a", project.configuration.user_configuration.models.dur.mdlf]
        }

        project.configurationVoiceBuilding.hts_wrapper.HHEdOnMMF(script_file.getAsFile().get().toString(),
                                                                 list_file.getAsFile().get().toString(),
                                                                 fullcontext_model_file.getAsFile().get().toString(),
                                                                 clustered_model_file.getAsFile().get().toString(),
                                                                 params)
    }
}
