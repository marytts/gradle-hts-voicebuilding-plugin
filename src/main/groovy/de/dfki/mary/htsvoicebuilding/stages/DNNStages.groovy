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
        project.task("generateSynthConfigFile", type: GenerateConfigFileTask)
        {
            template_file = new File("${project.template_dir}/synth.cfg")
            configuration_file = new File("${project.config_dir}/synth.cfg")
        }

        project.task("generateImposedSCP", type: GenerateLabSCPTask) {
            list_basenames = new File(project.configuration.user_configuration.data.list_files)
            lab_dir = new File(project.configuration.user_configuration.data.full_lab_dir)
            scp_file = new File(project.train_dnn_scp)
        }

        project.task('generateFullAllList', type: GenerateListTask) {
            list_basenames = new File(project.configuration.user_configuration.data.list_files)
            lab_dir = new File(project.configuration.user_configuration.data.full_lab_dir)
            list_file = new File("${project.list_dir}/list_all")
        }

        project.task("generateCMPTreeConversionScript", type: GenerateCMPTreeConversionScriptTask) {
            list_file = project.generateFullAllList.list_file
            script_file = new File("${project.hhed_script_dir}/cmp_conv.hed")
        }

        project.task("generateDURTreeConversionScript", type: GenerateDURTreeConversionScriptTask) {
            list_file = project.generateFullAllList.list_file
            script_file = new File("${project.hhed_script_dir}/dur_conv.hed")
        }

        project.task("treeConversion", type: TreeConversionTask) {
            // List of labels
            list_file = project.generateFullAllList.list_file

            // Script files
            dur_script_file = project.generateDURTreeConversionScript.script_file
            cmp_script_file = project.generateCMPTreeConversionScript.script_file

            // Input mode files
            input_dur_model_file = new File("${project.global_model_dir}/dur/clustered.mmf.${project.configuration.user_configuration.settings.training.nb_clustering-1}")
            input_cmp_model_file = new File("${project.global_model_dir}/cmp/clustered.mmf.${project.configuration.user_configuration.settings.training.nb_clustering-1}")

            // Output model files
            output_dur_model_file = new File("${project.global_model_dir}/dur/clustered_all.mmf.${project.configuration.user_configuration.settings.training.nb_clustering-1}")
            output_cmp_model_file = new File("${project.global_model_dir}/cmp/clustered_all.mmf.${project.configuration.user_configuration.settings.training.nb_clustering-1}")
        }

        project.task("paramGeneration", type: GenerateAlignedParametersTask) {
            configuration_file = project.generateSynthConfigFile.configuration_file
            scp_file = project.generateImposedSCP.scp_file

            cmp_tiedlist_file = new File("${project.list_dir}/tiedlist_cmp") // FIXME: more linking
            dur_tiedlist_file = new File("${project.list_dir}/tiedlist_dur") // FIXME: more linking

            cmp_model_file = project.treeConversion.output_cmp_model_file
            dur_model_file = project.treeConversion.output_dur_model_file
            parameters_dir = new File("${project.buildDir}/gen_align")
        }

        project.task("convertDurToLab", type: ConvertDurToLabTask) {
            list_file = new File(project.configuration.user_configuration.data.list_files)
            dur_dir = project.paramGeneration.parameters_dir
            lab_dir = new File(project.alignment_dir)
        }

        project.task("generateFeatures", type: GenerateFeatureTask) {
            list_file = new File(project.configuration.user_configuration.data.list_files)

            qconf_file = project.configurationVoiceBuilding.qconf

            if (System.getProperty("skipHMMTraining")) {
                aligned_lab_dir = project.tasks.convertDurToLab.lab_dir
            } else {
                aligned_lab_dir = project.tasks.generateStateForceAlignment.alignment_dir
            }


            ffi_dir = new File(project.ffi_dir);
        }

        project.task("generateDNNSCP", type: GenerateDNNSCPTask) {
            ffo_dir = new File(project.ffo_dir);
            ffi_dir = project.generateFeatures.ffi_dir;
            scp_file = new File("${project.buildDir}/dnn/train_dnn.scp")
            list_file = new File(project.configuration.user_configuration.data.list_files)
        }

        project.task("generateDNNConfig", type: GenerateTrainingConfigFileTask) {
            qconf_file = project.configurationVoiceBuilding.qconf
            template_file = new File ("$project.template_dir/train_dnn.cfg")
            configuration_file = new File ("$project.config_dir/train_dnn.cfg")
        }

        project.task("computeVar", type:ComputeVarTask) {
            ffo_dir = project.generateDNNSCP.ffo_dir
            global_var_file = new File(project.var_dir + "/global.var")
        }

        project.task("trainDNN", type:TrainDNNTask) {
            scp_file = project.generateDNNSCP.scp_file
            global_var_file = project.computeVar.global_var_file
            configuration_file = project.generateDNNConfig.configuration_file

            model_dir = new File(project.dnn_dir)
        }
    }
}
