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
public class GenerateFullModelsTask extends DefaultTask {

    @InputFile
    final RegularFileProperty mono_list_file = newInputFile()

    @InputFile
    final RegularFileProperty full_list_file = newInputFile()

    @InputFile
    final RegularFileProperty mono_model_cmp_file = newInputFile()

    @InputFile
    final RegularFileProperty mono_model_dur_file = newInputFile()

    @OutputFile
    final RegularFileProperty full_model_cmp_file = newOutputFile()

    @OutputFile
    final RegularFileProperty full_model_dur_file = newOutputFile()

    @OutputFile
    final RegularFileProperty m2f_cmp_script_file = newOutputFile()

    @OutputFile
    final RegularFileProperty m2f_dur_script_file = newOutputFile()

    /**
     *  The actual generateion method
     *
     */
    @TaskAction
    public void generate() {

        // CMP
        def content = "// copy monophone models to fullcontext ones\n"
        content += "CL \"" + full_list_file.getAsFile().get().toString() + "\"\n\n"
        content += "// tie state transition probability\n"
        mono_list_file.getAsFile().get().eachLine { phone ->
            if (phone != "") {
                content += "TI T_$phone {*-$phone+*.transP}\n"
            }
        }
        m2f_cmp_script_file.getAsFile().get().text = content

        project.configurationVoiceBuilding.hts_wrapper.HHEdOnMMF(m2f_cmp_script_file.getAsFile().get().toString(),
                                                                 mono_list_file.getAsFile().get().toString(),
                                                                 mono_model_cmp_file.getAsFile().get().toString(),
                                                                 full_model_cmp_file.getAsFile().get().toString(),
                                                                 [])


        // Duration
        content = "// copy monophone models to fullcontext ones\n"
        content += "CL \"" + full_list_file.getAsFile().get().toString() + "\"\n\n"
        content += "// tie state transition probability\n"
        mono_list_file.getAsFile().get().eachLine { phone ->
            if (phone != "") {
                content += "TI T_$phone {*-$phone+*.transP}\n"
            }
        }
        m2f_dur_script_file.getAsFile().get().text = content


        project.configurationVoiceBuilding.hts_wrapper.HHEdOnMMF(m2f_dur_script_file.getAsFile().get().toString(),
                                                                 mono_list_file.getAsFile().get().toString(),
                                                                 mono_model_dur_file.getAsFile().get().toString(),
                                                                 full_model_dur_file.getAsFile().get().toString(),
                                                                 [])
    }
}
