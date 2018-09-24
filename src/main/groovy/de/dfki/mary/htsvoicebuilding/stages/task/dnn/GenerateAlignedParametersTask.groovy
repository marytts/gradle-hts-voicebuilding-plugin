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
public class GenerateAlignedParametersTask extends DefaultTask {


    @InputFile
    final RegularFileProperty configuration_file = newOutputFile()

    @InputFile
    final RegularFileProperty scp_file = newOutputFile()

    @InputFile
    final RegularFileProperty cmp_tiedlist_file = newOutputFile()

    @InputFile
    final RegularFileProperty dur_tiedlist_file = newOutputFile()

    @InputFile
    final RegularFileProperty cmp_model_file = newOutputFile()

    @InputFile
    final RegularFileProperty dur_model_file = newOutputFile()


    @OutputDirectory
    final DirectoryProperty parameters_dir = newOutputDirectory()

    /**
     *  The actual generateion method
     *
     */
    @TaskAction
    public void generate() {

        project.configurationVoiceBuilding.hts_wrapper.HMGenS(
            configuration_file.getAsFile().get().toString(),
            scp_file.getAsFile().get().toString(),
            cmp_tiedlist_file.getAsFile().get().toString(),
            dur_tiedlist_file.getAsFile().get().toString(),
            cmp_model_file.getAsFile().get().toString(),
            dur_model_file.getAsFile().get().toString(),
            0,
            output_dir.getAsFile().get().toString())
    }
}
