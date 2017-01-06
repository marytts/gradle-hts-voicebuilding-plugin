package de.dfki.mary.htsvoicebuilding.stages

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.MavenPlugin
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.bundling.Zip

import static groovyx.gpars.GParsPool.runForkJoin
import static groovyx.gpars.GParsPool.withPool

import de.dfki.mary.htsvoicebuilding.HTSWrapper
import de.dfki.mary.htsvoicebuilding.DataFileFinder
import de.dfki.mary.utils.StandardTask

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.xml.*

class DNNStages
{
    public static void addTasks(Project project)
    {
        def dnn_output_dir = "$project.buildDir/DNN/"

        project.task("makeFeatures", type:StandardTask) //, dependsOn: "generateStateForceAlignment")
        {
            def mkf_script_file = "$project.utils_dir/makefeature.pl";
            output = "$dnn_output_dir/ffi"

            def qconf = (new File(DataFileFinder.getFilePath(project.user_configuration.settings.dnn.qconf)));
            def val = 1E+4 * project.user_configuration.signal.frameshift; // FIXME: why this frameshift

            outputs.files output
            doLast {
                withPool(project.nb_proc)
                {
                    def file_list = (new File(DataFileFinder.getFilePath(project.user_configuration.data.list_files))).readLines() as List
                    file_list.eachParallel { cur_file ->
                        String command = "perl $mkf_script_file $qconf $val ${project.tasks.generateStateForceAlignment.output}/${cur_file}.lab | x2x +af > $output/${cur_file}.ffi".toString()
                        HTSWrapper.executeOnShell(command)
                    }
                }
            }
        }

        project.task("makeDataSCP", type:StandardTask, dependsOn: "makeFeatures")
        {
            output = "$dnn_output_dir/train_dnn.scp"
            doLast {
                def ffo_dir = "" // TODO: link
                (new File(DataFileFinder.getFilePath(project.user_configuration.data.list_files))).eachLine { cur_file ->
                    output << "${project.tasks.makeFeatures.output}/${cur_file}.ffi $ffo_dir/${cur_file}.ffo" + System.getProperty("line.separator")
                }
            }

        }

        project.task("generateDNNConfig", type:StandardTask)
        {
            output = "$project.config_dir/train_dnn.config"
            doLast {

                def vec_size = 0
                project.user_configuration.models.ffo.streams.each { stream ->
                    vec_size += stream.order + 1
                }
                def dnn_settings = project.user_configuration.settings.dnn

                // Now adapt the proto template
                def binding = [
                num_input_units: 694, // FIXME: fix according to the number of questions
                num_hidden_units: dnn_settings.num_hidden_units,
                num_output_units: vec_size,

                hidden_activation: dnn_settings.hidden_activation,
                output_activation: "Linear",
                optimizer: dnn_settings.optimizer,
                learning_rate: dnn_settings.learning_rate,
                keep_prob: dnn_settings.keep_prob,

                use_queue: dnn_settings.usequeue,
                queue_size: dnn_settings.queue_size,

                batch_size: dnn_settings.batch_size,
                num_epochs: dnn_settings.num_epochs,
                num_threads: dnn_settings.num_threads,
                random_seed: dnn_settings.random_seed,

                num_models_to_keep: dnn_settings.num_models_to_keep,

                log_interval: dnn_settings.log_interval,
                save_interval: dnn_settings.save_interval
                ]

                // Copy
                project.copy {
                    from project.template_dir
                    into project.config_dir

                    include "train_dnn.cfg"

                    expand(binding)
                }
            }
        }

        project.task("computeVAR", type:StandardTask, dependsOn: "prepareEnvironment")
        {
            dependsOn "prepareEnvironment"
            def output = new File("$dnn_output_dir/var")
        }

        project.task("trainDNN", type:StandardTask)
        {
            dependsOn "makeDataSCP", "generateDNNConfig", "computeVAR"

            def train_script_file = "$project.utils_dir/DNNTraining.py";
            inputs.files train_script_file

            def output = new File("$dnn_output_dir/models")
            outputs.files output

            doLast {
                output.mkdirs()
                String command = "python $train_script_file -C ${project.tasks.generateDNNConfig.output} -s ${project.tasks.makeDataSCP.output} -H output -z ${project.tasks.computeVAR.output}".toString()
                HTSWrapper.executeOnShell(command)
            }
        }
    }
}
