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


import de.dfki.mary.htsvoicebuilding.HTSWrapper;


/**
 *  Definition of the task type to generate spectrum, f0 and aperiodicity using world vocoder
 *
 */
public class TrainDNNTask extends DefaultTask {


    @InputFile
    final RegularFileProperty configuration_file = newInputFile()

    @InputFile
    final RegularFileProperty scp_file = newInputFile()

    @InputDirectory
    final DirectoryProperty var_dir = newInputDirectory()


    @OutputDirectory
    final DirectoryProperty model_dir = newOutputDirectory()

    /**
     *  The actual generateion method
     *
     */
    @TaskAction
    public void generate() {
        def train_script_file = "$project.utils_dir/DNNTraining.py";
        model_dir.getAsFile().get().mkdirs()
        def command = sprintf("python -u -B %s -C %s -S %s -H %s -z %s",
                              train_script_file,
                              configuration_file.getAsFile().get().toString(),
                              scp_file.getAsFile().get().toString(),
                              model_dir.getAsFile().get().toString(),
                              var_dir.getAsFile().get().toString())
        HTSWrapper.executeOnShell(command.toString())
    }
}
