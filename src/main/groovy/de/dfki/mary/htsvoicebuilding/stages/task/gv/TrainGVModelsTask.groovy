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
public class TrainGVModelsTask extends DefaultTask {

    def options = []

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
    final RegularFileProperty init_model_file = newInputFile()

    /** The init model file generated for the DUR part */
    @OutputFile
    final RegularFileProperty trained_model_file = newOutputFile()


    /**
     *  The actual generateion method
     *
     */
    @TaskAction
    public void train() {

        project.configurationVoiceBuilding.hts_wrapper.HERestGV(scp_file.getAsFile().get().toString(),
                                                                list_file.getAsFile().get().toString(),
                                                                mlf_file.getAsFile().get().toString(),
                                                                init_model_file.getAsFile().get().toString(),
                                                                trained_model_file.getAsFile().get().getParent(),
                                                                options)
    }
}
