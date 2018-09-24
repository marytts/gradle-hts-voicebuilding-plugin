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
public class TrainDNNTask extends DefaultTask {


    @InputFile
    final RegularFileProperty configuration_file = newOutputFile()

    @InputFile
    final RegularFileProperty scp_file = newOutputFile()

    @InputFile
    final RegularFileProperty var_file = newOutputFile()


    @OutputDirectory
    final DirectoryProperty model_dir = newOutputDirectory()

    /**
     *  The actual generateion method
     *
     */
    @TaskAction
    public void generate() {
        model_dir.mkdirs()
        // script_file, configuration_file, scp_file, model_dir, var_file
        def command = sprintf("python -u -B %s -C %s -S %s -H %s -z %s",
                              train_script_file,
                              configuration_file.getAsFile().get().toString(),
                              scp_file.getAsFile().get().toString(),
                              model_dir.getAsFile().get().toString(),
                              var_file.getAsFile().get().toString())
        HTSWrapper.executeOnShell(command.toString())
    }
}
