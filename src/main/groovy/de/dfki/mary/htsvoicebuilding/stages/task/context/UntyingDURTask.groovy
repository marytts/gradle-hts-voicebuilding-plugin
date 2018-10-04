package de.dfki.mary.htsvoicebuilding.stages.task.context

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
public class UntyingDURTask extends DefaultTask {

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

        //  1. Generate hhed script file
        def script_content = "// untie parameter sharing structure\n"
        script_content += "UT {*.state[2]}\n"
        untying_script_file.getAsFile().get().text = script_content


        //  2. untying
        project.configurationVoiceBuilding.hts_wrapper.HHEdOnMMF(untying_script_file.getAsFile().get().toString(),
                                                                 list_file.getAsFile().get().toString(),
                                                                 input_model_file.getAsFile().get().toString(),
                                                                 output_model_file.getAsFile().get().toString(),
                                                                 [])
    }
}
