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

import de.dfki.mary.htsvoicebuilding.HTSWrapper

import de.dfki.mary.htsvoicebuilding.stages.task.dnn.*

import de.dfki.mary.htsvoicebuilding.stages.task.config.GenerateLabSCPTask
import de.dfki.mary.htsvoicebuilding.stages.task.config.GenerateListTask

class DNNStages
{
    public static void addTasks(Project project)
    {
        project.task("generateFeatures", type: GenerateFeatureTask) {
            description "Generate the input linguistic vector features for DNN training"

            // Inputs
            list_file = project.file(project.configuration.user_configuration.data.list_files)
            qconf_file = project.configurationVoiceBuilding.qconf
            aligned_lab_dir = project.generateStateForceAlignment.alignment_dir

            // Outputs
            ffi_dir = project.configurationVoiceBuilding.ffi_dir
        }

        project.task("generateDNNSCP", type: GenerateDNNSCPTask) {
            description "Generate the DNN scp list file"

            // Inputs
            ffo_dir = project.configurationVoiceBuilding.ffo_dir;
            ffi_dir = project.generateFeatures.ffi_dir;
            list_file = project.file(project.configuration.user_configuration.data.list_files)

            // Outputs
            scp_file = project.file("${project.buildDir}/dnn/train_dnn.scp")
        }

        project.task("generateDNNConfig", type: GenerateTrainingConfigFileTask) {
            description "Generate the DNN configuration training file"

            // Inputs
            qconf_file = project.configurationVoiceBuilding.qconf
            template_file = project.file("${project.configurationVoiceBuilding.template_dir}/train_dnn.cfg")

            // Outputs
            configuration_file = project.file("${project.configurationVoiceBuilding.config_dir}/train_dnn.cfg")
        }

        project.task("computeVar", type:ComputeVarTask) {
            description "Generate the global variance for the DNN format"

            // Inputs
            ffo_dir = project.generateDNNSCP.ffo_dir

            // Outputs
            global_var_file = project.file("${project.configurationVoiceBuilding.var_dir}/global.var")
        }

        project.task("trainDNN", type:TrainDNNTask) {
            description "Train the DNN model"

            // Inputs
            scp_file = project.generateDNNSCP.scp_file
            global_var_file = project.computeVar.global_var_file
            configuration_file = project.generateDNNConfig.configuration_file

            // Outputs
            model_dir = project.configurationVoiceBuilding.dnn_dir
        }
    }
}
