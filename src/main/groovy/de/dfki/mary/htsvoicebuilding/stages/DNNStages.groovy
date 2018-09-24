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
import de.dfki.mary.utils.StandardTask
import de.dfki.mary.utils.StandardFileTask

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.xml.*

import de.dfki.mary.htsvoicebuilding.stages.task.dnn.*

import de.dfki.mary.htsvoicebuilding.stages.task.GenerateLabSCPTask
import de.dfki.mary.htsvoicebuilding.stages.task.GenerateListTask

class DNNStages
{
    public static void addTasks(Project project)
    {
        project.task("generateSynthConfigFile", type: GenerateConfigFileTask)
        {
            template_file = new File("${project.template_file}/synth.cfg")
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
            lab_dir = new File("${project.buildDir}/alignment/")
        }

        project.task("makeFeatures", type:StandardTask) {
            def alignment_lab = null
            if (System.getProperty("skipHMMTraining")) {
                dependsOn "convertDurToLab"
                alignment_lab = project.tasks.convertDurToLab.output
            } else {
                dependsOn "generateStateForceAlignment"
                alignment_lab = project.tasks.generateStateForceAlignment.alignment_dir
            }

            def mkf_script_file = "$project.utils_dir/makefeature.pl";
            output = "$dnn_output_dir/ffi"

            def val = 1E+4 * project.configuration.user_configuration.signal.frameshift;

            outputs.files output
            doLast {
                def qconf = (new File(project.configuration.user_configuration.settings.dnn.qconf));
                withPool(project.configuration.nb_proc)
                {
                    def file_list = (new File(project.configuration.user_configuration.data.list_files)).readLines() as List
                    file_list.eachParallel { cur_file ->
                        String command = "perl $mkf_script_file $qconf $val ${alignment_lab}/${cur_file}.lab | x2x +af > $output/${cur_file}.ffi".toString()
                        HTSWrapper.executeOnShell(command)
                    }
                }
            }
        }

        project.task("makeDNNSCP", type:StandardTask, dependsOn: "makeFeatures") {
            output = "$dnn_output_dir"
            doLast {
                def output_file = new File("$output/train_dnn.scp")
                output_file.text = ""
                def ffo_dir = project.buildDir.toString() + "/ffo" // TODO: properly dealing with that
                (new File(project.configuration.user_configuration.data.list_files)).eachLine { cur_file ->
                    output_file << "${project.tasks.makeFeatures.output}/${cur_file}.ffi $ffo_dir/${cur_file}.ffo" + System.getProperty("line.separator")
                }
            }

        }

        project.task("generateDNNConfig", type: GenerateTrainingConfigFileTask) {
            qconf_file = new File(project.configuration.user_configuration.settings.dnn.qconf)
            template_file = new File ("$project.template_dir/train_dnn.cfg")
            configuration_file = new File ("$project.config_dir/train_dnn.cfg")
        }

        project.task("computeVAR", type:StandardTask) {
            dependsOn "configurationVoiceBuilding"
            output = new File("$dnn_output_dir/var")

            doLast {
                def ffodim = 0
                project.configuration.user_configuration.models.ffo.streams.each { stream ->
                    ffodim += (stream.order + 1) * stream.winfiles.size()
                }
                def command_global_var = "cat $project.buildDir/ffo/*.ffo | vstat -l $ffodim -d -o 2 > $output/global.var"
                HTSWrapper.executeOnShell(command_global_var)


                def start = 0
                project.configuration.user_configuration.models.ffo.streams.each { stream ->
                    def dim = (stream.order + 1) * stream.winfiles.size()
                    if (stream.stats)
                        {
                        def command_stream_var = "bcut +f -s $start -e ${start+dim-1} -l 1 $output/global.var > $output/${stream.kind}.var"
                        HTSWrapper.executeOnShell(command_stream_var)
                    }
                    start += dim
                }
            }
        }

        project.task("trainDNN", type:TrainDNNTask) {
            scp_file = project.generateDNNSCP.scp_file
            var_file = project.computeVAR.var_file
            configuration_file = project.generateDNNConfig.configuration_file

            model_dir = new File("$dnn_output_dir/models")
        }
    }
}
