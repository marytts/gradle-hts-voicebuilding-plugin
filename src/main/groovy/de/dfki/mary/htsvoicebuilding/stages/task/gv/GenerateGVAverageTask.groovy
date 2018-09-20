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
public class GenerateGVAverageTask extends DefaultTask {

    @InputFile
    final RegularFileProperty scp_file = newInputFile()

    @InputFile
    final RegularFileProperty proto_file = newInputFile()

    /** The directory containing the spectrum files */
    @OutputFile
    final RegularFileProperty average_file = newOutputFile()

    @OutputFile
    final RegularFileProperty vfloor_file = newOutputFile()

    /**
     *  The actual generateion method
     *
     */
    @TaskAction
    public void generate() {
        project.configurationVoiceBuilding.hts_wrapper.HCompV(scp_file.getAsFile().get().toString(),
                                                              proto_file.getAsFile().get().toString(),
                                                              average_file.getAsFile().get().toString(),
                                                              vfloor_file.getAsFile().get().getParent())
    }
}
