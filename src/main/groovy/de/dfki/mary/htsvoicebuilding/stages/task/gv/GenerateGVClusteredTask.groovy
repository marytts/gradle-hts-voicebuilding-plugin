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
public class GenerateGVClusteredTask extends DefaultTask {

    @InputFile
    final RegularFileProperty question_file = newInputFile()

    @InputFile
    final RegularFileProperty script_template_file = newInputFile()

    @InputFile
    final RegularFileProperty fullcontext_model_file = newInputFile()

    @InputFile
    final RegularFileProperty list_file = newInputFile()

    /** The directory containing the spectrum files */
    @OutputFile
    final RegularFileProperty clustered_model_file = newOutputFile()


    /**
     *  The actual generateion method
     *
     */
    @TaskAction
    public void generate() {

        // Initialize by copying the fullcontext to the clustered

        project.copy {
            from fullcontext_model_file.getAsFile().get().getParent()
            into clustered_model_file.getAsFile().get().getParent()
            include fullcontext_model_file.getAsFile().get().getName()

            rename { file -> clustered_model_file.getAsFile().get().getName() }
        }

        // Apply clustering!
        def s = 1
        project.configuration.user_configuration.models.cmp.streams.each { stream ->

            // FIXME: what to do with this stuff ?
            //             make_edfile_state_gv( $type, $s );

            //   2. generate HHEd scripts
            project.copy {

                from script_template_file.getAsFile().get().getParent()
                // FIXME: rethink the "script out files"
                into project.hhed_script_dir
                include 'cxc.hed'
                rename {file -> "cxc_gv_" + stream.name + ".hed"}

                def questions = question_file.getAsFile().get().text
                def streamline = "TB " + stream.gv.thr + " gv_" + stream.name +  "_ {*.state[2].stream[$s]}\n"
                def binding = [
                    GAM : stream.gv.gam,
                    STATSFILE:project.gv_dir + "/stats",
                    QUESTIONS:questions,
                    STREAMLINE:streamline,
                    OUTPUT:project.gv_dir + "/" + stream.name + ".inf"
                ]

                expand(binding)
            }

            //   3. build the decision tree
            def params = []
            if (stream.gv.thr == 0) {
                params += ["-m", "-a", stream.gv.mdlf]
            }

            project.configurationVoiceBuilding.hts_wrapper.HHEdOnMMF(project.hhed_script_dir + "cxc_gv_" + stream.name + ".hed",
                                                                     list_file.getAsFile().get().toString(),
                                                                     clustered_model_file.getAsFile().get().toString(),
                                                                     clustered_model_file.getAsFile().get().toString(),
                                                                     params)
            s += 1
        }
    }
}
