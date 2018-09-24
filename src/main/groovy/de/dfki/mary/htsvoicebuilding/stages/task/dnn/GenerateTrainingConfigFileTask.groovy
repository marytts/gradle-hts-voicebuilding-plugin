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
public class GenerateTrainingConfigFileTask extends DefaultTask {

    @InputFile
    final RegularFileProperty qconf_file = newInputFile()

    @InputFile
    final RegularFileProperty template_file = newInputFile()

    /** The directory containing the spectrum files */
    @OutputFile
    final RegularFileProperty configuration_file = newOutputFile()


    /**
     *  The actual generateion method
     *
     */
    @TaskAction
    public void generate() {

        def nb_input_features = 0
        qconf_file.getAsFile().get().eachLine { line ->
            if (line =~ /^[^#].*$/) { //All empty lines & lines starting by # should be ignored
                nb_input_features += 1
            }
        }
        def vec_size = 0
        project.configuration.user_configuration.models.ffo.streams.each { stream ->
            vec_size += (stream.order + 1) * stream.winfiles.size()
        }
        def dnn_settings = project.configuration.user_configuration.settings.dnn

        // Now adapt the proto template
        def binding = [
            num_input_units: nb_input_features,
            num_hidden_units: dnn_settings.num_hidden_units,
            num_output_units: vec_size,

            hidden_activation: dnn_settings.hidden_activation,
            output_activation: "Linear",
            optimizer: dnn_settings.optimizer,
            learning_rate: dnn_settings.learning_rate,
            keep_prob: dnn_settings.keep_prob,

            use_queue: dnn_settings.use_queue,
            queue_size: dnn_settings.queue_size,

            batch_size: dnn_settings.batch_size,
            num_epochs: dnn_settings.num_epochs,
            num_threads: dnn_settings.num_threads,
            random_seed: dnn_settings.random_seed,

            num_models_to_keep: dnn_settings.num_models_to_keep,

            log_interval: dnn_settings.log_interval,
            save_interval: dnn_settings.save_interval
        ]

        project.copy {
            from template_file.getAsFile().get()
            into configuration_file.getAsFile().get().getParent()

            rename { file -> configuration_file.getAsFile().get().getName() }
            expand(binding)
        }
    }
}
