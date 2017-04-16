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
import de.dfki.mary.utils.StandardFileTask

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.xml.*

class DNNStages
{
    public static void addTasks(Project project)
    {
        def dnn_output_dir = "$project.buildDir/DNN/"


        project.task("generateSynthConfigFile", type:StandardFileTask)
        {
            dependsOn "prepareEnvironment"
            inputs.files project.config_dir
            output = new File("${project.config_dir}/synth.cfg")
            outputs.files output

            doLast {
                // train.cfg
                def nbstream = 0
                def cmpstream = []
                def pdfstrkind = []
                def pdfstrorder = []
                def pdfstrwin = []
                project.configuration.user_configuration.models.cmp.streams.each { stream ->
                    pdfstrkind << stream.kind
                    pdfstrorder << (stream.order + 1).toString()

                    pdfstrwin << "StrVec"
                    pdfstrwin << stream.winfiles.size().toString()
                    stream.winfiles.each {
                        pdfstrwin << DataFileFinder.getFilePath(it)
                    }

                    if (stream.is_msd) {
                        cmpstream << stream.winfiles.size().toString()

                    } else {
                        cmpstream << "1"
                    }
                    nbstream += 1
                }


                def binding = [
                    NB_STREAMS: nbstream,
                               MAXEMITER: 20, // FIXME: hardcoded
                               CMP_STREAM: cmpstream.join(" "),
                               VEC_SIZE: pdfstrorder.join(" "),
                               EXT_LIST: pdfstrkind.join(" "),
                               WIN_LIST: pdfstrwin.join(" ")

                ]

                project.copy {
                    from project.template_dir
                    into project.config_dir

                    include "synth.cfg"
                    rename { file -> output.name }
                    expand(binding)
                }
            }
        }

        project.task("generateImposedSCP", type:StandardFileTask)
        {
            output = new File(project.train_dnn_scp)
            inputs.files project.configuration.user_configuration.data.list_files
            outputs.files output

            doLast {
                output.text = "" // To be sure we do not append...
                (new File(DataFileFinder.getFilePath(project.configuration.user_configuration.data.list_files))).eachLine{ cur_file ->
                    output << (new File(DataFileFinder.getFilePath(project.configuration.user_configuration.data.full_lab_dir + "/" + cur_file + ".lab")))
                    output << "\n"
                }
            }
        }

        project.task('generateFullAllList', dependsOn:'generateImposedSCP', type:StandardFileTask)
        {
            output = new File("${project.list_dir}/list_all")
            outputs.files output

            doLast {

                // 2. From known full_lab_dir and train scp infos
                def model_set = new HashSet()
                (project.tasks.generateImposedSCP.output).eachLine{ cur_file ->
                    (new File(cur_file)).eachLine { line ->
                        def line_arr = line =~ /^[ \t]*([0-9]+)[ \t]+([0-9]+)[ \t]+(.+)/
                        model_set.add(line_arr[0][3])
                    }
                }
                output.text = model_set.join("\n")
            }
        }


        project.task("generateCMPTreeConversionScript", type: StandardFileTask)
        {
            dependsOn "prepareEnvironment", "generateFullAllList"
            output = new File("${project.hhed_script_dir}/cmp_conv.hed")
            outputs.files output
            doLast {
                output.text = ""
                output << "TR 2\n"

                project.configuration.user_configuration.models.cmp.streams.each { stream ->
                    output << "LT \"$project.tree_dir/${stream.kind}.1.inf\"\n"
                }

                output << "AU \"${project.tasks.generateFullAllList.output}\"\n"
                output << "CO \"${project.list_dir}/tiedlist_cmp\"\n"
            }
        }

        project.task("generateDURTreeConversionScript", type: StandardFileTask)
        {
            dependsOn "prepareEnvironment", "generateFullAllList"
            output = new File("${project.hhed_script_dir}/dur_conv.hed")
            outputs.files output
            doLast {
                output.text = ""
                output << "TR 2\n"

                output << "LT \"${project.tree_dir}/dur.1.inf\"\n"
                output << "AU \"${project.tasks.generateFullAllList.output}\"\n"
                output << "CO \"${project.list_dir}/tiedlist_dur\"\n"
            }
        }

        project.task("treeConversion")
        {
            dependsOn "generateCMPTreeConversionScript", "generateDURTreeConversionScript"
            ["cmp", "dur"].each { kind ->
                outputs.files "${project.global_model_dir}/$kind/clustered_all.mmf.1"
            }

            doLast
            {
                withPool(project.configuration.nb_proc) {
                    ["cmp", "dur"].eachParallel { kind ->

                        project.configuration.hts_wrapper.HHEdOnMMF(
                            project.hhed_script_dir + "/${kind}_conv.hed",
                            project.full_list_filename,
                            "${project.global_model_dir}/$kind/clustered.mmf.1",
                            "${project.global_model_dir}/$kind/clustered_all.mmf.1",
                            [])
                    }
                }
            }
        }


        project.task("paramGeneration", type:StandardTask)
        {
            dependsOn "treeConversion"
            output = "${project.buildDir}/gen_align"
            outputs.files output

            doLast {
                // FIXME
                def scp
                project.configuration.hts_wrapper.HMGenS(
                    project.tasks.generateSynthConfigFile.output,
                    project.tasks.generateImposedSCP.output,
                    "${project.list_dir}/tiedlist_cmp",
                    "${project.list_dir}/tiedlist_dur",
                    "${project.global_model_dir}/cmp/clustered_all.mmf.1",
                    "${project.global_model_dir}/dur/clustered_all.mmf.1",
                    0, output
                )
            }
        }

        project.task("convertDurToLab", type:StandardTask)
        {
            dependsOn "paramGeneration"

            output = "${project.buildDir}/alignment"
            outputs.files output

            doLast {
                def dur_regexp =  "(.*)\\.state\\[[0-9]*\\].*duration=([0-9]*) .*"
                withPool(project.configuration.nb_proc)
                {
                    def file_list = (new File(DataFileFinder.getFilePath(project.configuration.user_configuration.data.list_files))).readLines() as List
                    file_list.eachParallel { cur_file ->

                        project.configuration.user_configuration.models.cmp.streams.each { stream ->
                            new File("${project.buildDir}/gen_align/${cur_file}.${stream.kind}").delete()
                        }
                        def input = new File("${project.buildDir}/gen_align/${cur_file}.dur")
                        def output_lab = new File("${project.buildDir}/alignment/${cur_file}.lab")

                        output_lab.text = "";
                        def total_state = 5 // FIXME:
                        def id_state = 1
                        def t = 0
                        input.eachLine { line ->
                            line = line.trim()

                            if (id_state <= total_state)
                            {
                                // Retrieve infos
                                def pattern = ~dur_regexp
                                def matcher = pattern.matcher(line)

                                def label = matcher[0][1]
                                def nb_frames = Integer.parseInt(matcher[0][2])

                                // Compute start
                                def start = t * project.configuration.user_configuration.signal.frameshift * 1E+4
                                def end = (t + nb_frames) * project.configuration.user_configuration.signal.frameshift * 1E+4

                                // Output
                                def result = String.format("%d %d %s[%d]",
                                                           start.intValue(), end.intValue(), label, id_state+1)
                                if (id_state == 1)
                                {
                                    result += " " + label
                                }
                                output_lab << result + "\n"

                                t += nb_frames
                                id_state += 1
                            }
                            else
                            {
                                id_state = 1;
                            }
                        }
                    }
                }
            }
        }

        project.task("makeFeatures", type:StandardTask)
        {
            def alignment_lab = null
            if (System.getProperty("skipHMMTraining"))
            {
                dependsOn "convertDurToLab"
                alignment_lab = project.tasks.convertDurToLab.output
            }
            else
            {
                dependsOn "generateStateForceAlignment"
                alignment_lab = project.tasks.generateStateForceAlignment.output
            }

            def mkf_script_file = "$project.utils_dir/makefeature.pl";
            output = "$dnn_output_dir/ffi"

            def val = 1E+4 * project.configuration.user_configuration.signal.frameshift; // FIXME: why this frameshift

            outputs.files output
            doLast {
                def qconf = (new File(DataFileFinder.getFilePath(project.configuration.user_configuration.settings.dnn.qconf)));
                withPool(project.configuration.nb_proc)
                {
                    def file_list = (new File(DataFileFinder.getFilePath(project.configuration.user_configuration.data.list_files))).readLines() as List
                    file_list.eachParallel { cur_file ->
                        String command = "perl $mkf_script_file $qconf $val ${alignment_lab}/${cur_file}.lab | x2x +af > $output/${cur_file}.ffi".toString()
                        HTSWrapper.executeOnShell(command)
                    }
                }
            }
        }

        project.task("makeDNNSCP", type:StandardTask, dependsOn: "makeFeatures")
        {
            output = "$dnn_output_dir"
            doLast {
                def output_file = new File("$output/train_dnn.scp")
                output_file.text = ""
                def ffo_dir = project.buildDir.toString() + "/ffo" // TODO: properly dealing with that
                (new File(DataFileFinder.getFilePath(project.configuration.user_configuration.data.list_files))).eachLine { cur_file ->
                    output_file << "${project.tasks.makeFeatures.output}/${cur_file}.ffi $ffo_dir/${cur_file}.ffo" + System.getProperty("line.separator")
                }
            }

        }

        project.task("generateDNNConfig", type:StandardTask)
        {
            output = "$project.config_dir"
            doLast {

                def qconf = (new File(DataFileFinder.getFilePath(project.configuration.user_configuration.settings.dnn.qconf)));
                def nb_input_features = 0
                qconf.eachLine { line ->
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

        project.task("trainDNN", type:StandardTask)
        {
            dependsOn "makeDNNSCP", "generateDNNConfig", "computeVAR"

            def train_script_file = "$project.utils_dir/DNNTraining.py";
            inputs.files train_script_file

            output = new File("$dnn_output_dir/models")

            doLast {
                output.mkdirs()
                String command = "python -u -B $train_script_file -C ${project.tasks.generateDNNConfig.output}/train_dnn.cfg -S ${project.tasks.makeDNNSCP.output}/train_dnn.scp -H $output -z ${project.tasks.computeVAR.output}/global.var".toString()
                println(command)
                HTSWrapper.executeOnShell(command)
            }
        }
    }
}
