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
public class GenerateStateForceAlignmentTask extends DefaultTask {

    @InputFile
    final RegularFileProperty scp_file = newInputFile()

    @InputFile
    final RegularFileProperty list_file = newInputFile()

    @InputFile
    final RegularFileProperty mlf_file = newInputFile()

    @InputFile
    final RegularFileProperty model_cmp_file = newInputFile()

    @InputFile
    final RegularFileProperty model_dur_file = newInputFile()

    /** The directory containing the spectrum files */
    @OutputDirectory
    final DirectoryProperty alignment_dir = newOutputDirectory()


    /**
     *  The actual generateion method
     *
     */
    @TaskAction
    public void generate() {

        project.configurationVoiceBuilding.hts_wrapper.HSMMAlign(scp_file.getAsFile().get().toString(),
                                                                 list_file.getAsFile().get().toString(),
                                                                 mlf_file.getAsFile().get().toString(),
                                                                 model_cmp_file.getAsFile().get().toString(),
                                                                 model_dur_file.getAsFile().get().toString(),
                                                                 alignment_dir.getAsFile().get().toString(),
                                                                 true)
    }
}
