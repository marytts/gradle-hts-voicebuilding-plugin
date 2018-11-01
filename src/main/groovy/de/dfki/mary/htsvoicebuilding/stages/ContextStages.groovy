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


// Task imports
import de.dfki.mary.htsvoicebuilding.stages.task.context.*
import de.dfki.mary.htsvoicebuilding.stages.task.config.GenerateMLFTask
import de.dfki.mary.htsvoicebuilding.stages.task.config.GenerateListTask
import de.dfki.mary.htsvoicebuilding.stages.task.TrainModelsTask

class ContextStages
{
    public static void addTasks(Project project)
    {
        project.task('generateFullMLF', type: GenerateMLFTask) {
            description "Generate the master label file for the full context labels"

            // Inputs
            lab_dir = project.file(project.configuration.user_configuration.data.full_lab_dir)

            // Outputs
            mlf_file = project.file(project.configurationVoiceBuilding.full_mlf_filename)
        }

        project.task('generateFullList', type: GenerateListTask) {
            description "Generate the full context label list file"

            // Inputs
            lab_dir = project.file(project.configuration.user_configuration.data.full_lab_dir)
            list_basenames = project.file(project.configuration.user_configuration.data.list_files)

            // Outputs
            list_file = project.file(project.configurationVoiceBuilding.full_list_filename)
        }

        project.task('generateFullcontextFromMonophone', type: GenerateFullModelsTask) {
            description "Generate the fullcontext MMF files from the trained monophone MMF"

            // Lists
            mono_list_file = project.generateMonophoneList.list_file
            full_list_file = project.generateFullList.list_file

            // Mono
            mono_model_cmp_file = project.trainMonophoneMMF.trained_cmp_file
            mono_model_dur_file = project.trainMonophoneMMF.trained_dur_file

            // Full
            full_model_cmp_file = project.file("${project.configurationVoiceBuilding.cmp_model_dir}/fullcontext_0/init/fullcontext.mmf")
            full_model_dur_file = project.file("${project.configurationVoiceBuilding.dur_model_dir}/fullcontext_0/init/fullcontext.mmf")

            // Scripts
            m2f_script_file = project.file("${project.configurationVoiceBuilding.hhed_script_dir}/m2f.hed")
        }

        project.task('trainFullContext0', type: TrainModelsTask) {
            description "Train the full context model files (iteration 0)"

            // Disable daem as it is only for monophone
            use_daem = false

            // Configuration file
            scp_file = project.generateSCPFile.scp_file
            list_file = project.generateFullList.list_file
            mlf_file = project.generateFullMLF.mlf_file

            // Input models
            init_cmp_file = project.generateFullcontextFromMonophone.full_model_cmp_file
            init_dur_file = project.generateFullcontextFromMonophone.full_model_dur_file

            // Output models
            trained_cmp_file = project.file("${project.configurationVoiceBuilding.cmp_model_dir}/fullcontext_0/trained/fullcontext.mmf")
            trained_dur_file = project.file("${project.configurationVoiceBuilding.dur_model_dir}/fullcontext_0/trained/fullcontext.mmf")

            // Parameters
            options = ["-C", project.configurationVoiceBuilding.non_variance_config_filename, "-s", "${project.configurationVoiceBuilding.cmp_model_dir}/stats.0", "-w", 0.0]
        }


        project.configuration.user_configuration.settings.training.nb_clustering.times { cur_clus_it ->

            // Define some variables to compute stream boundaries
            def start_stream = 0
            def end_stream = 0
            def prev_stream = null


            /**********************************************************************************
             ** Parallel CMP clustering part
             **********************************************************************************/

            project.task("prepareCMPClustering_${cur_clus_it}", type: PrepareCMPTask) {
                fullcontext_model_file = project.property("trainFullContext${cur_clus_it}").trained_cmp_file;
                clustered_model_file = project.file("${project.configurationVoiceBuilding.cmp_model_dir}/fullcontext_${cur_clus_it}/init/clustered.mmf")
                output_flag = project.file("${project.configurationVoiceBuilding.cmp_model_dir}/fullcontext_${cur_clus_it}/init/prepare.flag")
            }

            project.configuration.user_configuration.models.cmp.streams.each { stream ->

                /*=================================================
                 # Compute stream boundaries
                 =================================================*/
                start_stream = end_stream + 1
                end_stream = start_stream
                if (stream.is_msd) {
                    end_stream += stream.winfiles.size() - 1
                }


                /*=================================================
                 # Define the needed tasks
                 =================================================*/
                project.task("clusteringCMPTo${stream.name}_${cur_clus_it}", type: ClusteringCMPTask) {
                    description "Cluster CMP part to ${stream.name} (iteration $cur_clus_it)"

                    // FIXME: refactor
                    dependsOn "generateMOCCCMPConfigurationFiles"

                    // Some variables (iteration id + stream)
                    local_cur_clus_it = cur_clus_it;
                    stream_name = stream.name
                    stream_thr = stream.thr
                    stream_gam = stream.gam
                    stream_mdlf = stream.mdlf
                    stream_start = start_stream
                    stream_end = end_stream

                    // Inputs
                    script_template_file = project.file("${project.configurationVoiceBuilding.template_dir}/cxc.hed");
                    list_file = project.generateFullList.list_file
                    stats_cmp_file = project.file("${project.configurationVoiceBuilding.cmp_model_dir}/stats.${cur_clus_it}")
                    question_file = project.file(project.configuration.user_configuration.data.question_file);
                    config_file = project.file("${project.configurationVoiceBuilding.config_dir}/${stream.name}.cfg") // FIXME: refactor using the task MOCC dependency

                    // Transitive file
                    if (prev_stream == null) {
                        transitive_model_file =  project.property("prepareCMPClustering_${cur_clus_it}").clustered_model_file
                        input_flag = project.property("prepareCMPClustering_${cur_clus_it}").output_flag
                    } else {
                        transitive_model_file =  project.property("joinClusteredTo${prev_stream.name}_${cur_clus_it}").clustered_model_file
                        input_flag = project.property("joinClusteredTo${prev_stream.name}_${cur_clus_it}").output_flag
                    }


                    // Outputs
                    def tmp_tree_files = []
                    for (i in 2..project.configuration.user_configuration.models.global.nb_emitting_states+1) {
                        def f = project.file("${project.configurationVoiceBuilding.tree_dir}/fullcontext_${cur_clus_it}/${stream.name}_${i}.inf")
                        tmp_tree_files.add(f)
                    }
                    tree_files.setFrom(tmp_tree_files)
                    tree_file_list = tmp_tree_files;


                    def model_files = []
                    for (i in 2..project.configuration.user_configuration.models.global.nb_emitting_states+1) {
                        def f = project.file("${project.configurationVoiceBuilding.cmp_model_dir}/fullcontext_${cur_clus_it}/init/${stream.name}_${i}.mmf")
                        model_files.add(f)
                    }
                    clustered_model_files.setFrom(model_files)
                    clustered_model_file_list = model_files
                }

                project.task("joinClusteredTo${stream.name}_${cur_clus_it}", type: JoinClusteredCMPTask) {
                    description "Join the clustered mmf model files to ${stream.name} (iteration $cur_clus_it)"

                    // FIXME: why do we need that?
                    dependsOn "clusteringCMPTo${stream.name}_${cur_clus_it}"

                    // Parameters
                    local_cur_clus_it = cur_clus_it;
                    stream_start = start_stream
                    stream_end = end_stream

                    // Inputs
                    list_file = project.generateFullList.list_file
                    script_file = project.file("${project.configurationVoiceBuilding.hhed_script_dir}/${cur_clus_it}/join_${stream.name}.hed")
                    clustered_cmp_files = project.property("clusteringCMPTo${stream.name}_${cur_clus_it}").clustered_model_files
                    def model_files = []
                    for (i in 2..project.configuration.user_configuration.models.global.nb_emitting_states+1) {
                        def f = project.file("${project.configurationVoiceBuilding.cmp_model_dir}/fullcontext_${cur_clus_it}/init/${stream.name}_${i}.mmf")
                        model_files.add(f)
                    }
                    clustered_cmp_file_list = model_files

                    // Transitive
                    clustered_model_file = project.property("clusteringCMPTo${stream.name}_${cur_clus_it}").transitive_model_file

                    // FIXME: output file flag
                    output_flag = project.file("$project.configurationVoiceBuilding.cmp_model_dir/fullcontext_$cur_clus_it/init/join_${stream.name}.flag")
                }

                // Save stream
                prev_stream = stream
            }

            project.task("clusteringDUR_${cur_clus_it}", type: ClusteringDURTask) {
                description "Cluster the duration part (iteration $cur_clus_it)"

                // FIXME: refactor
                dependsOn "generateMOCCDURConfigurationFile"

                // local_cur_clus_it = cur_clus_it;
                list_file = project.generateFullList.list_file
                script_template_file =  project.file("${project.configurationVoiceBuilding.template_dir}/cxc.hed");

                // Question file
                question_file = project.file(project.configuration.user_configuration.data.question_file)
                script_file = project.file("${project.configurationVoiceBuilding.hhed_script_dir}/cxc_dur.hed.${cur_clus_it}")
                tree_file = project.file("${project.configurationVoiceBuilding.tree_dir}/dur.${cur_clus_it}.inf")
                config_file = project.file("${project.configurationVoiceBuilding.config_dir}/dur.cfg")

                // Copy stats
                stats_cmp_file = project.file("${project.configurationVoiceBuilding.cmp_model_dir}/stats.$cur_clus_it")
                stats_dur_file = project.file("${project.configurationVoiceBuilding.dur_model_dir}/stats.$cur_clus_it")

                // Model paths
                fullcontext_model_file = project.property("trainFullContext${cur_clus_it}").trained_dur_file
                clustered_model_file = project.file("${project.configurationVoiceBuilding.dur_model_dir}/fullcontext_${cur_clus_it}/init/clustered.mmf")
            }

            project.task("trainClusteredModels_${cur_clus_it}", type: TrainModelsTask) {
                description "Train the clustered models (iteration $cur_clus_it)"

                def stream = project.configuration.user_configuration.models.cmp.streams.last()

                // FIXME: find a way to get rid of this
                dependsOn "joinClusteredTo${stream.name}_${cur_clus_it}"

                // Configuration files
                scp_file = project.generateSCPFile.scp_file
                list_file = project.generateFullList.list_file
                mlf_file = project.generateFullMLF.mlf_file

                // Init. model files
                init_cmp_file = project.property("joinClusteredTo${stream.name}_${cur_clus_it}").clustered_model_file
                init_dur_file = project.property("clusteringDUR_${cur_clus_it}").clustered_model_file

                // Trained model files
                trained_cmp_file = project.file("${project.configurationVoiceBuilding.cmp_model_dir}/fullcontext_${cur_clus_it}/trained/clustered.mmf")
                trained_dur_file = project.file("${project.configurationVoiceBuilding.dur_model_dir}/fullcontext_${cur_clus_it}/trained/clustered.mmf")
            }


            if (cur_clus_it  < project.configuration.user_configuration.settings.training.nb_clustering) {

                project.task("untyingCMP_${cur_clus_it}", type: UntyingCMPTask) {

                    description "Untie the CMP clustered models (iteration $cur_clus_it)"

                    // Inputs
                    list_file = project.generateFullList.list_file
                    input_model_file = project.property("trainClusteredModels_${cur_clus_it}").trained_cmp_file

                    // Outputs
                    untying_script_file = project.file("${project.configurationVoiceBuilding.hhed_script_dir}/untying_cmp.hhed")
                    output_model_file = project.file("${project.configurationVoiceBuilding.cmp_model_dir}/fullcontext_${cur_clus_it+1}/init/fullcontext.mmf")
                }


                project.task("untyingDUR_${cur_clus_it}", type:UntyingDURTask) {
                    description "Untie the duration clustered model (iteration $cur_clus_it)"

                    // Inputs
                    list_file = project.generateFullList.list_file
                    input_model_file = project.property("trainClusteredModels_${cur_clus_it}").trained_dur_file

                    // Outputs
                    untying_script_file = project.file("${project.configurationVoiceBuilding.hhed_script_dir}/untying_dur.hhed")
                    output_model_file = project.file("${project.configurationVoiceBuilding.dur_model_dir}/fullcontext_${cur_clus_it+1}/init/fullcontext.mmf")
                }

                project.task("trainFullContext${cur_clus_it+1}", type: TrainModelsTask) {
                    description "Train the fullcontext models (iteration ${cur_clus_it+1})"

                    // Configuration files
                    scp_file = project.generateSCPFile.scp_file
                    list_file = project.generateFullList.list_file
                    mlf_file = project.generateFullMLF.mlf_file

                    // Input model files
                    init_cmp_file = project.property("untyingCMP_${cur_clus_it}").output_model_file
                    init_dur_file = project.property("untyingDUR_${cur_clus_it}").output_model_file

                    // Output model files
                    trained_cmp_file = project.file("${project.configurationVoiceBuilding.cmp_model_dir}/fullcontext_${(cur_clus_it+1)}/trained/fullcontext.mmf")
                    trained_dur_file = project.file("${project.configurationVoiceBuilding.dur_model_dir}/fullcontext_${(cur_clus_it+1)}/trained/fullcontext.mmf")

                    // Parameters
                    options = ["-C", project.configurationVoiceBuilding.non_variance_config_filename.toString(),
                               "-s", "${project.configurationVoiceBuilding.cmp_model_dir}/stats.${cur_clus_it+1}",
                               "-w", 0.0]
                }
            }
        }
    }
}
